package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.recovery.RecoveryController
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaPrimaryButton
import com.luti.dshlauncher.ui.dsha.DshaSecondaryButton
import com.luti.dshlauncher.ui.dsha.DshaSelectableText
import com.luti.dshlauncher.ui.dsha.setDshaContent

/**
 * 应急网页保留自己的页面，旋转和正式实例的状态变化均不替换其 URL。
 * 外框改为 Compose；WebView 仍由 [RecoveryBrowserSurface] 持有并通过 AndroidView 承载。
 */
class RecoveryWebActivity : androidx.fragment.app.FragmentActivity() {

    class Retained : ViewModel() {
        @JvmField var browser: RecoveryBrowserSurface? = null
        @JvmField var identity: String = ""
        override fun onCleared() { browser?.close() }
    }

    private lateinit var retained: Retained
    private var microphone: BrowserMicrophone? = null
    private val main = Handler(Looper.getMainLooper())

    private var error by mutableStateOf("")
    private var missingCredential by mutableStateOf(false)
    /** 由 surface.attach 返回、需要放进 AndroidView 的网页视图。 */
    private var webView by mutableStateOf<View?>(null)

    fun microphone(): BrowserMicrophone? = microphone

    fun microphoneSessionCurrent(): Boolean {
        val snapshot = RecoveryController.get(this).snapshot()
        return !isFinishing && !isDestroyed && ::retained.isInitialized && snapshot.ready &&
            retained.identity == snapshot.instanceId + ":" + snapshot.generation
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        microphone = BrowserMicrophone(this)
        retained = ViewModelProvider(this)[Retained::class.java]
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (retained.browser?.back() != true) finish()
            }
        })
        setDshaContent { Page() }

        val snapshot = RecoveryController.get(this).snapshot()
        if (!snapshot.ready) {
            showError(t("应急服务尚未就绪，请返回查看启动状态。", "The emergency service is not ready. Return to review its status."))
            return
        }
        missingCredential = !snapshot.modelCredentialAvailable
        val identity = snapshot.instanceId + ":" + snapshot.generation
        if (identity != retained.identity) {
            retained.browser?.close()
            retained.browser = null
            retained.identity = identity
        }
        try {
            val browser = retained.browser ?: RecoveryBrowserFactory.create(this).also { retained.browser = it }
            webView = browser.attach(this) { showError(it) }
            browser.load(snapshot.authUrl, snapshot.baseUrl, snapshot.cookie)
            browser.syncLanguage(ConfigStore(this).uiLanguage)
        } catch (failure: RuntimeException) {
            showError(t("应急网页无法加载：", "Cannot load the emergency page: ") + failure.javaClass.simpleName)
        } catch (failure: LinkageError) {
            showError(t("应急网页无法加载：", "Cannot load the emergency page: ") + failure.javaClass.simpleName)
        }
    }

    @Composable
    private fun Page() {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column(Modifier.weight(3f)) {
                    DshaPrimaryButton(t("应急 DSH · 修复提案", "Emergency DSH · Repairs")) { openRecovery() }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    DshaSecondaryButton(t("重载", "Reload")) { reload() }
                }
            }
            if (error.isNotEmpty()) DshaSelectableText(error)
            if (missingCredential) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    DshaNote(t(
                        "本次应急启动未取得模型密钥。诊断页仍可用；发送对话前，请返回应急页停止服务并填写临时 API Key 后重新启动。",
                        "No model key was available at recovery startup. Diagnostics remain available. To chat, return to Recovery, stop the service, enter a temporary API key, and restart.",
                    ))
                    DshaSecondaryButton(t("返回应急页配置", "Open recovery settings")) { openRecovery() }
                }
            }
            val view = webView
            if (view != null) {
                // key(view)：surface 重建出新 WebView 时整体换一个 AndroidView，
                // 旧容器在 onRelease 中先释放子 View，保证新容器挂入时无父容器。
                key(view) {
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                (view.parent as? android.view.ViewGroup)?.removeView(view)
                                addView(view, FrameLayout.LayoutParams(-1, -1))
                            }
                        },
                        onRelease = { frame -> frame.removeAllViews() },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }

    private fun openRecovery() {
        startActivity(Intent(this, RecoveryActivity::class.java))
    }

    private fun reload() {
        retained.browser?.close()
        retained.browser = null
        recreate()
    }

    private fun showError(text: String) {
        runOnUiThread { error = SensitiveData.redact(text) }
    }

    private val monitor: Runnable = object : Runnable {
        override fun run() {
            val state = RecoveryController.get(this@RecoveryWebActivity).snapshot()
            if (retained.identity != state.instanceId + ":" + state.generation || !state.ready) {
                retained.browser?.close()
                retained.browser = null
                webView = null
                showError(t("应急服务已停止或代次已变化，请返回恢复页。", "The emergency service stopped or changed. Return to Recovery."))
            } else {
                main.postDelayed(this, 1000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        retained.browser?.syncLanguage(ConfigStore(this).uiLanguage)
        main.post(monitor)
    }

    override fun onPause() {
        main.removeCallbacks(monitor)
        super.onPause()
    }

    override fun onDestroy() {
        if (::retained.isInitialized) retained.browser?.detach()
        microphone?.close()
        super.onDestroy()
    }

    private companion object {
        fun t(zh: String, en: String): String = UiText.choose(zh, en)
    }
}