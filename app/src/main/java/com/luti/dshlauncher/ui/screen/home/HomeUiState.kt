package com.luti.dshlauncher.ui.screen.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.util.LatestVersionInfo

/** Accent blue used by the primary DSH action buttons. */
val DshBlue = Color(0xFF0A84FF)

@Immutable
data class HomeUiState(
    val checkUpdateEnabled: Boolean,
    val latestVersionInfo: LatestVersionInfo,
    val currentAppVersionCode: Long,
    val systemInfo: SystemInfo,
)

@Immutable
data class HomeActions(
    val onPermissionsClick: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onDshCardClick: () -> Unit,
    val onDshToggleRunning: () -> Unit,
    val onDshStart: () -> Unit,
    val onDshActionClick: (DshAction) -> Unit,
    val onOpenModelSettings: () -> Unit,
    val onOpenPluginSettings: () -> Unit,
    /** 打开 DSHA 应用更新页（下载 / 校验 / 安装）。 */
    val onOpenUpdate: () -> Unit,
)

/** Actions exposed by the DSH action card on the home page. */
enum class DshAction(
    @get:StringRes val label: Int,
    @get:StringRes val summary: Int,
    val icon: ImageVector,
) {
    Enter(R.string.dsh_action_enter, R.string.dsh_action_enter_summary, Icons.Rounded.Login),
    Reboot(R.string.dsh_options_reboot, R.string.dsh_action_reboot_summary, Icons.Rounded.RestartAlt),
    Stop(R.string.dsh_action_stop, R.string.dsh_action_stop_summary, Icons.Rounded.Stop),
    SafeBoot(
        R.string.dsh_options_safe_boot,
        R.string.dsh_action_safe_boot_summary,
        Icons.Rounded.Shield,
    ),
    Recovery(
        R.string.dsh_options_recovery,
        R.string.dsh_action_recovery_summary,
        Icons.Rounded.SettingsBackupRestore,
    ),
    ;

    companion object {
        /** Shown in the home action card while DSH is running. */
        val runningActions: List<DshAction> = listOf(Enter, Reboot, Stop, SafeBoot, Recovery)
    }
}