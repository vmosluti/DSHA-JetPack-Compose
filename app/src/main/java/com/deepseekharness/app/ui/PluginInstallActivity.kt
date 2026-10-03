package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModelProvider
import com.deepseekharness.app.core.PluginRepository
import com.deepseekharness.app.util.PluginInstallLink
import com.luti.dshlauncher.ui.dsha.PluginInstallPage
import com.luti.dshlauncher.ui.dsha.setDshaContent

/**
 * 网站唤起的插件安装确认页宿主：解析安装链接、处理「环境未就绪」降级，
 * 页面本身由 Compose 渲染。类名与 `AndroidManifest.xml` 的 dsha.cc/install 过滤器保持一致。
 */
class PluginInstallActivity : androidx.fragment.app.FragmentActivity() {

    private var repository: PluginRepository? = null

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val parsed = runCatching { PluginInstallLink.parse(intent?.dataString) }
        val link = parsed.getOrNull()
        val linkError = parsed.exceptionOrNull()?.message
        // builtin 请求或链接无效时不需要仓库；与原实现一致。
        if (link != null && link.builtin.isEmpty()) {
            repository = ViewModelProvider(this)[PluginRepository::class.java]
        }
        val repo = repository
        setDshaContent {
            BackHandler(enabled = true) { close() }
            PluginInstallPage(
                link = link,
                linkError = linkError,
                repository = repo,
                autoInspect = saved == null,
                onClose = { close() },
                onOpenMain = { plugins -> openMain(plugins) },
                onDeferUntilReady = { deferUntilReady() },
            )
        }
    }

    private fun close() {
        repository?.discardPreview()
        finish()
    }

    private fun openMain(plugins: Boolean) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("open_plugins", plugins)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    /** 环境未就绪：记住链接，交给主界面完成初始化后回到安装确认。 */
    private fun deferUntilReady() {
        getSharedPreferences("dsha-install-link", 0)
            .edit()
            .putString("pending", intent?.dataString)
            .apply()
        openMain(false)
        finish()
    }
}