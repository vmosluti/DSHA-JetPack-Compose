package com.deepseekharness.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luti.dshlauncher.ui.dsha.StoragePage
import com.luti.dshlauncher.ui.dsha.setDshaContent

class StorageActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            StoragePage(onBack = { finish() })
        }
    }
}