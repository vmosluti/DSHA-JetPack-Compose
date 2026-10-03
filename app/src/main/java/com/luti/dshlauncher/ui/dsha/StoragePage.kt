package com.luti.dshlauncher.ui.dsha

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.deepseekharness.app.backup.BackupErrorCode
import com.deepseekharness.app.backup.StorageMaintenance
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.Fmt
import com.deepseekharness.app.util.MaintenanceErrorText
import com.luti.dshlauncher.ui.component.dialog.ConfirmResult
import com.luti.dshlauncher.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SIZE_ZH = listOf(
    "当前 Linux 与 DSH",
    "运行时回退副本",
    "环境重建保护副本",
    "旧版更新记录",
    "备份副本",
    "兼容浏览器数据",
    "宿主个人数据",
    "应用缓存",
)
private val SIZE_EN = listOf(
    "Current Linux and DSH",
    "Runtime rollback copies",
    "Environment protection copies",
    "Legacy update records",
    "Backup copies",
    "Compatibility browser data",
    "Persistent user data",
    "App cache",
)

@Composable
fun StoragePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf<DshaKv>()) }
    var sharingVisible by remember { mutableStateOf(false) }
    val workdir = remember {
        HarnessController.get(context).config().getWorkdir()
    }

    fun load(remove: Boolean) {
        if (busy) return
        busy = true
        status = context.dshaT("正在处理…", "Working…")
        scope.launch {
            try {
                val (reclaimed, sizes) = withContext(Dispatchers.IO) {
                    val freed = if (remove) StorageMaintenance.clean(context) else 0L
                    freed to StorageMaintenance.inspect(context)
                }
                rows = sizes.entries.mapIndexed { index, entry ->
                    val label = context.dshaT(
                        SIZE_ZH.getOrElse(index) { entry.key },
                        SIZE_EN.getOrElse(index) { entry.key },
                    )
                    val extra = if (entry.value.unreadable > 0) {
                        context.dshaT(" + 部分不可读", " + unreadable entries")
                    } else {
                        ""
                    }
                    DshaKv(label, Fmt.bytes(entry.value.bytes) + extra)
                }
                status = if (remove) {
                    context.dshaT("已释放 ", "Freed ") + Fmt.bytes(reclaimed)
                } else {
                    context.dshaT(
                        "已完成统计。清理只移除可再生缓存和已核验的多余运行时副本，个人数据与备份保留。",
                        "Sizes updated. Cleanup removes only reproducible caches and verified excess runtime copies. Personal data and backups are retained.",
                    )
                }
            } catch (error: Exception) {
                status = MaintenanceErrorText.render(BackupErrorCode.from(error))
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { load(false) }

    DshaPageScaffold(
        title = context.dshaT("存储与文件", "Storage and files"),
        subtitle = context.dshaT(
            "完整 Linux 环境约占 1 GB。历史环境和回退副本另行保留；它们不等于聊天数据。以下为文件大小估算。",
            "The full Linux environment uses about 1 GB. Older environments and rollback copies are retained separately from chats. Sizes below are estimates.",
        ),
        onBack = onBack,
        footer = listOf(
            DshaAction(
                title = context.dshaT("清理可再生缓存与多余运行时副本", "Clean reproducible caches and excess runtime copies"),
                primary = true,
                enabled = !busy,
                onClick = {
                    scope.launch {
                        val result = confirm.awaitConfirm(
                            title = context.dshaT("清理存储空间", "Clean storage"),
                            content = context.dshaT(
                                "清理前会安全停止 DSH 和终端，再清理历史验收缓存，以及超过保留数量且已核验的受管回退副本。对话、附件、设置、插件、手动备份和无法核验的原件保留。",
                                "DSH and terminals are stopped safely before cleanup. Removes old verification caches and verified excess managed rollback copies. Keeps chats, attachments, settings, plugins, manual backups and unverified originals.",
                            ),
                            confirm = context.dshaT("开始清理", "Clean"),
                            dismiss = context.dshaT("取消", "Cancel"),
                        )
                        if (result == ConfirmResult.Confirmed) load(true)
                    }
                },
            ),
            DshaAction(
                title = context.dshaT("重新统计", "Refresh sizes"),
                enabled = !busy,
                onClick = { load(false) },
            ),
        ),
    ) {
        dshaEntryCard(
            listOf(
                DshaKv(context.dshaT("工作目录", "Workspace"), workdir).let {
                    DshaEntry(it.label, it.value)
                },
                DshaEntry(
                    context.dshaT("文件共享", "File sharing"),
                    context.dshaT("通过系统文件选择器或 MT 管理器访问 DSHA", "Access DSHA from the system file picker or MT Manager"),
                ) { sharingVisible = true },
            ),
        )
        dshaKvCard(rows)
        item { DshaNote(status) }
    }
    DshaMessageDialog(
        title = context.dshaT("文件共享", "File sharing"),
        message = context.dshaT(
            "在文件管理器中添加本地存储，选择 DocumentsProvider → DSHA。找不到 DSHA 时，先打开本 App 后重试。",
            "Add local storage in your file manager and select DocumentsProvider → DSHA. Open this app first if DSHA is missing.",
        ),
        visible = sharingVisible,
        onDismiss = { sharingVisible = false },
    )
}