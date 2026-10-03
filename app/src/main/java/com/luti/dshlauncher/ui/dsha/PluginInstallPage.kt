package com.luti.dshlauncher.ui.dsha

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.PluginRepository
import com.deepseekharness.app.util.PluginInstallLink
import com.deepseekharness.app.util.PluginSource
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiStateText
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R

/**
 * 网站唤起的插件安装确认页：显示真实包信息，用户确认后才写入插件配置。
 *
 * @param link 解析好的安装请求；解析失败时为 null，并由 [linkError] 给出原因
 * @param repository builtin 请求或链接无效时为 null
 * @param autoInspect 首次进入且环境就绪时自动解析（对应原 saved == null 分支）
 */
@Composable
fun PluginInstallPage(
    link: PluginInstallLink?,
    linkError: String?,
    repository: PluginRepository?,
    autoInspect: Boolean,
    onClose: () -> Unit,
    onOpenMain: (plugins: Boolean) -> Unit,
    onDeferUntilReady: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { HarnessController.get(context) }
    var state by remember { mutableStateOf(repository?.state()?.value) }
    var preview by remember { mutableStateOf(repository?.preview()?.value) }

    if (repository != null) {
        DisposableEffect(repository, lifecycleOwner) {
            val stateObserver = Observer<PluginRepository.State> { state = it }
            val previewObserver = Observer<PluginRepository.Preview?> { preview = it }
            repository.state().observe(lifecycleOwner, stateObserver)
            repository.preview().observe(lifecycleOwner, previewObserver)
            onDispose {
                repository.state().removeObserver(stateObserver)
                repository.preview().removeObserver(previewObserver)
            }
        }
    }

    fun inspect() {
        val request = link ?: return
        val repo = repository ?: return
        try {
            repo.inspect(PluginSource.parse(request.url), request.sha256, request.name, request.version)
        } catch (error: IllegalArgumentException) {
            repo.selectionMessage(UiText.text(error.message.orEmpty()))
        }
    }

    LaunchedEffect(Unit) {
        if (autoInspect && repository != null && controller.isEnvironmentReady) inspect()
    }

    val ready = controller.isEnvironmentReady
    val status: String
    val details: String
    val buttonLabel: String
    val buttonEnabled: Boolean
    val onButton: () -> Unit
    when {
        link == null -> {
            status = UiText.text(linkError.orEmpty())
            details = ""
            buttonLabel = context.getString(R.string.ui_m0185)
            buttonEnabled = false
            onButton = {}
        }
        link.builtin.isNotEmpty() || repository == null -> {
            status = UiText.text("请在插件管理中查看 ") + link.builtin + UiText.text(" 的安装和启用状态")
            details = ""
            buttonLabel = UiText.text("打开插件管理")
            buttonEnabled = true
            onButton = { onOpenMain(true) }
        }
        else -> {
            val message = state?.message.orEmpty()
            status = when {
                message.isNotEmpty() -> UiStateText.render(message)
                !ready -> UiText.text("请先完成 DSHA 首次初始化，完成后会返回插件安装确认页")
                else -> ""
            }
            val shown = preview
            if (repository.installationSucceeded()) {
                details = UiText.text(repository.installedDescription())
                buttonLabel = UiText.text("安装完成")
                buttonEnabled = false
            } else if (shown != null) {
                details = shown.description()
                buttonLabel = UiText.text("确认安装")
                buttonEnabled = state?.busy != true
            } else {
                details = UiText.text("下载并解析插件包后，会显示真实作者、版本和兼容范围。确认安装前不会启用插件。\n\n") +
                    SensitiveData.redact(link.url)
                buttonLabel = if (ready) UiText.text("解析链接 / 重试") else UiText.text("初始化 DSHA")
                buttonEnabled = state?.busy != true
            }
            onButton = {
                when {
                    !controller.isEnvironmentReady -> onDeferUntilReady()
                    repository.preview().value != null -> repository.confirmPreview()
                    else -> inspect()
                }
            }
        }
    }

    DshaPageScaffold(
        title = context.getString(R.string.ui_m0106),
        onBack = onClose,
        footer = listOf(
            DshaAction(title = buttonLabel, primary = true, enabled = buttonEnabled, onClick = onButton),
            DshaAction(title = context.getString(R.string.ui_m0013), onClick = { onOpenMain(true) }),
        ),
    ) {
        if (state?.busy == true) item { DshaProgressBar(null) }
        if (status.isNotEmpty()) item { DshaCard { DshaSelectableText(status) } }
        if (details.isNotEmpty()) item { DshaCard { DshaSelectableText(details) } }
    }
}
