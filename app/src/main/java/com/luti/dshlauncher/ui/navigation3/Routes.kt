package com.luti.dshlauncher.ui.navigation3

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
sealed interface Route : NavKey, Parcelable {
    @Parcelize
    @Serializable
    data object Main : Route

    @Parcelize
    @Serializable
    data object Home : Route

    @Parcelize
    @Serializable
    data object Settings : Route

    @Parcelize
    @Serializable
    data object About : Route

    @Parcelize
    @Serializable
    data object ColorPalette : Route

    @Parcelize
    @Serializable
    data object Permissions : Route

    /** Simulated DSH startup / boot log page. */
    @Parcelize
    @Serializable
    data object BootLog : Route

    /** DSH recovery options page. */
    @Parcelize
    @Serializable
    data object Recovery : Route

    @Parcelize
    @Serializable
    data object Install : Route

    @Parcelize
    @Serializable
    data object Config : Route

    @Parcelize
    @Serializable
    data object DataBackup : Route

    @Parcelize
    @Serializable
    data object DeviceAuth : Route

    /** 插件市场 / 已装插件管理（原 DshaPageActivity.PAGE_PLUGINS）。 */
    @Parcelize
    @Serializable
    data class Plugins(val showInstalled: Boolean = false) : Route

    /** 悬浮条设置（原 DshaPageActivity.PAGE_OVERLAY）。 */
    @Parcelize
    @Serializable
    data object Overlay : Route
}