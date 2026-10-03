package com.luti.dshlauncher.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.theme.LocalEnableBlur
import com.luti.dshlauncher.ui.util.BlurredBar
import com.luti.dshlauncher.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@Composable
fun TerminalPagerMiuix(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    sessions: List<String>,
    selectedSession: Int,
    onSelectSession: (Int) -> Unit,
    onNewSession: () -> Unit,
    onClearSession: () -> Unit,
    onRunCommand: (String) -> Unit,
    bottomInnerPadding: Dp,
) {
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
                    title = stringResource(R.string.terminal_panel_title),
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
                    val tabLabels = if (sessions.isEmpty()) {
                        listOf(stringResource(R.string.terminal_session_name, 1))
                    } else {
                        sessions.mapIndexed { index, _ ->
                            stringResource(R.string.terminal_session_name, index + 1)
                        }
                    }
                    val activeIndex = selectedSession.coerceIn(0, tabLabels.lastIndex)
                    val activeHistory = sessions.getOrNull(activeIndex).orEmpty()
                    val lines = if (activeHistory.isEmpty()) {
                        listOf(stringResource(R.string.terminal_empty_session))
                    } else {
                        activeHistory.split('\n')
                    }
                    val windowListState = rememberLazyListState()
                    var input by rememberSaveable { mutableStateOf("") }
                    val submit: () -> Unit = {
                        val command = input
                        input = ""
                        onRunCommand(command)
                    }

                    LaunchedEffect(activeHistory) {
                        if (lines.isNotEmpty()) {
                            windowListState.animateScrollToItem(lines.lastIndex)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.terminal_font_size),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.terminal_font_size_value,
                                        fontSize.roundToInt()
                                    ),
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Slider(
                                value = fontSize,
                                onValueChange = onFontSizeChange,
                                valueRange = 12f..24f,
                                showKeyPoints = true,
                                keyPoints = listOf(12f, 14f, 16f, 18f, 20f, 24f),
                                magnetThreshold = 0.01f,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.terminal_new),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Add,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.terminal_new),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = onNewSession,
                        )
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.terminal_sessions_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            TabRow(
                                tabs = tabLabels,
                                selectedTabIndex = activeIndex,
                                onTabSelected = onSelectSession,
                                height = 48.dp,
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.terminal_window_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                )
                                TextButton(
                                    text = stringResource(R.string.terminal_clear),
                                    onClick = onClearSession,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1B1B1F))
                                    .padding(12.dp)
                            ) {
                                LazyColumn(
                                    state = windowListState,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(count = lines.size) { index ->
                                        Text(
                                            text = lines[index],
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize * 1.4f).sp,
                                            color = Color(0xFFE6E6E6),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2A2A2E))
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                ) {
                                    BasicTextField(
                                        value = input,
                                        onValueChange = { input = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = fontSize.sp,
                                            color = Color(0xFFE6E6E6),
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFF7EE787)),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { submit() }),
                                        decorationBox = { innerTextField ->
                                            if (input.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.terminal_input_hint),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = fontSize.sp,
                                                    color = Color(0xFF8A8A8A),
                                                )
                                            }
                                            innerTextField()
                                        },
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    text = stringResource(R.string.terminal_send),
                                    onClick = submit,
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(bottomInnerPadding + 12.dp))
                }
            }
        }
    }
}
