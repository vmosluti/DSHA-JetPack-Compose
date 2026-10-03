package com.luti.dshlauncher.ui.dsha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.luti.dshlauncher.data.repository.SettingsRepositoryImpl
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.theme.LocalColorMode
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.theme.LocalEnableFloatingBottomBar
import com.luti.dshlauncher.ui.theme.LocalEnableFloatingBottomBarBlur
import com.luti.dshlauncher.ui.theme.TemplateTheme
import com.luti.dshlauncher.ui.theme.ThemeController

/**
 * 独立 DSHA Activity / Fragment 共用的 Miuix / Material 主题注入。
 */
@Composable
fun DshaThemeHost(applySystemBars: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val repo = remember { SettingsRepositoryImpl() }
    val appSettings = remember { ThemeController.getAppSettings(context) }
    val uiMode = UiMode.fromValue(repo.uiMode)
    val darkMode = appSettings.colorMode.isDark ||
        (appSettings.colorMode.isSystem && isSystemInDarkTheme())
    DisposableEffect(activity, darkMode) {
        if (applySystemBars) activity?.let {
            it.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkMode },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkMode },
            )
            it.window.isNavigationBarContrastEnforced = false
        }
        onDispose { }
    }
    val systemDensity = LocalDensity.current
    val density = remember(systemDensity, repo.pageScale) {
        Density(systemDensity.density * repo.pageScale, systemDensity.fontScale)
    }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalColorMode provides appSettings.colorMode.value,
        LocalEnableBlur provides repo.enableBlur,
        LocalEnableFloatingBottomBar provides repo.enableFloatingBottomBar,
        LocalEnableFloatingBottomBarBlur provides repo.enableFloatingBottomBarBlur,
        LocalUiMode provides uiMode,
    ) {
        TemplateTheme(appSettings = appSettings, uiMode = uiMode, content = content)
    }
}

fun ComponentActivity.setDshaContent(content: @Composable () -> Unit) {
    setContent { DshaThemeHost(content = content) }
}

/**
 * 主界面（MainActivity）提供的页内跳转：把 DshaPageActivity.PAGE_* 映射到 Navigation3 路由，
 * 返回 true 表示已在当前活动内打开。独立 DshaPageActivity 中为 null，回落到新开活动。
 */
val LocalDshaPageOpener = staticCompositionLocalOf<((String) -> Boolean)?> { null }

/** DSHA 页面互跳统一入口：主界面内走子页面路由，避免同一内容再开一层活动。 */
@Composable
fun rememberDshaPageOpener(): (String) -> Unit {
    val context = LocalContext.current
    val inApp = LocalDshaPageOpener.current
    return remember(context, inApp) {
        { page ->
            if (inApp?.invoke(page) != true) com.deepseekharness.app.ui.DshaPageActivity.open(context, page)
        }
    }
}

fun Context.dshaT(zh: String, en: String): String =
    com.deepseekharness.app.util.UiText.choose(zh, en)