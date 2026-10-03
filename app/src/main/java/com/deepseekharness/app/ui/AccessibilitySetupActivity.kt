package com.deepseekharness.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luti.dshlauncher.ui.dsha.AccessibilitySetupPage
import com.luti.dshlauncher.ui.dsha.setDshaContent

/** 两步屏幕操作设置；权限仍由系统页面和用户决定。 */
class AccessibilitySetupActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            AccessibilitySetupPage(onBack = { finish() })
        }
    }
}