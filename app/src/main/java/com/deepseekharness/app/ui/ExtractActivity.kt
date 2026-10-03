package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.EnvironmentAccess
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.BackupTaskState
import com.deepseekharness.app.util.EnvironmentIdentity
import com.deepseekharness.app.util.MaintenanceErrorText
import com.deepseekharness.app.util.UiStateText
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DshaActionDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.ExtractPage
import com.luti.dshlauncher.ui.dsha.ExtractUiState
import com.luti.dshlauncher.ui.dsha.setDshaContent
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * 解压门禁宿主：保留原类名与全部调度语义，界面改为 Compose 页面。
 *
 * 只展示应用级维护任务；旋转、返回或进程重建均不自动重复覆盖环境。
 * 就绪检查的 15 秒缓存与「状态变化立即重查」策略保持原样，避免逐帧重复解析运行时文件。
 */
class ExtractActivity : androidx.fragment.app.FragmentActivity() {

    private lateinit var controller: HarnessController
    private lateinit var task: BackupTask

    private val main = Handler(Looper.getMainLooper())

    private var taskId: Long = 0
    private var automaticEntry = false
    private var automaticDeclined = false
    private var rebuildRequested = false
    private var freshStartScheduled = false
    private var lastRender = ""

    private var cachedReady: Boolean? = null
    private var cachedReadyTaskId = -1L
    private var cachedReadyStatus: BackupTaskState.Status? = null
    private var cachedReadyBusy = false
    private var cachedReadyPending = false
    private var cachedReadyAt = 0L

    private val uiState = mutableStateOf(
        ExtractUiState(title = "", status = "", detail = ""),
    )

    // 0 关闭、1 首次确认、2 最终确认、3 暂时无法格式化。
    private var formatStage by mutableStateOf(0)

    // 非空时展示「恢复/更新/重建」确认对话框。
    private var maintenancePrompt by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        controller = HarnessController.get(this)
        task = BackupTask.get(this)

        rebuildRequested = if (saved != null) {
            saved.getBoolean("rebuild_requested", false)
        } else {
            intent.getBooleanExtra("force_extract", false) ||
                UiText.text("重建环境") == UiStateText.render(task.snapshot().kind)
        }

        taskId = if (saved == null) intent.getLongExtra("data_task_id", 0) else saved.getLong("data_task_id", 0)
        automaticEntry = saved != null && saved.getBoolean("automatic_entry", false)
        automaticDeclined = saved != null && saved.getBoolean("automatic_declined", false)

        if (task.maintenanceBusy()) {
            taskId = task.snapshot().id
        } else if (saved == null && taskId == 0L && !task.pendingMaintenance() &&
            !intent.getBooleanExtra("review_only", false)
        ) {
            val force = intent.getBooleanExtra("force_extract", false)
            val latest = EnvironmentAccess.runtimeLatest(controller)
            if (!force && controller.isEnvironmentReady && !EnvironmentAccess.shouldAttemptRuntimeUpdate(controller)) {
                proceed()
                return
            }
            val attempts = getSharedPreferences("dsha_environment_upgrade", MODE_PRIVATE)
            var attemptKey = ""
            try {
                attemptKey = "runtime:" + controller.proot().expectedRuntimeDescriptor().id()
            } catch (_: IOException) {
                // 描述不可读时不生成尝试标识，交由后续启动重新判定。
            }
            if (!force && EnvironmentIdentity.shouldAutoStart(
                    controller.isEnvironmentReady && latest,
                    task.busy(),
                    task.pendingMaintenance(),
                    attemptKey,
                    attempts.getString("attempted_identity", ""),
                )
            ) {
                // 先记一次尝试再启动应用级任务；旋转、失败和进程重建都不会重复覆盖旧环境。
                if (attempts.edit().putString("attempted_identity", attemptKey).commit()) {
                    if (task.updateEnvironment()) {
                        taskId = task.snapshot().id
                        automaticEntry = true
                    } else {
                        automaticDeclined = true
                    }
                }
            }
        }

        // 从受限主界面重新查看时也展示上次失败；自动重试判定已完成，不能因此阻止修复包升级。
        val previous = task.snapshot()
        if (taskId == 0L && !controller.isEnvironmentReady &&
            (previous.status == BackupTaskState.Status.FAILED || previous.status == BackupTaskState.Status.INTERRUPTED)
        ) {
            taskId = previous.id
        }

        setDshaContent {
            val state by uiState
            LaunchedEffect(Unit) {
                cachedReady = null
                while (true) {
                    refresh()
                    delay(1_000)
                }
            }
            ExtractPage(
                state = state,
                onRetry = { requestMaintenance() },
                onEnter = { proceed() },
                onEmergency = { startActivity(Intent(this, RecoveryActivity::class.java)) },
                onLogs = { startActivity(DiagnosticActivity.downloadLogs(this)) },
                onFormat = { formatStage = 1 },
            )
            renderFormatDialog()
            renderMaintenanceDialog()
        }

        if (saved == null && intent.getBooleanExtra("request_factory_reset", false)) {
            formatStage = 1
        }
    }

    @androidx.compose.runtime.Composable
    private fun renderFormatDialog() {
        when (formatStage) {
            1 -> DshaActionDialog(
                title = UiText.choose("格式化并全新开始？", "Format and start fresh?"),
                message = UiText.choose(
                    "这会永久删除 DSHA 的运行环境、会话、配置、API Key、插件和本机自动备份，并尝试清理旧版 Documents/dshdata。公共或外置目录不可访问时会记录提示，不会阻止私有数据完成格式化；手动导出的备份与其他个人目录会保留。",
                    "This permanently deletes the DSHA runtime, conversations, configuration, API key, plugins, and local automatic backups, and attempts to remove legacy Documents/dshdata. Unavailable public or external storage is reported without blocking private-data formatting. Exported backups and other personal folders stay untouched.",
                ),
                visible = true,
                onDismiss = { formatStage = 0 },
                actions = listOf(
                    DshaDialogAction(UiText.choose("取消", "Cancel")) {},
                    DshaDialogAction(UiText.choose("继续", "Continue")) { formatStage = 2 },
                ),
            )

            2 -> DshaActionDialog(
                title = UiText.choose("最后确认", "Final confirmation"),
                message = UiText.choose(
                    "格式化不可撤销。确认后会展示清理过程，完成时直接返回欢迎页并重新准备环境。",
                    "Formatting cannot be undone. Progress will be shown, then DSHA will return directly to welcome and prepare a fresh environment.",
                ),
                visible = true,
                onDismiss = { formatStage = 0 },
                actions = listOf(
                    DshaDialogAction(UiText.choose("返回", "Back")) {},
                    DshaDialogAction(UiText.choose("删除全部并格式化", "Delete all and format")) {
                        if (task.factoryReset()) {
                            taskId = task.snapshot().id
                            automaticEntry = false
                            automaticDeclined = false
                        } else {
                            formatStage = 3
                        }
                        refresh()
                    },
                ),
            )

            3 -> DshaActionDialog(
                title = UiText.choose("暂时无法格式化", "Cannot format yet"),
                message = UiText.choose(
                    "另一个任务正在运行，请结束后重试。",
                    "Another task is running. Try again after it finishes.",
                ),
                visible = true,
                onDismiss = { formatStage = 0 },
                actions = listOf(DshaDialogAction(UiText.choose("知道了", "OK")) {}),
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun renderMaintenanceDialog() {
        val prompt = maintenancePrompt ?: return
        DshaActionDialog(
            title = prompt.first,
            message = prompt.second,
            visible = true,
            onDismiss = { maintenancePrompt = null },
            actions = listOf(
                DshaDialogAction(UiText.text("取消")) {},
                DshaDialogAction(UiText.text("继续")) { applyMaintenance() },
            ),
        )
    }

    private fun requestMaintenance() {
        val recovery = task.pendingMaintenance()
        val update = !rebuildRequested && controller.proot().canUpdateManagedRuntime()
        val title = when {
            recovery -> UiText.text("恢复原环境？")
            update -> UiText.text("更新运行时？")
            else -> UiText.text("保护个人数据并重建？")
        }
        val message = when {
            recovery -> UiText.text("先停止 Web，再回切旧环境，保留所有安全副本。")
            update -> UiText.text("会停止 Web 和终端任务，再更新 dsh 和内置插件，个人目录、会话和配置保持原位。验证失败回切原运行时。")
            else -> UiText.text("将停止 Web 和终端，只备份对话、附件、配置、插件与个人项目，不打包 Ubuntu / Node 系统。数据保护失败不切换环境，重建失败保留原环境。")
        }
        maintenancePrompt = title to message
    }

    private fun applyMaintenance() {
        val started = when {
            task.pendingMaintenance() -> task.recoverMaintenance()
            rebuildRequested -> task.rebuild()
            else -> task.updateEnvironment()
        }
        if (started) {
            taskId = task.snapshot().id
            automaticDeclined = false
        }
        refresh()
    }

    private fun refresh() {
        if (isFinishing || isDestroyed) return
        val s = task.snapshot()
        val busy = task.maintenanceBusy()
        val pending = task.pendingMaintenanceForUi()
        val executionBusy = task.busy()
        val ready = environmentReady(s, busy, pending)
        val formatTask = task.isFactoryReset(taskId)
        val formatComplete = task.isCompletedFactoryReset(taskId)
        val renderKey = s.id.toString() + ":" + s.status + ":" + s.detail + ":" + busy + ":" +
            executionBusy + ":" + pending + ":" + ready + ":" + taskId + ":" + formatComplete
        if (renderKey == lastRender) return
        lastRender = renderKey

        val title = if (formatTask) {
            UiText.choose("格式化 DSHA", "Format DSHA")
        } else {
            UiText.text("运行环境维护")
        }
        val status = when {
            formatComplete -> UiText.choose("格式化完成", "Formatting complete")
            formatTask && busy -> UiText.choose("正在格式化 DSHA", "Formatting DSHA")
            busy -> UiStateText.render(s.kind)
            pending -> UiText.text("上次维护未完成，请先恢复原环境")
            else -> UiText.text("环境维护")
        }
        val mine = taskId != 0L && taskId == s.id
        var detail = if (mine) {
            MaintenanceErrorText.render(s.detail)
        } else {
            UiText.text("相同基础环境只更新 dsh 与内置插件，个人数据保持原位；基础环境变更时先保护数据再重建。验证失败可恢复原环境。")
        }
        val failed = mine &&
            (s.status == BackupTaskState.Status.FAILED || s.status == BackupTaskState.Status.INTERRUPTED)
        if (failed) {
            detail = UiText.choose("未完成的操作：", "Incomplete operation: ") +
                UiStateText.render(s.kind) +
                "\n" + UiText.choose("最后记录阶段：", "Last recorded stage: ") +
                (if (s.lastStage.isEmpty()) {
                    UiText.choose("旧记录未提供；请查看维护日志", "Not available in the old record; inspect maintenance logs")
                } else {
                    UiStateText.render(s.lastStage)
                }) +
                "\n\n" + MaintenanceErrorText.render(s.detail) +
                "\n\n" + UiText.choose(
                    "原件状态：尚未确认恢复完成。请查看记录后使用“恢复中断维护”；也可进入受限主界面导出可读副本。",
                    "Original state: recovery has not been confirmed. Inspect the record and use Recover interrupted maintenance, or enter the limited interface to export readable copies.",
                )
        }
        val incomplete = mine && s.status == BackupTaskState.Status.SUCCEEDED && !ready && !formatTask
        val error = when {
            incomplete -> UiText.choose(
                "维护步骤已结束，但环境就绪检查未通过。已停止自动跳转，请查看原因或进入受限主界面。",
                "Maintenance ended, but readiness checks did not pass. Automatic navigation stopped. Review the reason or enter the limited interface.",
            )
            automaticDeclined -> UiText.choose(
                "本次自动维护未能启动。已有任务结束后可手动重试，不会反复启动维护。",
                "Automatic maintenance could not start. Retry manually after the current task ends; maintenance will not start repeatedly.",
            )
            failed -> UiText.text("任务未完成。请按上方原因处理后重试；本页不会自动覆盖环境。")
            else -> null
        }
        val retryTitle = when {
            pending -> UiText.text("恢复中断维护")
            !rebuildRequested && controller.proot().canUpdateManagedRuntime() -> UiText.text("更新运行时")
            else -> UiText.text("保护数据并重建")
        }
        val enterTitle = if (!busy && !pending && ready) {
            UiText.text("进入主界面")
        } else {
            UiText.text("进入受限主界面 · 查看日志与配置")
        }
        val formatHidden = formatTask && (busy || formatComplete)

        uiState.value = ExtractUiState(
            title = title,
            status = status,
            detail = detail,
            error = error,
            busy = busy,
            retryTitle = retryTitle,
            retryVisible = !formatTask,
            retryEnabled = !executionBusy,
            enterTitle = enterTitle,
            enterVisible = !formatHidden,
            formatVisible = !formatHidden,
            formatEnabled = !executionBusy,
        )

        if (formatComplete && !freshStartScheduled) {
            freshStartScheduled = true
            main.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                if (task.completeFactoryReset(taskId) || !controller.config().isWelcomed()) {
                    // 系统清除数据过去靠进程重启恢复默认语言/主题；应用内格式化需显式重载。
                    LanguageController.apply(this)
                    ThemeController.apply(this)
                    val welcome = Intent(this, WelcomeActivity::class.java).setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    )
                    startActivity(welcome)
                    finish()
                } else {
                    freshStartScheduled = false
                    lastRender = ""
                    refresh()
                }
            }, 900)
        }
        if (EnvironmentIdentity.mayAdvanceAfterMaintenance(
                automaticEntry,
                mine,
                s.status == BackupTaskState.Status.SUCCEEDED,
                busy,
                pending,
                ready,
            )
        ) {
            automaticEntry = false
            proceed()
        }
    }

    /**
     * 就绪检查会读取并解析运行时描述、健康回执和 Bash ELF。维护页每秒刷新进度，
     * 但这些文件只会在任务代次/状态变化时改变；逐帧重复检查会让失败页在静止时
     * 仍持续分配对象并触发 GC。状态变化立即重查，闲置时每 15 秒兜底重查。
     */
    private fun environmentReady(snapshot: BackupTaskState.Snapshot, busy: Boolean, pending: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (cachedReady == null || cachedReadyTaskId != snapshot.id || cachedReadyStatus != snapshot.status ||
            cachedReadyBusy != busy || cachedReadyPending != pending || now - cachedReadyAt >= 15_000
        ) {
            cachedReady = controller.isEnvironmentReady
            cachedReadyTaskId = snapshot.id
            cachedReadyStatus = snapshot.status
            cachedReadyBusy = busy
            cachedReadyPending = pending
            cachedReadyAt = now
        }
        return cachedReady == true
    }

    private fun proceed() {
        val limited = task.maintenanceBusy() || task.pendingMaintenance() || !controller.isEnvironmentReady
        if (limited) controller.config().allowLimitedEntry(controller.proot().environmentIdentity())
        val firstSetup = intent.getBooleanExtra("first_setup", false)
        val target = if (firstSetup && !limited) ModelSetupActivity::class.java else MainActivity::class.java
        val out = Intent(this, target)
        out.putExtra("first_run", firstSetup)
        out.putExtra("limited_entry", limited)
        out.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(out)
        finish()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putLong("data_task_id", taskId)
        state.putBoolean("automatic_entry", automaticEntry)
        state.putBoolean("automatic_declined", automaticDeclined)
        state.putBoolean("rebuild_requested", rebuildRequested)
        super.onSaveInstanceState(state)
    }
}