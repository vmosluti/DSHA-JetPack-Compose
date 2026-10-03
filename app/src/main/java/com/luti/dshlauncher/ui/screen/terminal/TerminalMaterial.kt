package com.luti.dshlauncher.ui.screen.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.component.material.TonalCard
import kotlin.math.roundToInt

@Composable
fun TerminalPagerMaterial(
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
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.terminal_panel_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
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

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
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
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.terminal_font_size_value,
                                fontSize.roundToInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..24f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            TonalCard(onClick = onNewSession) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.terminal_new),
                    )
                    Text(
                        text = stringResource(R.string.terminal_new),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.terminal_sessions_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tabLabels.forEachIndexed { index, label ->
                            AssistChip(
                                onClick = { onSelectSession(index) },
                                label = { Text(label) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (index == activeIndex) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(onClick = onClearSession) {
                            Text(stringResource(R.string.terminal_clear))
                        }
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
                        TextButton(onClick = submit) {
                            Text(stringResource(R.string.terminal_send))
                        }
                    }
                }
            }

            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}
