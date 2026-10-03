package com.luti.dshlauncher.ui.screen.terminal

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.SimpleTerminalBackend
import com.deepseekharness.app.core.TerminalSessionOwner
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode

/** 单个会话最多渲染的字符数（与原 TerminalFragment 一致，更早的输出截断）。 */
private const val MAX_RENDER = 100_000

/**
 * 终端页：数据源是 DSHA 的 [TerminalSessionOwner] 简易终端（proot 内的 bash，
 * 后端 [SimpleTerminalBackend]），会话在进程内共享，离开页面不会结束。
 * 维护期间（环境任务 / 未完成维护）拒绝新建与发送，文案取自引擎。
 */
@Composable
fun TerminalPager(bottomInnerPadding: Dp) {
    val context = LocalContext.current
    val owner = remember { TerminalSessionOwner.shared() }
    val banner = stringResource(R.string.terminal_banner)
    var fontSize by rememberSaveable { mutableFloatStateOf(14f) }
    // 引擎回调（任意线程）→ 主线程递增，触发重组
    var revision by remember { mutableIntStateOf(0) }

    DisposableEffect(owner) {
        val main = Handler(Looper.getMainLooper())
        val bump = Runnable { revision++ }
        val observer = object : TerminalSessionOwner.SimpleObserver {
            override fun onOutput(tab: TerminalSessionOwner.SimpleTab) { main.removeCallbacks(bump); main.postDelayed(bump, 50) }
            override fun onState(tab: TerminalSessionOwner.SimpleTab) { main.post(bump) }
            override fun onTabsChanged() { main.post(bump) }
        }
        owner.attachSimple(observer)
        main.post(bump)
        onDispose {
            main.removeCallbacks(bump)
            owner.detachSimple(observer)
        }
    }

    fun blocked(): Boolean {
        val message = SimpleTerminalBackend.blockMessage(HarnessController.get(context).proot())
        if (message.isEmpty()) return false
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return true
    }

    val onNewSession: () -> Unit = {
        if (!blocked()) {
            val tab = owner.addSimple(SimpleTerminalBackend.create(HarnessController.get(context).proot()))
            owner.selectSimple(tab.id)
            tab.value.session.ensureStarted()
        }
    }

    // 首次进入且从未建过会话时自动新建一个（与原 TerminalFragment 一致）
    DisposableEffect(Unit) {
        if (!owner.simpleTabs().wasInitialized()) onNewSession()
        onDispose { }
    }

    // ---- 每次 revision 变化重新取快照 ----
    // 读取 revision 建立快照依赖：引擎回调递增后此处重新取标签
    val tabs = revision.let { owner.simpleTabs().snapshot() }
    val current = owner.currentSimple()
    val selectedIndex = tabs.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
    val sessions: List<String> = tabs.map { tab ->
        val buffer = tab.value.output()
        val shown = when {
            buffer.isEmpty() -> banner
            buffer.length > MAX_RENDER -> UiText.text("…（输出过长已截断）\n") + buffer.substring(buffer.length - MAX_RENDER)
            else -> buffer
        }
        SensitiveData.redact(shown)
    }

    val onSelectSession: (Int) -> Unit = { index ->
        tabs.getOrNull(index)?.let { owner.selectSimple(it.id) }
    }

    val onClearSession: () -> Unit = {
        owner.currentSimple()?.value?.clearOutput()
        revision++
    }

    val onRunCommand: (String) -> Unit = run@{ command ->
        if (command.isBlank() || blocked()) return@run
        val tab = owner.currentSimple()
        if (tab == null) {
            onNewSession()
            owner.currentSimple()?.value?.session?.submit(command)
            return@run
        }
        if (tab.isClosing || !tab.value.session.submit(command)) {
            Toast.makeText(
                context,
                UiText.text("命令未发送：请检查长度（最多 16K 字符）及空字符，输入已保留"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> TerminalPagerMiuix(
            fontSize = fontSize,
            onFontSizeChange = { fontSize = it },
            sessions = sessions,
            selectedSession = selectedIndex,
            onSelectSession = onSelectSession,
            onNewSession = onNewSession,
            onClearSession = onClearSession,
            onRunCommand = onRunCommand,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> TerminalPagerMaterial(
            fontSize = fontSize,
            onFontSizeChange = { fontSize = it },
            sessions = sessions,
            selectedSession = selectedIndex,
            onSelectSession = onSelectSession,
            onNewSession = onNewSession,
            onClearSession = onClearSession,
            onRunCommand = onRunCommand,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}