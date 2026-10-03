package com.luti.dshlauncher.ui.screen.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.luti.dshlauncher.data.repository.DshRuntime
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.util.BlurredBar
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 第 2 区块「恢复与修复」的 4 个可点击条目。 */
private val RecoveryRepairActions: List<Int> = listOf(
    R.string.recovery_action_retry_boot,
    R.string.recovery_action_safe_boot,
    R.string.recovery_action_new_profile,
    R.string.recovery_action_restore_interrupted,
)

/** 第 3 区块「其他工具」的 3 个可点击条目。 */
private val RecoveryToolActions: List<Int> = listOf(
    R.string.recovery_action_env_repair,
    R.string.recovery_action_view_logs,
    R.string.recovery_action_refresh_records,
)

/**
 * 恢复条目点击 → DSHA 引擎。
 * 配置类修复（新建配置 / 恢复中断修复 / 刷新记录）涉及快照校验与确认对话框，
 * 交给原生恢复工作台 StartupRecoveryActivity，不在这里重写这些不变式。
 */
@Composable
private fun rememberRecoveryClick(): (Int) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navigator = com.luti.dshlauncher.ui.navigation3.LocalNavigator.current
    return remember(context, navigator) {
        { rowRes ->
            fun toast(msg: String) =
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            fun open(cls: Class<*>) = context.startActivity(android.content.Intent(context, cls))
            when (rowRes) {
                R.string.recovery_action_retry_boot, R.string.recovery_action_safe_boot -> {
                    val safe = rowRes == R.string.recovery_action_safe_boot
                    val controller = com.deepseekharness.app.core.HarnessController.get(context)
                    when {
                        !controller.isEnvironmentReady -> toast(
                            com.deepseekharness.app.util.UiText.text("运行环境尚未就绪，可先修复配置或导出数据，再进入安装与修复。")
                        )
                        com.deepseekharness.app.core.StartupRepairs.pending(context) -> toast(
                            com.deepseekharness.app.util.UiText.text("配置修复未完成，请使用“恢复中断的配置修复”。")
                        )
                        else -> {
                            controller.recoverWeb(safe, null) { }
                            DshRuntime.startKeepAlive(context)
                            navigator.pop()
                            navigator.push(com.luti.dshlauncher.ui.navigation3.Route.BootLog)
                        }
                    }
                }
                R.string.recovery_action_new_profile,
                R.string.recovery_action_restore_interrupted,
                R.string.recovery_action_refresh_records ->
                    open(com.deepseekharness.app.ui.StartupRecoveryActivity::class.java)
                R.string.recovery_action_env_repair -> context.startActivity(
                    android.content.Intent(context, com.deepseekharness.app.ui.ExtractActivity::class.java)
                        .putExtra("review_only", true)
                )
                R.string.recovery_action_view_logs ->
                    context.startActivity(com.deepseekharness.app.ui.DiagnosticActivity.downloadLogs(context))
            }
        }
    }
}

/**
 * 恢复选项页面。按 [LocalUiMode] 分发到 miuix / material 两套实现。
 * 全屏 push 页面，只有 [onBack] 回调，不涉及导航。
 */
@Composable
fun RecoveryScreen(onBack: () -> Unit) {
    val onRowClick = rememberRecoveryClick()
    when (LocalUiMode.current) {
        UiMode.Miuix -> RecoveryScreenMiuix(onBack = onBack, onRowClick = onRowClick)
        UiMode.Material -> RecoveryScreenMaterial(onBack = onBack, onRowClick = onRowClick)
    }
}

@Composable
private fun RecoveryScreenMiuix(onBack: () -> Unit, onRowClick: (Int) -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.recovery_title),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
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
                    RecoveryBootReasonCardMiuix()
                }
                item {
                    RecoveryActionCardMiuix(
                        titleRes = R.string.recovery_section_repair,
                        summaryRes = R.string.recovery_repair_summary,
                        rows = RecoveryRepairActions,
                        onRowClick = onRowClick,
                    )
                }
                item {
                    RecoveryActionCardMiuix(
                        titleRes = R.string.recovery_section_tools,
                        summaryRes = R.string.recovery_tools_summary,
                        rows = RecoveryToolActions,
                        onRowClick = onRowClick,
                    )
                }
                item {
                    RecoveryTextCardMiuix(
                        titleRes = R.string.recovery_section_recent_boots,
                        summaryRes = R.string.recovery_recent_boots_empty,
                    )
                }
                item {
                    RecoveryTextCardMiuix(
                        titleRes = R.string.recovery_section_snapshots,
                        summaryRes = R.string.recovery_snapshots_summary,
                    )
                }
                item {
                    Spacer(
                        modifier = Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp,
                        ),
                    )
                }
            }
        }
    }
}

/** 第 1 区块：区块标题 + 进入原因 + 说明段落。 */
@Composable
private fun RecoveryBootReasonCardMiuix() {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.recovery_section_boot),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.recovery_boot_reason),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.recovery_boot_desc),
                fontSize = 13.sp,
                color = colorScheme.onBackground.copy(alpha = 0.72f),
            )
        }
    }
}

/** 区块标题 + 区块摘要 + 若干可点击条目。 */
@Composable
private fun RecoveryActionCardMiuix(
    titleRes: Int,
    summaryRes: Int,
    rows: List<Int>,
    onRowClick: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(titleRes),
                summary = stringResource(summaryRes),
            )
            rows.forEach { rowRes ->
                BasicComponent(
                    title = stringResource(rowRes),
                    onClick = { onRowClick(rowRes) },
                )
            }
        }
    }
}

/** 区块标题 + 区块摘要（无可点击条目）。 */
@Composable
private fun RecoveryTextCardMiuix(
    titleRes: Int,
    summaryRes: Int,
) {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        BasicComponent(
            title = stringResource(titleRes),
            summary = stringResource(summaryRes),
        )
    }
}

@Composable
private fun RecoveryScreenMaterial(onBack: () -> Unit, onRowClick: (Int) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    MaterialScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { MaterialText(stringResource(R.string.recovery_title)) },
                navigationIcon = {
                    MaterialIconButton(onClick = onBack) {
                        MaterialIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            MaterialSectionHeader(
                titleRes = R.string.recovery_section_boot,
                summaryRes = R.string.recovery_boot_reason,
            )
            MaterialParagraph(textRes = R.string.recovery_boot_desc)

            MaterialSectionHeader(
                titleRes = R.string.recovery_section_repair,
                summaryRes = R.string.recovery_repair_summary,
            )
            MaterialActionList(rows = RecoveryRepairActions, onRowClick = onRowClick)

            MaterialSectionHeader(
                titleRes = R.string.recovery_section_tools,
                summaryRes = R.string.recovery_tools_summary,
            )
            MaterialActionList(rows = RecoveryToolActions, onRowClick = onRowClick)

            MaterialSectionHeader(
                titleRes = R.string.recovery_section_recent_boots,
                summaryRes = R.string.recovery_recent_boots_empty,
            )

            MaterialSectionHeader(
                titleRes = R.string.recovery_section_snapshots,
                summaryRes = R.string.recovery_snapshots_summary,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Spacer(
                modifier = Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}

@Composable
private fun MaterialSectionHeader(
    titleRes: Int,
    summaryRes: Int,
) {
    MaterialText(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 4.dp),
    )
    MaterialText(
        text = stringResource(summaryRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 4.dp),
    )
}

@Composable
private fun MaterialParagraph(textRes: Int) {
    MaterialText(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun MaterialActionList(rows: List<Int>, onRowClick: (Int) -> Unit) {
    val items: List<@Composable () -> Unit> = rows.map { rowRes ->
        {
            SegmentedListItem(
                onClick = { onRowClick(rowRes) },
                headlineContent = { MaterialText(stringResource(rowRes)) },
            )
        }
    }

    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        content = items,
    )
}