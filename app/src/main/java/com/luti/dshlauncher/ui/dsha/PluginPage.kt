package com.luti.dshlauncher.ui.dsha

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.PluginRepository
import com.deepseekharness.app.ui.BackgroundTasksActivity
import com.deepseekharness.app.ui.CommunityActivity
import com.deepseekharness.app.ui.PluginFilePicker
import com.deepseekharness.app.util.PluginDownloadSource
import com.deepseekharness.app.util.PluginSort
import com.deepseekharness.app.util.PluginSource
import com.deepseekharness.app.util.UiStateText
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.dialog.ConfirmResult
import com.luti.dshlauncher.ui.component.dialog.rememberConfirmDialog
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TabRow as MiuixTabRow

/**
 * 已装插件状态的失效记录。Repository 随 Activity 留存，失效记录不能挂在页面上。仅主线程访问。
 */
object PluginInstalledState {
    @Volatile private var revision = 0L
    private val refreshed = WeakHashMap<PluginRepository, Long>()

    @JvmStatic fun invalidate() { revision++ }

    /** 本次修订是否尚未同步；返回 true 时同时登记为已同步。 */
    fun claim(repository: PluginRepository): Boolean {
        if (refreshed[repository] == revision) return false
        refreshed[repository] = revision
        return true
    }
}

private tailrec fun Context.componentActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.componentActivity()
    else -> error("PluginPage 需要宿主 ComponentActivity")
}

private fun downloadSourceLabels() = listOf(
    UiText.text("自动选择（推荐）"),
    UiText.text("npm 官方源"),
    UiText.text("npmmirror 国内镜像"),
)

/** 插件市场入口与已装插件管理；耗时任务由 Activity 范围的 Repository 承接。 */
@Composable
fun PluginPage(onBack: () -> Unit, showInstalled: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val repository = remember { ViewModelProvider(context.componentActivity())[PluginRepository::class.java] }
    val config = remember { ConfigStore(context) }
    val controller = remember { HarnessController.get(context) }

    var current by remember { mutableStateOf(repository.state().value) }
    var preview by remember { mutableStateOf(repository.preview().value) }
    var resumed by remember { mutableStateOf(false) }

    var market by rememberSaveable { mutableStateOf(!showInstalled) }
    var sortName by rememberSaveable { mutableStateOf(config.pluginSort.name) }
    val sortOrder = PluginSort.Mode.parse(sortName)
    var deferredPreviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExports by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var downloadSource by remember { mutableStateOf(config.pluginDownloadSource) }

    var linkInput by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var hideBuiltin by rememberSaveable { mutableStateOf(false) }
    val expanded = remember { mutableStateListOf<String>() }
    var environmentNotice by remember { mutableStateOf<String?>(null) }

    var statusDialog by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<PluginRepository.Item?>(null) }
    var detailItem by remember { mutableStateOf<PluginRepository.Item?>(null) }
    var exportChoices by remember { mutableStateOf<List<String>?>(null) }

    fun toast(message: String?) {
        if (!message.isNullOrEmpty()) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    DisposableEffect(repository, lifecycleOwner) {
        val stateObserver = Observer<PluginRepository.State> { current = it }
        val previewObserver = Observer<PluginRepository.Preview?> { value ->
            if (value == null) deferredPreviewId = null
            preview = value
        }
        repository.state().observe(lifecycleOwner, stateObserver)
        repository.preview().observe(lifecycleOwner, previewObserver)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            repository.state().removeObserver(stateObserver)
            repository.preview().removeObserver(previewObserver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    /** 环境任务、启动/停止中或插件任务进行时不同步；顺带维护环境拦截提示。 */
    fun refreshBlocked(): Boolean {
        val blocked = repository.environmentBlockMessage()
        if (blocked.isNotEmpty()) {
            if (!repository.isBusy) {
                environmentNotice = blocked
                if (blocked != repository.state().value?.message) repository.selectionMessage(blocked)
            }
            return true
        }
        environmentNotice?.let { notice ->
            if (!repository.isBusy && notice == repository.state().value?.message) {
                repository.selectionMessage(UiText.text("环境任务已结束；当前显示缓存列表，可点「刷新」同步插件状态"))
            }
            environmentNotice = null
        }
        return repository.isBusy || controller.isStarting || controller.isStopping
    }

    // 只在首次读取或安全启动失效后同步一次；失败由操作结果显示，不无限全量刷新。
    LaunchedEffect(resumed, market) {
        while (resumed && !market) {
            if (refreshBlocked()) {
                delay(500)
                continue
            }
            if (PluginInstalledState.claim(repository)) repository.refresh()
            break
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        var uri = data?.data
        if (uri == null) {
            val clip = data?.clipData
            if (clip != null && clip.itemCount > 0) uri = clip.getItemAt(0).uri
        }
        when {
            result.resultCode != Activity.RESULT_OK || uri == null ->
                repository.selectionMessage(UiText.text("未选择文件。可再次点击导入插件包。"))
            uri.scheme != "content" && uri.scheme != "file" ->
                repository.selectionMessage(UiText.text("文件管理器返回的地址无法读取，请改用系统文件选择器。"))
            else -> {
                val needsPermission = uri.scheme == "file" && Build.VERSION.SDK_INT < 30 &&
                    context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) pendingImport = uri else repository.importArchive(uri)
            }
        }
    }
    val readPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val selected = pendingImport
        pendingImport = null
        if (allowed && selected != null) repository.importArchive(selected)
        else repository.selectionMessage(UiText.text("未获得文件读取权限，请改用系统文件选择器导入。"))
    }
    // 权限申请必须在 launcher 注册后发起，用状态驱动
    LaunchedEffect(pendingImport) {
        if (pendingImport != null) readPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null && pendingExports.isNotEmpty()) repository.exportArchives(ArrayList(pendingExports), uri)
        pendingExports = ArrayList()
    }

    fun chooseImport(alternative: Boolean) {
        if (repository.isBusy) {
            toast(UiText.text("请等待当前插件操作完成后再导入"))
            return
        }
        focus.clearFocus()
        repository.selectionMessage(UiText.text("请选择 ZIP / TAR.GZ 插件包。"))
        try {
            importPicker.launch(PluginFilePicker.intent(context, alternative))
        } catch (_: ActivityNotFoundException) {
            if (!alternative) {
                chooseImport(true)
                return
            }
            repository.selectionMessage(UiText.text("未找到可用的文件选择器，请启用系统「文件」应用后重试。"))
            toast(UiText.text("没有可用的文件选择器"))
        } catch (error: RuntimeException) {
            repository.selectionMessage(UiText.text("无法打开文件选择器，请检查系统文件应用：") + error.javaClass.simpleName)
        }
    }

    fun beginExport(names: List<String>) {
        if (names.isEmpty() || repository.isBusy) return
        pendingExports = ArrayList(names)
        val name = if (names.size == 1) names[0].replace(Regex("[^A-Za-z0-9._-]"), "_") else "DSHA-plugins"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        try {
            exportPicker.launch("$name-$stamp.tar.gz")
        } catch (_: Exception) {
            pendingExports = ArrayList()
            toast(UiText.text("无法打开保存位置选择器"))
        }
    }

    fun chooseExport() {
        val state = current ?: return
        if (repository.isBusy) return
        val names = state.items.filter { it.exportable }.map { it.name }
        if (names.isEmpty()) toast(UiText.text("没有可导出的插件")) else exportChoices = names
    }

    fun installLink() {
        if (repository.isBusy) return
        try {
            val source = PluginSource.parse(linkInput)
            focus.clearFocus()
            repository.install(source)
        } catch (error: IllegalArgumentException) {
            toast(error.message)
        }
    }

    fun pasteLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            toast(UiText.text("剪贴板没有链接"))
            return
        }
        clip.getItemAt(0).coerceToText(context)?.let { linkInput = it.toString() }
    }

    fun openPluginWebsite() {
        try {
            context.startActivity(Intent(context, CommunityActivity::class.java))
        } catch (_: RuntimeException) {
            toast(UiText.text("无法打开浏览器，请在浏览器中访问 https://dsha.cc/"))
        }
    }

    fun toggle(item: PluginRepository.Item, enabled: Boolean) {
        if (repository.isBusy) return
        if (item.official && !enabled) {
            scope.launch {
                val result = confirm.awaitConfirm(
                    title = UiText.text("禁用官方核心？"),
                    content = item.name + UiText.text(" 是 Web 运行所需的核心，禁用后页面可能无法启动。"),
                    confirm = UiText.text("禁用"),
                    dismiss = UiText.text("取消"),
                )
                if (result == ConfirmResult.Confirmed) repository.setEnabled(item, false)
            }
        } else {
            repository.setEnabled(item, enabled)
        }
    }

    fun confirmDelete(item: PluginRepository.Item) {
        if (repository.isBusy) {
            toast(UiText.text("请等待当前插件操作完成"))
            return
        }
        scope.launch {
            val result = confirm.awaitConfirm(
                title = UiText.text("删除插件？"),
                content = UiText.text("将删除 ") + item.name + UiText.text(" 的安装文件和启用记录。") +
                    UiText.text("\n对话、其他插件及外部源码目录会保留。需要留存时可先导出。"),
                confirm = UiText.text("删除"),
                dismiss = UiText.text("取消"),
            )
            if (result == ConfirmResult.Confirmed) repository.delete(item)
        }
    }

    fun confirmRollback(item: PluginRepository.Item) {
        scope.launch {
            val result = confirm.awaitConfirm(
                title = UiText.text("回退插件？"),
                content = item.name + UiText.text("：") + item.version + " → " + item.rollbackVersion +
                    UiText.text("\n只恢复插件文件，当前启用状态和对话数据保留；重启 Web 生效。"),
                confirm = UiText.text("回退"),
                dismiss = UiText.text("取消"),
            )
            if (result == ConfirmResult.Confirmed) repository.rollback(item)
        }
    }

    fun confirmRestore() {
        scope.launch {
            val result = confirm.awaitConfirm(
                title = UiText.text("恢复第三方插件？"),
                content = UiText.text("恢复安全启动前已启用的插件；之后手动禁用的插件保持禁用。恢复后重启 Web 生效。"),
                confirm = UiText.text("恢复"),
                dismiss = UiText.text("取消"),
            )
            if (result == ConfirmResult.Confirmed) repository.safeMode(false, null)
        }
    }

    // ---- 派生状态 ----
    val state = current
    val busy = state?.busy == true
    val sortLabels = listOf(
        context.getString(R.string.plugin_sort_az),
        context.getString(R.string.plugin_sort_za),
        context.getString(R.string.plugin_sort_enabled),
        context.getString(R.string.plugin_sort_updates),
    )
    val parsedLink = runCatching { PluginSource.parse(linkInput) }
    val linkHint = parsedLink.fold(
        onSuccess = { UiText.text("已识别：") + it.description() },
        onFailure = {
            if (linkInput.isBlank()) UiText.text("支持仓库、分支/子目录、Release 下载和压缩包直链") else it.message.orEmpty()
        },
    )
    val visibleItems = remember(state?.items, query, sortOrder, hideBuiltin) {
        val needle = query.trim().lowercase(Locale.ROOT)
        state?.items.orEmpty()
            .filter { !(hideBuiltin && (it.builtin || it.official)) }
            .filter { (it.name + " " + it.description).lowercase(Locale.ROOT).contains(needle) }
            .sortedWith(
                PluginSort.comparator<PluginRepository.Item>(
                    sortOrder,
                    { it.name },
                    { it.enabled },
                    { it.updateAvailable },
                ),
            )
    }
    val retained = preview
    val deferred = retained != null && retained.id == deferredPreviewId && !busy
    val statusMessage = state?.message.orEmpty()

    DshaPageScaffold(
        title = context.getString(if (market) R.string.ui_m0133 else R.string.ui_m0135),
        onBack = onBack,
    ) {
        item {
            PluginTabs(
                tabs = listOf(context.getString(R.string.ui_m0133), context.getString(R.string.ui_m0135)),
                selected = if (market) 0 else 1,
                onSelect = {
                    market = it == 0
                    focus.clearFocus()
                },
            )
        }

        if (market) {
            dshaEntryCard(
                listOf(
                    DshaEntry(context.getString(R.string.ui2_community), context.getString(R.string.ui2_community_sub)) {
                        openPluginWebsite()
                    },
                ),
            )
            item {
                DshaCard {
                    DshaTextField(
                        title = context.getString(R.string.ui2_from_link),
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        hint = context.getString(R.string.ui_m0137),
                        summary = linkHint,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    )
                    DshaSecondaryButton(context.getString(R.string.ui_m0179)) { pasteLink() }
                    DshaPrimaryButton(
                        context.getString(R.string.ui2_resolve),
                        enabled = parsedLink.isSuccess && !busy,
                    ) { installLink() }
                }
            }
        } else {
            item {
                DshaCard {
                    DshaTextField(
                        title = context.getString(R.string.ui2_installed),
                        value = query,
                        onValueChange = { query = it },
                        hint = context.getString(R.string.ui_m0138),
                    )
                    DshaSwitchRow(
                        title = context.getString(R.string.ui_m0081),
                        checked = hideBuiltin,
                        onCheckedChange = { hideBuiltin = it },
                    )
                    DshaDropdownRow(
                        title = context.getString(R.string.plugin_sort_title),
                        items = sortLabels,
                        selectedIndex = sortOrder.ordinal,
                        onSelectedIndexChange = { index ->
                            val mode = PluginSort.Mode.values()[index]
                            config.pluginSort = mode
                            sortName = mode.name
                        },
                    )
                }
            }
            item {
                DshaSecondaryButton(context.getString(R.string.ui_m0159), enabled = !busy) { repository.refresh() }
                DshaSecondaryButton(context.getString(R.string.ui_m0018), enabled = !busy) { repository.checkUpdates(null) }
            }
        }

        item {
            DshaPreferenceGroup {
                DshaDropdownRow(
                    title = UiText.text("插件下载源"),
                    summary = UiText.text("自动选择可用的 npm 源；镜像失败时回退官方源。GitHub 使用官方直连。"),
                    items = downloadSourceLabels(),
                    selectedIndex = downloadSource.ordinal,
                    onSelectedIndexChange = { index ->
                        if (!repository.isBusy) {
                            val source = PluginDownloadSource.values()[index]
                            config.pluginDownloadSource = source
                            downloadSource = source
                        }
                    },
                )
            }
        }

        if (busy) {
            item { DshaProgressBar(state?.percent?.takeIf { it >= 0 }?.let { it / 100f }) }
            item {
                DshaSecondaryButton(context.getString(R.string.ui_m0079), enabled = state?.cancellable == true) {
                    repository.cancelTask()
                }
            }
        }
        if (statusMessage.isNotEmpty()) {
            val text = UiStateText.render(statusMessage) + if (deferred) {
                UiText.choose(
                    "\n清理未确认；预览已保留。点此重试或稍后处理。",
                    "\nCleanup is unconfirmed; the preview is retained. Tap to retry or handle it later.",
                )
            } else {
                ""
            }
            dshaEntryCard(listOf(DshaEntry(UiText.text("插件操作结果"), text) { statusDialog = true }))
        }
        dshaEntryCard(
            listOf(
                DshaEntry(UiText.choose("后台任务", "Background tasks"), UiText.choose("查看全部环境与插件任务", "View all tasks")) {
                    BackgroundTasksActivity.open(context)
                },
            ),
        )

        if (!market) {
            item { DshaNote(UiText.text("共 ") + visibleItems.size + UiText.text(" 个插件")) }
            if (visibleItems.isEmpty()) {
                item { DshaNote(if (busy) UiText.text("正在读取插件…") else UiText.text("没有符合条件的插件")) }
            }
            visibleItems.forEach { plugin ->
                item(key = "plugin:" + plugin.name) {
                    PluginItemCard(
                        item = plugin,
                        busy = busy,
                        expanded = plugin.name in expanded,
                        onExpand = { if (!expanded.remove(plugin.name)) expanded.add(plugin.name) },
                        onToggle = { toggle(plugin, it) },
                        onActions = { actionItem = plugin },
                        onDelete = { confirmDelete(plugin) },
                    )
                }
            }
        }

        if (market) {
            item { DshaCategory(context.getString(R.string.ui2_local_plugins)) }
            dshaEntryCard(
                listOf(
                    DshaEntry(context.getString(R.string.ui2_import), context.getString(R.string.ui2_import_sub)) {
                        chooseImport(false)
                    },
                    DshaEntry(context.getString(R.string.ui2_export), context.getString(R.string.ui2_export_sub)) {
                        chooseExport()
                    },
                ),
            )
        }
        if (repository.isSafeMode) {
            item { DshaSecondaryButton(context.getString(R.string.ui_m0104), enabled = !busy) { confirmRestore() } }
        }
    }

    // ---- 对话框 ----

    // 安装/启用/回退预览：非忙碌且未被用户延后时弹出；阻断项存在时不提供确认按钮
    val shown = preview
    if (shown != null && !busy && !repository.isBusy && shown.id != deferredPreviewId) {
        val discard = {
            // 清理失败会保留同一预览；不在下次状态更新时重复弹出，用户可从状态行重试
            deferredPreviewId = shown.id
            repository.discardPreview()
        }
        val confirmLabel = when (shown.action) {
            "install" -> UiText.text("确认安装")
            "rollback" -> UiText.text("确认回退")
            else -> UiText.text("确认启用")
        }
        DshaActionDialog(
            title = UiText.text(if (shown.action == "install") "确认安装插件" else "审阅插件启用或回退"),
            message = shown.description(),
            visible = true,
            onDismiss = discard,
            onClose = {},
            actions = buildList {
                add(DshaDialogAction(UiText.text("取消")) { discard() })
                if (!shown.blocked()) add(DshaDialogAction(confirmLabel) { repository.confirmPreview() })
            },
        )
    }

    if (statusDialog && statusMessage.isNotEmpty()) {
        val keep = retained
        DshaActionDialog(
            title = UiText.text("插件操作结果"),
            message = UiStateText.render(statusMessage),
            visible = true,
            onDismiss = { statusDialog = false },
            actions = if (deferred && keep != null) {
                listOf(
                    DshaDialogAction(UiText.choose("稍后处理", "Handle later")) {},
                    DshaDialogAction(UiText.choose("重试清理", "Retry cleanup")) {
                        deferredPreviewId = keep.id
                        repository.discardPreview()
                    },
                    DshaDialogAction(UiText.choose("查看保留预览", "View retained preview")) { deferredPreviewId = null },
                )
            } else {
                listOf(DshaDialogAction(UiText.text("关闭")) {})
            },
        )
    }

    actionItem?.let { item ->
        val actions = buildList<Pair<String, () -> Unit>> {
            add("查看详情" to { detailItem = item })
            add("复制插件名称" to { copyText(context, item.name) })
            if (item.source.isNotEmpty()) add("复制来源链接" to { copyText(context, item.source) })
            if (item.exportable) add("导出插件包" to { beginExport(listOf(item.name)) })
            if (item.deletable) add("检查插件更新" to { repository.checkUpdates(item) })
            if (item.updateAvailable) add(UiText.text("更新至 ") + item.latestVersion to { repository.prepareUpdate(item) })
            if (item.rollbackVersion.isNotEmpty()) add(UiText.text("回退至 ") + item.rollbackVersion to { confirmRollback(item) })
            if (item.deletable) add("删除插件" to { confirmDelete(item) })
        }
        DshaChoiceSheet(
            title = item.name,
            items = actions.map { UiText.text(it.first) },
            visible = true,
            onDismiss = { actionItem = null },
            onSelect = { index -> actions[index].second() },
        )
    }

    detailItem?.let { item ->
        DshaMessageDialog(
            title = item.name,
            message = item.description + UiText.text("\n\n版本：") + item.version +
                (if (item.location.isEmpty()) "" else UiText.text("\n位置：") + item.location) +
                (if (item.source.isEmpty()) "" else UiText.text("\n来源：") + item.source) +
                (if (item.latestVersion.isEmpty()) "" else UiText.text("\n上次检查版本：") + item.latestVersion) +
                (if (item.updateMessage.isEmpty()) "" else "\n" + UiStateText.render(item.updateMessage)) +
                (if (item.rollbackVersion.isEmpty()) "" else UiText.text("\n可回退：") + item.rollbackVersion),
            visible = true,
            onDismiss = { detailItem = null },
        )
    }

    exportChoices?.let { names ->
        val checked = remember(names) { mutableStateListOf<String>() }
        DshaContentDialog(
            title = UiText.text("选择要导出的插件"),
            visible = true,
            onDismiss = { exportChoices = null },
            actions = buildList {
                add(DshaDialogAction(UiText.text("取消")) {})
                if (checked.isNotEmpty()) {
                    add(DshaDialogAction(UiText.text("选择保存位置")) { beginExport(names.filter { it in checked }) })
                }
            },
        ) {
            names.forEach { name ->
                DshaSwitchRow(
                    title = name,
                    checked = name in checked,
                    onCheckedChange = { on -> if (on) checked.add(name) else checked.remove(name) },
                )
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(UiText.text("插件"), text))
    Toast.makeText(context, UiText.text("已复制"), Toast.LENGTH_LONG).show()
}

private fun pluginStateText(item: PluginRepository.Item): String {
    val base = if (!item.dynamic && item.loadState.isNotEmpty()) {
        when (item.loadState) {
            "review-required" -> "待审阅启用"
            "queued" -> "已批准，等待加载"
            "attempted" -> "正在确认加载"
            "loaded" -> if (item.enabled) "已确认加载" else "已禁用"
            "failed", "unconfirmed" -> "加载未确认，保持停用"
            "changed" -> "内容已变化，需要重新审阅"
            else -> if (item.enabled) "已启用" else "已禁用"
        }
    } else when {
        item.dynamic -> if (item.enabled) "临时插件 · 已运行" else "临时插件 · 未运行"
        !item.available -> "实体缺失，请重新导入"
        item.enabled -> "已启用"
        item.detected -> "已检测，可开启以加入 Web"
        else -> "已禁用"
    }
    return UiText.text(base) +
        (if (item.version.isEmpty()) "" else " · " + item.version) +
        (if (item.updateAvailable) UiText.text("\n可更新：") + item.latestVersion else "")
}

private fun pluginDescription(item: PluginRepository.Item): String = when {
    item.description.isEmpty() -> when {
        item.official -> UiText.text("官方核心")
        item.builtin -> UiText.text("DSHA 内置插件")
        else -> UiText.text("第三方插件")
    }
    item.builtin || item.official || item.dynamic -> UiStateText.render(item.description)
    else -> item.description
}

@Composable
private fun PluginItemCard(
    item: PluginRepository.Item,
    busy: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onActions: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = pluginStateText(item) + "\n" + pluginDescription(item)
    DshaCard {
        if (item.dynamic) {
            PluginRow(item.name, summary, null)
        } else {
            DshaSwitchRow(
                title = item.name,
                summary = summary,
                checked = item.enabled,
                enabled = !busy && (item.available || item.enabled),
                onCheckedChange = { if (it != item.enabled) onToggle(it) },
            )
        }
        if (expanded) {
            val location = item.location.ifEmpty { UiText.choose("未提供路径", "Path not provided") }
            val source = item.source.ifEmpty {
                when {
                    item.builtin -> UiText.choose("随包内置", "Bundled")
                    item.official -> UiText.choose("DSH 官方组件", "Official DSH component")
                    else -> UiText.choose("来源未记录", "Source not recorded")
                }
            }
            PluginRow(
                UiText.choose("插件详情", "Plugin details"),
                item.description + "\n\n" + UiText.choose("版本：", "Version: ") + item.version + "\n" +
                    UiText.choose("位置：", "Location: ") + location + "\n" +
                    UiText.choose("来源：", "Source: ") + source,
                onExpand,
            )
        } else {
            PluginRow(UiText.choose("展开插件详情", "Plugin details"), null, onExpand)
        }
        PluginRow(UiText.text("更多操作：") + item.name, null, onActions)
        if (item.deletable && !busy) PluginRow(UiText.choose("删除插件", "Delete plugin"), null, onDelete)
    }
}

@Composable
private fun PluginRow(title: String, summary: String?, onClick: (() -> Unit)?) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> BasicComponent(title = title, summary = summary, onClick = onClick)
        UiMode.Material -> SegmentedListItem(
            onClick = onClick,
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it) } },
        )
    }
}

@Composable
private fun PluginTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTabRow(
            tabs = tabs,
            selectedTabIndex = selected,
            onTabSelected = onSelect,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth(),
            height = 48.dp,
        )
        UiMode.Material -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, label ->
                FilterChip(selected = selected == index, onClick = { onSelect(index) }, label = { Text(label) })
            }
        }
    }
}