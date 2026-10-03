package com.luti.dshlauncher.ui.dsha

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.deepseekharness.app.util.UiText

/**
 * 社区插件生态页（Compose 版）。
 *
 * 真实网站加载在独立 WebView 中完成，不注入本机桥、模型凭据或文件访问能力；
 * 站内链接保留在应用内，其余协议交给系统处理。状态保存/销毁由宿主负责。
 */
@Composable
fun CommunityPage(
    controller: CommunityWebController,
    onBack: () -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) { onBack() }

    // 供宿主在 onSaveInstanceState/onDestroy 中调用；页面只负责创建与事件回传。
    remember(controller) { controller.onStatus = { status = it }; controller }

    DshaPageScaffold(
        title = UiText.choose("社区插件生态", "Community plugins"),
        onBack = onBack,
        footer = listOf(
            DshaAction(
                title = UiText.choose("重试", "Retry"),
                primary = true,
                onClick = { controller.reload() },
            ),
        ),
    ) {
        status?.let { message ->
            item { DshaNote(message) }
        }
        item {
            // WebView 由 controller 缓存复用：每次 factory 都新建外层 FrameLayout，
            // 先把 WebView 从旧容器摘下再挂入；onRelease 时主动摘下，
            // 避免 Lazy 项回收重建时出现 "child already has a parent"。
            AndroidView(
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = 12.dp),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        val web = controller.attach(ctx)
                        (web.parent as? ViewGroup)?.removeView(web)
                        addView(web, FrameLayout.LayoutParams(-1, -1))
                    }
                },
                onRelease = { frame -> frame.removeAllViews() },
            )
        }
    }
}

/** 社区页的 WebView 控制器：宿主持有生命周期，Compose 页面只借用视图。 */
class CommunityWebController(private val onLeave: () -> Unit) {

    var onStatus: (String?) -> Unit = {}

    private var browser: WebView? = null
    private var pageFailed = false

    fun attach(context: Context): WebView {
        browser?.let { return it }
        @SuppressLint("SetJavaScriptEnabled")
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && navigate(context, request.url)

                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    navigate(context, Uri.parse(url))

                override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) {
                    pageFailed = false
                    onStatus(UiText.choose("正在连接社区…", "Connecting to the community…"))
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (!pageFailed) onStatus(null)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        pageFailed = true
                        onStatus(UiText.choose("社区暂时无法连接，请检查网络后重试。", "Unable to connect. Check your network and retry."))
                    }
                }
            }
            loadUrl(HOME)
        }
        browser = view
        return view
    }

    fun reload() {
        pageFailed = false
        browser?.loadUrl(HOME)
    }

    fun leave() {
        val view = browser
        if (view != null && view.canGoBack()) view.goBack() else onLeave()
    }

    fun saveState(out: android.os.Bundle) {
        browser?.saveState(out)
    }

    fun destroy() {
        browser?.let {
            it.stopLoading()
            it.destroy()
        }
        browser = null
    }

    private fun navigate(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme
        val host = uri.host
        if ("https" == scheme && ("dsha.cc" == host || "www.dsha.cc" == host)) return false
        try {
            if ("dsha" == scheme) {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(context.packageName))
                return true
            }
            if ("https" == scheme || "http" == scheme) {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        } catch (_: RuntimeException) {
            onStatus(UiText.choose("无法打开此链接。", "Unable to open this link."))
        }
        return true
    }

    companion object {
        const val HOME = "https://dsha.cc/"
    }
}