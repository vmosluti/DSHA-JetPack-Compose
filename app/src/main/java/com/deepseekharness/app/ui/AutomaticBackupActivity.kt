package com.deepseekharness.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.luti.dshlauncher.ui.dsha.AutomaticBackupPage
import com.luti.dshlauncher.ui.dsha.dshaBackupStage
import com.luti.dshlauncher.ui.dsha.setDshaContent

/** 自动计划、本机副本与原有恢复预检共用同一条数据路径。 */
class AutomaticBackupActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            AutomaticBackupPage(onBack = { finish() })
        }
    }

    companion object {
        @JvmStatic
        fun stage(value: String?): String = dshaBackupStage(value)
    }
}