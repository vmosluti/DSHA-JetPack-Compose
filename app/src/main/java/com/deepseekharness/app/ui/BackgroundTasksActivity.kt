package com.deepseekharness.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.deepseekharness.app.backup.NativeBackupJobs
import com.deepseekharness.app.core.RuntimeTasks
import com.luti.dshlauncher.ui.dsha.BackgroundTasksPage
import com.luti.dshlauncher.ui.dsha.setDshaContent

/** 任务观察页只读状态；取消操作仍交给原业务页面的安全提交边界。 */
class BackgroundTasksActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDshaContent {
            BackgroundTasksPage(onBack = { finish() })
        }
    }

    companion object {
        @JvmStatic
        fun open(context: Context) {
            context.startActivity(Intent(context, BackgroundTasksActivity::class.java))
        }

        @JvmStatic
        fun busy(context: Context): Boolean =
            RuntimeTasks.isBusy() || NativeBackupJobs.get(context).state().busy
    }
}