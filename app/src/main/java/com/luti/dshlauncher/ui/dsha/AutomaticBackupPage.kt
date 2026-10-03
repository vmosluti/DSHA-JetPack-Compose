package com.luti.dshlauncher.ui.dsha

import android.app.TimePickerDialog
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.backup.AutomaticBackups
import com.deepseekharness.app.backup.BackupErrorCode
import com.deepseekharness.app.backup.NativeBackupJobs
import com.deepseekharness.app.ui.NativeDataActivity
import com.deepseekharness.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun dshaBackupStage(value: String?): String = when (value) {
    "PREPARING" -> com.deepseekharness.app.util.UiText.choose("准备数据", "Preparing data")
    "CAPTURING" -> com.deepseekharness.app.util.UiText.choose("保存并核验文件", "Saving and verifying files")
    "ENCRYPTING" -> com.deepseekharness.app.util.UiText.choose("加密并验证副本", "Encrypting and verifying the copy")
    "EXPORTING" -> com.deepseekharness.app.util.UiText.choose("完成副本记录", "Finishing the copy record")
    else -> com.deepseekharness.app.util.UiText.choose("正在处理", "Working")
}

private val MODE_KEYS = listOf("daily", "interval", "stop")

@Composable
fun AutomaticBackupPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { AutomaticBackups.prefs(context) }
    val jobs = remember { NativeBackupJobs.get(context) }

    var enabled by remember { mutableStateOf(AutomaticBackups.enabled(context)) }
    var mode by remember { mutableStateOf(prefs.getString("mode", "daily") ?: "daily") }
    var minute by remember { mutableIntStateOf(prefs.getInt("minute", 180)) }
    var hours by remember { mutableIntStateOf(prefs.getInt("hours", 24)) }
    var status by remember { mutableStateOf("") }
    var copies by remember { mutableStateOf(listOf<DshaEntry>()) }
    var reading by remember { mutableStateOf(false) }
    var hoursPromptVisible by remember { mutableStateOf(false) }
    var selectedCopyId by remember { mutableStateOf<String?>(null) }
    var selectedCopyTitle by remember { mutableStateOf("") }
    var copySheetVisible by remember { mutableStateOf(false) }

    val modeNames = listOf(
        context.dshaT("每天指定时间", "Daily at a chosen time"),
        context.dshaT("每隔一段时间", "At an interval"),
        context.dshaT("停止 DSH 后", "After stopping DSH"),
    )

    fun refresh() {
        if (reading) return
        reading = true
        status = context.dshaT("正在读取记录…", "Reading records…")
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { jobs.verifiedCopies() }
                val last = prefs.getLong("last", 0)
                val error = prefs.getString("error", "").orEmpty()
                val on = AutomaticBackups.enabled(context)
                enabled = on
                mode = prefs.getString("mode", "daily") ?: "daily"
                minute = prefs.getInt("minute", 180)
                hours = prefs.getInt("hours", 24)
                var next = if (on) {
                    context.dshaT("已开启 · 等待到期且 DSH 停止", "On · waiting for the scheduled time and DSH to stop")
                } else {
                    context.dshaT("已关闭", "Off")
                }
                if (last > 0) {
                    next += "\n" + context.dshaT("上次完成：", "Last completed: ") +
                        DateFormat.format("yyyy-MM-dd HH:mm", last)
                }
                if (error.isNotEmpty()) next += "\n" + context.dshaT("待处理：", "Needs attention: ") + error
                val job = jobs.state()
                if (job.busy) {
                    next = context.dshaT("备份任务进行中：", "Backup in progress: ") +
                        dshaBackupStage(job.stage) + " · " + job.entries + context.dshaT(" 项", " entries")
                }
                if (job.result == "DATA_SAVED_PLUGIN_WARNINGS") {
                    next += "\n" + context.dshaT(
                        "数据已保存；部分插件依赖需在恢复预检中核对。",
                        "Data saved; some plugin dependencies need review before restore.",
                    )
                }
                status = next
                copies = found.valid.mapNotNull { copy ->
                    if (copy.metadata["automatic"] != true) return@mapNotNull null
                    val title = DateFormat.format("MM-dd HH:mm", copy.created).toString()
                    val extra = if (copy.result(true) == "DATA_SAVED_PLUGIN_WARNINGS") {
                        context.dshaT(" · 插件依赖待核对", " · review plugin dependencies")
                    } else {
                        ""
                    }
                    DshaEntry(title, Fmt.bytes(copy.bytes) + extra) {
                        selectedCopyId = copy.id
                        selectedCopyTitle = title
                        copySheetVisible = true
                    }
                }
            } catch (error: Exception) {
                status = BackupErrorCode.from(error)
            } finally {
                reading = false
            }
        }
    }

    DisposableEffect(lifecycleOwner, jobs) {
        val observer = Observer<NativeBackupJobs.State> { value ->
            if (value.busy) {
                status = context.dshaT("备份任务进行中：", "Backup in progress: ") +
                    dshaBackupStage(value.stage) + " · " + value.entries + context.dshaT(" 项", " entries")
            } else {
                refresh()
            }
        }
        jobs.changes().observe(lifecycleOwner, observer)
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        refresh()
        onDispose {
            jobs.changes().removeObserver(observer)
            lifecycleOwner.lifecycle.removeObserver(resumeObserver)
        }
    }

    val modeIndex = MODE_KEYS.indexOf(mode).coerceAtLeast(0)
    val timingEntries = buildList {
        if (mode == "daily") {
            add(
                DshaEntry(
                    context.dshaT("每天时间", "Daily time"),
                    String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60),
                ) {
                    TimePickerDialog(
                        context,
                        { _, hour, minuteOfHour ->
                            minute = hour * 60 + minuteOfHour
                            prefs.edit().putInt("minute", minute).commit()
                        },
                        minute / 60,
                        minute % 60,
                        true,
                    ).show()
                },
            )
        } else if (mode == "interval") {
            add(
                DshaEntry(
                    context.dshaT("间隔小时", "Interval in hours"),
                    hours.toString(),
                ) { hoursPromptVisible = true },
            )
        }
    }

    DshaPageScaffold(
        title = context.dshaT("自动备份", "Automatic backups"),
        subtitle = context.dshaT(
            "默认开启。备份对话、附件、配置和插件；不重复备份 Ubuntu。个人项目请使用手动备份。",
            "On by default. Saves conversations, attachments, settings and plugins, without copying Ubuntu. Use manual backup for personal projects.",
        ),
        onBack = onBack,
        footer = listOf(
            DshaAction(
                context.dshaT("现在创建自动备份", "Create automatic backup now"),
                primary = true,
            ) {
                when {
                    jobs.state().busy -> status = context.dshaT(
                        "备份任务进行中，请等待完成。",
                        "A backup task is running. Wait for it to finish.",
                    )
                    !AutomaticBackups.enabled(context) -> status = context.dshaT(
                        "请先打开自动备份",
                        "Enable automatic backups first",
                    )
                    else -> {
                        AutomaticBackups.request(context, true)
                        status = context.dshaT(
                            "已安排：DSH 和终端停止后可执行，稍后刷新查看结果。",
                            "Scheduled. DSH and terminals must be stopped. Refresh shortly to view the result.",
                        )
                    }
                }
            },
            DshaAction(context.dshaT("刷新备份记录", "Refresh backup records")) { refresh() },
        ),
    ) {
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.dshaT("自动备份", "Automatic backups"),
                    checked = enabled,
                    onCheckedChange = { value ->
                        enabled = value
                        prefs.edit().putBoolean("enabled", value).commit()
                        AutomaticBackups.schedule(context)
                        refresh()
                    },
                )
                DshaDropdownRow(
                    title = context.dshaT("备份时机", "Backup timing"),
                    items = modeNames,
                    selectedIndex = modeIndex,
                    onSelectedIndexChange = { index ->
                        mode = MODE_KEYS.getOrElse(index) { "daily" }
                        prefs.edit()
                            .putString("mode", mode)
                            .putLong("anchor", System.currentTimeMillis())
                            .commit()
                    },
                )
            }
        }
        if (timingEntries.isNotEmpty()) {
            dshaEntryCard(timingEntries)
        }
        item {
            DshaNote(
                context.dshaT(
                    "到期时仍在运行则延后，不会停止对话或终端。时间由系统调度，可能略有延迟。保留最近 3 份已验证自动副本。关闭后取消后续任务，已有副本保留。",
                    "If DSH or a terminal is running, backup waits. Android scheduling may delay the selected time. Keeps the latest 3 verified automatic copies. Turning off cancels future jobs and preserves existing copies.",
                ),
            )
            DshaNote(
                context.dshaT(
                    "自动副本保存在本机，卸载应用会清除。换机或卸载前，请导出副本并保存解密密码。",
                    "Automatic copies are stored on this device and removed when the app is uninstalled. Export a copy and save its decryption password before moving or uninstalling.",
                ),
            )
            DshaNote(status)
        }
        if (copies.isEmpty()) {
            item {
                DshaNote(
                    context.dshaT(
                        "还没有自动备份。到达所选时间后，在 DSH 停止且没有终端任务时创建第一份。",
                        "No automatic backup yet. The first copy is created when due, with DSH and terminals stopped.",
                    ),
                )
            }
        } else {
            dshaEntryCard(copies)
        }
    }
    DshaNumberPrompt(
        title = context.dshaT("间隔 1–168 小时", "Interval: 1–168 hours"),
        visible = hoursPromptVisible,
        value = hours,
        range = 1..168,
        onDismiss = { hoursPromptVisible = false },
        onConfirm = { value ->
            hours = value
            prefs.edit().putInt("hours", value).commit()
        },
        invalidMessage = context.dshaT("请输入 1–168", "Enter 1–168"),
    )
    DshaChoiceSheet(
        title = selectedCopyTitle.ifEmpty { context.dshaT("自动备份", "Automatic backups") },
        items = listOf(
            context.dshaT("预检恢复", "Inspect for restore"),
            context.dshaT("导出加密副本", "Export encrypted copy"),
        ),
        visible = copySheetVisible,
        onDismiss = { copySheetVisible = false },
        onSelect = { action ->
            val id = selectedCopyId ?: return@DshaChoiceSheet
            context.startActivity(
                Intent(context, NativeDataActivity::class.java)
                    .putExtra(if (action == 0) "auto_restore_id" else "auto_export_id", id),
            )
        },
    )
}