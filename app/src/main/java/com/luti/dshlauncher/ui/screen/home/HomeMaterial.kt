package com.luti.dshlauncher.ui.screen.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.permission.PermissionState
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import com.luti.dshlauncher.ui.component.material.TonalCard
import com.luti.dshlauncher.ui.dsha.DshaCategory

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    permissionState: PermissionState,
    dshRunning: Boolean,
    updateVersion: String?,
    browserUrl: String?,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 仅在 UpdateEngine 发现比当前更新的版本时出现，点击进入更新页。
            if (updateVersion != null) {
                WarningCard(
                    message = stringResource(R.string.home_update_available, updateVersion),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = actions.onOpenUpdate,
                )
            }
            DshStatusCard(
                running = dshRunning,
                onCardClick = actions.onDshCardClick,
                onToggleRunning = actions.onDshToggleRunning,
            )
            DshActionCard(
                running = dshRunning,
                onStart = actions.onDshStart,
                onActionClick = actions.onDshActionClick,
            )
            DshaCategory(stringResource(R.string.dsh_category_quick_jump))
            QuickJumpCard(
                onOpenModelSettings = actions.onOpenModelSettings,
                onOpenPluginSettings = actions.onOpenPluginSettings,
            )
            if (browserUrl != null) {
                BrowserLinkCard(url = browserUrl, onOpenUrl = actions.onOpenUrl)
            }
            ExampleLinkCard(onOpenUrl = actions.onOpenUrl)
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

/**
 * DSH status card.
 *
 * Stopped: tapping the card starts DSH, the bottom action reads "启动".
 * Running: the bottom action reads "停止"; the available actions live in the action card below.
 */
@Composable
private fun DshStatusCard(
    running: Boolean,
    onCardClick: () -> Unit,
    onToggleRunning: () -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (running) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "dshStatusIconScale",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
        onClick = onCardClick,
        colors = CardDefaults.cardColors(
            containerColor =
                if (running) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            contentColor =
                if (running) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text =
                        stringResource(
                            if (running) R.string.dsh_status_running_title
                            else R.string.dsh_status_stopped_title
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!running) {
                    Text(
                        text = stringResource(R.string.dsh_status_stopped_summary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            if (running) R.string.dsh_status_running_action
                            else R.string.dsh_status_stopped_action
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.clickable { onToggleRunning() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                    )
                    Text(
                        text =
                            stringResource(
                                if (running) R.string.dsh_action_stop
                                else R.string.dsh_action_start
                            )
                    )
                }
            }
        }
    }
}

/**
 * Home action card: the primary "进入工作台" button, the 重启 / 停止 pair on one row, and the
 * secondary 安全启动 / 恢复选项 rows below them.
 */
@Composable
private fun DshActionCard(
    running: Boolean,
    onStart: () -> Unit,
    onActionClick: (DshAction) -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (running) 1f else 0.97f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "dshActionCardScale",
    )

    if (running) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onActionClick(DshAction.Enter) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DshBlue,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dsh_action_enter_workbench))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 次要操作用 Tonal 按钮，与蓝色主按钮「进入工作台」区分点击优先级。
                FilledTonalButton(
                    onClick = { onActionClick(DshAction.Reboot) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dsh_options_reboot))
                }
                FilledTonalButton(
                    onClick = { onActionClick(DshAction.Stop) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.dsh_action_stop))
                }
            }

            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                content = buildList {
                    listOf(DshAction.SafeBoot, DshAction.Recovery).forEach { action ->
                        add {
                            SegmentedListItem(
                                onClick = { onActionClick(action) },
                                headlineContent = { Text(stringResource(action.label)) },
                                supportingContent = { Text(stringResource(action.summary)) },
                                leadingContent = {
                                    Icon(action.icon, stringResource(action.label))
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                },
                            )
                        }
                    }
                }
            )
        }
    } else {
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = DshBlue,
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dsh_action_start))
        }
    }
}

/** "快速跳转" category: model settings and plugin settings shortcuts. */
@Composable
private fun QuickJumpCard(
    onOpenModelSettings: () -> Unit,
    onOpenPluginSettings: () -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content = listOf(
            {
                SegmentedListItem(
                    onClick = onOpenModelSettings,
                    headlineContent = { Text(stringResource(R.string.dsh_model_settings)) },
                    supportingContent = { Text(stringResource(R.string.dsh_model_settings_summary)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Tune, stringResource(R.string.dsh_model_settings))
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    },
                )
            },
            {
                SegmentedListItem(
                    onClick = onOpenPluginSettings,
                    headlineContent = { Text(stringResource(R.string.dsh_plugin_settings)) },
                    supportingContent = { Text(stringResource(R.string.dsh_plugin_settings_summary)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Extension, stringResource(R.string.dsh_plugin_settings))
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    },
                )
            }
        )
    )
}

@Composable
private fun WarningCard(
    message: String,
    color: Color = MaterialTheme.colorScheme.error,
    onClick: (() -> Unit)? = null
) {
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (onClick != null) {
        TonalCard(containerColor = color, onClick = onClick, content = content)
    } else {
        TonalCard(containerColor = color, content = content)
    }
}

@Composable
private fun BrowserLinkCard(url: String, onOpenUrl: (String) -> Unit) {
    TonalCard(onClick = { onOpenUrl(url) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_browser_link_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(Icons.Filled.OpenInNew, contentDescription = null)
        }
    }
}

@Composable
private fun ExampleLinkCard(onOpenUrl: (String) -> Unit) {
    val url = stringResource(R.string.home_example_link_url)
    TonalCard(onClick = { onOpenUrl(url) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = stringResource(R.string.home_example_link_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_example_link_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}