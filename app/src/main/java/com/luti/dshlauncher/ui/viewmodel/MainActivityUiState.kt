package com.luti.dshlauncher.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)
