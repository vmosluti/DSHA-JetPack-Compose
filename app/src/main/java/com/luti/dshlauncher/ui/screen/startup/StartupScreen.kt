package com.luti.dshlauncher.ui.screen.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekharness.app.ui.DiagnosticActivity
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R
import com.luti.dshlauncher.data.repository.DshRuntime
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import kotlinx.coroutines.delay

/** 启动日志最多显示的行数；完整日志走「下载日志」。 */
private const val MAX_LINES = 400

/**
 * DSH 启动页：实时显示引擎 [com.deepseekharness.app.util.StartupTrace] 的启动输出。
 * Web 就绪后短暂停留再返回首页；启动失败（不再 starting 且未就绪）时保留日志不自动返回。
 * 等待本身不会终止启动（与原 LaunchFragment 一致）。
 */
@Composable
fun StartupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val state by DshRuntime.state.collectAsStateWithLifecycle()
    var sawBusy by remember { mutableStateOf(state.busy) }
    if (state.busy) sawBusy = true

    val finished = state.ready
    LaunchedEffect(finished) {
        if (finished) {
            delay(600)
            onFinished()
        }
    }

    val lines = remember(state.logRevision, state.stage, state.stageElapsedMs / 1000, state.status, finished) {
        buildList {
            val logLines = state.log.lineSequence().filter { it.isNotBlank() }.toList()
            if (logLines.isEmpty()) add(UiText.text("还没有日志。")) else addAll(logLines.takeLast(MAX_LINES))
            if (state.starting && state.stage.isNotEmpty()) {
                add(
                    state.stage + UiText.text(" · 本阶段 ") + state.stageElapsedMs / 1000 +
                        UiText.text(" 秒 · 总计 ") + state.elapsedMs / 1000 + UiText.text(" 秒")
                )
            }
            if (state.issues.isNotEmpty()) add(UiText.text("检测到插件或配置异常，可查看恢复选项。"))
            if (!state.busy && !state.ready && sawBusy && state.status.isNotEmpty()) add(state.status)
            if (finished) add(context.getString(R.string.dsh_boot_log_ready))
        }
    }

    // 下载日志：交给 DSHA 诊断页导出错误日志（统一脱敏）。
    val onDownloadLog: () -> Unit = {
        runCatching { context.startActivity(DiagnosticActivity.downloadLogs(context)) }
        Unit
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> StartupMiuix(
            lines = lines,
            finished = finished,
            onDownloadLog = onDownloadLog,
        )

        UiMode.Material -> StartupMaterial(
            lines = lines,
            finished = finished,
            onDownloadLog = onDownloadLog,
        )
    }
}