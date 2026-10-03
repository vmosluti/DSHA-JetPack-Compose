package com.deepseekharness.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.deepseekharness.app.core.DiagnosticRepository
import com.deepseekharness.app.core.ErrorLogRepository
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DiagnosticPage
import com.luti.dshlauncher.ui.dsha.setDshaContent
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自检与诊断宿主：保留原有类名与入口契约，界面改为 Compose 页面分发。
 *
 * [downloadLogs] 仍是其它页面（如维护记录）打开「下载错误日志」的静态入口；
 * `download_error_logs` 与 `open_plugins` 两个 extra 语义保持不变。
 */
class DiagnosticActivity : androidx.fragment.app.FragmentActivity() {

    private lateinit var repository: DiagnosticRepository
    private lateinit var logs: ErrorLogRepository

    // 复现步骤由 Compose 页面持有，导出/复制时通过回调回传完整文本。
    private var latestReport: String = ""

    private val logExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) logs.export(uri)
    }

    private val exporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val report = latestReport
        Thread({
            val message = try {
                contentResolver.openOutputStream(uri, "wt").use { stream ->
                    if (stream == null) throw IOException(UiText.text("无法写入所选位置"))
                    stream.write(report.toByteArray(StandardCharsets.UTF_8))
                }
                UiText.text("诊断报告已导出")
            } catch (error: Exception) {
                UiText.text("导出失败：") + error.javaClass.simpleName
            }
            runOnUiThread {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }, "diagnostic-export").start()
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        repository = ViewModelProvider(this)[DiagnosticRepository::class.java]
        logs = ViewModelProvider(this)[ErrorLogRepository::class.java]

        setDshaContent {
            DiagnosticPage(
                repository = repository,
                logs = logs,
                onBack = { finish() },
                onOpenRecovery = { startActivity(Intent(this, StartupRecoveryActivity::class.java)) },
                onOpenPlugins = {
                    startActivity(Intent(this, MainActivity::class.java).putExtra("open_plugins", true))
                },
                onSaveLogs = { launchLogExport() },
                onExportReport = { report ->
                    latestReport = report
                    launchReportExport()
                },
            )
        }

        if (saved == null && intent?.getBooleanExtra("download_error_logs", false) == true) {
            logs.download()
        }
        // 首页先展示检查范围，由用户发起耗时探针。
    }

    private fun launchLogExport() {
        try {
            logExporter.launch(ErrorLogRepository.filename())
        } catch (_: RuntimeException) {
            Toast.makeText(this, UiText.text("无法打开文件管理器，请尝试下载到默认目录"), Toast.LENGTH_LONG).show()
        }
    }

    private fun launchReportExport() {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            exporter.launch("DSHA-diagnostic-$stamp.txt")
        } catch (_: RuntimeException) {
            Toast.makeText(
                this,
                UiText.text("无法打开保存位置，请使用复制报告或检查系统文件管理器"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    companion object {
        /** 打开诊断页并立即生成错误日志；保持原 Java 调用点的静态入口形态。 */
        @JvmStatic
        fun downloadLogs(context: Context): Intent =
            Intent(context, DiagnosticActivity::class.java).putExtra("download_error_logs", true)
    }
}