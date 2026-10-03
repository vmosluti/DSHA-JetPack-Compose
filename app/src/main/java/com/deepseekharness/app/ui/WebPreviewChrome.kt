package com.deepseekharness.app.ui

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.dsha.DshaCard
import com.luti.dshlauncher.ui.dsha.DshaCardTitle
import com.luti.dshlauncher.ui.dsha.DshaPrimaryButton
import com.luti.dshlauncher.ui.dsha.DshaProgressBar
import com.luti.dshlauncher.ui.dsha.DshaSecondaryButton
import com.luti.dshlauncher.ui.dsha.DshaSelectableText
import com.luti.dshlauncher.ui.dsha.DshaThemeHost
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网页预览的原生外框（取代 activity_web_preview.xml）。
 * WebView 仍放在 [container]（原生 FrameLayout，WebView 只能以 View 承载）；
 * 错误面板与顶部进度条改为 Compose 覆盖层，配色走 Miuix / Material 主题。
 */
class WebPreviewChrome(
    activity: ComponentActivity,
    private val onRetry: Runnable,
    private val onRecovery: Runnable,
    private val onLogs: Runnable,
    private val onBrowser: Runnable,
) {
    /** 外层根视图：网页容器 + Compose 覆盖层。 */
    @JvmField val root: FrameLayout = FrameLayout(activity)
    /** 网页容器，WebView 由 WebPreviewActivity 加入/移除。 */
    @JvmField val container: FrameLayout = FrameLayout(activity)

    private var errorVisible by mutableStateOf(false)
    private var errorTitle by mutableStateOf("")
    private var errorDetail by mutableStateOf("")
    // 状态名与 Java 侧调用的 setProgress/setProgressVisible 区分开，避免委托属性的 setter 与之同名冲突。
    private var barVisible by mutableStateOf(true)
    private var barValue by mutableIntStateOf(0)

    init {
        root.addView(container, FrameLayout.LayoutParams(-1, -1))
        val overlay = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // 网页页面自己管理系统栏（WebFullscreenUi），这里不再 enableEdgeToEdge。
            setContent { DshaThemeHost(applySystemBars = false) { Overlay() } }
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    }

    fun showError(title: String, detail: String) {
        errorTitle = title
        errorDetail = detail
        errorVisible = true
        barVisible = false
    }

    fun hideError() { errorVisible = false }

    fun setProgressVisible(visible: Boolean) { barVisible = visible }

    fun setProgress(value: Int) { barValue = value.coerceIn(0, 100) }

    @androidx.compose.runtime.Composable
    private fun Overlay() {
        Box(Modifier.fillMaxSize()) {
            // 进度条覆盖在网页顶部，不占高度，避免加载完成时页面跳动。
            if (barVisible && !errorVisible) {
                DshaProgressBar(
                    progress = barValue / 100f,
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopStart),
                )
            }
            if (errorVisible) {
                val background = when (LocalUiMode.current) {
                    UiMode.Miuix -> MiuixTheme.colorScheme.surface
                    UiMode.Material -> MaterialTheme.colorScheme.surface
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    DshaCard {
                        DshaCardTitle(errorTitle)
                        DshaSelectableText(errorDetail)
                    }
                    DshaPrimaryButton(UiText.choose("重试", "Retry")) { onRetry.run() }
                    DshaSecondaryButton(UiText.choose("返回启动页处理插件 / 安全启动", "Back to Start: plugins / safe start")) { onRecovery.run() }
                    DshaSecondaryButton(UiText.choose("下载错误日志", "Download error logs")) { onLogs.run() }
                    DshaSecondaryButton(UiText.choose("在系统浏览器中打开", "Open in browser")) { onBrowser.run() }
                }
            }
        }
    }
}