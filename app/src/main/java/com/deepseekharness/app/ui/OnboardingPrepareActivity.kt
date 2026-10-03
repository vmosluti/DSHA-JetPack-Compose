package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.core.HarnessController
import com.luti.dshlauncher.ui.dsha.OnboardingPrepareScreen
import com.luti.dshlauncher.ui.dsha.setDshaContent

class OnboardingPrepareActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            OnboardingPrepareScreen(
                onInstall = {
                    ConfigStore(this).setWelcomed(true)
                    val ready = HarnessController.get(this).isEnvironmentReady
                    val next = if (ready) ModelSetupActivity::class.java else ExtractActivity::class.java
                    startActivity(
                        Intent(this, next)
                            .putExtra("first_setup", true)
                            .putExtra("first_run", true),
                    )
                    finish()
                },
                onBack = { finish() },
            )
        }
    }
}