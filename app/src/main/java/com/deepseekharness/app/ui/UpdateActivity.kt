package com.deepseekharness.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.deepseekharness.app.core.UpdateRepository
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.RuntimeRollbackPage
import com.luti.dshlauncher.ui.dsha.UpdatePage
import com.luti.dshlauncher.ui.dsha.setDshaContent

/**
 * 版本更新页宿主：安装调度（权限、FileProvider、系统安装器）留在 Activity，
 * 页面本身由 Compose 渲染。类名与 `UpdateDownloadService` 通知、设置页跳转保持一致。
 */
class UpdateActivity : androidx.fragment.app.FragmentActivity() {

    companion object {
        /** 由原 RuntimeRecoveryUi 等调用点传入，直接进入兼容运行时回退页。 */
        const val EXTRA_ROLLBACK = "rollback"
    }

    private lateinit var repository: UpdateRepository
    private var resumeInstall = false
    private var installDispatchReady = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 对应原 requestCode 104：无论是否允许，都继续下载（通知只是提示渠道）。
            repository.download()
        }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        repository = ViewModelProvider(this)[UpdateRepository::class.java]
        resumeInstall = saved != null && saved.getBoolean("resumeInstall")
        repository.restoreInterruptedInstall(saved != null && saved.getBoolean("installPending"))
        setDshaContent {
            var showRollback by rememberSaveable {
                mutableStateOf(intent.getBooleanExtra(EXTRA_ROLLBACK, false))
            }
            BackHandler(enabled = showRollback) { showRollback = false }
            if (showRollback) {
                RuntimeRollbackPage(onBack = { showRollback = false })
            } else {
                UpdatePage(
                    repository = repository,
                    onBack = { finish() },
                    onOpenExtract = {
                        startActivity(
                            Intent(this, ExtractActivity::class.java).putExtra("review_only", true),
                        )
                    },
                    onOpenRollback = { showRollback = true },
                    onRequestNotifications = { requestNotifications() },
                    onInstall = { install() },
                    onCancel = { repository.cancel() },
                )
            }
        }
        if (saved == null && !repository.hasTask()) repository.check()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            repository.download()
        }
    }

    private fun install() {
        if (repository.installationPending()) return
        try {
            if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                resumeInstall = true
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
                return
            }
            repository.requestInstall()
        } catch (error: Exception) {
            resumeInstall = false
            showInstallError(error)
        }
    }

    private fun dispatchInstall() {
        if (!installDispatchReady || isFinishing || isDestroyed) return
        val apk = repository.takeInstallReady() ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.updates", apk)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        } catch (error: Exception) {
            showInstallError(error)
        }
    }

    private fun showInstallError(error: Exception) {
        repository.installFailed(
            UiText.text("无法安装：") + error.message + UiText.text("；可重试"),
        )
        Toast.makeText(this, repository.installation().value?.error, Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(saved: Bundle) {
        saved.putBoolean("resumeInstall", resumeInstall)
        saved.putBoolean("installPending", repository.installationPending())
        super.onSaveInstanceState(saved)
    }

    override fun onResume() {
        super.onResume()
        if (resumeInstall) {
            resumeInstall = false
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
                install()
            } else {
                Toast.makeText(
                    this,
                    UiText.text("未允许安装更新，可稍后重试"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        installDispatchReady = true
        dispatchInstall()
    }

    override fun onPause() {
        installDispatchReady = false
        super.onPause()
    }
}