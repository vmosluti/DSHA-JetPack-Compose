package com.luti.dshlauncher.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.navigation3.Navigator
import com.luti.dshlauncher.ui.navigation3.Route
import com.luti.dshlauncher.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val actions = SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onOpenAbout = { navigator.push(Route.About) },
        onSetBackgroundRunning = viewModel::setBackgroundRunning,
        onOpenInstall = { navigator.push(Route.Install) },
        onOpenConfig = { navigator.push(Route.Config) },
        onOpenDataBackup = { navigator.push(Route.DataBackup) },
        onOpenDeviceAuth = { navigator.push(Route.DeviceAuth) },
        onSetEcoMode = viewModel::setEcoMode,
        onOpenOverlay = { navigator.push(Route.Overlay) },
        onOpenUpdates = {
            context.startActivity(android.content.Intent(context, com.deepseekharness.app.ui.UpdateActivity::class.java))
        },
        onOpenDiagnostics = {
            context.startActivity(android.content.Intent(context, com.deepseekharness.app.ui.DiagnosticActivity::class.java))
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}