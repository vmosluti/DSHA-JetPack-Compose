package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luti.dshlauncher.ui.dsha.observeAsState
import com.deepseekharness.app.backup.AndroidBackupFileSystem
import com.deepseekharness.app.backup.BackupArchive
import com.deepseekharness.app.backup.BackupControl
import com.deepseekharness.app.backup.BackupErrorCode
import com.deepseekharness.app.backup.HostOperationArchive
import com.deepseekharness.app.backup.ProfileSettingsTransaction
import com.deepseekharness.app.backup.RetainedCatalogue
import com.deepseekharness.app.backup.UserDataLayout
import com.deepseekharness.app.backup.VerifiedBackupCopy
import com.deepseekharness.app.core.PluginRepository
import com.deepseekharness.app.core.RuntimeTasks
import com.deepseekharness.app.util.UiStateText
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DshaActionDialog
import com.luti.dshlauncher.ui.dsha.DshaCard
import com.luti.dshlauncher.ui.dsha.DshaCardButton
import com.luti.dshlauncher.ui.dsha.DshaContentDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaPageScaffold
import com.luti.dshlauncher.ui.dsha.DshaSecondaryButton
import com.luti.dshlauncher.ui.dsha.DshaSelectableText
import com.luti.dshlauncher.ui.dsha.DshaSwitchRow
import com.luti.dshlauncher.ui.dsha.setDshaContent
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 可识别原件的只读管理（Compose 版）；批量检查逐项报告，不提供自动或批量删除。
 * 分页游标、插件预览延后/重试、profile 设置隔离验证的语义与原 Java 版一致。
 */
class RetainedDataActivity : androidx.fragment.app.FragmentActivity() {

    class Model : ViewModel() {
        val selected = LinkedHashMap<String, RetainedCatalogue.Entry>()
        val entries = MutableLiveData<RetainedCatalogue.Page?>()
        val report = MutableLiveData("")
        val working = AtomicBoolean()
        val starts = ArrayList<RetainedCatalogue.Cursor?>().apply { add(null) }
        var control: BackupControl? = null
        var page = 0
        var requestId = 0L
        var pluginAction = false
        var deferredPreviewId: String? = null

        override fun onCleared() { control?.cancel() }
    }

    private sealed interface Dialog {
        data class Details(val entry: RetainedCatalogue.Entry) : Dialog
        data class ReviewSettings(val entry: RetainedCatalogue.Entry) : Dialog
        data class SettingsDiff(val result: Map<String, Any?>) : Dialog
        data class PluginPreview(val preview: PluginRepository.Preview) : Dialog
        data class RetainedPlugin(val preview: PluginRepository.Preview, val detail: String) : Dialog
    }

    private lateinit var model: Model
    private lateinit var plugins: PluginRepository
    private var dialog by mutableStateOf<Dialog?>(null)
    /** 已勾选条目的 key，驱动复选状态重组；真实条目在 model.selected。 */
    private val checkedKeys = mutableStateListOf<String>()

    private fun catalogue(): RetainedCatalogue {
        val fs = AndroidBackupFileSystem()
        val files = filesDir.canonicalFile
        return RetainedCatalogue(fs, files, UserDataLayout(fs, files).current())
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        model = ViewModelProvider(this)[Model::class.java]
        plugins = ViewModelProvider(this)[PluginRepository::class.java]
        checkedKeys.addAll(model.selected.keys)

        setDshaContent { Page() }

        if (model.entries.value == null) refresh()
        plugins.state().observe(this) { state ->
            if (model.pluginAction && state != null && !state.message.isNullOrEmpty()) {
                val retained = plugins.preview().value
                val deferred = retained != null && retained.id == model.deferredPreviewId && !state.busy
                model.report.value = UiStateText.render(state.message) + if (deferred) {
                    t("\n清理未确认；预览已保留。点此重试或稍后处理。", "\nCleanup is unconfirmed; the preview is retained. Tap to retry or handle it later.")
                } else ""
            }
            // 等 finishTask 应用 onSuccess 后再弹；清理失败会保留预览。
            if (model.pluginAction && state != null && !state.busy) window.decorView.post { showPluginPreviewIfReady() }
        }
        plugins.preview().observe(this) { preview ->
            if (preview == null) {
                model.deferredPreviewId = null
                val state = plugins.state().value
                if (model.pluginAction && state != null && !state.busy && state.message != null) {
                    model.report.value = UiStateText.render(state.message)
                }
            }
            showPluginPreviewIfReady()
        }
    }

    // ---------------- 页面 ----------------

    @Composable
    private fun Page() {
        val page by model.entries.observeAsState()
        val rawReport by model.report.observeAsState("")
        val report = remember(rawReport) {
            rawReport.orEmpty().split("\n").joinToString("\n") { UiStateText.render(it) }
        }
        DshaPageScaffold(
            title = t("保留副本与旧树", "Retained copies and old trees"),
            subtitle = t(
                "原件保持只读。记录中的成功状态不是本次重新验证；检查、导出与恢复分别处理。未知或受损原件继续保留。",
                "Originals remain read-only. A recorded success is not a fresh verification. Inspect, export and restore are separate operations. Unknown or damaged originals remain retained.",
            ),
            onBack = { finish() },
        ) {
            item {
                DshaCard {
                    DshaCardButton(t("刷新清单", "Refresh list")) {
                        if (!model.working.get()) {
                            model.page = 0
                            model.starts.clear(); model.starts.add(null)
                            refresh()
                        }
                    }
                    DshaCardButton(t("检查所选记录", "Inspect selected records")) { inspect() }
                    DshaCardButton(t("取消检查", "Cancel inspection")) { model.control?.cancel() }
                }
            }
            if (report.isNotBlank()) item {
                DshaCard(Modifier.clickable { showRetainedPluginOptions() }) { DshaSelectableText(report) }
            }
            val current = page
            if (current != null) {
                val first = model.page.toLong() * current.size
                val last = first + current.entries.size
                item {
                    DshaNote(
                        t("已显示 ", "Showing ") + (if (current.total == 0) 0 else first + 1) + "–" + last + " / " +
                            current.total + t(" 条 · 第 ", " records · Page ") + (model.page + 1),
                    )
                }
                if (current.entries.isEmpty()) {
                    item { DshaNote(t("暂无保留副本。", "No retained copies.")) }
                }
                current.entries.forEach { entry ->
                    item(key = entry.key() + "|" + entry.directory.absolutePath) {
                        val duplicate = entry.status == "DUPLICATE"
                        DshaCard {
                            DshaSwitchRow(
                                title = label(entry),
                                checked = !duplicate && entry.key() in checkedKeys,
                                enabled = !duplicate,
                                onCheckedChange = { on -> toggle(entry, on) },
                            )
                            DshaCardButton(t("查看与操作", "Details and actions")) { dialog = Dialog.Details(entry) }
                        }
                    }
                }
                if (model.page > 0) item {
                    DshaSecondaryButton(t("上一页", "Previous page")) {
                        if (!model.working.get()) { model.page--; refresh() }
                    }
                }
                if (current.hasNext()) item {
                    DshaSecondaryButton(t("下一页", "Next page")) {
                        if (!model.working.get()) {
                            if (model.starts.size > model.page + 1) model.starts[model.page + 1] = current.next
                            else model.starts.add(current.next)
                            model.page++
                            refresh()
                        }
                    }
                }
            }
        }
        Dialogs()
    }

    private fun toggle(entry: RetainedCatalogue.Entry, on: Boolean) {
        if (on) {
            model.selected[entry.key()] = entry
            if (entry.key() !in checkedKeys) checkedKeys.add(entry.key())
        } else {
            model.selected.remove(entry.key())
            checkedKeys.remove(entry.key())
        }
    }

    @Composable
    private fun Dialogs() {
        val close = { dialog = null }
        when (val current = dialog) {
            null -> Unit
            is Dialog.Details -> DetailsDialog(current.entry)
            is Dialog.ReviewSettings -> DshaActionDialog(
                title = t("检查 profile 设置", "Inspect profile settings"),
                message = t(
                    "将停止 Web、终端和写任务，在独立服务中读取当前 schema 并验证候选；活动设置在确认差异前保持原位。可执行配置和插件构成继续隔离。",
                    "Web, terminals and writers will stop. An isolated service will validate the candidate against the current schema. Active settings remain unchanged until you confirm the differences; executable configuration and plugin composition stay quarantined.",
                ),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("开始检查", "Inspect")) {
                        val key = current.entry.key()
                        settingsWork(preview = true) { ProfileSettingsTransaction.preview(this, key, model.control) }
                    },
                ),
            )
            is Dialog.SettingsDiff -> SettingsDiffDialog(current.result)
            is Dialog.PluginPreview -> {
                val preview = current.preview
                val blocked = remember(preview.id) { preview.blocked() }
                val actions = buildList {
                    add(DshaDialogAction(t("稍后处理", "Handle later")) { deferPluginPreview(preview) })
                    add(DshaDialogAction(t("取消", "Cancel")) { discardShownPluginPreview(preview) })
                    // 存在冲突或缺失依赖时不提供确认启用（对应原禁用的确认按钮）。
                    if (!blocked) add(DshaDialogAction(t("确认启用", "Confirm enable")) { plugins.confirmPreview() })
                }
                DshaActionDialog(
                    title = t("审阅隔离插件", "Review quarantined plugin"),
                    message = preview.description(),
                    visible = true,
                    // 仅返回键/点外部视为“稍后处理”；按钮点击只关闭，动作各自执行。
                    onDismiss = { close(); deferPluginPreview(preview) },
                    actions = actions,
                    onClose = close,
                )
            }
            is Dialog.RetainedPlugin -> DshaActionDialog(
                title = t("保留的插件预览", "Retained plugin preview"),
                message = current.detail.ifEmpty { t("预览仍在；请选择后续处理。", "The preview remains. Choose what to do next.") },
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("稍后处理", "Handle later")) {},
                    DshaDialogAction(t("重试清理", "Retry cleanup")) {
                        model.deferredPreviewId = current.preview.id
                        plugins.discardPreview()
                    },
                    DshaDialogAction(t("重新打开预览", "Reopen preview")) {
                        model.deferredPreviewId = null
                        window.decorView.post { showPluginPreviewIfReady() }
                    },
                ),
            )
        }
    }

    @Composable
    private fun DetailsDialog(entry: RetainedCatalogue.Entry) {
        val scope = when (entry.scope) {
            "application" -> t("应用数据", "Application data")
            "sessions" -> t("对话与附件", "Conversations and attachments")
            "settings" -> t("设置", "Settings")
            "plugins" -> t("插件", "Plugins")
            "projects" -> t("项目文件", "Project files")
            else -> t("范围未确认", "Scope unconfirmed")
        }
        val protection = when (entry.protection) {
            "VERIFY_BEFORE_EXPORT_OR_RESTORE" -> t("操作前重新核对加密文件摘要", "Recheck the encrypted file checksum before use")
            "REVIEW_REQUIRED" -> t("启用前需要审阅", "Review required before activation")
            "READ_ONLY_RESCUE_NO_AUTOMATIC_DELETION" -> t("只读救援来源", "Read-only rescue source")
            else -> t("原件或记录保持保留", "Originals or records remain retained")
        }
        val detail = label(entry) + "\n\n" + t("范围：", "Scope: ") + scope + "\n" + protection + "\n\n" +
            entry.directory.absolutePath + "\n\n" +
            t("不会执行保留目录中的程序，也不会自动删除原件。", "Programs in the retained directory will not run, and originals will not be deleted automatically.")
        val encrypted = entry.part == "encrypted"
        val actions = buildList {
            add(DshaDialogAction(t("关闭", "Close")) {})
            if (entry.source != null && entry.status != "DUPLICATE") {
                add(DshaDialogAction(t("导出", "Export")) { open(entry, if (encrypted) "reexport" else "export-tree") })
                when (entry.kind) {
                    RetainedCatalogue.Kind.SETTINGS -> add(DshaDialogAction(t("检查设置差异", "Review settings differences")) {
                        dialog = Dialog.ReviewSettings(entry)
                    })
                    RetainedCatalogue.Kind.PRESET -> add(DshaDialogAction(t("检查并审阅预设", "Inspect and review preset")) {
                        model.pluginAction = true
                        plugins.reviewLegacyPreset(entry.key())
                    })
                    RetainedCatalogue.Kind.MIGRATION -> Unit // 迁移记录只读，仅可导出
                    RetainedCatalogue.Kind.QUARANTINE -> add(DshaDialogAction(t("审阅启用", "Review activation")) {
                        model.pluginAction = true
                        plugins.reviewRestored(entry.id, entry.part)
                    })
                    else -> add(DshaDialogAction(t("预检恢复", "Inspect restore")) {
                        open(entry, if (encrypted) "restore-copy" else "restore-tree")
                    })
                }
            }
        }
        // onClose 先关闭本详情，动作可能再打开下一个对话框。
        DshaActionDialog(
            title = t("保留记录", "Retained record"),
            message = detail,
            visible = true,
            onDismiss = { dialog = null },
            actions = actions,
        )
    }

    @Composable
    private fun SettingsDiffDialog(result: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val items = (result["items"] as? List<Map<String, Any?>>).orEmpty()
        val selected = remember(result) { mutableStateListOf(*Array(items.size) { false }) }
        DshaContentDialog(
            title = t("已验证的设置差异", "Validated settings differences"),
            visible = true,
            onDismiss = { dialog = null },
            onClose = {},
            actions = listOf(
                DshaDialogAction(t("保留待处理", "Keep pending")) { dialog = null },
                DshaDialogAction(t("应用所选并读回", "Apply selection and read back")) {
                    val keys = LinkedHashSet<String>()
                    items.forEachIndexed { i, item -> if (selected[i]) keys.add(item["id"].toString()) }
                    if (keys.isEmpty()) return@DshaDialogAction
                    dialog = null
                    val operation = result["operation"] as String
                    settingsWork(preview = false) { ProfileSettingsTransaction.apply(this, operation, keys, model.control) }
                },
            ),
        ) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DshaNote(
                    t("Profile：", "Profile: ") + result["profile"] + "\n" +
                        t("请选择需要恢复的字段；未选字段保持当前值。", "Select fields to restore. Unselected fields retain their current values.") +
                        "\n" + result["warnings"].toString(),
                )
                items.forEachIndexed { i, item ->
                    DshaSwitchRow(
                        title = item["id"].toString(),
                        summary = item["before"].toString() + "\n→ " + item["after"],
                        checked = selected[i],
                        onCheckedChange = { selected[i] = it },
                    )
                }
            }
        }
    }

    // ---------------- 插件预览 ----------------

    private fun showPluginPreviewIfReady() {
        if (!model.pluginAction || plugins.isBusy || dialog is Dialog.PluginPreview || isFinishing || isDestroyed) return
        val preview = plugins.preview().value ?: return
        if (preview.id == model.deferredPreviewId) return
        dialog = Dialog.PluginPreview(preview)
    }

    private fun deferPluginPreview(preview: PluginRepository.Preview) {
        model.deferredPreviewId = preview.id
        model.report.value = t(
            "插件预览已保留。点此重新打开、重试清理或稍后处理。",
            "The plugin preview is retained. Tap to reopen, retry cleanup, or handle it later.",
        )
    }

    private fun discardShownPluginPreview(preview: PluginRepository.Preview) {
        model.deferredPreviewId = preview.id
        plugins.discardPreview()
    }

    private fun showRetainedPluginOptions() {
        val retained = plugins.preview().value
        if (!model.pluginAction || retained == null || retained.id != model.deferredPreviewId || plugins.isBusy) return
        val state = plugins.state().value
        val detail = if (state == null) "" else UiStateText.render(state.message)
        dialog = Dialog.RetainedPlugin(retained, detail)
    }

    // ---------------- 逻辑（与原 Java 版一致） ----------------

    private fun refresh() {
        if (!model.working.compareAndSet(false, true)) return
        val requested = model.starts[model.page]
        val request = ++model.requestId
        Thread({
            try {
                val catalog = catalogue()
                var reset = false
                val result = try {
                    catalog.page(requested, 50)
                } catch (changed: IOException) {
                    if (changed.message != "RETAINED_PAGE_CHANGED") throw changed
                    reset = true
                    catalog.page(null, 50)
                }
                runOnUiThread {
                    if (request != model.requestId) { model.working.set(false); return@runOnUiThread }
                    if (reset) {
                        model.page = 0
                        model.starts.clear(); model.starts.add(null)
                        model.report.value = t("历史清单已变化，已刷新到第一页。", "History changed; refreshed to the first page.")
                    }
                    // 重复 ID 的条目不允许参与批量检查。
                    result.entries.filter { it.status == "DUPLICATE" }.forEach { dup ->
                        val owner = dup.kind.name + ":" + dup.id + ":"
                        model.selected.keys.removeIf { it.startsWith(owner) }
                        checkedKeys.removeAll { it.startsWith(owner) }
                    }
                    model.entries.value = result
                    model.working.set(false)
                }
            } catch (error: Exception) {
                val message = UiText.text("保留记录无法读取，原件未改动。") + "\n" + BackupErrorCode.from(error)
                runOnUiThread {
                    if (request == model.requestId) model.report.value = message
                    model.working.set(false)
                }
            }
        }, "retained-list").start()
    }

    private fun label(entry: RetainedCatalogue.Entry): String {
        val kind = when (entry.kind) {
            RetainedCatalogue.Kind.BACKUP -> t("加密副本", "Encrypted copy")
            RetainedCatalogue.Kind.ENVIRONMENT -> t("旧环境数据", "Old environment data")
            RetainedCatalogue.Kind.RUNTIME -> t("运行时原件", "Runtime original")
            RetainedCatalogue.Kind.RESTORE -> t("恢复原件", "Restore original")
            RetainedCatalogue.Kind.PLUGIN -> t("插件原件", "Plugin original")
            RetainedCatalogue.Kind.QUARANTINE -> t("隔离插件", "Quarantined plugin")
            RetainedCatalogue.Kind.SETTINGS -> t("待恢复 profile 设置", "Profile settings to restore")
            RetainedCatalogue.Kind.PRESET -> t("旧 Agent 预设候选", "Legacy Agent preset candidate")
            RetainedCatalogue.Kind.MIGRATION -> t("rc1 迁移记录", "rc1 migration records")
            null -> t("原件", "Original")
        }
        val state = when (entry.status) {
            "COMMITTED" -> t("原操作已提交", "Original operation committed")
            "PENDING_RETRY" -> t("迁移待重试或待处理", "Migration pending retry or review")
            "ROLLED_BACK" -> t("原操作已回切", "Original operation rolled back")
            "RECOVERY_REQUIRED" -> t("需要恢复中断操作", "Interrupted operation needs recovery")
            "DUPLICATE" -> t("同 ID 重复原件；操作拒绝", "Duplicate ID; actions blocked")
            "RECORDED_VERIFIED_COPY" -> t("已记录验证，操作前复核", "Previously verified; recheck before use")
            "UNREADABLE", "UNRECOGNIZED" -> t("来源或记录未确认", "Source or record unconfirmed")
            else -> t("原件保留", "Original retained")
        }
        val origin = if (entry.directory.parentFile?.name == "completed") t("完成历史", "Completed history") else t("活动目录", "Active directory")
        return kind + " · " + entry.id.take(8) + " · " + entry.displayName + "\n" + origin + " · " + state + " · " +
            t("不自动删除", "No automatic deletion")
    }

    private fun open(entry: RetainedCatalogue.Entry, action: String) {
        startActivity(
            Intent(this, NativeDataActivity::class.java)
                .putExtra("retained_key", entry.key())
                .putExtra("retained_action", action),
        )
    }

    private fun settingsWork(preview: Boolean, work: () -> Map<String, Any?>) {
        if (!model.working.compareAndSet(false, true)) return
        model.control = BackupControl(null)
        model.report.value = t("正在隔离验证设置；可使用取消检查。", "Validating settings in isolation; Cancel inspection is available.")
        Thread({
            try {
                val result = work()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (preview) {
                        dialog = Dialog.SettingsDiff(result)
                    } else {
                        model.report.value = t(
                            "所选设置已事务提交并由实际设置服务读回确认。其它配置和原件保留；可重新进入 Web。",
                            "Selected settings were committed and read back by the actual settings service. Other configuration and originals remain; you can reopen Web.",
                        )
                    }
                }
            } catch (error: Exception) {
                model.report.postValue(
                    t("设置操作未完成，原件保留。可重新检查后继续。\n", "Settings operation incomplete; originals retained. Inspect again to continue.\n") +
                        BackupErrorCode.from(error),
                )
            } finally {
                model.working.set(false)
            }
        }, "profile-settings-review").start()
    }

    private fun inspect() {
        val selected = LinkedHashMap(model.selected)
        if (selected.isEmpty() || !model.working.compareAndSet(false, true)) return
        val control = BackupControl(null)
        model.control = control
        model.report.value = t("正在检查所选原件…", "Inspecting selected originals…")
        val filesRoot = filesDir
        Thread({
            val results = StringBuilder()
            var passed = 0
            var failed = 0
            var lastReported = 0
            var lastProgress = SystemClock.elapsedRealtime()
            try {
                RuntimeTasks.begin().use {
                    val catalog = catalogue()
                    val fs = AndroidBackupFileSystem()
                    val checked = catalog.resolveAll(selected.keys)
                    for (key in selected.keys) {
                        if (control.isCancelled) {
                            results.append(UiText.text("检查已取消，尚未检查的条目保持原状。"))
                            break
                        }
                        try {
                            checked.errors[key]?.let { throw IOException(it) }
                            val entry = checked.entries[key]
                            if (!RetainedCatalogue.sameListing(selected[key], entry)) throw IOException("RETAINED_SOURCE_CHANGED")
                            entry!!
                            if (entry.part == "encrypted") {
                                VerifiedBackupCopy.inspect(fs, HostOperationArchive.root(File(filesRoot.canonicalPath)), entry.id)
                                    .verify(fs, control)
                            } else if (entry.source != null) {
                                for (source in catalog.inspectionSources(checked, key)) {
                                    source.walk({ item ->
                                        if (item.kind == "MISSING" || item.kind == "UNREADABLE") throw IOException("RETAINED_SOURCE_PARTIAL")
                                        if (item.kind == "FILE") {
                                            val hash = source.open(item).use { input -> BackupArchive.digest(input, control) }
                                            source.verify(item, hash, control)
                                        }
                                    }, control)
                                }
                            } else {
                                throw IOException("RECORD_ONLY_NOT_A_VERIFIED_COPY")
                            }
                            passed++
                            results.append(key).append(" · ").append(
                                if (entry.part == "encrypted") t("摘要与验证记录一致", "Checksum matches the verification record")
                                else t("本次读取一致；没有据此确认历史格式或完整性", "Reads are consistent; historical format/integrity remains unconfirmed"),
                            ).append('\n')
                        } catch (error: Exception) {
                            failed++
                            results.append(key).append(" · ").append(BackupErrorCode.from(error)).append('\n')
                        }
                        val processed = passed + failed
                        val now = SystemClock.elapsedRealtime()
                        if (processed - lastReported >= 25 || now - lastProgress >= 500) {
                            model.report.postValue(
                                t("已检查 ", "Inspected ") + processed + " / " + selected.size +
                                    t("；通过 ", "; passed ") + passed + t("；未通过 ", "; failed ") + failed,
                            )
                            lastReported = processed
                            lastProgress = now
                        }
                    }
                }
            } catch (error: Exception) {
                results.append(BackupErrorCode.from(error))
            } finally {
                if (results.isNotEmpty() && results.last() != '\n') results.append('\n')
                results.append(UiStateText.render("检查通过：$passed；未通过或未完整读取：$failed"))
                model.report.postValue(results.toString())
                model.working.set(false)
            }
        }, "retained-inspection").start()
    }

    private companion object {
        fun t(zh: String, en: String): String = UiText.choose(zh, en)
    }
}