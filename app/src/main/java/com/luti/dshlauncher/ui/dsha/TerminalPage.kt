package com.luti.dshlauncher.ui.dsha

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.PtySession
import com.deepseekharness.app.core.DiagnosticLog
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.SimpleTerminalBackend
import com.deepseekharness.app.core.TerminalSessionOwner
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.TerminalSession as SimpleSession
import com.deepseekharness.app.util.TerminalTabs
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.util.concurrent.atomic.AtomicBoolean
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 与原 PtyTerminalFragment / TerminalFragment 共用的偏好文件与键名，不可改。 */
const val TERMINAL_PREFS = "deepseekharness"
const val TERMINAL_KEY_PTY = "term_pty"
private const val KEY_FONT_PX = "term_font_px"
private const val FONT_MIN_SP = 8
private const val FONT_MAX_SP = 24
private const val FONT_DEF_SP = 13
private const val SIMPLE_MAX_RENDER = 100_000
private const val SIMPLE_HEADER = "Ubuntu 24.04 · 回车执行 · 中止会重启 shell · 交互输入请用 PTY\n"

/** 扩展键：显示 → 序列；null 为状态键（Ctrl / Alt）。手机软键盘缺这排 TUI 没法用。 */
private val PTY_KEYS: List<Pair<String, String?>> = listOf(
    "ESC" to "\u001b", "TAB" to "\t", "CTRL" to null, "ALT" to null,
    "↑" to "\u001b[A", "↓" to "\u001b[B", "←" to "\u001b[D", "→" to "\u001b[C",
    "^C" to "\u0003", "^D" to "\u0004", "^Z" to "\u001a",
    "|" to "|", "~" to "~", "/" to "/", "-" to "-",
)

/**
 * 终端页：PTY（Termux TerminalView）与简易终端两种模式可随时互切，选择记在 [TERMINAL_KEY_PTY]。
 * 会话由进程级 [TerminalSessionOwner] 持有，离开页面不结束。
 */
@Composable
fun DshaTerminalPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(TERMINAL_PREFS, Context.MODE_PRIVATE) }
    var pty by remember { mutableStateOf(prefs.getBoolean(TERMINAL_KEY_PTY, true)) }
    val switchMode: (Boolean) -> Unit = switch@{ toPty ->
        if (toPty) {
            val blocked = SimpleTerminalBackend.blockMessage(HarnessController.get(context).proot())
            if (blocked.isNotEmpty()) {
                Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
                return@switch
            }
        }
        prefs.edit().putBoolean(TERMINAL_KEY_PTY, toPty).apply()
        if (!toPty) {
            Toast.makeText(context, UiText.text("已切到简易终端（PTY 会话仍在后台）"), Toast.LENGTH_SHORT).show()
        }
        pty = toPty
    }
    TerminalFrame(title = context.dshaT("终端", "Terminal"), onBack = onBack) {
        if (pty) PtyTerminal(onSwitchSimple = { switchMode(false) })
        else SimpleTerminal(onSwitchPty = { switchMode(true) })
    }
}

// ==================== 公共外观 ====================

private data class TermPalette(
    val panel: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val selected: Color,
)

@Composable
private fun termPalette(): TermPalette = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.let {
        TermPalette(it.surfaceContainerHigh, it.onSurface, it.onSurfaceVariantSummary, it.primary, it.primary.copy(alpha = 0.14f))
    }
    UiMode.Material -> MaterialTheme.colorScheme.let {
        TermPalette(it.surfaceContainerHighest, it.onSurface, it.onSurfaceVariant, it.primary, it.secondaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val backLabel = LocalContext.current.dshaT("返回", "Back")
    val body: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            content = content,
        )
    }
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixScaffold(
            topBar = {
                MiuixTopAppBar(
                    color = MiuixTheme.colorScheme.surface,
                    title = title,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack) {
                            MiuixIcon(MiuixIcons.Back, contentDescription = backLabel)
                        }
                    },
                    scrollBehavior = MiuixScrollBehavior(),
                )
            },
            popupHost = { },
            // safeDrawing 含 IME：软键盘弹出时输入栏/扩展键跟着上移
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) { inner -> body(inner) }
        UiMode.Material -> androidx.compose.material3.Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { inner -> body(inner) }
    }
}

/** 文本按钮：最小 48dp 触摸目标，颜色取当前主题主色。 */
@Composable
private fun TermAction(text: String, enabled: Boolean = true, description: String? = null, onClick: () -> Unit) {
    val palette = termPalette()
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            style = TextStyle(
                color = if (enabled) palette.primary else palette.muted.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 顶部状态胶囊（原 bg_chip 的标题条）。 */
@Composable
private fun TermChip(text: String, modifier: Modifier = Modifier) {
    val palette = termPalette()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            style = TextStyle(
                color = palette.muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** PTY 与简易终端共用的标签行；新建和关闭始终是独立的 48dp 触摸目标。 */
@Composable
private fun <T> TerminalTabRow(
    tabs: List<TerminalTabs.Tab<T>>,
    currentId: Long?,
    newEnabled: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val palette = termPalette()
    val listState = rememberLazyListState()
    val index = tabs.indexOfFirst { it.id == currentId }
    LaunchedEffect(currentId, tabs.size) { if (index >= 0) listState.animateScrollToItem(index) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                val selected = tab.id == currentId
                val closing = tab.isClosing
                val name = UiText.text("终端 ") + tab.number + if (closing) UiText.text(" · 关闭中…") else ""
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) palette.selected else palette.panel),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val selectLabel = UiText.text("切换到终端 ") + tab.number
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 72.dp)
                            .semantics { contentDescription = selectLabel }
                            .clickable(role = Role.Tab) { onSelect(tab.id) }
                            .padding(start = 12.dp, end = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = name,
                            maxLines = 1,
                            style = TextStyle(color = if (selected) palette.primary else palette.muted, fontSize = 13.sp),
                        )
                    }
                    val closeLabel = UiText.text("关闭终端 ") + tab.number
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .alpha(if (closing) 0.4f else 1f)
                            .semantics { contentDescription = closeLabel }
                            .clickable(enabled = !closing, role = Role.Button) { onClose(tab.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(text = "×", style = TextStyle(color = palette.muted, fontSize = 20.sp))
                    }
                }
            }
        }
        TermAction(
            text = stringResource(R.string.ui_m0218),
            enabled = newEnabled,
            description = stringResource(R.string.ui_m0142),
            onClick = onNew,
        )
    }
}

/** 页面 resume/pause 回调；与原 Fragment onResume/onPause 的挂接时机一致。 */
@Composable
private fun OnResumePause(key: Any, onResume: () -> Unit, onPause: () -> Unit, onRelease: () -> Unit = {}) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, key) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            onPause()
            onRelease()
        }
    }
}

// ==================== 简易终端 ====================

@Composable
private fun ColumnScope.SimpleTerminal(onSwitchPty: () -> Unit) {
    val context = LocalContext.current
    val owner = remember { TerminalSessionOwner.shared() }
    val palette = termPalette()
    val main = remember { Handler(Looper.getMainLooper()) }
    var revision by remember { mutableIntStateOf(0) }
    var hintOverride by remember { mutableStateOf<String?>(null) }

    // 输出 32ms 合并一次重绘，状态与标签变化立即刷新
    val observer = remember {
        var scheduled = false
        val bump = Runnable { scheduled = false; revision++ }
        object : TerminalSessionOwner.SimpleObserver {
            override fun onOutput(tab: TerminalSessionOwner.SimpleTab) {
                main.post { if (!scheduled) { scheduled = true; main.postDelayed(bump, 32) } }
            }
            override fun onState(tab: TerminalSessionOwner.SimpleTab) { main.post { revision++ } }
            override fun onTabsChanged() { main.post { revision++ } }
        }
    }
    OnResumePause(
        key = observer,
        onResume = { owner.attachSimple(observer); revision++ },
        onPause = { owner.detachSimple(observer) },
        onRelease = { main.removeCallbacksAndMessages(null) },
    )

    fun proot() = HarnessController.get(context).proot()
    fun refuseDuringMaintenance(): Boolean {
        val blocked = SimpleTerminalBackend.blockMessage(proot())
        if (blocked.isEmpty()) return false
        Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
        hintOverride = blocked
        return true
    }
    fun newTerminal() {
        if (refuseDuringMaintenance()) return
        val tab = owner.addSimple(SimpleTerminalBackend.create(proot()))
        tab.value.session.ensureStarted()
        revision++
    }
    fun closeTerminal(id: Long) {
        if (owner.simpleTabs().find(id) == null || !owner.beginCloseSimple(id)) return
        revision++
        val app = context.applicationContext
        Thread({
            var failure: String? = null
            try {
                owner.closeSimple(id, 5000)
            } catch (error: Exception) {
                failure = SensitiveData.redact(error.message.toString())
            }
            val problem = failure
            main.post {
                if (problem != null) {
                    Toast.makeText(app, UiText.text("关闭失败，会话仍保留：") + problem, Toast.LENGTH_LONG).show()
                }
                revision++
            }
        }, "dsha-close-simple-terminal").start()
    }

    LaunchedEffect(Unit) { if (!owner.simpleTabs().wasInitialized()) newTerminal() }
    LaunchedEffect(revision) { hintOverride = null }

    val tabs = remember(revision) { owner.simpleTabs().snapshot() }
    val currentTab = remember(revision) { owner.currentSimple() }
    val selected = currentTab?.value
    val state = remember(revision, selected) { selected?.session?.state() }
    val closing = currentTab?.isClosing == true

    // 每个标签各自保留输入草稿与光标（原 saveDraft 语义）
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var bound by remember { mutableStateOf<TerminalSessionOwner.SimpleTab?>(null) }
    LaunchedEffect(selected) {
        if (bound !== selected) {
            bound = selected
            input = selected?.let {
                val length = it.draft.length
                TextFieldValue(it.draft, TextRange(it.cursorStart.coerceIn(0, length), it.cursorEnd.coerceIn(0, length)))
            } ?: TextFieldValue("")
        }
    }

    fun sendCommand() {
        val command = input.text
        if (command.isBlank()) return
        if (refuseDuringMaintenance()) return
        val active = bound
        val current = owner.currentSimple()
        if (active != null && current != null && !current.isClosing && active.session.submit(command)) {
            input = TextFieldValue("")
            active.draft = ""
            active.cursorStart = 0
            active.cursorEnd = 0
        } else {
            Toast.makeText(
                context,
                UiText.text("命令未发送：请检查长度（最多 16K 字符）及空字符，输入已保留"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val shown = remember(revision, selected) {
        if (selected == null) {
            UiText.text("暂无终端\n点击「新建」打开一个终端")
        } else {
            val buffer = selected.output()
            SensitiveData.redact(
                when {
                    buffer.length > SIMPLE_MAX_RENDER ->
                        UiText.text("…（输出过长已截断）\n") + buffer.substring(buffer.length - SIMPLE_MAX_RENDER)
                    buffer.isEmpty() -> SIMPLE_HEADER
                    else -> buffer
                },
            )
        }
    }
    val hint = hintOverride ?: when {
        currentTab == null -> UiText.text("点击「新建」打开终端")
        closing -> UiText.text("正在关闭此终端…")
        else -> when (state) {
            SimpleSession.State.STARTING -> UiText.text("会话启动中，输入命令可排队")
            SimpleSession.State.BUSY -> UiText.text("命令执行中，新命令将排队")
            SimpleSession.State.STOPPING -> UiText.text("中止并重启中，新命令将排队")
            SimpleSession.State.STOPPED -> UiText.text("会话已退出，输入命令自动启动")
            SimpleSession.State.FAILED -> UiText.text("会话失败，待发命令保留；点重试重启")
            else -> UiText.text("输入命令，回车执行")
        }
    }
    val cancelLabel = when (state) {
        SimpleSession.State.STOPPING -> UiText.text("中止中…")
        SimpleSession.State.FAILED -> UiText.text("重试重启")
        else -> UiText.text("中止并重启")
    }
    val inputEnabled = currentTab != null && !closing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermChip(stringResource(R.string.ui_m0178), Modifier.weight(1f))
        TermAction(cancelLabel, enabled = inputEnabled && state != SimpleSession.State.STOPPING) {
            val active = bound
            if (active != null && !refuseDuringMaintenance()) active.session.cancelAndRestart()
        }
        TermAction(stringResource(R.string.ui_m0164)) {
            bound?.clearOutput()
            revision++
        }
        TermAction("PTY", onClick = onSwitchPty)
    }
    TerminalTabRow(
        tabs = tabs,
        currentId = currentTab?.id,
        newEnabled = currentTab == null || state != SimpleSession.State.STARTING,
        onSelect = { id -> if (owner.selectSimple(id)) revision++ },
        onClose = ::closeTerminal,
        onNew = ::newTerminal,
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.panel),
    ) {
        val scroll = rememberScrollState()
        LaunchedEffect(shown) {
            withFrameNanos { } // 等新文本完成布局再贴底
            scroll.scrollTo(scroll.maxValue)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(12.dp),
        ) {
            SelectionContainer {
                BasicText(
                    text = shown,
                    style = TextStyle(color = palette.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "# ",
                style = TextStyle(color = palette.primary, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
            BasicTextField(
                value = input,
                onValueChange = { value ->
                    input = value
                    bound?.let {
                        it.draft = value.text
                        it.cursorStart = value.selection.start
                        it.cursorEnd = value.selection.end
                    }
                },
                enabled = inputEnabled,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = hint },
                textStyle = TextStyle(color = palette.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendCommand() }, onGo = { sendCommand() }, onDone = { sendCommand() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.text.isEmpty()) {
                            BasicText(
                                text = hint,
                                maxLines = 1,
                                style = TextStyle(color = palette.muted, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

// ==================== PTY 终端 ====================

@Composable
private fun ColumnScope.PtyTerminal(onSwitchSimple: () -> Unit) {
    val context = LocalContext.current
    val owner = remember { TerminalSessionOwner.shared() }
    val palette = termPalette()
    val host = remember { PtyHost(context) }
    OnResumePause(key = host, onResume = host::resume, onPause = host::pause, onRelease = host::dispose)

    val revision = host.revision
    val tabs = remember(revision) { owner.ptyTabs().snapshot() }
    val currentId = remember(revision) { owner.currentPty()?.id }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermChip(host.title, Modifier.weight(1f))
        TermAction("A−", description = context.dshaT("缩小字号", "Smaller text")) { host.bumpFont(-1) }
        TermAction("A+", description = context.dshaT("放大字号", "Larger text")) { host.bumpFont(+1) }
        TermAction(stringResource(R.string.ui_m0177), onClick = onSwitchSimple)
    }
    TerminalTabRow(
        tabs = tabs,
        currentId = currentId,
        newEnabled = host.ready,
        onSelect = { id -> if (owner.selectPty(id)) host.attachSelected() },
        onClose = host::closeTerminal,
        onNew = host::startTerminal,
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.panel)
            .padding(4.dp),
    ) {
        // Termux TerminalView 自绘字符网格与输入连接，只能以 AndroidView 嵌入
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.terminal_surface))
                    host.bind(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (host.empty) {
            BasicText(
                text = stringResource(R.string.ui_m0145),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                style = TextStyle(color = palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center),
            )
        }
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(PTY_KEYS, key = { it.first }) { (label, seq) ->
            val active = when (label) {
                "CTRL" -> host.ctrlDown
                "ALT" -> host.altDown
                else -> false
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .widthIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) palette.selected else palette.panel)
                    .clickable(role = Role.Button) {
                        if (seq == null) host.toggleModifier(ctrl = label == "CTRL") else host.send(seq)
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = if (active) palette.primary else palette.muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

/**
 * PTY 页的会话绑定与 TerminalViewClient 实现（原 PtyTerminalFragment 逻辑原样迁入）。
 * 每次绑定捕获会话身份，旧会话排队中的回调不能改写新标签。
 */
private class PtyHost(private val context: Context) : TerminalViewClient {
    private val owner = TerminalSessionOwner.shared()
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(TERMINAL_PREFS, Context.MODE_PRIVATE)

    /** PTY 输出回调很密，用 pending 标志把连续输出合并成一次重绘。 */
    private val redrawPending = AtomicBoolean(false)
    private var view: TerminalView? = null
    private var attachedSession: PtySession? = null
    private var sessionListener: PtySession.Listener? = null
    private var resumed = false
    private var disposed = false

    var title by mutableStateOf("Ubuntu · PTY")
        private set
    var ctrlDown by mutableStateOf(false)
        private set
    var altDown by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var empty by mutableStateOf(false)
        private set
    var revision by mutableIntStateOf(0)
        private set

    private val tabsObserver = TerminalSessionOwner.PtyObserver { main.post { attachSelected() } }

    private fun proot() = HarnessController.get(context).proot()
    private fun environmentReady() = proot().isEnvironmentReady

    fun bind(target: TerminalView) {
        view = target
        target.setTerminalViewClient(this)
        applyFontSize(fontSp())
        if (!environmentReady()) {
            attachSelected()
            return
        }
        if (!owner.ptyTabs().wasInitialized()) startTerminal() else attachSelected()
    }

    fun resume() {
        if (disposed) return
        resumed = true
        owner.attachPty(tabsObserver)
        if (view != null) attachSelected()
    }

    fun pause() {
        owner.detachPty(tabsObserver)
        attachedSession?.detachListener(sessionListener)
        resumed = false
    }

    fun dispose() {
        disposed = true
        pause()
        attachedSession = null
        sessionListener = null
        main.removeCallbacksAndMessages(null)
        redrawPending.set(false)
        view = null
    }

    /** 已有会话就接回去（切页面回来不丢历史），没有就起一个。 */
    fun startTerminal() {
        try {
            val blocked = SimpleTerminalBackend.blockMessage(proot())
            if (blocked.isNotEmpty()) {
                title = blocked
                return
            }
            // 80x24 只是占位：attachSession 后 TerminalView 按实测字宽重算行列并通知 PTY
            val session = PtySession.start(proot(), 80, 24, null)
            owner.addPty(session)
            attachSelected()
        } catch (error: Throwable) {
            val safe = SensitiveData.redact(error.toString())
            title = UiText.text("终端启动失败：") + safe
            DiagnosticLog.record(context, "PTY_START", safe)
            Log.w("DSHA", UiText.text("PTY 启动失败：") + safe)
        }
    }

    fun attachSelected() {
        val target = view ?: return
        if (disposed) return
        val isReady = environmentReady()
        ready = isReady
        attachedSession?.detachListener(sessionListener)
        attachedSession = null
        sessionListener = null
        ctrlDown = false
        altDown = false
        val tab = owner.currentPty()
        empty = tab == null
        target.visibility = if (tab == null) View.INVISIBLE else View.VISIBLE
        target.isEnabled = isReady && tab != null && !tab.isClosing
        if (tab == null) {
            title = UiText.text("暂无终端")
        } else {
            val selected = tab.value
            attachedSession = selected
            val listener = object : PtySession.Listener {
                private fun active() = attachedSession === selected
                override fun onOutput() { if (active()) redraw() }
                override fun onTitle(value: String?) { if (active()) titleChanged(value) }
                override fun onExit(status: Int) { if (active()) exited(status) }
                override fun onCopy(text: String?) { if (active()) copy(text) }
                override fun onPasteRequest() { if (active()) paste() }
                override fun onBell() { if (active()) bell() }
            }
            sessionListener = listener
            if (resumed) selected.attachListener(listener)
            target.attachSession(selected.session())
            target.onScreenUpdated()
            title = if (selected.isRunning) displayTitle(selected.session()) else UiText.text("会话已结束")
        }
        if (!isReady) title = UiText.text("环境未就绪 —— 先到「安装」页装完再回来")
        revision++
    }

    fun closeTerminal(id: Long) {
        if (owner.ptyTabs().find(id) == null || !owner.beginClosePty(id)) return
        attachSelected()
        val app = context.applicationContext
        Thread({
            var failure: String? = null
            try {
                owner.closePty(id, 5000)
            } catch (error: Exception) {
                failure = SensitiveData.redact(error.message.toString())
            }
            val problem = failure
            main.post {
                if (problem != null) {
                    Toast.makeText(app, UiText.text("关闭失败，会话仍保留：") + problem, Toast.LENGTH_LONG).show()
                }
                attachSelected()
            }
        }, "dsha-close-terminal").start()
    }

    private fun displayTitle(session: TerminalSession?): String {
        val value = session?.title
        return if (value.isNullOrBlank()) "Ubuntu · PTY" else SensitiveData.redact(value.trim())
    }

    // ---- 扩展键与字号 ----

    /** Ctrl / Alt 是状态键：按一下亮起，下一个字符带上修饰键（TerminalView 会来问）。 */
    fun toggleModifier(ctrl: Boolean) {
        if (ctrl) ctrlDown = !ctrlDown else altDown = !altDown
        showKeyboard()
    }

    fun send(seq: String) {
        if (!inputAllowed()) return
        val session = attachedSession
        if (session == null || !session.isRunning) {
            Toast.makeText(context, UiText.text("会话已结束，请点击「新建」"), Toast.LENGTH_SHORT).show()
            return
        }
        val selected = owner.currentPty()
        if (selected == null || selected.isClosing) return
        session.write(seq)
        // 修饰键是一次性的：发完就灭
        if (ctrlDown || altDown) {
            ctrlDown = false
            altDown = false
        }
    }

    private fun fontSp(): Int = prefs.getInt(KEY_FONT_PX, FONT_DEF_SP).coerceIn(FONT_MIN_SP, FONT_MAX_SP)

    fun bumpFont(delta: Int) {
        val next = (fontSp() + delta).coerceIn(FONT_MIN_SP, FONT_MAX_SP)
        prefs.edit().putInt(KEY_FONT_PX, next).apply()
        applyFontSize(next)
    }

    private fun applyFontSize(sp: Int) {
        val target = view ?: return
        // TerminalView.setTextSize 收 px；行列数由它按字宽重算并同步给 PTY
        target.setTextSize(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics).toInt(),
        )
    }

    private fun showKeyboard() {
        val target = view ?: return
        if (!inputAllowed()) return
        target.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(target, 0)
    }

    private fun inputAllowed(): Boolean {
        val tab = owner.currentPty()
        return view != null && tab != null && !tab.isClosing && environmentReady()
    }

    // ---- PtySession.Listener 转发 ----

    private fun redraw() {
        if (!redrawPending.compareAndSet(false, true)) return
        val target = view
        if (target == null) {
            redrawPending.set(false)
            return
        }
        target.postOnAnimation {
            redrawPending.set(false)
            if (disposed || view !== target || target.windowToken == null) return@postOnAnimation
            target.onScreenUpdated()
        }
    }

    private fun titleChanged(value: String?) {
        val source = attachedSession
        main.post {
            if (disposed || attachedSession !== source) return@post
            if (!value.isNullOrBlank()) title = SensitiveData.redact(value.trim())
        }
    }

    private fun exited(status: Int) {
        val source = attachedSession
        main.post {
            if (!disposed && attachedSession === source) {
                title = UiText.text("会话已结束（退出码 ") + status + UiText.text("）")
            }
        }
    }

    private fun copy(text: String?) {
        val target = view
        val source = attachedSession
        main.post {
            try {
                if (disposed || target == null || view !== target || attachedSession !== source) return@post
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null && text != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("term", SensitiveData.redact(text)))
                    Toast.makeText(context, UiText.text("已复制"), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun paste() {
        val target = view
        val source = attachedSession
        main.post {
            try {
                if (disposed || target == null || view !== target || attachedSession !== source) return@post
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@post
                val clip = clipboard.primaryClip ?: return@post
                if (clip.itemCount == 0) return@post
                val text = clip.getItemAt(0).coerceToText(context)
                if (!text.isNullOrEmpty()) send(text.toString())
            } catch (_: Throwable) {
            }
        }
    }

    private fun bell() {
        val target = view
        main.post {
            try {
                if (disposed || target == null || view !== target) return@post
                // 响铃改成震一下：手机上「响」多半是骚扰
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(30)
            } catch (_: Throwable) {
            }
        }
    }

    // ---- TerminalViewClient ----

    override fun onScale(scale: Float): Float {
        // 双指缩放调字号：返回值是库要的「当前字号」
        if (scale < 0.9f || scale > 1.1f) bumpFont(if (scale > 1f) 1 else -1)
        return fontSp().toFloat()
    }

    override fun onSingleTapUp(e: MotionEvent?) = showKeyboard()

    /** 返回键就该是返回键，映射成 Esc 会让人退不出去。 */
    override fun shouldBackButtonBeMappedToEscape() = false

    /** 对中文输入法友好：逐字符提交，避免候选词把整行吞掉。 */
    override fun shouldEnforceCharBasedInput() = true

    override fun shouldUseCtrlSpaceWorkaround() = false

    override fun isTerminalViewSelected() = inputAllowed()

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?) = !inputAllowed()

    override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false

    /** 返回 false 才会走库自带的文本选择。 */
    override fun onLongPress(event: MotionEvent?) = false

    override fun readControlKey() = ctrlDown

    override fun readAltKey() = altDown

    override fun readShiftKey() = false

    override fun readFnKey() = false

    override fun onCodePoint(codePoint: Int, ctrl: Boolean, session: TerminalSession?) = !inputAllowed()

    override fun onEmulatorSet() = redraw()

    override fun logError(tag: String?, message: String?) {
        Log.e("DSHA-ptyview", SensitiveData.redact("$tag: $message"))
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w("DSHA-ptyview", SensitiveData.redact("$tag: $message"))
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i("DSHA-ptyview", SensitiveData.redact("$tag: $message"))
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d("DSHA-ptyview", SensitiveData.redact("$tag: $message"))
    }

    override fun logVerbose(tag: String?, message: String?) = Unit

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.w("DSHA-ptyview", SensitiveData.redact("$tag: $message $e"))
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.w("DSHA-ptyview", SensitiveData.redact("$tag: $e"))
    }
}