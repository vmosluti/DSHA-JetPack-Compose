package com.deepseekharness.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luti.dshlauncher.ui.dsha.CommunityPage
import com.luti.dshlauncher.ui.dsha.CommunityWebController
import com.luti.dshlauncher.ui.dsha.setDshaContent

/**
 * 社区插件生态宿主：保留原类名与入口，界面改为 Compose 页面 + 独立 WebView。
 *
 * 真实网站加载不注入本机桥、模型凭据或文件访问能力；返回键优先回退站内历史。
 */
class CommunityActivity : androidx.fragment.app.FragmentActivity() {

    private lateinit var browser: CommunityWebController

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        browser = CommunityWebController(onLeave = { finish() })
        setDshaContent {
            CommunityPage(controller = browser, onBack = { browser.leave() })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        browser.destroy()
        super.onDestroy()
    }
}