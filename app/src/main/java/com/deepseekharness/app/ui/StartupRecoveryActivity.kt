package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.luti.dshlauncher.ui.dsha.observeAsState
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.StartupRecoveryModel
import com.deepseekharness.app.core.StartupRepairs
import com.deepseekharness.app.util.BuiltinPlugins
import com.deepseekharness.app.util.StartupHistoryStore
import com.deepseekharness.app.util.StartupText
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DshaActionDialog
import com.luti.dshlauncher.ui.dsha.DshaCard
import com.luti.dshlauncher.ui.dsha.DshaCardButton
import com.luti.dshlauncher.ui.dsha.DshaCardTitle
import com.luti.dshlauncher.ui.dsha.DshaChoiceSheet
import com.luti.dshlauncher.ui.dsha.DshaContentDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaPageScaffold
import com.luti.dshlauncher.ui.dsha.DshaSelectableText
import com.luti.dshlauncher.ui.dsha.setDshaContent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Web 无法加载时仍可使用的原生恢复工作台（Compose 版）。
 * 修复执行权仍归应用级 [BackupTask]；读取由 [StartupRecoveryModel] 承担。
 */
class StartupRecoveryActivity : androidx.fragment.app.FragmentActivity() {

    private sealed interface Dialog {
        data class Confirm(val title: String, val detail: String, val work: () -> Unit) : Dialog
        data object NewConfiguration : Dialog
        data class Attempt(val entry: StartupHistoryStore.Entry) : Dialog
    }

    private lateinit var controller: HarnessController
    private lateinit var model: StartupRecoveryModel
    private val ui = Handler(Looper.getMainLooper())
    private var previousTask = ""
    private var wasBusy = false

    private var dialog by mutableStateOf<Dialog?>(null)
    private var status by mutableStateOf("")
    /** 受保护动作（修复/恢复/卸载）在任务进行中禁用，对应原 StartupRecoveryLayout.enabled。 */
    private var busy by mutableStateOf(false)

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        controller = HarnessController.get(this)
        if (!controller.config().isWelcomed) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        var reason = controller.config().webFailureReason
        if (reason.isEmpty()) reason = controller.startupDiagnostics().snapshot().stage
        val shownReason = StartupText.render(reason)
        if (!controller.proot().hasBash()) {
            status = t(
                "运行环境的 Bash、启动链接或加载器缺失。请先修复运行环境；对话和配置会单独保护。",
                "Bash, its startup link or loader is missing. Repair the runtime first; conversations and configuration will be protected separately.",
            )
        }
        model = ViewModelProvider(this)[StartupRecoveryModel::class.java]
        val initial = BackupTask.get(this).snapshot()
        previousTask = "${initial.id}:${initial.status}:${initial.detail}"
        model.error.observe(this) { value -> if (!value.isNullOrEmpty()) status = readable(value) }
        if (model.contents.value == null) model.load()

        setDshaContent { Page(shownReason) }
    }

    // ---------------- 页面 ----------------

    @Composable
    private fun Page(reason: String) {
        val contents by model.contents.observeAsState()
        // error 变化时 attempts 也已刷新（与原 renderAttempts 时机一致）。
        model.error.observeAsState()
        val bashMissing = !controller.proot().hasBash()

        DshaPageScaffold(
            title = t("启动恢复", "Startup recovery"),
            subtitle = t("进入原因：", "Reason: ") + reason + "\n" + t(
                "此页面不依赖 Web。可查看最近启动记录、恢复配置快照，或卸载出错的插件。",
                "This page works independently of Web. Review recent starts, restore configuration snapshots, or remove faulty plugins.",
            ),
            onBack = { finish() },
        ) {
            if (status.isNotEmpty()) item { DshaCard { DshaSelectableText(status) } }

            item {
                DshaCard {
                    DshaCardTitle(t("恢复与修复", "Recovery & repair"))
                    DshaNote(t("通过以下操作尝试解决启动问题", "Try these actions to resolve startup problems"))
                    DshaCardButton(t("启动独立应急 DSH", "Start independent emergency DSH")) {
                        startActivity(Intent(this@StartupRecoveryActivity, RecoveryActivity::class.java))
                    }
                    if (bashMissing) {
                        DshaCardButton(t("保护数据并修复运行环境", "Protect data and repair runtime"), primary = true) { openExtract() }
                    }
                    DshaCardButton(t("重试启动", "Retry startup"), enabled = !busy) { retry(false) }
                    DshaCardButton(t("安全启动基础界面", "Start the basic interface in safe mode"), enabled = !busy) { retry(true) }
                    DshaCardButton(t("新建配置文件", "Create configuration file"), enabled = !busy) { dialog = Dialog.NewConfiguration }
                    DshaCardButton(t("恢复中断的配置修复", "Recover interrupted configuration repair"), enabled = !busy) {
                        confirm(
                            t("恢复修复前配置", "Restore the configuration from before the repair"),
                            t("将使用修复前的快照回退中断的配置写入，然后才能重新启动。", "Roll back the interrupted configuration changes using the saved pre-repair copy before restarting."),
                        ) { repair(request("recover")) }
                    }
                }
            }

            item {
                DshaCard {
                    DshaCardTitle(t("其他工具", "Other tools"))
                    DshaNote(t("更多高级选项，帮助排查和解决问题", "More options to diagnose and resolve problems"))
                    DshaCardButton(t("宿主备份与只读救援", "Host backup and read-only rescue")) {
                        startActivity(Intent(this@StartupRecoveryActivity, NativeDataActivity::class.java))
                    }
                    DshaCardButton(t("回退兼容运行时", "Roll back compatible runtime"), enabled = !busy) {
                        RuntimeRecoveryUi.show(this@StartupRecoveryActivity)
                    }
                    DshaCardButton(t("环境安装与修复", "Environment installation and repair")) { openExtract() }
                    DshaCardButton(t("查看并下载日志", "View and download logs")) {
                        startActivity(DiagnosticActivity.downloadLogs(this@StartupRecoveryActivity))
                    }
                    DshaCardButton(t("刷新恢复记录", "Refresh recovery records")) { model.load() }
                }
            }

            item {
                DshaCard {
                    DshaCardTitle(t("最近五次启动", "Last five starts"))
                    val attempts = model.attempts
                    if (attempts.isEmpty()) {
                        val error = controller.startupDiagnostics().historyError()
                        DshaNote(
                            if (error.isEmpty()) t("还没有启动记录。下一次启动会自动记录。", "No startup records yet. The next start will be recorded automatically.")
                            else t("启动记录暂不可用：", "Startup records are currently unavailable: ") + error,
                        )
                    }
                    attempts.forEach { entry ->
                        DshaCardButton(date(entry.started) + " · " + attemptStatus(entry.status) + " · " + entry.elapsed / 1000 + "s") {
                            dialog = Dialog.Attempt(entry)
                        }
                    }
                }
            }

            val value = contents
            if (value != null) {
                item { SnapshotCard(value) }
                pluginCard(value)
            }
        }
        Dialogs()
    }

    @Composable
    private fun SnapshotCard(value: JSONObject) {
        DshaCard {
            DshaCardTitle(t("配置快照", "Configuration snapshots"))
            DshaNote(
                t(
                    "保留三次健康启动与三次修复前快照；仅保存配置，不复制会话、工作区或依赖目录。",
                    "Keeps three healthy-start and three pre-repair snapshots. Saves configuration only, without sessions, workspaces, or dependency directories.",
                ),
            )
            val records = value.optJSONArray("snapshots") ?: JSONArray()
            for (i in 0 until records.length()) {
                val record = records.optJSONObject(i) ?: continue
                if (record.optBoolean("invalid")) {
                    DshaNote(t("快照损坏，已禁止恢复：", "Snapshot is damaged and cannot be restored: ") + record.optString("slot"))
                    continue
                }
                val healthy = record.optBoolean("healthy", record.optString("slot").startsWith("healthy"))
                val label = (if (healthy) t("健康启动", "Healthy start") else t("修复之前", "Before repair")) + " · " + date(record.optLong("created"))
                DshaCardButton(label, enabled = !busy) {
                    val request = request("restore").put("slot", record.optString("slot")).put("id", record.optString("id"))
                    val missingList = record.optJSONArray("missing")
                    val missing = if (missingList != null && missingList.length() > 0) {
                        t("\n快照中原本不存在，将恢复为缺失：", "\nAbsent in this snapshot; restore to absence: ") + missingList
                    } else ""
                    confirm(
                        t("恢复所选配置快照", "Restore selected configuration snapshot"),
                        label + "\n" + record.optJSONArray("files") + missing +
                            t("\n只恢复这些配置文件；已卸载的插件不会自动重新安装。", "\nRestore only these configuration files. Removed plugins are not automatically reinstalled."),
                    ) { repair(request) }
                }
            }
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.pluginCard(value: JSONObject) {
        val items = value.optJSONArray("items") ?: JSONArray()
        val names = ArrayList<String>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (!item.optBoolean("deletable")) continue
            val name = item.optString("name")
            if (BuiltinPlugins.internal(name)) continue
            names.add(name)
        }
        val pluginError = value.optString("pluginError")
        if (names.isEmpty() && pluginError.isEmpty()) return
        item {
            DshaCard {
                DshaCardTitle(t("插件卸载", "Remove plugins"))
                names.forEach { name ->
                    DshaCardButton(name + " · " + t("卸载", "Remove"), enabled = !busy) {
                        confirm(
                            t("卸载插件", "Remove plugin"),
                            name + t("\n仅卸载所选插件，使用现有插件管理器核验结果。", "\nRemove only the selected plugin and verify the result with the plugin manager."),
                        ) {
                            if (!BackupTask.get(this@StartupRecoveryActivity).removeStartupPlugin(name)) status = readable("RECOVERY_BUSY")
                        }
                    }
                }
                if (pluginError.isNotEmpty()) {
                    DshaNote(t("插件清单暂不可读；可先恢复或新建配置。\n", "Cannot read the plugin list. Restore or create configuration first.\n") + readable(pluginError))
                }
            }
        }
    }

    @Composable
    private fun Dialogs() {
        val close = { dialog = null }
        when (val current = dialog) {
            null -> Unit
            is Dialog.Confirm -> DshaActionDialog(
                title = current.title,
                message = current.detail + t("\n\n操作会停止 Web 和终端，并保留修复前配置。", "\n\nThis stops Web and terminal sessions and saves the configuration before making changes."),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("继续", "Continue")) { current.work() },
                ),
            )
            Dialog.NewConfiguration -> {
                val names = listOf(
                    t("Web 启动配置", "Web startup configuration"),
                    t("共享设置（settings.yaml）", "Shared settings (settings.yaml)"),
                    t("共享补丁（cordis.patch.yml）", "Shared patch (cordis.patch.yml)"),
                )
                val targets = listOf("web", "settings.yaml", "cordis.patch.yml")
                DshaChoiceSheet(
                    title = t("选择新建的配置文件", "Choose the configuration to create"),
                    items = names,
                    visible = true,
                    onDismiss = close,
                    onSelect = { index ->
                        val request = request("new").put("target", targets[index])
                        confirm(
                            names[index],
                            t(
                                "将以默认内容替换所选配置。原文件保存到修复前快照，插件文件、会话和原生 API Key 保留。",
                                "Replace the selected configuration with defaults. Save the original files in a pre-repair snapshot and retain plugin files, sessions, and the native API key.",
                            ),
                        ) { repair(request) }
                    },
                )
            }
            is Dialog.Attempt -> DshaContentDialog(
                title = date(current.entry.started),
                visible = true,
                onDismiss = close,
                actions = listOf(DshaDialogAction(t("关闭", "Close")) {}),
            ) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    DshaSelectableText(current.entry.stage + "\n\n" + StartupText.render(current.entry.reason) + "\n\n" + current.entry.log)
                }
            }
        }
    }

    // ---------------- 逻辑（与原 Java 版一致） ----------------

    /** 打开确认框；从选择列表进入时替换当前对话框。 */
    private fun confirm(title: String, detail: String, work: () -> Unit) {
        // 对话框按钮先 onClose 置空，再执行动作；post 保证新对话框不被随后的关闭覆盖。
        ui.post { dialog = Dialog.Confirm(title, detail, work) }
    }

    private fun openExtract() {
        startActivity(Intent(this, ExtractActivity::class.java).putExtra("review_only", true))
    }

    private fun repair(request: JSONObject) {
        if (!BackupTask.get(this).repairStartup(request)) {
            Toast.makeText(this, t("已有任务进行中，请完成后重试。", "Another task is in progress. Retry when it finishes."), Toast.LENGTH_LONG).show()
        }
        refresh.run()
    }

    private fun retry(safe: Boolean) {
        if (!controller.isEnvironmentReady) {
            status = t("运行环境尚未就绪，可先修复配置或导出数据，再进入安装与修复。", "The runtime is not ready. Repair configuration or export data, then open installation and repair.")
            return
        }
        if (StartupRepairs.pending(this)) {
            status = readable("RECOVERY_PENDING")
            return
        }
        controller.recoverWeb(safe, null) { }
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("open_launch", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun attemptStatus(value: String?): String = when (value) {
        "ready" -> t("就绪", "Ready")
        "failed" -> t("失败", "Failed")
        "stopped" -> t("已停止", "Stopped")
        else -> t("启动中／已中断", "Starting / interrupted")
    }

    private fun date(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))

    private fun readable(value: String): String = when {
        value.contains("RECOVERY_PENDING") -> t("配置修复未完成，请使用“恢复中断的配置修复”。", "Configuration repair is incomplete. Select Recover interrupted configuration repair.")
        value.contains("RECOVERY_BUSY") -> t("环境任务正在执行，完成后刷新即可。", "An environment task is running. Refresh when it finishes.")
        value.contains("RECOVERY_LINK") -> t("配置文件使用软链接，已保留原文件。请手动检查链接目标。", "A configuration file uses a symbolic link. The original was retained. Check the link target manually.")
        value.contains("RECOVERY_CHECKSUM") || value.contains("RECOVERY_FORMAT") -> t("配置快照校验失败，未应用修复。", "Configuration snapshot validation failed. The repair was not applied.")
        value.contains("RECOVERY_CHANGED") -> t("配置或快照已变化，请刷新后重新选择。", "The configuration or snapshot changed. Refresh and select it again.")
        value.contains("RECOVERY_SIZE") -> t("配置文件超过快照大小限制或不是普通文件，原文件保留。", "A configuration file exceeds the snapshot size limit or is not a regular file. The original was retained.")
        else -> StartupText.render(value)
    }

    /** 700ms 轮询任务状态：更新进度文本、禁用受保护动作、维护结束后重读记录。 */
    private val refresh: Runnable = object : Runnable {
        override fun run() {
            if (isFinishing) return
            val task = BackupTask.get(this@StartupRecoveryActivity)
            val state = task.snapshot()
            val key = "${state.id}:${state.status}:${state.detail}"
            if (key != previousTask) {
                previousTask = key
                if (state.id > 0) status = readable(state.detail)
            }
            val maintenance = task.maintenanceBusy()
            if (wasBusy && !maintenance) model.load()
            wasBusy = maintenance
            busy = task.busy()
            ui.removeCallbacks(this)
            ui.postDelayed(this, 700)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        super.onPause()
    }

    private companion object {
        fun t(zh: String, en: String): String = UiText.choose(zh, en)
        fun request(command: String): JSONObject = JSONObject().put("command", command)
    }
}