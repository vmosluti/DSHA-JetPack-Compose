package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luti.dshlauncher.ui.dsha.WelcomeScreen
import com.luti.dshlauncher.ui.dsha.setDshaContent

/** 欢迎引导（3 页）：第 3 页点「开始」进入解压/主界面。不再使用 XML ViewPager。 */
class WelcomeActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            WelcomeScreen(
                onContinue = {
                    startActivity(Intent(this, OnboardingPrepareActivity::class.java))
                },
                onSkip = {
                    startActivity(Intent(this, OnboardingPrepareActivity::class.java))
                },
            )
        }
    }
}