package com.luti.dshlauncher.ui.dsha

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.core.DiagnosticRepository
import com.deepseekharness.app.core.ErrorLogRepository
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * 自检与诊断页（Compose 版）：检查范围、脱敏报告、启动记录与错误日志导出。
 *
 * 探针、日志收集仍由应用范围仓库执行；导出/另存为交给宿主 Activity 的文件选择器。
 */
@Composable
fun DiagnosticPage(
    repository: DiagnosticRepository,
    logs: ErrorLogRepository,
    onBack: () -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenPlugins: () -> Unit,
    onSaveLogs: () -> Unit,
    onExportReport: (report: String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var report by remember { mutableStateOf(repository.report.value.orEmpty()) }
    var phase by remember { mutableStateOf(repository.phase.value) }
    var busy by remember { mutableStateOf(repository.busy.value == true) }
    var results by remember { mutableStateOf(repository.results.value.orEmpty()) }
    var logState by remember { mutableStateOf(logs.state.value) }
    var steps by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var history by remember { mutableStateOf<List<StartupEntry>?>(null) }

    DisposableEffect(repository, logs, lifecycleOwner) {
        val reportObserver = Observer<String> { report = it.orEmpty() }
        val phaseObserver = Observer<DiagnosticRepository.Phase> { phase = it }
        val busyObserver = Observer<Boolean> { busy = it == true }
        val resultsObserver = Observer<List<DiagnosticRepository.Result>> { results = it.orEmpty() }
        val logsObserver = Observer<ErrorLogRepository.State> { logState = it }
        repository.report.observe(lifecycleOwner, reportObserver)
        repository.phase.observe(lifecycleOwner, phaseObserver)
        repository.busy.observe(lifecycleOwner, busyObserver)
        repository.results.observe(lifecycleOwner, resultsObserver)
        logs.state.observe(lifecycleOwner, logsObserver)
        onDispose {
            repository.report.removeObserver(reportObserver)
            repository.phase.removeObserver(phaseObserver)
            repository.busy.removeObserver(busyObserver)
            repository.results.removeObserver(resultsObserver)
            logs.state.removeObserver(logsObserver)
        }
    }

    fun completeReport(): String =
        SensitiveData.redact(report + UiText.text("\n用户补充复现步骤：\n") + steps)

    // 每次点按都发起一次加载；用自增计数触发，避免「空结果」被当成未加载而反复重跑。
    var historyRequest by remember { mutableStateOf(0) }

    LaunchedEffect(historyRequest) {
        if (historyRequest == 0) return@LaunchedEffect
        val entries = withContext(Dispatchers.IO) {
            runCatching { HarnessController.get(context).startupDiagnostics().history() }.getOrDefault(emptyList())
        }
        history = entries.map { StartupEntry(it.started, it.stage, it.status, it.reason, it.log) }
    }

    val headline: String
    val statusLine: String
    when (phase) {
        DiagnosticRepository.Phase.RUNNING -> {
            headline = UiText.choose("正在检查", "Checking")
            statusLine = UiText.text("正在检查环境…")
        }
        DiagnosticRepository.Phase.FAILED -> {
            headline = UiText.choose("检查未完成", "Check incomplete")
            statusLine = UiText.choose(
                "请打开失败卡片查看原因，也可复制或导出脱敏报告。",
                "Open the failure card for details, or copy or export the redacted report.",
            )
        }
        DiagnosticRepository.Phase.SUCCEEDED -> {
            headline = UiText.choose("检查已完成", "Checks completed")
            statusLine = UiText.text("报告保留在本机，复制或导出后可用于反馈")
        }
        else -> {
            headline = context.getString(R.string.ui134_check_ready)
            statusLine = UiText.text("报告保留在本机，复制或导出后可用于反馈")
        }
    }

    DshaPageScaffold(
        title = context.getString(R.string.ui2_diagnostics),
        subtitle = context.getString(R.string.ui2_diagnostics_intro),
        onBack = onBack,
        footer = buildList {
            add(
                DshaAction(
                    title = context.getString(R.string.ui_m0209),
                    primary = true,
                    enabled = !busy,
                    onClick = { repository.generate() },
                ),
            )
            add(
                DshaAction(
                    title = context.getString(R.string.ui_m0103),
                    enabled = !busy,
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                ?: throw IllegalStateException(UiText.text("剪贴板不可用"))
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(UiText.text("DSHA 诊断"), completeReport()),
                            )
                            Toast.makeText(context, UiText.text("已复制脱敏报告"), Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, UiText.text("复制失败，可尝试导出报告"), Toast.LENGTH_LONG).show()
                        }
                    },
                ),
            )
            add(
                DshaAction(
                    title = context.getString(R.string.ui_m0109),
                    enabled = !busy,
                    onClick = { onExportReport(completeReport()) },
                ),
            )
        },
    ) {
        item {
            DshaCard {
                DshaSelectableText(headline)
                DshaNote(statusLine)
            }
        }
        if (busy) item { DshaProgressBar(null) }

        item { DshaNote(context.getString(R.string.ui134_results)) }
        if (results.isEmpty()) {
            item { DshaNote(UiText.text("尚未生成检查结果，点「重新诊断」开始。")) }
        } else {
            dshaEntryCard(
                results.map { item ->
                    DshaEntry(
                        title = item.title,
                        summary = item.status,
                        onClick = { detail = item.title to item.detail },
                    )
                },
            )
        }

        item { DshaNote(context.getString(R.string.ui134_start_records)) }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    title = context.getString(R.string.ui134_recent_startups),
                    summary = context.getString(R.string.ui134_recent_startups_sub),
                    onClick = { historyRequest++ },
                ),
                DshaEntry(
                    title = context.getString(R.string.ui134_startup_recovery),
                    summary = context.getString(R.string.ui134_startup_recovery_sub),
                    onClick = onOpenRecovery,
                ),
            ),
        )

        item { DshaCategory(context.getString(R.string.ui2_error_logs)) }
        item {
            DshaCard {
                DshaSelectableText(logState?.message.orEmpty())
                DshaPrimaryButton(
                    title = context.getString(R.string.ui_m0047),
                    enabled = logState?.busy != true,
                ) { logs.download() }
                DshaSecondaryButton(
                    title = context.getString(R.string.ui_m0080),
                    enabled = logState?.busy != true,
                ) { onSaveLogs() }
                val uri = logState?.uri
                if (uri != null && logState?.busy != true) {
                    DshaSecondaryButton(title = UiText.text("查看上次日志")) { openLog(context, uri) }
                }
            }
        }

        item { DshaCategory(context.getString(R.string.ui2_troubleshooting)) }
        item {
            DshaCard {
                DshaPrimaryButton(
                    title = context.getString(R.string.ui_m0060),
                    enabled = !busy,
                ) { repository.repairNetworkTools() }
                DshaSecondaryButton(title = context.getString(R.string.ui_m0013)) { onOpenPlugins() }
            }
        }

        item { DshaCategory(context.getString(R.string.ui2_reproduction)) }
        item {
            DshaTextField(
                title = UiText.text("复现步骤"),
                value = steps,
                onValueChange = { steps = it.take(2000) },
                hint = context.getString(R.string.ui_m0182),
            )
        }
        if (report.isNotEmpty()) {
            item { DshaLogBox(report, height = 220.dp) }
        }
    }

    detail?.let { (title, body) ->
        DshaMessageDialog(
            title = title,
            message = body,
            visible = true,
            onDismiss = { detail = null },
        )
    }

    history?.let { entries ->
        if (entries.isEmpty()) {
            DshaMessageDialog(
                title = context.getString(R.string.ui134_recent_startups),
                message = UiText.choose("还没有启动记录。", "No startup records yet."),
                visible = true,
                onDismiss = { history = null },
            )
        } else {
            DshaContentDialog(
                title = context.getString(R.string.ui134_recent_startups),
                visible = true,
                onDismiss = { history = null },
                actions = listOf(DshaDialogAction(UiText.choose("关闭", "Close")) {}),
            ) {
                entries.forEach { entry ->
                    DshaSelectableText(
                        entry.date + "\n" + entry.stage + " · " + entry.status +
                            "\n\n" + entry.reason + "\n\n" + entry.log,
                    )
                }
            }
        }
    }
}

/** 启动记录条目（对应原 startupDiagnostics().history() 的展示字段）。 */
private data class StartupEntry(
    val started: Long,
    val stage: String,
    val status: String,
    val reason: String,
    val log: String,
) {
    val date: String get() = DateFormat.getDateTimeInstance().format(Date(started))
}

private fun openLog(context: Context, uri: Uri) {
    try {
        var target = uri
        if ("file" == target.scheme) {
            target = FileProvider.getUriForFile(
                context,
                context.packageName + ".updates",
                File(target.path.orEmpty()),
            )
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(target, "text/plain")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    } catch (_: RuntimeException) {
        Toast.makeText(
            context,
            UiText.text("无法打开日志，可在文件管理器的 Download/DSHA 中查看，或重新下载"),
            Toast.LENGTH_LONG,
        ).show()
    }
}