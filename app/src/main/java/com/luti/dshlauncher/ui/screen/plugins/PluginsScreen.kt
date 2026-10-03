package com.luti.dshlauncher.ui.screen.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deepseekharness.app.core.PluginRepository
import com.deepseekharness.app.ui.DshaPageActivity
import com.deepseekharness.app.util.BuiltinPlugins
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode

/** Compose 侧插件页所需的全部状态与动作。 */
class PluginsUi(
    val items: List<PluginRepository.Item>,
    val busy: Boolean,
    val message: String,
    /** 运行时未就绪 / 维护中时的拦截文案，非空则只读。 */
    val blockMessage: String,
    val safeMode: Boolean,
    val onToggle: (PluginRepository.Item, Boolean) -> Unit,
    /** 打开 DSHA 原生插件页（市场、安装、更新、回滚、导出等带确认流程的操作）。 */
    val onOpenFull: () -> Unit,
    val onRefresh: () -> Unit,
)

@Composable
private fun rememberPluginsUi(): PluginsUi {
    val context = LocalContext.current
    // PluginRepository 是 AndroidViewModel；Navigation3 条目的默认工厂不一定带 Application，显式指定。
    val repository = viewModel<PluginRepository>(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(repository.state().value) }

    DisposableEffect(repository, lifecycleOwner) {
        val observer = Observer<PluginRepository.State> { state = it }
        repository.state().observe(lifecycleOwner, observer)
        if (repository.state().value == null && !repository.isBusy) repository.refresh()
        onDispose { repository.state().removeObserver(observer) }
    }

    val current = state
    val openPage = com.luti.dshlauncher.ui.dsha.rememberDshaPageOpener()
    return PluginsUi(
        // 内部插件不对用户展示（与原 StartupRecovery / PluginFragment 一致）
        items = current?.items.orEmpty().filterNot { BuiltinPlugins.internal(it.name) },
        busy = current?.busy == true,
        message = current?.message.orEmpty(),
        blockMessage = runCatching { repository.environmentBlockMessage() }.getOrNull().orEmpty(),
        safeMode = repository.isSafeMode,
        onToggle = { item, enable ->
            // 官方插件的禁用需要确认（原 PluginFragment 行为），交给完整插件页处理
            if (item.official && !enable) openPage(DshaPageActivity.PAGE_PLUGINS)
            else repository.setEnabled(item, enable)
        },
        onOpenFull = { openPage(DshaPageActivity.PAGE_PLUGINS) },
        onRefresh = { if (!repository.isBusy) repository.refresh() },
    )
}

@Composable
fun PluginsPager(bottomInnerPadding: Dp) {
    val ui = rememberPluginsUi()
    when (LocalUiMode.current) {
        UiMode.Miuix -> PluginsPagerMiuix(bottomInnerPadding, ui)
        UiMode.Material -> PluginsPagerMaterial(bottomInnerPadding, ui)
    }
}