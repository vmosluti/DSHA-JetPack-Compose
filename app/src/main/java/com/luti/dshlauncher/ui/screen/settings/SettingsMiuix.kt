package com.luti.dshlauncher.ui.screen.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.dialog.rememberLoadingDialog
import com.luti.dshlauncher.ui.component.miuix.SendLogDialog
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.util.BlurredBar
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val loadingDialog = rememberLoadingDialog()
    val showSendLogDialog = rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.settings_check_update),
                            summary = stringResource(id = R.string.settings_check_update_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Update,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_check_update),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.checkUpdate,
                            onCheckedChange = actions.onSetCheckUpdate
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_ui_mode),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                            onSelectedIndexChange = actions.onSetUiModeIndex
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_theme),
                            summary = stringResource(id = R.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_theme),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenTheme
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_install),
                            summary = stringResource(id = R.string.settings_install_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Download,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_install),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenInstall,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_config),
                            summary = stringResource(id = R.string.settings_config_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Tune,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_config),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenConfig,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_data_backup),
                            summary = stringResource(id = R.string.settings_data_backup_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Backup,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_data_backup),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenDataBackup,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_device_auth),
                            summary = stringResource(id = R.string.settings_device_auth_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Security,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_device_auth),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenDeviceAuth,
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.settings_background),
                            summary = stringResource(id = R.string.settings_background_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BatterySaver,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_background),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.backgroundRunning,
                            onCheckedChange = actions.onSetBackgroundRunning
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.settings_eco_mode),
                            summary = stringResource(
                                id = if (uiState.ecoMode) R.string.settings_eco_mode_summary_on
                                else R.string.settings_eco_mode_summary_off,
                            ),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BatterySaver,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_eco_mode),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = uiState.ecoMode,
                            onCheckedChange = actions.onSetEcoMode
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_overlay),
                            summary = stringResource(id = R.string.settings_overlay_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Layers,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_overlay),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenOverlay,
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_updates),
                            summary = stringResource(id = R.string.settings_updates_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Update,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_updates),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenUpdates,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_diagnostics),
                            summary = stringResource(id = R.string.settings_diagnostics_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_diagnostics),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenDiagnostics,
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.send_log),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.send_log),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showSendLogDialog.value = true },
                        )
                        SendLogDialog(
                            show = showSendLogDialog.value,
                            onDismissRequest = { showSendLogDialog.value = false },
                            loadingDialog = loadingDialog
                        )
                        val about = stringResource(id = R.string.about)
                        ArrowPreference(
                            title = about,
                            startAction = {
                                Icon(
                                    Icons.Rounded.ContactPage,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = about,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenAbout,
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}