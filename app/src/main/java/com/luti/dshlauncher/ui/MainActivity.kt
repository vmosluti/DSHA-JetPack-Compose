package com.luti.dshlauncher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.MutableStateFlow
import com.luti.dshlauncher.data.repository.DshRuntime
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.component.bottombar.BottomBar
import com.luti.dshlauncher.ui.component.bottombar.MainPagerState
import com.luti.dshlauncher.ui.component.bottombar.SideRail
import com.luti.dshlauncher.ui.component.bottombar.rememberMainPagerState
import com.luti.dshlauncher.ui.navigation3.LocalNavigator
import com.luti.dshlauncher.ui.navigation3.Navigator
import com.luti.dshlauncher.ui.navigation3.Route
import com.luti.dshlauncher.ui.navigation3.rememberNavigator
import com.luti.dshlauncher.ui.screen.about.AboutScreen
import com.luti.dshlauncher.ui.screen.colorpalette.ColorPaletteScreen
import com.luti.dshlauncher.ui.screen.home.HomePager
import com.luti.dshlauncher.ui.screen.permission.PermissionScreen
import com.luti.dshlauncher.ui.screen.plugins.PluginsPager
import com.luti.dshlauncher.ui.screen.recovery.RecoveryScreen
import com.luti.dshlauncher.ui.screen.settings.SettingPager
import com.luti.dshlauncher.ui.dsha.ConfigPage
import com.luti.dshlauncher.ui.dsha.DeviceGrantsPage
import com.luti.dshlauncher.ui.dsha.InstallPage
import com.luti.dshlauncher.ui.dsha.LocalDshaPageOpener
import com.luti.dshlauncher.ui.dsha.OverlayPage
import com.luti.dshlauncher.ui.dsha.PluginPage
import com.luti.dshlauncher.ui.dsha.WorkspacePage
import com.deepseekharness.app.ui.DshaPageActivity
import com.deepseekharness.app.ui.LocalNetworkRequester
import com.deepseekharness.app.bridge.LocalNetworkAccess
import androidx.activity.result.contract.ActivityResultContracts

import com.luti.dshlauncher.ui.screen.startup.StartupScreen
import com.luti.dshlauncher.ui.screen.terminal.TerminalPager
import com.luti.dshlauncher.ui.theme.TemplateTheme
import com.luti.dshlauncher.ui.theme.LocalColorMode
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.theme.LocalEnableFloatingBottomBar
import com.luti.dshlauncher.ui.theme.LocalEnableFloatingBottomBarBlur
import com.luti.dshlauncher.ui.util.DiagLog
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import com.luti.dshlauncher.ui.util.rememberContentReady
import com.luti.dshlauncher.ui.viewmodel.MainActivityViewModel
import com.luti.dshlauncher.ui.viewmodel.MainPagerConfig
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
// 必须是 FragmentActivity：引擎的 ForegroundActivity 只登记 FragmentActivity，
// 隔离试运行 / 桥接确认弹窗都要靠它找到前台窗口，否则会一直卡在"请保持应用在前台"。
class MainActivity : androidx.fragment.app.FragmentActivity(), LocalNetworkRequester {

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) LocalNetworkAccess.applyConfiguredFeatures(this)
        }

    /** 主界面内的 ConfigPage / DeviceGrantsPage 开启 LAN 或无线 ADB 时调用。 */
    override fun requestLocalNetwork() {
        if (LocalNetworkAccess.granted(this)) LocalNetworkAccess.applyConfiguredFeatures(this)
        else localNetworkPermission.launch(LocalNetworkAccess.PERMISSION)
    }

    companion object {
        /** 由 DSHA 门禁跳板（com.deepseekharness.app.ui.MainActivity）设置：门禁已执行，无需再判。 */
        const val EXTRA_GATE_PASSED = "com.luti.dshlauncher.GATE_PASSED"

        /** 外部意图请求切换的主页面（0 首页 / 1 插件 / 2 终端 / 3 设置），由 MainScreen 消费。 */
        internal val requestedPage = MutableStateFlow<Int?>(null)
    }

    private val intentState = MutableStateFlow(0)

    /**
     * 消费 DSHA 引擎发来的导航意图（通知、PluginInstall、Onboarding 等）。
     * 语义与原 DSHA MainActivity 一致：open_plugins / open_terminal / open_launch / open_install / open_web。
     */
    private fun consumeNavigationExtras(intent: Intent?) {
        if (intent == null) return
        fun take(key: String): Boolean =
            intent.getBooleanExtra(key, false).also { if (it) intent.removeExtra(key) }
        when {
            take("open_plugins") -> requestedPage.value = 1
            take("open_terminal") -> requestedPage.value = 2
            take("open_install") -> {
                requestedPage.value = 3
                com.deepseekharness.app.ui.DshaPageActivity.open(
                    this, com.deepseekharness.app.ui.DshaPageActivity.PAGE_INSTALL
                )
            }
            take("open_launch") -> requestedPage.value = 0
            take("open_web") -> {
                requestedPage.value = 0
                DshRuntime.enter(this) { failure ->
                    if (failure != null) android.widget.Toast.makeText(this, failure, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DSHA 启动门禁（AGENTS.md：welcomed 校验不可绕过；维护 / 环境未就绪先进 Extract）。
        // 桌面图标直接启动时没经过跳板，这里补判一次。
        if (savedInstanceState == null && !intent.getBooleanExtra(EXTRA_GATE_PASSED, false)) {
            if (com.deepseekharness.app.ui.MainActivity.gate(this, intent)) {
                finish()
                return
            }
        }
        intent.removeExtra(EXTRA_GATE_PASSED)
        if (savedInstanceState == null) consumeNavigationExtras(intent)
        DshRuntime.ensurePolling()

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val uiMode = uiState.uiMode
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            // 系统栏只在这里接管：透明背景 + 按应用明暗切换图标颜色。
            // 主题层（MiuixTheme / MaterialTheme）不再重复改 insets controller，避免两边互相覆盖闪变。
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val navigator = rememberNavigator(Route.Main)
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            // DSHA 功能页互跳映射到本活动内的路由，避免同一内容再开一层 Activity。
            val pageOpener: (String) -> Boolean = remember(navigator) {
                { page ->
                    val route: Route? = when (page) {
                        DshaPageActivity.PAGE_INSTALL -> Route.Install
                        DshaPageActivity.PAGE_CONFIG -> Route.Config
                        DshaPageActivity.PAGE_DATA -> Route.DataBackup
                        DshaPageActivity.PAGE_DEVICE -> Route.DeviceAuth
                        DshaPageActivity.PAGE_PLUGINS -> Route.Plugins()
                        DshaPageActivity.PAGE_OVERLAY -> Route.Overlay
                        else -> null
                    }
                    if (route != null) navigator.push(route)
                    route != null
                }
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDshaPageOpener provides pageOpener,
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalUiMode provides uiMode,
            ) {
                TemplateTheme(appSettings = appSettings, uiMode = uiMode) {
                    val mainScreenEntry = @Composable {
                        MainScreen(
                            initialPage = selectedMainPage,
                            onPageChanged = viewModel::setSelectedMainPage,
                        )
                    }

                    val navDisplay = @Composable {
                        NavDisplay(
                            backStack = navigator.backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            onBack = {
                                navigator.pop()
                            },
                            // 不用 NavDisplay 默认的 700ms 交叉淡入（两页同时绘制、内容要等动画结束才加载）：
                            // 改成与系统活动切换一致的横向推入，主界面子页与独立活动观感统一。
                            transitionSpec = { pushEnter() togetherWith pushExit() },
                            popTransitionSpec = { popTransform() },
                            predictivePopTransitionSpec = { _ -> popTransform() },
                            entryProvider = entryProvider {
                                entry<Route.Main> { mainScreenEntry() }
                                entry<Route.About> { AboutScreen() }
                                entry<Route.ColorPalette> { ColorPaletteScreen() }
                                entry<Route.Permissions> { PermissionScreen() }
                                entry<Route.Home> { mainScreenEntry() }
                                entry<Route.Settings> { mainScreenEntry() }
                                entry<Route.BootLog> {
                                    StartupScreen(onFinished = { navigator.pop() })
                                }
                                entry<Route.Recovery> {
                                    RecoveryScreen(onBack = { navigator.pop() })
                                }
                                // 设置子页面直接渲染 DSHA 功能页，不再经过"行列表 → 新开活动"两层。
                                entry<Route.Install> { InstallPage(onBack = { navigator.pop() }) }
                                entry<Route.Config> { ConfigPage(onBack = { navigator.pop() }) }
                                entry<Route.DataBackup> { WorkspacePage(onBack = { navigator.pop() }) }
                                entry<Route.DeviceAuth> { DeviceGrantsPage(onBack = { navigator.pop() }) }
                                entry<Route.Plugins> { route ->
                                    PluginPage(onBack = { navigator.pop() }, showInstalled = route.showInstalled)
                                }
                                entry<Route.Overlay> { OverlayPage(onBack = { navigator.pop() }) }
                            }
                        )
                    }

                    when (uiMode) {
                        UiMode.Material -> androidx.compose.material3.Scaffold { navDisplay() }
                        UiMode.Miuix -> Scaffold { navDisplay() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationExtras(intent)
        // Increment intentState to trigger LaunchedEffect re-execution
        intentState.value += 1
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

// 子页面切换动画：新页从右推入、旧页轻微左移，返回时反向且退出页保持在上层。
private const val NAV_ANIM_MS = 300
private val navEasing = FastOutSlowInEasing

private fun pushEnter(): EnterTransition =
    slideInHorizontally(tween(NAV_ANIM_MS, easing = navEasing)) { it }

private fun pushExit(): ExitTransition =
    slideOutHorizontally(tween(NAV_ANIM_MS, easing = navEasing)) { -it / 4 }

private fun popEnter(): EnterTransition =
    slideInHorizontally(tween(NAV_ANIM_MS, easing = navEasing)) { -it / 4 }

private fun popExit(): ExitTransition =
    slideOutHorizontally(tween(NAV_ANIM_MS, easing = navEasing)) { it }

/** 返回时让退出页盖在进入页之上，否则新页会从旧页上方滑过，看起来像"前进"。 */
private fun popTransform(): ContentTransform =
    (popEnter() togetherWith popExit()).apply { targetContentZIndex = -1f }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
) {
    val navController = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val pagerState = key("main-pager-v2") {
        rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    }
    val mainPagerState = rememberMainPagerState(pagerState)
    var userScrollEnabled by remember { mutableStateOf(true) }
    val uiMode = LocalUiMode.current
    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface // Blur is not used in Material, this is just a placeholder
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    // 外部意图（通知 / 深链）请求切页
    val pendingPage by MainActivity.requestedPage.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPage) {
        val page = pendingPage ?: return@LaunchedEffect
        MainActivity.requestedPage.value = null
        if (page in 0 until MainPagerConfig.PAGE_COUNT) mainPagerState.animateToPage(page)
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useNavigationRail = isLandscape && !(uiMode == UiMode.Miuix && enableFloatingBottomBar)

    CompositionLocalProvider(
        LocalMainPagerState provides mainPagerState
    ) {
        val contentReady = rememberContentReady()
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    modifier = Modifier
                        .then(if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = if (contentReady) 1 else 0,
                    userScrollEnabled = userScrollEnabled,
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (isCurrentPage || contentReady) HomePager(navController, bottomInnerPadding, isCurrentPage)
                        1 -> if (isCurrentPage || contentReady) PluginsPager(bottomInnerPadding)
                        2 -> if (isCurrentPage || contentReady) TerminalPager(bottomInnerPadding)
                        3 -> if (isCurrentPage || contentReady) SettingPager(navController, bottomInnerPadding)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold {
                    Row {
                        SideRail(
                            blurBackdrop = blurBackdrop,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold { _ ->
                    Row {
                        SideRail(
                            blurBackdrop = blurBackdrop,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}