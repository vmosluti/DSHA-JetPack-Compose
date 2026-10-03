package com.luti.dshlauncher.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luti.dshlauncher.data.repository.DshRuntime
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

import com.luti.dshlauncher.permission.PermissionManager
import com.deepseekharness.app.core.UpdateEngine
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.dsha.observeAsState
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.navigation3.Navigator
import com.luti.dshlauncher.ui.navigation3.Route
import com.luti.dshlauncher.ui.viewmodel.HomeViewModel

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionManager = remember(context) { PermissionManager(context) }
    val permissionState by permissionManager.state.collectAsStateWithLifecycle()

    val dshRunning by DshRuntime.running.collectAsStateWithLifecycle()
    // 第三方浏览器入口只在 DSH 真正就绪后出现，端口取运行时实际值。
    // 只订阅派生出的 url：启动期间日志 / 阶段耗时每轮都在变，直接收集整个 State 会让首页跟着反复重组。
    val browserUrlFlow = remember {
        DshRuntime.state
            .map { if (it.ready && it.port > 0) "http://127.0.0.1:${it.port}/" else null }
            .distinctUntilChanged()
    }
    val browserUrl by browserUrlFlow.collectAsStateWithLifecycle(initialValue = null)

    // 版本更新：走 DSHA 引擎真实链（dsha.cc 更新清单），进程内最多自动检查一次。
    val updateEngine = remember(context) { UpdateEngine.get(context.applicationContext) }
    val updateState by updateEngine.state().observeAsState()
    val updateVersion = updateState?.release
        ?.takeIf { it.versionCode > uiState.currentAppVersionCode }
        ?.version

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    if (hasActivated) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
        }
        LaunchedEffect(uiState.checkUpdateEnabled) {
            updateEngine.checkOnStartup(uiState.checkUpdateEnabled)
        }
    }
    LifecycleResumeEffect(permissionManager) {
        permissionManager.refresh()
        onPauseOrDispose { }
    }

    // 启动：环境未就绪时先走 DSHA 安装 / 修复；否则调用引擎并打开启动日志页。
    val startDsh: () -> Unit = remember(navigator, context) {
        {
            when (DshRuntime.start(context)) {
                DshRuntime.StartResult.NeedsSetup -> DshRuntime.openSetup(context)
                DshRuntime.StartResult.Rejected -> android.widget.Toast.makeText(
                    context, com.deepseekharness.app.util.UiText.text("启动请求被拒绝，请查看恢复选项"),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                // Busy：已在启动 / 停止，直接看日志
                else -> navigator.push(Route.BootLog)
            }
        }
    }
    val enterDsh: () -> Unit = remember(context) {
        {
            (context as? android.app.Activity)?.let { activity ->
                DshRuntime.enter(activity) { failure ->
                    if (failure != null) android.widget.Toast.makeText(
                        context, failure, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            Unit
        }
    }

    val actions = HomeActions(
        onPermissionsClick = { navigator.push(Route.Permissions) },
        onOpenUrl = uriHandler::openUri,
        onDshCardClick = {
            if (!dshRunning) startDsh() else navigator.push(Route.BootLog)
        },
        onDshToggleRunning = {
            if (dshRunning) {
                DshRuntime.stop(context)
            } else {
                startDsh()
            }
        },
        onDshStart = startDsh,
        onDshActionClick = { action ->
            when (action) {
                DshAction.Stop -> DshRuntime.stop(context)
                DshAction.Recovery -> navigator.push(Route.Recovery)
                DshAction.Enter -> enterDsh()
                DshAction.Reboot -> when (DshRuntime.restart(context)) {
                    DshRuntime.StartResult.NeedsSetup -> DshRuntime.openSetup(context)
                    else -> navigator.push(Route.BootLog)
                }
                DshAction.SafeBoot -> when (DshRuntime.start(context, safe = true)) {
                    DshRuntime.StartResult.NeedsSetup -> DshRuntime.openSetup(context)
                    else -> navigator.push(Route.BootLog)
                }
                else -> Unit
            }
        },
        // 模型（API / Key）设置：DSHA 原生模型配置页
        onOpenModelSettings = {
            context.startActivity(
                android.content.Intent(context, com.deepseekharness.app.ui.ModelSetupActivity::class.java)
            )
        },
        // 插件设置：本活动内的子页面（插件市场 / 已装管理），不再只是切到插件 Tab。
        onOpenPluginSettings = { navigator.push(Route.Plugins()) },
        onOpenUpdate = {
            context.startActivity(
                android.content.Intent(context, com.deepseekharness.app.ui.UpdateActivity::class.java)
            )
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            permissionState = permissionState,
            dshRunning = dshRunning,
            updateVersion = updateVersion,
            browserUrl = browserUrl,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            permissionState = permissionState,
            dshRunning = dshRunning,
            updateVersion = updateVersion,
            browserUrl = browserUrl,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}