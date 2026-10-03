package com.luti.dshlauncher.ui.dsha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.util.BlurredBar
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class DshaKv(val label: String, val value: String)

data class DshaEntry(
    val title: String,
    val summary: String,
    val onClick: (() -> Unit)? = null,
)

data class DshaAction(
    val title: String,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun DshaPageScaffold(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    footer: List<DshaAction> = emptyList(),
    content: LazyListScope.() -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> DshaPageMiuix(title, subtitle, onBack, footer, content)
        UiMode.Material -> DshaPageMaterial(title, subtitle, onBack, footer, content)
    }
}

@Composable
private fun DshaPageMiuix(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    footer: List<DshaAction>,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    MiuixScaffold(
        topBar = {
            BlurredBar(backdrop) {
                MiuixTopAppBar(
                    color = barColor,
                    title = title,
                    navigationIcon = {
                        if (onBack != null) {
                            MiuixIconButton(onClick = onBack) {
                                MiuixIcon(MiuixIcons.Back, contentDescription = null)
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = {
            if (footer.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 12.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    footer.forEach { action ->
                        MiuixActionButton(action.title, action.primary, action.enabled, action.onClick)
                    }
                }
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ) {
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
                if (subtitle.isNotEmpty()) {
                    item {
                        MiuixText(
                            text = subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
                        )
                    }
                }
                content()
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DshaPageMaterial(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    footer: List<DshaAction>,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    androidx.compose.material3.Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (footer.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    footer.forEach { action ->
                        if (action.primary) {
                            Button(
                                onClick = action.onClick,
                                enabled = action.enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(action.title)
                            }
                        } else {
                            OutlinedButton(
                                onClick = action.onClick,
                                enabled = action.enabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(action.title)
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (subtitle.isNotEmpty()) {
                item {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            content()
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun DshaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            content = content,
        )
        UiMode.Material -> androidx.compose.material3.Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(4.dp), content = content)
        }
    }
}

fun LazyListScope.dshaKvCard(rows: List<DshaKv>) {
    if (rows.isEmpty()) return
    item {
        when (LocalUiMode.current) {
            UiMode.Miuix -> DshaCard {
                rows.forEach { row ->
                    BasicComponent(title = row.label, summary = row.value)
                }
            }
            UiMode.Material -> SegmentedColumn(
                modifier = Modifier.padding(vertical = 8.dp),
                content = rows.map { row ->
                    {
                        SegmentedListItem(
                            headlineContent = { Text(row.label) },
                            supportingContent = { Text(row.value) },
                        )
                    }
                },
            )
        }
    }
}

fun LazyListScope.dshaEntryCard(rows: List<DshaEntry>) {
    if (rows.isEmpty()) return
    item {
        when (LocalUiMode.current) {
            UiMode.Miuix -> DshaCard {
                rows.forEach { row ->
                    val click = row.onClick
                    // 可跳转项统一用 ArrowPreference，自带"下一步"箭头；纯展示项保留 BasicComponent。
                    if (click != null) {
                        ArrowPreference(title = row.title, summary = row.summary, onClick = click)
                    } else {
                        BasicComponent(title = row.title, summary = row.summary)
                    }
                }
            }
            UiMode.Material -> SegmentedColumn(
                modifier = Modifier.padding(vertical = 8.dp),
                content = rows.map { row ->
                    {
                        SegmentedListItem(
                            onClick = row.onClick,
                            headlineContent = { Text(row.title) },
                            supportingContent = { Text(row.summary) },
                            trailingContent = if (row.onClick != null) {
                                { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                            } else null,
                        )
                    }
                },
            )
        }
    }
}

/**
 * 卡片组之上的分类标题。Miuix 直接用库自带的 [SmallTitle]，Material 用与 SegmentedColumn
 * 标题一致的 titleSmall + primary，不再各页手写。
 */
@Composable
fun DshaCategory(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SmallTitle(
            text = text,
            modifier = Modifier.padding(top = 12.dp),
        )
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
fun DshaNote(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
fun DshaPrimaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixActionButton(title, primary = true, enabled = enabled, onClick = onClick)
        UiMode.Material -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(title)
        }
    }
}

@Composable
fun DshaSecondaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixActionButton(title, primary = false, enabled = enabled, onClick = onClick)
        UiMode.Material -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(title)
        }
    }
}

/** Miuix 0.9.4 的 Button 只接受 content 槽位与 colors；主按钮用主题主色，次按钮用默认配色。 */
@Composable
private fun MiuixActionButton(title: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val primaryColor = MiuixTheme.colorScheme.primary
    MiuixButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = if (primary) {
            MiuixButtonDefaults.buttonColors(color = primaryColor, contentColor = Color.White)
        } else {
            MiuixButtonDefaults.buttonColors()
        },
    ) {
        MiuixText(text = title, color = if (primary) Color.White else MiuixTheme.colorScheme.onSurface)
    }
}