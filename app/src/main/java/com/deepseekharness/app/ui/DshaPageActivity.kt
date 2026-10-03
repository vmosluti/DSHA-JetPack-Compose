package com.deepseekharness.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.deepseekharness.app.bridge.LocalNetworkAccess
import com.luti.dshlauncher.ui.dsha.AboutPage
import com.luti.dshlauncher.ui.dsha.ConfigPage
import com.luti.dshlauncher.ui.dsha.DeviceGrantsPage
import com.luti.dshlauncher.ui.dsha.DshaTerminalPage
import com.luti.dshlauncher.ui.dsha.InstallPage
import com.luti.dshlauncher.ui.dsha.OverlayPage
import com.luti.dshlauncher.ui.dsha.PluginPage
import com.luti.dshlauncher.ui.dsha.WorkspacePage
import com.luti.dshlauncher.ui.dsha.setDshaContent

/** 能为 Compose 页面申请局域网权限的宿主（DshaPageActivity 与主界面 MainActivity）。 */
interface LocalNetworkRequester {
    fun requestLocalNetwork()
}

/**
 * Compose 外壳打开 DSHA 原生功能页（安装/配置/数据/设备授权/插件/悬浮条/关于/终端）的宿主。
 * 直接分发 Compose 页面，不再托管 Fragment 与布局容器。
 * 主界面内的设置子页面已改为 Navigation3 路由，这里保留给通知、深链等外部入口。
 */
class DshaPageActivity : androidx.fragment.app.FragmentActivity(), LocalNetworkRequester {

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) LocalNetworkAccess.applyConfiguredFeatures(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra(EXTRA_PAGE)?.takeIf { it in PAGES }
        if (page == null) {
            finish()
            return
        }
        val showInstalled = intent.getBooleanExtra(EXTRA_SHOW_INSTALLED, false)
        val onBack: () -> Unit = { onBackPressedDispatcher.onBackPressed() }
        setDshaContent {
            when (page) {
                PAGE_INSTALL -> InstallPage(onBack = onBack)
                PAGE_CONFIG -> ConfigPage(onBack = onBack)
                PAGE_DATA -> WorkspacePage(onBack = onBack)
                PAGE_DEVICE -> DeviceGrantsPage(onBack = onBack)
                PAGE_PLUGINS -> PluginPage(onBack = onBack, showInstalled = showInstalled)
                PAGE_OVERLAY -> OverlayPage(onBack = onBack)
                PAGE_ABOUT -> AboutPage(onBack = onBack)
                PAGE_TERMINAL -> DshaTerminalPage(onBack = onBack)
            }
        }
    }

    /** ConfigPage / DeviceGrantsPage 开启 LAN 或无线 ADB 时调用。 */
    override fun requestLocalNetwork() {
        if (LocalNetworkAccess.granted(this)) LocalNetworkAccess.applyConfiguredFeatures(this)
        else localNetworkPermission.launch(LocalNetworkAccess.PERMISSION)
    }

    companion object {
        const val PAGE_INSTALL = "install"
        const val PAGE_CONFIG = "config"
        const val PAGE_DATA = "data"
        const val PAGE_DEVICE = "device"
        const val PAGE_PLUGINS = "plugins"
        const val PAGE_OVERLAY = "overlay"
        const val PAGE_ABOUT = "about"
        const val PAGE_TERMINAL = "terminal"
        private const val EXTRA_PAGE = "dsha_page"
        private const val EXTRA_SHOW_INSTALLED = "show_installed"
        private val PAGES = setOf(
            PAGE_INSTALL, PAGE_CONFIG, PAGE_DATA, PAGE_DEVICE,
            PAGE_PLUGINS, PAGE_OVERLAY, PAGE_ABOUT, PAGE_TERMINAL,
        )

        @JvmStatic
        fun intent(context: Context, page: String): Intent =
            Intent(context, DshaPageActivity::class.java).putExtra(EXTRA_PAGE, page)

        @JvmStatic
        fun open(context: Context, page: String) {
            val intent = intent(context, page)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}