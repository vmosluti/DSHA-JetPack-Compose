package com.luti.dshlauncher.ui.dsha

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.backup.AutomaticBackups
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.ui.NativeDataActivity
import com.deepseekharness.app.ui.StorageActivity
import com.deepseekharness.app.util.BackupTaskState
import com.deepseekharness.app.util.MaintenanceErrorText
import com.deepseekharness.app.util.UiStateText
import com.luti.dshlauncher.ui.component.dialog.ConfirmResult
import com.luti.dshlauncher.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkspacePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val previewConfirm = rememberConfirmDialog()
    val controller = remember { HarnessController.get(context) }
    val task = remember { BackupTask.get(context) }

    var resumed by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var actionsEnabled by remember { mutableStateOf(true) }
    var statusDetailVisible by remember { mutableStateOf(false) }
    var previewId by remember { mutableLongStateOf(0L) }
    var previewText by remember { mutableStateOf("") }
    var handledPreviewId by remember { mutableLongStateOf(0L) }

    fun taskRejected() {
        val message = if (task.pendingMaintenance()) {
            context.dshaT("请先点「恢复中断维护」，原环境尚未确认。", "Tap “Resume interrupted maintenance” first. The original environment is not confirmed yet.")
        } else {
            context.dshaT("已有数据任务进行中，请等待或处理恢复预览。", "A data task is already running. Wait or handle the restore preview.")
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun gated(action: () -> Unit): () -> Unit = {
        if (!actionsEnabled) taskRejected() else action()
    }

    fun render() {
        val time = controller.config().lastBackupSuccess
        var success = if (time == 0L) {
            context.dshaT("还没有手动导出备份", "No manually exported backup yet")
        } else {
            context.dshaT("最近手动备份：", "Latest manual backup: ") +
                DateFormat.getDateTimeInstance().format(Date(time)) +
                "\n" + controller.config().lastBackupName
        }
        val automatic = AutomaticBackups.prefs(context).getLong("last", 0)
        if (automatic > 0) {
            success += "\n" + context.dshaT("最近自动副本：", "Latest automatic copy: ") +
                DateFormat.getDateTimeInstance().format(Date(automatic))
        }
        val failure = controller.config().lastBackupError
        val snapshot = task.snapshot()
        val pending = !controller.isEnvironmentReady() || task.pendingMaintenanceForUi()
        val busy = task.busy()
        status = success +
            (if (failure.isEmpty()) "" else context.dshaT("\n上次未完成：", "\nLast incomplete: ") + failure) +
            (if (snapshot.id == 0L) "" else "\n\n" + snapshot.kind + "\n" + MaintenanceErrorText.render(snapshot.detail)) +
            (if (pending) context.dshaT("\n\n有未完成的环境维护，请恢复原环境后再继续。", "\n\nUnfinished environment maintenance. Restore the original environment before continuing.") else "")
        actionsEnabled = !busy && !pending
        if (snapshot.status == BackupTaskState.Status.PREVIEW &&
            snapshot.id != 0L &&
            snapshot.id != handledPreviewId &&
            previewId == 0L &&
            resumed
        ) {
            handledPreviewId = snapshot.id
            previewText = UiStateText.render(snapshot.detail)
            previewId = snapshot.id
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    val id = previewId
                    if (id != 0L) {
                        task.decide(id, false)
                        previewId = 0L
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            render()
            delay(1000)
        }
    }

    LaunchedEffect(previewId) {
        val id = previewId
        if (id == 0L) return@LaunchedEffect
        try {
            val result = previewConfirm.awaitConfirm(
                title = context.dshaT("恢复预览", "Restore preview"),
                content = previewText,
                confirm = context.dshaT("恢复此备份", "Restore this backup"),
                dismiss = context.dshaT("取消", "Cancel"),
            )
            task.decide(id, result == ConfirmResult.Confirmed)
        } catch (cancelled: CancellationException) {
            task.decide(id, false)
            throw cancelled
        } finally {
            if (previewId == id) previewId = 0L
        }
    }

    DshaPageScaffold(
        title = context.dshaT("数据与备份", "Data and backups"),
        subtitle = context.dshaT("加密导出、恢复与文件共享。", "Encrypted export, restore and file sharing."),
        onBack = onBack,
    ) {
        item { DshaNote(context.dshaT("备份与恢复", "Backup and restore")) }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.dshaT("备份与恢复", "Backup and restore"),
                    context.dshaT("导出、导入和自动备份", "Export, import and automatic backups"),
                    gated {
                        context.startActivity(
                            Intent(context, NativeDataActivity::class.java).putExtra("data_mode", "backup"),
                        )
                    },
                ),
                DshaEntry(
                    context.dshaT("存储与文件", "Storage and files"),
                    context.dshaT("空间占用、缓存清理和文件共享", "Space usage, cache cleanup and file sharing"),
                ) {
                    context.startActivity(Intent(context, StorageActivity::class.java))
                },
            ),
        )
        item { DshaNote(context.dshaT("维护与恢复", "Maintenance and recovery")) }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.dshaT("环境修复与数据救援", "Environment repair and data rescue"),
                    context.dshaT("查看作业、重新导出已验证副本", "Review jobs and re-export verified copies"),
                    gated {
                        context.startActivity(Intent(context, NativeDataActivity::class.java))
                    },
                ),
                DshaEntry(
                    context.dshaT("重置配置，保留对话", "Reset settings, keep conversations"),
                    context.dshaT("重置当前 web profile 的普通设置；插件、对话和凭据保留。", "Reset ordinary web-profile settings. Plugins, conversations and credentials are kept."),
                ) {
                    scope.launch {
                        if (task.busy() || task.pendingMaintenance()) {
                            taskRejected()
                            return@launch
                        }
                        val result = confirm.awaitConfirm(
                            title = context.dshaT("重置配置？", "Reset settings?"),
                            content = context.dshaT(
                                "先停止 Web、终端和写任务，在隔离服务中重置当前 web profile 的普通设置并读回，再事务提交与工作目录 .env。插件构成、对话和凭据保留；原配置可恢复。无法确认时保留原件并显示原因。",
                                "Stop Web, terminals and writers. Reset ordinary settings of the web profile in an isolated service, read them back, then commit together with the workspace .env. Plugin composition, conversations and credentials are retained. Failures preserve originals and report the reason.",
                            ),
                            confirm = context.dshaT("保留原件并重置", "Retain originals and reset"),
                            dismiss = context.dshaT("取消", "Cancel"),
                        )
                        if (result == ConfirmResult.Confirmed && !task.resetConfig()) taskRejected()
                    }
                },
            ),
        )
        item {
            SelectionContainer {
                DshaNote(status)
            }
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.dshaT("备份与维护记录", "Backup and maintenance history"),
                    context.dshaT("查看完整状态文本", "View the full status text"),
                ) { statusDetailVisible = true },
            ),
        )
    }
    DshaMessageDialog(
        title = context.dshaT("备份与维护记录", "Backup and maintenance history"),
        message = status,
        visible = statusDetailVisible,
        onDismiss = { statusDetailVisible = false },
    )
}
