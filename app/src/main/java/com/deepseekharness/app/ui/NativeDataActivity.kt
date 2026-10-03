package com.deepseekharness.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.deepseekharness.app.backup.AndroidBackupFileSystem
import com.deepseekharness.app.backup.AutomaticBackups
import com.deepseekharness.app.backup.BackupControl
import com.deepseekharness.app.backup.BackupErrorCode
import com.deepseekharness.app.backup.BackupPreview
import com.deepseekharness.app.backup.BackupSource
import com.deepseekharness.app.backup.NativeBackupJobs
import com.deepseekharness.app.backup.NativeDataLocations
import com.deepseekharness.app.backup.RetainedCatalogue
import com.deepseekharness.app.backup.SafBackupSource
import com.deepseekharness.app.backup.UserDataLayout
import com.deepseekharness.app.backup.VerifiedBackupCopy
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.data.PortableSettings
import com.deepseekharness.app.util.CredentialRead
import com.deepseekharness.app.util.Fmt
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DshaActionDialog
import com.luti.dshlauncher.ui.dsha.DshaCard
import com.luti.dshlauncher.ui.dsha.DshaCardButton
import com.luti.dshlauncher.ui.dsha.DshaCardTitle
import com.luti.dshlauncher.ui.dsha.DshaChoiceSheet
import com.luti.dshlauncher.ui.dsha.DshaContentDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.DshaDropdownRow
import com.luti.dshlauncher.ui.dsha.DshaMessageDialog
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaPageScaffold
import com.luti.dshlauncher.ui.dsha.DshaSelectableText
import com.luti.dshlauncher.ui.dsha.DshaSwitchRow
import com.luti.dshlauncher.ui.dsha.DshaTextField
import com.luti.dshlauncher.ui.dsha.setDshaContent
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 原生数据入口（Compose 版）：不等待 Ubuntu 或网页；界面只传递参数并观察应用级作业。
 * 作业、密码清零、敏感剪贴板与恢复确认的语义与原 Java 版一致。
 */
class NativeDataActivity : androidx.fragment.app.FragmentActivity() {

    class Pending : ViewModel() {
        var password: CharArray? = null
        var selection: NativeDataLocations.Selection? = null
        var filename: String? = null
        var rescue = false
        var scopeIndex = 0
        var includeKey = false
        var reexportId: String? = null
        val projects = ArrayList<BackupSource>()
        val preview = MutableLiveData<BackupPreview.Report?>()
        val previewError = MutableLiveData<String?>()
        @Volatile var previewBusy = false
        var previewControl: BackupControl? = null

        fun clear() {
            password?.let { Arrays.fill(it, '\u0000') }
            password = null
        }

        override fun onCleared() {
            clear()
            previewControl?.cancel()
        }
    }

    /** 同一时刻只显示一个业务对话框；恢复预检确认单独管理。 */
    private sealed interface Dialog {
        data class Message(val title: String, val text: String) : Dialog
        data object RecoverCommit : Dialog
        data object DataHomeChoice : Dialog
        data class DataHomeConfirm(val index: Int) : Dialog
        data class CopyChoice(val copies: List<VerifiedBackupCopy>) : Dialog
        data class CopyConfirm(val copy: VerifiedBackupCopy) : Dialog
        data class AutoPassword(val id: String, val password: String) : Dialog
        data class InspectTree(val entry: RetainedCatalogue.Entry) : Dialog
        data class ExportPassword(val rescue: Boolean, val retained: NativeDataLocations.Selection?) : Dialog
        data class RestorePassword(val uri: Uri?, val copy: String?) : Dialog
    }

    private lateinit var pending: Pending
    private lateinit var jobs: NativeBackupJobs

    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var dialog by mutableStateOf<Dialog?>(null)
    private var scopeIndex by mutableIntStateOf(0)
    private var includeKey by mutableStateOf(false)
    private var projectCount by mutableIntStateOf(0)
    /** 当前待确认的恢复预检：作业 id 与说明文本。 */
    private var previewConfirm by mutableStateOf<Pair<String, String>?>(null)
    /** 已作出决定的预检 id，避免同一预检在后续状态推送中再次弹出。 */
    private var decidedPreview: String? = null

    private val destination = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        pending.reexportId?.let { source ->
            pending.reexportId = null
            if (uri != null && !jobs.reexport(source, uri, pending.filename)) {
                status = t("副本无法重新导出，请检查记录或当前作业。", "The copy cannot be exported. Review its record or the current operation.")
            }
            return@registerForActivityResult
        }
        if (uri == null) { pending.clear(); return@registerForActivityResult }
        val password = pending.password
        if (password == null) {
            status = t("密码没有保存在设备中，请重新输入后导出。", "The password was not saved. Enter it again to export.")
            return@registerForActivityResult
        }
        if (!jobs.export(pending.selection, password, uri, pending.filename, pending.rescue)) {
            status = t("已有任务或无法创建私有作业，请检查状态。", "A task is already running or private storage is unavailable.")
        }
        pending.clear()
    }

    private val projectPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pending.projects.add(
                SafBackupSource(this, uri, "project-" + NativeDataLocations.hash(uri.toString()), "projects", ""),
            )
            projectCount = pending.projects.size
        } catch (_: Exception) {
            status = t("无法读取所选目录，请重新授权。", "Cannot read the folder. Grant access again.")
        }
    }

    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restorePassword(uri, null)
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        pending = ViewModelProvider(this)[Pending::class.java]
        jobs = NativeBackupJobs.get(this)
        scopeIndex = pending.scopeIndex
        includeKey = pending.includeKey
        projectCount = pending.projects.size

        val mode = intent.getStringExtra("data_mode")
        val exportOnly = mode == "export"
        val restoreOnly = mode == "restore"
        val backupMode = mode == "backup"

        setDshaContent { Page(exportOnly, restoreOnly, backupMode) }

        jobs.changes().observe(this) { render(it) }
        pending.preview.observe(this) { value -> if (value != null) showScope(value) }
        pending.previewError.observe(this) { value -> if (!value.isNullOrEmpty()) status = errorText(value) }

        if (saved == null) {
            val main = Handler(Looper.getMainLooper())
            if (intent.hasExtra("auto_restore_id") || intent.hasExtra("auto_export_id")) main.post { automaticCopy() }
            if (intent.hasExtra("retained_key")) main.post { retainedAction() }
            intent.getStringExtra("restore_uri")?.let { raw ->
                val input = Uri.parse(raw)
                if (input.scheme == "content") main.post { restorePassword(input, null) }
            }
        }
    }

    // ---------------- 页面 ----------------

    @Composable
    private fun Page(exportOnly: Boolean, restoreOnly: Boolean, backupMode: Boolean) {
        val title = when {
            backupMode -> t("备份与恢复", "Backup and restore")
            exportOnly -> t("加密导出", "Encrypted export")
            restoreOnly -> t("从备份恢复", "Restore a backup")
            else -> t("应用数据与救援", "Application data and recovery")
        }
        val subtitle = when {
            backupMode -> t("导出、导入和自动备份集中在这里。", "Export, import and automatic backups in one place.")
            exportOnly -> t("选择范围和项目后，设置密码保存加密副本。", "Choose data and projects, then protect the export with a password.")
            restoreOnly -> t("先验证备份密码与内容，再确认恢复范围。", "Verify the password and contents before confirming what to restore.")
            else -> t("运行环境不可用时，也能检查并保护可读取的数据。", "Inspect and protect readable data even when the runtime is unavailable.")
        }
        val full = !exportOnly && !restoreOnly && !backupMode
        DshaPageScaffold(title = title, subtitle = subtitle, onBack = { finish() }) {
            if (status.isNotEmpty()) item { DshaCard { DshaSelectableText(status) } }

            if (!restoreOnly) item {
                DshaCard {
                    DshaCardTitle(t("选择数据范围", "Choose data scope"))
                    DshaDropdownRow(
                        title = t("数据范围", "Data scope"),
                        items = scopeLabels(),
                        selectedIndex = scopeIndex,
                        onSelectedIndexChange = { scopeIndex = it; pending.scopeIndex = it },
                    )
                    DshaSwitchRow(
                        title = t("包含原生 API Key（默认不包含）", "Include native API key (excluded by default)"),
                        checked = includeKey,
                        onCheckedChange = { includeKey = it; pending.includeKey = it },
                    )
                    DshaNote(
                        if (projectCount == 0) {
                            t("项目文件需单独选择目录；不会扫描整部手机。", "Choose project folders explicitly; the app does not scan the whole device.")
                        } else {
                            t("已选择项目目录：", "Selected project folders: ") + projectCount
                        },
                    )
                    DshaCardButton(t("选择项目目录", "Choose project folder")) { projectPicker.launch(null) }
                    DshaCardButton(t("查看数据范围与位置", "Review data scope and locations")) { locations() }
                }
            }

            item {
                DshaCard {
                    DshaCardTitle(
                        when {
                            exportOnly -> t("设置备份密码", "Protect your backup")
                            restoreOnly -> t("选择并验证", "Select and verify")
                            else -> t("导出与恢复", "Export and restore")
                        },
                    )
                    if (!restoreOnly) {
                        DshaCardButton(t("导出应用数据", "Export application data"), primary = true) { password(false, null) }
                    }
                    if (full) {
                        DshaCardButton(t("只读救援导出", "Read-only rescue export")) { password(true, null) }
                    }
                    if (!exportOnly) {
                        DshaCardButton(t("导入备份", "Import backup"), primary = restoreOnly) {
                            restorePicker.launch(arrayOf("application/octet-stream", "application/gzip", "*/*"))
                        }
                    }
                    if (full) {
                        DshaCardButton(t("重新导出已验证副本", "Export a verified copy again")) { verifiedCopies() }
                    }
                    if (backupMode) {
                        DshaCardButton(t("自动备份与记录", "Automatic backups and history")) {
                            startActivity(Intent(this@NativeDataActivity, AutomaticBackupActivity::class.java))
                        }
                    }
                    if (busy) {
                        DshaCardButton(t("取消当前作业", "Cancel current operation")) { jobs.cancel() }
                    }
                }
            }

            if (!exportOnly && !restoreOnly) item {
                DshaCard {
                    DshaCardTitle(t("保留数据", "Retained data"))
                    DshaNote(
                        t(
                            "查看保留副本、旧环境和插件原件；检查与恢复分别处理。",
                            "Browse retained copies, old environments, and original plugins. Inspection and restoration are separate actions.",
                        ),
                    )
                    DshaCardButton(t("保留副本与旧树", "Retained copies and old trees")) {
                        startActivity(Intent(this@NativeDataActivity, RetainedDataActivity::class.java))
                    }
                }
            }

            if (full) item {
                DshaCard {
                    DshaCardTitle(t("恢复与维护", "Recovery and maintenance"))
                    DshaCardButton(t("选择已有数据目录", "Choose existing data directory")) { dialog = Dialog.DataHomeChoice }
                    DshaCardButton(t("恢复中断的数据提交", "Recover interrupted data commit")) { dialog = Dialog.RecoverCommit }
                    DshaCardButton(t("回退兼容运行时", "Roll back compatible runtime")) { RuntimeRecoveryUi.show(this@NativeDataActivity) }
                }
            }
        }
        Dialogs()
        PreviewConfirm()
    }

    @Composable
    private fun Dialogs() {
        val close = { dialog = null }
        when (val current = dialog) {
            null -> Unit
            is Dialog.Message -> DshaMessageDialog(current.title, current.text, visible = true, onDismiss = close)
            Dialog.RecoverCommit -> DshaActionDialog(
                title = t("恢复中断的数据提交", "Recover interrupted data commit"),
                message = t(
                    "将先停止写任务，再根据宿主日志恢复一致状态。无需旧 Python 或备份密码，原件将保留。",
                    "Stop writers first, then restore consistency from the host journal. The old Python and backup password are not required. Originals are retained.",
                ),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("继续", "Continue")) {
                        if (!jobs.recoverPending()) {
                            status = t(
                                "没有可自动处理的提交，或需要先检查损坏/冲突的日志。",
                                "No automatically recoverable commit, or damaged/conflicting records need review.",
                            )
                        }
                    },
                ),
            )
            Dialog.DataHomeChoice -> DshaChoiceSheet(
                title = t("选择要使用的数据目录", "Choose the data directory to use"),
                items = dataHomeNames(),
                visible = true,
                onDismiss = { if (dialog == Dialog.DataHomeChoice) close() },
                onSelect = { dialog = Dialog.DataHomeConfirm(it) },
            )
            is Dialog.DataHomeConfirm -> DshaActionDialog(
                title = dataHomeNames()[current.index],
                message = t(
                    "会先停止 Web 和终端，再选择已有目录。两份数据都保留，不合并或清空。所选目录必须存在且可读取。",
                    "Stop Web and terminals, then select an existing directory. Both data sets remain; neither is merged or cleared. The chosen directory must exist and be readable.",
                ),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("继续", "Continue")) {
                        BackupTask.get(this).selectDataHome(
                            if (current.index == 0) UserDataLayout.Home.LEGACY else UserDataLayout.Home.STABLE,
                        )
                    },
                ),
            )
            is Dialog.CopyChoice -> {
                val format = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                DshaChoiceSheet(
                    title = t("选择已验证的加密副本", "Choose a verified encrypted copy"),
                    items = current.copies.map {
                        format.format(Date(it.created)) + " · " + Fmt.bytes(it.bytes) + " · " + it.id.substring(0, 8)
                    },
                    visible = true,
                    onDismiss = { if (dialog is Dialog.CopyChoice) close() },
                    onSelect = { dialog = Dialog.CopyConfirm(current.copies[it]) },
                )
            }
            is Dialog.CopyConfirm -> DshaActionDialog(
                title = t("重新导出副本", "Export the copy again"),
                message = t(
                    "将先重新核对私有文件的摘要，再复制到新建目标。仍使用该副本原来的备份密码；不会重新读取工作目录，也不会覆盖已有备份。",
                    "Recheck the private file checksum, then copy it to a newly created destination. It keeps its original backup password. The workspace is not read again and existing backups are preserved.",
                ),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("选择保存位置", "Choose destination")) { launchReexport(current.copy.id) },
                ),
            )
            is Dialog.AutoPassword -> DshaContentDialog(
                title = t("导出自动备份", "Export automatic backup"),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("复制密码并选择保存位置", "Copy password and choose destination")) {
                        copySensitive(current.password)
                        launchReexport(current.id)
                    },
                ),
            ) {
                DshaNote(
                    t(
                        "这是此备份的解密密码。请另行保存，换机或卸载后仍需它恢复。",
                        "Save this decryption password separately. It is required after reinstalling or moving to another device.",
                    ),
                )
                DshaSelectableText(current.password)
            }
            is Dialog.InspectTree -> DshaActionDialog(
                title = t("预检保留原件", "Inspect retained original"),
                message = t(
                    "先只读扫描所选原件并建立私有验证副本，不执行其中的程序。预检完成后还需确认目标；项目原件会恢复到新的独立目录。",
                    "Read the selected original into private verified staging without running its programs. Confirm the targets after inspection. Project originals restore into a new separate directory.",
                ),
                visible = true,
                onDismiss = close,
                actions = listOf(
                    DshaDialogAction(t("取消", "Cancel")) {},
                    DshaDialogAction(t("开始预检", "Inspect")) {
                        if (!jobs.prepareRestoreTree(current.entry.key(), setOf(current.entry.scope), "PRIVATE")) {
                            status = t("无法开始预检，请检查记录或当前任务。", "Inspection could not start. Review the record or current operation.")
                        }
                    },
                ),
            )
            is Dialog.ExportPassword -> ExportPasswordDialog(current)
            is Dialog.RestorePassword -> RestorePasswordDialog(current)
        }
    }

    @Composable
    private fun ExportPasswordDialog(request: Dialog.ExportPassword) {
        var first by remember { mutableStateOf("") }
        var second by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val close = {
            first = ""; second = ""
            dialog = null
        }
        DshaContentDialog(
            title = if (request.rescue) t("救援导出", "Rescue export") else t("加密导出", "Encrypted export"),
            visible = true,
            onDismiss = close,
            onClose = {},
            actions = listOf(
                DshaDialogAction(t("取消", "Cancel")) { close() },
                DshaDialogAction(t("选择保存位置", "Choose destination")) {
                    if (first.length < 12 || first.length > 1024 || first != second) {
                        error = t("至少 12 个字符，且两次输入一致", "Use at least 12 characters and matching passwords")
                        return@DshaDialogAction
                    }
                    pending.clear()
                    pending.password = first.toCharArray()
                    pending.selection = request.retained ?: selection()
                    pending.rescue = request.rescue
                    pending.filename = newFilename()
                    close()
                    destination.launch(pending.filename!!)
                },
            ),
        ) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DshaNote(
                    if (request.rescue) {
                        t(
                            "救援不会停止当前写入，结果将标明尽力救援或部分数据。不能据此删除原件。",
                            "Rescue does not stop current writers. The result is best-effort or partial and cannot justify deleting originals.",
                        )
                    } else {
                        t(
                            "将停止 Web、终端和写入任务后建立快照。项目、配置与聊天都可能含敏感内容；请保存至少 12 个字符的密码。",
                            "Web, terminals and write tasks will stop before the snapshot. Projects, configuration and conversations may contain secrets. Keep a password of at least 12 characters.",
                        )
                    },
                )
                DshaTextField(
                    title = t("备份密码", "Backup password"), value = first, password = true,
                    keyboardType = KeyboardType.Password, error = error,
                    onValueChange = { first = it; error = null },
                )
                DshaTextField(
                    title = t("再次输入", "Repeat password"), value = second, password = true,
                    keyboardType = KeyboardType.Password,
                    onValueChange = { second = it; error = null },
                )
            }
        }
    }

    @Composable
    private fun RestorePasswordDialog(request: Dialog.RestorePassword) {
        var secret by remember { mutableStateOf("") }
        var include by remember { mutableStateOf(false) }
        var guestHome by remember { mutableStateOf(false) }
        val close = {
            secret = ""
            dialog = null
        }
        DshaContentDialog(
            title = t("备份预检", "Inspect backup"),
            visible = true,
            onDismiss = close,
            onClose = {},
            actions = listOf(
                DshaDialogAction(t("取消", "Cancel")) { close() },
                DshaDialogAction(t("开始预检", "Inspect")) {
                    val chars = secret.toCharArray()
                    close()
                    try {
                        val location = if (guestHome) "GUEST_HOME" else "PRIVATE"
                        val scopes = setOf(selection().scope)
                        val key = if (chars.isEmpty()) null else chars
                        val started = if (request.copy == null) {
                            jobs.prepareRestore(request.uri, key, scopes, include, location)
                        } else {
                            jobs.prepareRestoreCopy(request.copy, key, scopes, include, location)
                        }
                        if (!started) status = t("已有任务，完成后再试。", "Another task is in progress.")
                    } finally {
                        Arrays.fill(chars, '\u0000')
                    }
                },
            ),
        ) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DshaNote(
                    t(
                        "加密备份请输入密码；旧版明文 tar.gz 可留空。先在私有目录验证，确认前不会覆盖当前数据。",
                        "Enter the encrypted backup password, or leave it empty for legacy plain tar.gz. Verification uses private staging and does not overwrite current data.",
                    ),
                )
                DshaTextField(
                    title = t("备份密码", "Backup password"), value = secret, password = true,
                    keyboardType = KeyboardType.Password,
                    onValueChange = { secret = it },
                )
                DshaSwitchRow(
                    title = t("恢复原生 API Key（默认保留本机凭据）", "Restore native API key (keep local credentials by default)"),
                    checked = include,
                    onCheckedChange = { include = it },
                )
                DshaSwitchRow(
                    title = t("项目恢复到容器主目录 /root", "Restore projects into container home /root"),
                    summary = t(
                        "关闭时恢复到应用私有项目区；两种方式都会创建新的独立目录。",
                        "Off restores into app-private projects. Both create a new separate directory.",
                    ),
                    checked = guestHome,
                    onCheckedChange = { guestHome = it },
                )
            }
        }
    }

    @Composable
    private fun PreviewConfirm() {
        val (id, info) = previewConfirm ?: return
        val decide = { accept: Boolean ->
            decidedPreview = id
            previewConfirm = null
            jobs.decide(id, accept)
            Unit
        }
        DshaActionDialog(
            title = t("确认恢复范围", "Confirm restore scope"),
            message = info,
            visible = true,
            onDismiss = { decide(false) },
            onClose = {},
            actions = listOf(
                DshaDialogAction(t("取消", "Cancel")) { decide(false) },
                DshaDialogAction(t("确认并恢复", "Confirm and restore")) { decide(true) },
            ),
        )
    }

    // ---------------- 逻辑（与原 Java 版一致） ----------------

    private fun scopeLabels() = listOf(
        t("应用数据", "Application data"),
        t("对话与附件", "Conversations and attachments"),
        t("设置", "Settings"),
        t("插件", "Plugins"),
        t("所选项目文件", "Selected project files"),
    )

    private fun dataHomeNames() = listOf(
        t("原容器数据目录", "Original container data directory"),
        t("宿主持久数据目录", "Persistent host data directory"),
    )

    private fun newFilename() = "DSHA-data-v5-" + UUID.randomUUID() + ".dshbak"

    private fun launchReexport(id: String) {
        pending.reexportId = id
        pending.filename = newFilename()
        destination.launch(pending.filename!!)
    }

    /** 剪贴板标记为敏感，系统不在预览浮层中显示密码。 */
    private fun copySensitive(secret: String) {
        val clip = ClipData.newPlainText("DSHA backup password", secret)
        if (Build.VERSION.SDK_INT >= 24) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }

    private fun automaticCopy() {
        val restore = intent.hasExtra("auto_restore_id")
        val id = intent.getStringExtra(if (restore) "auto_restore_id" else "auto_export_id") ?: return
        Thread({
            var secret: CharArray? = null
            try {
                val copy = VerifiedBackupCopy.inspect(
                    AndroidBackupFileSystem(),
                    File(filesDir.canonicalFile, "host-backup-operations"),
                    id,
                )
                if (copy.metadata["automatic"] != true) throw IOException("NOT_AUTOMATIC_COPY")
                secret = AutomaticBackups.password(this)
                if (restore) {
                    if (!jobs.prepareRestoreCopy(id, secret, setOf("application"), false, "PRIVATE")) {
                        throw IOException("RESTORE_BUSY")
                    }
                } else {
                    val password = String(secret!!)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) dialog = Dialog.AutoPassword(id, password)
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { status = errorText(BackupErrorCode.from(error)) }
            } finally {
                secret?.let { Arrays.fill(it, '\u0000') }
            }
        }, "automatic-backup-copy").start()
    }

    private fun verifiedCopies() {
        status = t("正在读取私有副本记录…", "Reading private copy records…")
        Thread({
            try {
                val found = jobs.verifiedCopies()
                val copies = found.valid
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val unavailable = if (found.unreadable.isEmpty()) "" else {
                        found.unreadable.size.toString() + t(
                            " 份记录暂不可用，原件已保留；其余副本仍可选择。",
                            " records are unavailable and retained; other copies can still be selected.",
                        )
                    }
                    if (copies.isEmpty()) {
                        status = t("还没有可重新导出的私有验证副本。", "No private verified copies are available yet.") + "\n" + unavailable
                        return@runOnUiThread
                    }
                    status = unavailable
                    dialog = Dialog.CopyChoice(copies.toList())
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) status = errorText(BackupErrorCode.from(error))
                }
            }
        }, "verified-backup-records").start()
    }

    private fun retainedAction() {
        try {
            val fs = AndroidBackupFileSystem()
            val files = filesDir.canonicalFile
            val catalogue = RetainedCatalogue(fs, files, UserDataLayout(fs, files).current())
            val entry = catalogue.resolve(intent.getStringExtra("retained_key"))
            val action = intent.getStringExtra("retained_action")
            val index = SCOPES.indexOf(entry.scope)
            if (index >= 0) { scopeIndex = index; pending.scopeIndex = index }
            when {
                action == "reexport" && entry.part == "encrypted" -> launchReexport(entry.id)
                action == "restore-copy" && entry.part == "encrypted" -> restorePassword(null, entry.id)
                action == "export-tree" -> {
                    val selected = NativeDataLocations.Selection()
                    selected.scope = entry.scope
                    selected.retainedKey = entry.key()
                    password(true, selected)
                }
                action == "restore-tree" -> dialog = Dialog.InspectTree(entry)
            }
        } catch (error: Exception) {
            status = errorText(BackupErrorCode.from(error))
        }
    }

    private fun selection(): NativeDataLocations.Selection {
        val selected = NativeDataLocations.Selection()
        selected.scope = SCOPES[scopeIndex.coerceIn(0, SCOPES.lastIndex)]
        selected.includeApiKey = includeKey
        selected.documentProjects.addAll(pending.projects)
        return selected
    }

    private fun locations() {
        if (pending.previewBusy) return
        pending.previewBusy = true
        val control = BackupControl(null)
        pending.previewControl = control
        val selected = selection()
        val owner = pending
        val app = applicationContext
        status = t("正在统计所选范围、大小与排除项…", "Reviewing selected scope, sizes and exclusions…")
        Thread({
            try {
                val value = NativeDataLocations(app).locate(selected, control)
                owner.preview.postValue(BackupPreview.inspect(value.sources, app.filesDir.usableSpace, control))
            } catch (error: Exception) {
                owner.previewError.postValue(BackupErrorCode.from(error))
            } finally {
                owner.previewBusy = false
            }
        }, "data-scope-preview").start()
    }

    private fun showScope(value: BackupPreview.Report) {
        val text = StringBuilder()
        text.append(value.files).append(t(" 个文件 · 已知大小 ", " files · known size ")).append(Fmt.bytes(value.bytes))
        text.append(t("\n可用空间：", "\nFree space: ")).append(Fmt.bytes(value.freeBytes))
        if (value.unknown > 0) text.append(t("\n尺寸未知的文件：", "\nFiles with unknown size: ")).append(value.unknown)
        if (value.unreadable > 0) text.append(t("\n缺失或不可读取项：", "\nMissing or unreadable entries: ")).append(value.unreadable)
        text.append(t("\n这是读取时的预估；实际导出仍会检查变化和空间。\n", "\nThese are read-time estimates; export checks changes and space again.\n"))
        for (root in value.roots) {
            text.append('\n').append(if (root.id == "native-settings") t("原生设置", "Native settings") else root.name)
                .append(" · ").append(scopeText(root.scope))
            text.append('\n').append(root.files).append(t(" 项 · ", " items · ")).append(Fmt.bytes(root.bytes))
            if (!root.location.isNullOrEmpty()) text.append('\n').append(root.location)
            text.append('\n')
        }
        if (value.exclusions.isNotEmpty()) {
            text.append(t("\n排除项：", "\nExclusions: "))
            for ((reason, count) in value.exclusions) text.append('\n').append(reason).append(" · ").append(count)
        }
        dialog = Dialog.Message(t("数据范围与大小", "Data scope and size"), text.toString())
    }

    private fun scopeText(scope: String?): String = when (scope) {
        "application" -> t("应用数据", "Application data")
        "sessions" -> t("对话与附件", "Conversations and attachments")
        "settings" -> t("设置", "Settings")
        "plugins" -> t("插件", "Plugins")
        "projects" -> t("项目文件", "Project files")
        else -> t("待确认范围", "Scope needs review")
    }

    private fun password(rescue: Boolean, retained: NativeDataLocations.Selection?) {
        if (jobs.state().busy) return
        dialog = Dialog.ExportPassword(rescue, retained)
    }

    private fun restorePassword(uri: Uri?, copy: String?) {
        if (jobs.state().busy) return
        dialog = Dialog.RestorePassword(uri, copy)
    }

    private fun restorePreview(value: NativeBackupJobs.State) {
        if (previewConfirm != null || decidedPreview == value.id) return
        try {
            val preview = jobs.preview(value.id)
            var info = t("归档已通过完整预检。\n文件/目录：", "Archive inspection passed.\nFiles/directories: ") + preview["entries"] +
                t("\n同名文件冲突：", "\nExisting-file conflicts: ") + preview["conflicts"] +
                t(
                    "\n确认后将停止 Web、终端和写任务，合并所选范围并替换归档中同名文件。未提及的文件保留。插件在独立目录隔离导入，项目恢复到新的独立目录。",
                    "\nConfirmation stops Web, terminals and writers, merges selected roots and replaces matching files from the archive. Unmentioned files remain. Plugins are quarantined and projects use new folders.",
                )
            if (preview["legacyConfirmationRequired"] == true) {
                info += t("\n这是历史格式备份；摘要验证不代表来源身份认证。", "\nThis is a legacy backup; checksum validation does not authenticate its sender.")
            }
            val warnings = preview["warnings"]
            if (warnings is List<*> && warnings.isNotEmpty()) {
                info += t(
                    "\n该来源含未确认的格式或恢复提示；不承诺可用于所有历史运行时。原件继续保留。",
                    "\nThis source has unconfirmed format or restore warnings. Compatibility with every historical runtime is not established. Originals remain retained.",
                )
            }
            info += t("\n项目位置：", "\nProject destination: ") +
                if (preview["projectDestination"] == "GUEST_HOME") {
                    t("容器主目录的新目录", "A new directory in container home")
                } else {
                    t("应用私有项目区的新目录", "A new directory in app-private projects")
                }
            previewConfirm = value.id to info
        } catch (_: Exception) {
            status = t("预检记录不可读取，请重新选择备份。", "The inspection record is unavailable. Select the backup again.")
        }
    }

    private fun render(value: NativeBackupJobs.State?) {
        if (value == null) return
        busy = value.busy
        if (value.stage == "PREVIEW") { restorePreview(value); return }
        previewConfirm = null
        val result = value.result
        val text = when {
            result == "COMPLETE" -> t("导出完成，私有产物与目标均已校验。", "Export complete; private artifact and destination verified.")
            result == "WRITTEN_UNVERIFIED" -> t("已写入，未完成目标读回校验；私有验证副本保留。", "Written, but destination readback was not verified. The private verified copy is retained.")
            result == "PARTIAL_RESCUE" -> t("部分救援：缺失或变化的内容已记录，不能视为完整备份。", "Partial rescue: missing or changed content was recorded; this is not a complete backup.")
            result == "BEST_EFFORT_RESCUE" -> t("尽力救援完成，未承诺应用级一致性。", "Best-effort rescue completed without an application-consistency guarantee.")
            result.startsWith("DATA_RESTORED_SETTINGS_REVIEW") -> t(
                "所选数据已保存。Profile 设置尚未应用：请进入「保留副本与旧树」逐个 profile 检查差异、选择字段并读回；插件和代码仍待审阅。",
                "Selected data saved. Profile settings are pending: open Retained copies and old trees to inspect each profile, select fields and verify readback. Plugins and code still require review.",
            ) + if (result.contains("API_KEY")) {
                t("\n原生 API Key 未恢复，请到模型配置确认凭据。", "\nThe native API key was not restored; verify credentials in Model configuration.")
            } else ""
            result == "DATA_RESTORED_API_KEY_MISSING" -> t("数据已恢复，但备份中的 API Key 无法读取；请到模型配置重新填写。", "Data restored, but the selected API key could not be read. Enter it again in Model configuration.")
            result == "DATA_RESTORED_API_KEY_OMITTED" -> t("数据已恢复；本次备份未包含 API Key，请到模型配置填写。", "Data restored; this backup did not include an API key. Enter it in Model configuration.")
            result == "DATA_RESTORED_PLUGINS_QUARANTINED" -> t("数据已恢复，插件、可执行配置与待确认的自定义数据已隔离保存，尚未启用。", "Data restored; plugins, executable configuration and unclassified custom data are quarantined and not enabled.")
            result.startsWith("DATA_RESTORED") -> t("数据已恢复；运行兼容性仍需检查。", "Data restored; runtime compatibility still needs verification.")
            result == "DATA_SAVED_PLUGIN_WARNINGS" -> t("数据已保存，插件依赖有缺失或未确认项。", "Data saved with missing or unverified plugin dependencies.")
            result == "RECOVERED_INTERRUPTED_COMMIT" -> t("中断的提交已恢复一致状态，原件保留。", "The interrupted commit is consistent again; originals were retained.")
            else -> stage(value.stage)
        }
        val projection = PortableSettings.errorCode()
        status = text + "\n" + value.entries + t(" 项 · ", " items · ") + Fmt.bytes(value.bytes) +
            (if (value.error.isEmpty()) "" else "\n" + errorText(value.error)) +
            (if (projection.isEmpty()) "" else "\n" + errorText(projection))
    }

    private fun errorText(code: String): String {
        val message = when (code) {
            "AUTHENTICATION_FAILED" -> t("密码不匹配，或备份已损坏、被修改。当前数据保持原位。", "The password does not match, or the backup is damaged or modified. Current data stayed in place.")
            "NO_SPACE" -> t("可用空间不足。请释放空间后重试，原件与已验证副本保留。", "Not enough free space. Free up space and retry; originals and verified copies are retained.")
            "CREDENTIAL_TEMPORARILY_UNAVAILABLE" -> ConfigStore.credentialMessage(CredentialRead.failed(CredentialRead.Reason.TRANSIENT_STORE))
            "CREDENTIAL_NEEDS_ATTENTION" -> ConfigStore.credentialMessage(CredentialRead.failed(CredentialRead.Reason.UNREADABLE))
            "CREDENTIAL_UNAVAILABLE" -> t("原设备的密钥不可用。可先不包含原生 API Key 导出其他数据，再重新填写凭据。", "The original device key is unavailable. Export other data without the native API key, then re-enter the credential.")
            "PERMISSION_DENIED", "SAF_PERMISSION_REVOKED" -> t("数据访问权限不可用。请重新选择目录或授予访问权限。", "Data access is unavailable. Select the folder again or restore its permission.")
            "CRYPTO_HEADER", "ARCHIVE_VERSION", "UNSUPPORTED_LEGACY_VERSION" -> t("此备份格式尚不支持，当前数据未覆盖。", "This backup format is unsupported. Current data was not overwritten.")
            "SOURCE_CHANGED", "TARGET_CHANGED", "INPUT_CHANGED" -> t("数据在核验期间发生变化，已停止操作并保留原件。", "Data changed during verification. The operation stopped and originals were retained.")
            "DESTINATION_CHECKSUM" -> t("目标读回校验失败，私有验证副本保留，可另选位置导出。", "Destination readback failed verification. The private verified copy is retained for export elsewhere.")
            "VERIFIED_COPY_CHANGED", "VERIFIED_COPY_FORMAT", "VERIFIED_COPY_RECORD", "VERIFIED_COPY_SCOPE" -> t("所选私有副本或记录未通过复核，已停止复制并保留原件。可选择另一份副本。", "The private copy or its record failed verification. Copying stopped and originals were retained. Choose another copy.")
            "CANCELLED" -> t("操作已取消；若已进入提交阶段，会先恢复一致状态。", "Operation cancelled; a commit already in progress converges before releasing protection.")
            else -> t("数据操作未完成。请查看错误代码和保留记录后重试。", "The data operation did not complete. Review its error code and retained records before retrying.")
        }
        return message + "\n" + t("错误代码：", "Error code: ") + code
    }

    private fun stage(stage: String): String = when (stage) {
        "IDLE" -> t("等待操作", "Ready")
        "PREPARING", "COPYING_INPUT" -> t("准备与读取数据", "Preparing and reading data")
        "CAPTURING", "ARCHIVING" -> t("生成私有快照", "Creating private snapshot")
        "ENCRYPTING" -> t("加密备份", "Encrypting backup")
        "AUTHENTICATING", "VERIFYING" -> t("验证完整性与认证", "Verifying integrity and authentication")
        "EXPORTING" -> t("写入所选位置", "Writing to destination")
        "COMMITTING" -> t("提交数据，正在保持一致性", "Committing data and maintaining consistency")
        "STOPPING_WRITERS", "PREPARING_RESTORE" -> t("准备恢复并停止写入", "Preparing restore and stopping writers")
        "INTERRUPTED" -> t("上次作业中断，原件与私有副本保留", "Previous operation interrupted; originals and private copies retained")
        "CANCELLED" -> t("已取消", "Cancelled")
        "FAILED", "FAILED_RETAINED" -> t("作业未完成，原件保留", "Operation incomplete; originals retained")
        else -> t("处理数据中", "Processing data")
    }

    private companion object {
        val SCOPES = listOf("application", "sessions", "settings", "plugins", "projects")
        fun t(zh: String, en: String): String = UiText.choose(zh, en)
    }
}