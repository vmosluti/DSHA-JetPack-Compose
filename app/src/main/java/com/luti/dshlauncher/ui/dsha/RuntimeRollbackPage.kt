package com.luti.dshlauncher.ui.dsha

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.deepseekharness.app.backup.AndroidBackupFileSystem
import com.deepseekharness.app.backup.ManagedRuntimeTransaction
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.ui.ExtractActivity
import com.deepseekharness.app.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 兼容运行时回退页（Compose 版，对应原 RuntimeRecoveryUi）。
 *
 * 只呈现可确认兼容的直接前代；实际切换仍由原维护事务核验并隔离试运行，
 * 不会恢复旧对话快照。
 */
@Composable
fun RuntimeRollbackPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { HarnessController.get(context) }
    var options by remember { mutableStateOf<List<ManagedRuntimeTransaction.RollbackOption>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ManagedRuntimeTransaction.RollbackOption?>(null) }

    // 读取选项要碰文件系统，放到 IO；对应原实现的独立线程 + runOnUiThread。
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                ManagedRuntimeTransaction.rollbackOptions(
                    AndroidBackupFileSystem(),
                    context.filesDir.canonicalFile,
                    controller.proot().expectedRuntimeDescriptor(),
                    controller.proot().installedRuntimeDescriptor(),
                )
            }
        }
        result
            .onSuccess { options = it }
            .onFailure { failure = it.message.orEmpty() }
    }

    val loaded = options
    val entries = loaded.orEmpty().map { item ->
        DshaEntry(
            title = "DSH " + item.dshVersion,
            summary = item.runtimeId.take(12),
            onClick = { pending = item },
        )
    }

    DshaPageScaffold(
        title = UiText.text("兼容运行时回退"),
        subtitle = UiText.text("先核对兼容性，通过隔离试运行后再切换。"),
        onBack = onBack,
    ) {
        when {
            failure != null -> item {
                DshaCard {
                    DshaSelectableText(
                        UiText.text("存在未完成或不可读取的维护记录。请先恢复中断维护，原件不会被覆盖。"),
                    )
                }
            }

            loaded == null -> item { DshaProgressBar(null) }

            loaded.isEmpty() -> item {
                DshaNote(
                    UiText.text(
                        "没有可确认兼容的直接前代。未知或受损原件仍保留；可先导出数据，再向前修复环境。",
                    ),
                )
            }

            else -> dshaEntryCard(entries)
        }
    }

    pending?.let { item ->
        DshaActionDialog(
            title = UiText.text("回退确认"),
            message = buildString {
                append("DSH: ")
                append(item.dshVersion)
                append("\n")
                append(UiText.text("个人数据"))
                append(": ")
                append(UiText.text("保持原位"))
                append("\n\n")
                append(
                    UiText.text(
                        "将停止 Web、终端和写入任务，复核保留文件，随后在当前 APK 下重新进行隔离试运行。不会恢复旧对话快照。",
                    ),
                )
            },
            visible = true,
            onDismiss = { pending = null },
            actions = listOf(
                DshaDialogAction(UiText.text("取消")) {},
                DshaDialogAction(UiText.text("验证并回退")) {
                    val task = BackupTask.get(context)
                    if (task.rollbackRuntime(item.operationId)) {
                        pending = null
                        context.startActivity(
                            Intent(context, ExtractActivity::class.java)
                                .putExtra("review_only", true)
                                .putExtra("data_task_id", task.snapshot().id),
                        )
                        onBack()
                    } else {
                        Toast.makeText(
                            context,
                            UiText.text("当前有任务或维护记录需要处理，请稍后重试。"),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            ),
        )
    }
}
