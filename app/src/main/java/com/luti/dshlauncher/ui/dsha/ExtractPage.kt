package com.luti.dshlauncher.ui.dsha

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 运行环境维护页的可渲染状态：由宿主在每次刷新时整体替换，页面本身不持有任务逻辑。
 *
 * 对应原手拼 View 的 status/detail/error 三个文本区与 retry/enter/format 三个按钮的可见性。
 */
data class ExtractUiState(
    val title: String,
    val status: String,
    val detail: String,
    val error: String? = null,
    val busy: Boolean = false,
    val retryTitle: String = "",
    val retryVisible: Boolean = true,
    val retryEnabled: Boolean = true,
    val enterTitle: String = "",
    val enterVisible: Boolean = true,
    val formatVisible: Boolean = true,
    val formatEnabled: Boolean = true,
)

/**
 * 解压门禁页（Compose 版）：只展示应用级维护任务进度，不提供返回，避免中途离开覆盖环境。
 *
 * 任务调度、就绪判定与自动跳转全部留在宿主；本页只负责渲染状态与回传用户意图。
 */
@Composable
fun ExtractPage(
    state: ExtractUiState,
    onRetry: () -> Unit,
    onEnter: () -> Unit,
    onEmergency: () -> Unit,
    onLogs: () -> Unit,
    onFormat: () -> Unit,
) {
    val context = LocalContext.current

    // 维护执行期间保持屏幕常亮，对应原 render() 里的 FLAG_KEEP_SCREEN_ON 切换。
    LaunchedEffect(state.busy) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (state.busy) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DshaPageScaffold(
        title = state.title,
        onBack = null,
        footer = buildList {
            if (state.retryVisible) {
                add(
                    DshaAction(
                        title = state.retryTitle,
                        primary = true,
                        enabled = state.retryEnabled,
                        onClick = onRetry,
                    ),
                )
            }
            if (state.enterVisible) {
                add(DshaAction(title = state.enterTitle, onClick = onEnter))
            }
            add(DshaAction(title = state.emergencyTitle, onClick = onEmergency))
            add(DshaAction(title = state.logsTitle, onClick = onLogs))
            if (state.formatVisible) {
                add(DshaAction(title = state.formatTitle, enabled = state.formatEnabled, onClick = onFormat))
            }
        },
    ) {
        item {
            DshaCard {
                if (state.busy) DshaProgressBar(null)
                DshaSelectableText(state.status)
            }
        }
        item { DshaLogBox(state.detail, height = 200.dp) }
        state.error?.let { message ->
            item {
                DshaCard {
                    DshaSelectableText(message)
                }
            }
        }
    }
}

private val ExtractUiState.emergencyTitle: String
    get() = com.deepseekharness.app.util.UiText.choose("启动应急 DSH", "Start emergency DSH")

private val ExtractUiState.logsTitle: String
    get() = com.deepseekharness.app.util.UiText.choose("查看本次维护记录", "View maintenance record")

private val ExtractUiState.formatTitle: String
    get() = com.deepseekharness.app.util.UiText.choose("格式化并全新开始", "Format and start fresh")