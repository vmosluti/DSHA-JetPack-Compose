package com.luti.dshlauncher.ui.screen.plugins

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.util.BlurredBar
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun PluginsPagerMiuix(bottomInnerPadding: Dp, ui: PluginsUi) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(R.string.plugins_market),
        stringResource(R.string.plugins_management),
    )

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.nav_plugins),
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
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                        height = 48.dp,
                    )

                    Card(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        val notice = ui.blockMessage.ifEmpty { ui.message }
                        if (notice.isNotEmpty() || ui.busy) {
                            BasicComponent(
                                title = if (ui.busy) stringResource(R.string.plugins_management) else notice,
                                summary = if (ui.busy) notice.ifEmpty { null } else null,
                            )
                        }
                        if (selectedTab == 0 || ui.items.isEmpty()) {
                            // 市场 / 安装 / 更新走 DSHA 原生插件页（含签名校验与确认流程）
                            BasicComponent(
                                title =
                                    if (selectedTab == 0) stringResource(R.string.plugins_market_empty)
                                    else stringResource(R.string.plugins_management_empty),
                                onClick = ui.onOpenFull,
                            )
                        } else {
                            val readOnly = ui.busy || ui.blockMessage.isNotEmpty()
                            ui.items.forEach { item ->
                                SwitchPreference(
                                    title = item.name,
                                    summary = listOf(item.version, item.description)
                                        .filter { it.isNotEmpty() }.joinToString(" · "),
                                    checked = item.enabled,
                                    enabled = !readOnly && item.available,
                                    onCheckedChange = { ui.onToggle(item, it) },
                                )
                            }
                            BasicComponent(
                                title = stringResource(R.string.plugins_market),
                                onClick = ui.onOpenFull,
                            )
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}