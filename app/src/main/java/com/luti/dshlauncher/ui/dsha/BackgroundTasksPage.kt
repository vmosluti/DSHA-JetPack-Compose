package com.luti.dshlauncher.ui.dsha

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.backup.NativeBackupJobs
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.RuntimeTasks
import com.deepseekharness.app.ui.AutomaticBackupActivity
import com.deepseekharness.app.ui.MainActivity
import com.deepseekharness.app.ui.NativeDataActivity
import com.deepseekharness.app.util.UiStateText
import kotlinx.coroutines.delay

@Composable
fun BackgroundTasksPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }

    fun render(): String {
        val text = StringBuilder()
        val maintenance = BackupTask.get(context).snapshot()
        if (maintenance.busy()) {
            text.append(UiStateText.render(maintenance.kind)).append('\n')
                .append(UiStateText.render(maintenance.detail)).append("\n\n")
        }
        val backup = NativeBackupJobs.get(context).state()
        if (backup.busy) {
            text.append(context.dshaT("数据与备份", "Data and backups")).append('\n')
                .append(dshaBackupStage(backup.stage)).append(" · ")
                .append(backup.entries)
                .append(context.dshaT(" 项", " entries"))
                .append("\n\n")
        }
        for (row in RuntimeTasks.snapshot()) {
            text.append(UiStateText.render(row.kind)).append(" · ")
                .append(row.elapsedMillis / 1000)
                .append(context.dshaT(" 秒", " s")).append('\n')
            text.append(
                if (row.detail.isEmpty()) context.dshaT("正在执行", "Running")
                else UiStateText.render(row.detail),
            ).append("\n\n")
        }
        if (text.isEmpty()) {
            text.append(context.dshaT("当前没有原生后台任务。", "No native background tasks are running."))
        }
        return text.toString().trim()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (true) {
            status = render()
            delay(1000)
        }
    }

    fun openMain(extra: String) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(extra, true),
        )
        (context as? Activity)?.finish()
    }

    DshaPageScaffold(
        title = context.dshaT("后台任务", "Background tasks"),
        subtitle = context.dshaT(
            "查看正在执行的操作和进度。需要取消时，请进入对应页面。",
            "View active operations and progress. Open the related page to cancel safely.",
        ),
        onBack = onBack,
    ) {
        item {
            SelectionContainer {
                DshaNote(status)
            }
        }
        item {
            DshaNote(context.dshaT("相关页面", "Related pages"))
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.dshaT("插件管理", "Plugin management"),
                    context.dshaT("打开插件页查看安装与更新任务", "Open plugins to review install and update tasks"),
                ) { openMain("open_plugins") },
                DshaEntry(
                    context.dshaT("终端", "Terminals"),
                    context.dshaT("打开终端页查看会话任务", "Open terminals to review session tasks"),
                ) { openMain("open_terminal") },
                DshaEntry(
                    context.dshaT("数据与备份", "Data and backups"),
                    context.dshaT("打开备份与恢复作业", "Open backup and restore jobs"),
                ) { context.startActivity(Intent(context, NativeDataActivity::class.java)) },
                DshaEntry(
                    context.dshaT("自动备份", "Automatic backups"),
                    context.dshaT("打开自动备份计划与记录", "Open automatic backup schedule and records"),
                ) { context.startActivity(Intent(context, AutomaticBackupActivity::class.java)) },
                DshaEntry(
                    context.dshaT("安装与环境", "Installation and environment"),
                    context.dshaT("打开安装页查看环境维护", "Open installation to review environment maintenance"),
                ) { openMain("open_install") },
            ),
        )
    }
}