package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luti.dshlauncher.ui.dsha.OnboardingReadyScreen
import com.luti.dshlauncher.ui.dsha.setDshaContent

class OnboardingReadyActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            OnboardingReadyScreen(
                onOpen = {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                    finish()
                },
            )
        }
    }
}