package com.luti.dshlauncher.ui.dsha

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedDropdownItem
import com.luti.dshlauncher.ui.component.material.SegmentedSwitchItem
import com.luti.dshlauncher.ui.component.material.TonalCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator as MaterialLinearProgress
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Slider as MaterialSlider

@Composable
fun DshaSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SwitchPreference(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        UiMode.Material -> SegmentedSwitchItem(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
fun DshaDropdownRow(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    summary: String? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            onSelectedIndexChange = onSelectedIndexChange,
        )
        UiMode.Material -> SegmentedDropdownItem(
            title = title,
            summary = summary,
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onSelectedIndexChange,
        )
    }
}

@Composable
fun DshaSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    summary: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ArrowPreference(
            title = title,
            summary = summary,
            endActions = {
                MiuixText(
                    text = valueLabel,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            },
            bottomAction = {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = { onValueChangeFinished?.invoke() },
                    valueRange = valueRange,
                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                )
            },
        )
        UiMode.Material -> TonalCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        if (!summary.isNullOrEmpty()) {
                            Text(
                                summary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                    Text(
                        valueLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MaterialSlider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = valueRange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun DshaTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    error: String? = null,
    summary: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    /** >1 时为多行输入（如 JSON 编辑），最少显示这么多行的高度。 */
    lines: Int = 1,
) {
    val transformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
    val multiline = lines > 1
    when (LocalUiMode.current) {
        UiMode.Miuix -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MiuixText(text = title, color = MiuixTheme.colorScheme.onSurface)
            if (!summary.isNullOrEmpty()) {
                MiuixText(
                    text = summary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextField(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .then(if (multiline) Modifier.heightIn(min = (lines * 22).dp) else Modifier),
                value = value,
                maxLines = if (multiline) Int.MAX_VALUE else 1,
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                onValueChange = onValueChange,
            )
            if (!error.isNullOrEmpty()) {
                MiuixText(
                    text = error,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (hint.isNotEmpty() && value.isEmpty()) {
                MiuixText(
                    text = hint,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        UiMode.Material -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            label = { Text(title) },
            placeholder = { if (hint.isNotEmpty()) Text(hint) },
            supportingText = {
                val text = error ?: summary
                if (!text.isNullOrEmpty()) Text(text)
            },
            isError = !error.isNullOrEmpty(),
            singleLine = !multiline,
            minLines = lines,
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }
}

@Composable
fun DshaPreferenceGroup(content: @Composable () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> DshaCard { content() }
        UiMode.Material -> SegmentedColumn(
            modifier = Modifier.padding(vertical = 8.dp),
            content = listOf(content),
        )
    }
}

@Composable
fun DshaMessageDialog(
    title: String,
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    confirm: String = com.deepseekharness.app.util.UiText.choose("关闭", "Close"),
) {
    if (!visible) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                MiuixText(
                    text = message,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                MiuixTextButton(
                    text = confirm,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            },
        )
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(confirm) } },
        )
    }
}

@Composable
fun DshaChoiceSheet(
    title: String,
    items: List<String>,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    if (!visible) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                items.forEachIndexed { index, label ->
                    BasicComponent(
                        title = label,
                        onClick = {
                            onSelect(index)
                            onDismiss()
                        },
                    )
                }
            },
        )
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    items.forEachIndexed { index, label ->
                        TextButton(
                            onClick = {
                                onSelect(index)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
fun DshaNumberPrompt(
    title: String,
    visible: Boolean,
    value: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    invalidMessage: String,
) {
    if (!visible) return
    val draft = remember(value, visible) { mutableStateOf(value.toString()) }
    val error = remember { mutableStateOf<String?>(null) }
    fun submit() {
        val parsed = draft.value.toIntOrNull()
        if (parsed == null || parsed !in range) {
            error.value = invalidMessage
            return
        }
        onConfirm(parsed)
        onDismiss()
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                TextField(
                    modifier = Modifier.padding(bottom = 8.dp),
                    value = draft.value,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = {
                        draft.value = it.filter { ch -> ch.isDigit() }
                        error.value = null
                    },
                )
                error.value?.let {
                    MiuixText(
                        text = it,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                } ?: Spacer(Modifier.height(8.dp))
                Row {
                    MiuixTextButton(
                        text = com.deepseekharness.app.util.UiText.choose("取消", "Cancel"),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = com.deepseekharness.app.util.UiText.choose("保存", "Save"),
                        onClick = { submit() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft.value,
                        onValueChange = {
                            draft.value = it.filter { ch -> ch.isDigit() }
                            error.value = null
                        },
                        singleLine = true,
                        isError = error.value != null,
                        supportingText = error.value?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { submit() }) {
                    Text(com.deepseekharness.app.util.UiText.choose("保存", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(com.deepseekharness.app.util.UiText.choose("取消", "Cancel"))
                }
            },
        )
    }
}

/** 0..1 确定进度条，传 null 为不确定进度；Miuix 下套用 Miuix 主色。 */
@Composable
fun DshaProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val color = when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTheme.colorScheme.primary
        UiMode.Material -> MaterialTheme.colorScheme.primary
    }
    val barModifier = modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
    if (progress == null) {
        MaterialLinearProgress(color = color, modifier = barModifier)
    } else {
        val value = progress.coerceIn(0f, 1f)
        MaterialLinearProgress(progress = { value }, color = color, modifier = barModifier)
    }
}

/** 带自定义内容的对话框；最后一个按钮为主操作。按钮点击先调 onClose 再执行动作。 */
@Composable
fun DshaContentDialog(
    title: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<DshaDialogAction>,
    onClose: () -> Unit = onDismiss,
    content: @Composable () -> Unit,
) {
    if (!visible) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> OverlayDialog(
            show = true,
            title = title,
            onDismissRequest = onDismiss,
            content = {
                Column(modifier = Modifier.padding(bottom = 12.dp)) { content() }
                actions.forEachIndexed { index, action ->
                    val modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 0.dp else 8.dp)
                    val click = {
                        onClose()
                        action.onClick()
                    }
                    if (index == actions.lastIndex) {
                        MiuixTextButton(
                            text = action.title,
                            onClick = click,
                            modifier = modifier,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    } else {
                        MiuixTextButton(text = action.title, onClick = click, modifier = modifier)
                    }
                }
            },
        )
        UiMode.Material -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() } },
            confirmButton = {
                Row {
                    actions.forEach { action ->
                        TextButton(onClick = {
                            onClose()
                            action.onClick()
                        }) { Text(action.title) }
                    }
                }
            },
        )
    }
}

/** 对话框按钮：最多三个，最后一个视为主操作。 */
data class DshaDialogAction(val title: String, val onClick: () -> Unit)

@Composable
fun DshaActionDialog(
    title: String,
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    actions: List<DshaDialogAction>,
    onClose: () -> Unit = onDismiss,
) {
    val uiMode = LocalUiMode.current
    DshaContentDialog(title, visible, onDismiss, actions, onClose) {
        when (uiMode) {
            UiMode.Miuix -> MiuixText(
                text = message,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            UiMode.Material -> Text(message)
        }
    }
}

/** 卡片内的小节标题（对应原 View 版卡片顶部的粗体标题）。 */
@Composable
fun DshaCardTitle(text: String) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixText(
            text = text,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        )
        UiMode.Material -> Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

/** 卡片内的整行按钮，带卡片内边距；primary 用主题主色。 */
@Composable
fun DshaCardButton(title: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (primary) DshaPrimaryButton(title, enabled, onClick) else DshaSecondaryButton(title, enabled, onClick)
    }
}

/** 可选中复制的正文段落（对应原 textIsSelectable 的 TextView）。 */
@Composable
fun DshaSelectableText(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        when (LocalUiMode.current) {
            UiMode.Miuix -> MiuixText(text = text, color = MiuixTheme.colorScheme.onSurface)
            UiMode.Material -> Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 固定高度的等宽日志框，可选中复制；新内容到达时若已在底部则继续跟随（对应原 LogScrollView 语义）。
 */
@Composable
fun DshaLogBox(text: String, height: Dp = 220.dp) {
    val scroll = rememberScrollState()
    var following by remember { mutableStateOf(true) }
    // 只在滚动（用户拖动或程序滚动）结束时判定是否贴底，避免新内容撑大 maxValue 时误判
    LaunchedEffect(scroll.isScrollInProgress) {
        if (!scroll.isScrollInProgress) following = scroll.maxValue - scroll.value <= 48
    }
    LaunchedEffect(text) {
        if (!following) return@LaunchedEffect
        withFrameNanos { } // 等新文本完成布局，maxValue 才是最新值
        scroll.scrollTo(scroll.maxValue)
    }
    val background = when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTheme.colorScheme.surfaceContainerHigh
        UiMode.Material -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(height)
            .background(background, RoundedCornerShape(12.dp))
            .verticalScroll(scroll)
            .padding(12.dp),
    ) {
        SelectionContainer {
            when (LocalUiMode.current) {
                UiMode.Miuix -> MiuixText(text = text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                UiMode.Material -> Text(text = text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}