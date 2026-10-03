package com.luti.dshlauncher.ui.screen.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.permission.PermissionState
import com.luti.dshlauncher.ui.component.miuix.WarningCard
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.util.BlurredBar
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import com.luti.dshlauncher.ui.dsha.DshaCategory
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    permissionState: PermissionState,
    dshRunning: Boolean,
    updateVersion: String?,
    browserUrl: String?,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
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
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 仅在 UpdateEngine 发现比当前更新的版本时出现，点击进入更新页。
                        if (updateVersion != null) {
                            WarningCard(
                                message = stringResource(R.string.home_update_available, updateVersion),
                                onClick = actions.onOpenUpdate,
                            )
                        }
                        DshStatusCardMiuix(
                            running = dshRunning,
                            onCardClick = actions.onDshCardClick,
                            onToggleRunning = actions.onDshToggleRunning,
                        )
                        DshActionCardMiuix(
                            running = dshRunning,
                            onStart = actions.onDshStart,
                            onActionClick = actions.onDshActionClick,
                        )
                        Box(Modifier.fillMaxWidth()) {
                            DshaCategory(stringResource(R.string.dsh_category_quick_jump))
                        }
                        QuickJumpCardMiuix(
                            onOpenModelSettings = actions.onOpenModelSettings,
                            onOpenPluginSettings = actions.onOpenPluginSettings,
                        )
                        if (browserUrl != null) {
                            BrowserLinkCard(url = browserUrl, onOpenUrl = actions.onOpenUrl)
                        }
                        ExampleLinkCard(onOpenUrl = actions.onOpenUrl)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

/**
 * DSH status card.
 *
 * Stopped: tapping the card starts DSH, the bottom action reads "启动".
 * Running: the bottom action reads "停止"; the available actions live in the action card below.
 */
@Composable
private fun DshStatusCardMiuix(
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
    val iconColor = if (running) Color(0xFF36D167) else Color(0xFFF72727)
    val containerColor = if (running) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)
    val textColor = Color(0xFF111111)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onCardClick,
        showIndication = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 70.dp, y = 44.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier
                        .size(182.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    imageVector =
                        if (running) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 28.dp, end = 148.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text =
                            stringResource(
                                if (running) R.string.dsh_status_running_title
                                else R.string.dsh_status_stopped_title
                            ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    if (!running) {
                        Text(
                            text = stringResource(R.string.dsh_status_stopped_summary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.72f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (running) R.string.dsh_status_running_action
                                else R.string.dsh_status_stopped_action
                            ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.78f),
                    )
                    Row(
                        modifier = Modifier.clickable { onToggleRunning() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            tint = textColor.copy(alpha = 0.78f),
                            contentDescription = null,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (running) R.string.dsh_action_stop
                                    else R.string.dsh_action_start
                                ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Home action card: a rounded container holding the primary "进入工作台" button, the 重启 / 停止
 * pair on one row, and the secondary 安全启动 / 恢复选项 rows below them.
 */
@Composable
private fun DshActionCardMiuix(
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (running) {
                Button(
                    onClick = { onActionClick(DshAction.Enter) },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    colors = ButtonDefaults.buttonColors(
                        color = DshBlue,
                        contentColor = Color.White,
                    ),
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
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
                    Button(
                        onClick = { onActionClick(DshAction.Reboot) },
                        modifier = Modifier.weight(1f),
                        cornerRadius = 14.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.dsh_options_reboot))
                    }
                    Button(
                        onClick = { onActionClick(DshAction.Stop) },
                        modifier = Modifier.weight(1f),
                        cornerRadius = 14.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.dsh_action_stop))
                    }
                }

                listOf(DshAction.SafeBoot, DshAction.Recovery).forEach { action ->
                    ArrowPreference(
                        title = stringResource(action.label),
                        summary = stringResource(action.summary),
                        startAction = {
                            Icon(
                                action.icon,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(action.label),
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { onActionClick(action) },
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    colors = ButtonDefaults.buttonColors(
                        color = DshBlue,
                        contentColor = Color.White,
                    ),
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dsh_action_start))
                }
            }
        }
    }
}

/** "快速跳转" category: model settings and plugin settings shortcuts. */
@Composable
private fun QuickJumpCardMiuix(
    onOpenModelSettings: () -> Unit,
    onOpenPluginSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = stringResource(R.string.dsh_model_settings),
            summary = stringResource(R.string.dsh_model_settings_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Tune,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.dsh_model_settings),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onOpenModelSettings,
        )
        ArrowPreference(
            title = stringResource(R.string.dsh_plugin_settings),
            summary = stringResource(R.string.dsh_plugin_settings_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Extension,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.dsh_plugin_settings),
                    tint = colorScheme.onBackground
                )
            },
            onClick = onOpenPluginSettings,
        )
    }
}

@Composable
private fun TopBar(
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun BrowserLinkCard(
    url: String,
    onOpenUrl: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.home_browser_link_title),
            summary = url,
            endActions = {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    tint = colorScheme.onSurface,
                    contentDescription = null
                )
            },
            onClick = { onOpenUrl(url) }
        )
    }
}

@Composable
private fun ExampleLinkCard(
    onOpenUrl: (String) -> Unit,
) {
    val url = stringResource(R.string.home_example_link_url)
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.home_example_link_title),
            summary = stringResource(R.string.home_example_link_subtitle),
            endActions = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    tint = colorScheme.onSurface,
                    contentDescription = null
                )
            },
            onClick = { onOpenUrl(url) }
        )
    }
}