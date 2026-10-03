package com.luti.dshlauncher.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.luti.dshlauncher.ui.UiMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = true,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,
    val backgroundRunning: Boolean = false,
    val ecoMode: Boolean = false,
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onOpenAbout: () -> Unit,
    val onSetBackgroundRunning: (Boolean) -> Unit,
    val onOpenInstall: () -> Unit,
    val onOpenConfig: () -> Unit,
    val onOpenDataBackup: () -> Unit,
    val onOpenDeviceAuth: () -> Unit,
    val onSetEcoMode: (Boolean) -> Unit,
    val onOpenOverlay: () -> Unit,
    val onOpenUpdates: () -> Unit,
    val onOpenDiagnostics: () -> Unit,
)