package com.luti.dshlauncher.ui.screen.plugins

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.component.material.TonalCard
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton

@Composable
fun PluginsPagerMaterial(bottomInnerPadding: Dp, ui: PluginsUi) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_plugins)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(R.string.plugins_market, R.string.plugins_management).forEachIndexed { index, label ->
                    AssistChip(
                        onClick = { selectedTab = index },
                        label = { Text(stringResource(label)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor =
                                if (selectedTab == index) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }

            TonalCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val notice = ui.blockMessage.ifEmpty { ui.message }
                    if (notice.isNotEmpty()) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ui.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (selectedTab == 0 || ui.items.isEmpty()) {
                        Text(
                            text =
                                if (selectedTab == 0) {
                                    stringResource(R.string.plugins_market_empty)
                                } else {
                                    stringResource(R.string.plugins_management_empty)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    // 市场 / 安装 / 更新走 DSHA 原生插件页（含签名校验与确认流程）
                    TextButton(onClick = ui.onOpenFull) {
                        Text(stringResource(R.string.plugins_market))
                    }
                }
            }
            if (selectedTab == 1 && ui.items.isNotEmpty()) {
                val readOnly = ui.busy || ui.blockMessage.isNotEmpty()
                SegmentedColumn(
                    content = ui.items.map { item ->
                        {
                            SegmentedListItem(
                                checked = item.enabled,
                                onCheckedChange = { ui.onToggle(item, it) },
                                enabled = !readOnly && item.available,
                                headlineContent = { Text(item.name) },
                                supportingContent = {
                                    Text(
                                        listOf(item.version, item.description)
                                            .filter { it.isNotEmpty() }.joinToString(" · ")
                                    )
                                },
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}