package com.luti.dshlauncher.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedDropdownItem
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import com.luti.dshlauncher.ui.component.material.SegmentedSwitchItem
import com.luti.dshlauncher.ui.component.material.SendLogBottomSheet
import com.luti.dshlauncher.ui.component.material.SnackBarHost

/**
 * @author weishu
 * @date 2023/1/1.
 */
@Composable
fun SettingPagerMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.padding(bottom = bottomInnerPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Update,
                        title = stringResource(id = R.string.settings_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = uiState.checkUpdate,
                        onCheckedChange = actions.onSetCheckUpdate
                    )
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = buildList {
                    add {
                        SegmentedDropdownItem(
                            icon = Icons.Rounded.Dashboard,
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = UiMode.entries.map { it.name },
                            selectedIndex = if (uiState.uiMode == UiMode.Material.value) 1 else 0,
                            onItemSelected = actions.onSetUiModeIndex
                        )
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onOpenTheme,
                            headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                            leadingContent = { Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme)) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenInstall,
                            headlineContent = { Text(stringResource(id = R.string.settings_install)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_install_summary)) },
                            leadingContent = { Icon(Icons.Filled.Download, stringResource(id = R.string.settings_install)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenConfig,
                            headlineContent = { Text(stringResource(id = R.string.settings_config)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_config_summary)) },
                            leadingContent = { Icon(Icons.Filled.Tune, stringResource(id = R.string.settings_config)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenDataBackup,
                            headlineContent = { Text(stringResource(id = R.string.settings_data_backup)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_data_backup_summary)) },
                            leadingContent = { Icon(Icons.Filled.Backup, stringResource(id = R.string.settings_data_backup)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenDeviceAuth,
                            headlineContent = { Text(stringResource(id = R.string.settings_device_auth)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_device_auth_summary)) },
                            leadingContent = { Icon(Icons.Filled.Security, stringResource(id = R.string.settings_device_auth)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.BatterySaver,
                            title = stringResource(id = R.string.settings_background),
                            summary = stringResource(id = R.string.settings_background_summary),
                            checked = uiState.backgroundRunning,
                            onCheckedChange = actions.onSetBackgroundRunning
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.BatterySaver,
                            title = stringResource(id = R.string.settings_eco_mode),
                            summary = stringResource(
                                id = if (uiState.ecoMode) R.string.settings_eco_mode_summary_on
                                else R.string.settings_eco_mode_summary_off,
                            ),
                            checked = uiState.ecoMode,
                            onCheckedChange = actions.onSetEcoMode
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenOverlay,
                            headlineContent = { Text(stringResource(id = R.string.settings_overlay)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_overlay_summary)) },
                            leadingContent = { Icon(Icons.Filled.Layers, stringResource(id = R.string.settings_overlay)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    }
                )
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenUpdates,
                            headlineContent = { Text(stringResource(id = R.string.settings_updates)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_updates_summary)) },
                            leadingContent = { Icon(Icons.Filled.Update, stringResource(id = R.string.settings_updates)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenDiagnostics,
                            headlineContent = { Text(stringResource(id = R.string.settings_diagnostics)) },
                            supportingContent = { Text(stringResource(id = R.string.settings_diagnostics_summary)) },
                            leadingContent = { Icon(Icons.Filled.BugReport, stringResource(id = R.string.settings_diagnostics)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = { showBottomSheet = true },
                            headlineContent = { Text(stringResource(id = R.string.send_log)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.BugReport,
                                    stringResource(id = R.string.send_log)
                                )
                            },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenAbout,
                            headlineContent = { Text(stringResource(id = R.string.about)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.ContactPage,
                                    stringResource(id = R.string.about)
                                )
                            },
                        )
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (showBottomSheet) {
                SendLogBottomSheet(
                    onDismiss = { showBottomSheet = false },
                    snackbarHostState = snackBarHost,
                )
            }
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}