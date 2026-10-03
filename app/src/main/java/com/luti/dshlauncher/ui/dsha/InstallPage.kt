package com.luti.dshlauncher.ui.dsha

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.core.BackupTask
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.InstallRepository
import com.deepseekharness.app.core.MaintenanceCoordinator
import com.deepseekharness.app.ui.BackgroundTasksActivity
import com.deepseekharness.app.ui.ExtractActivity
import com.deepseekharness.app.ui.StartupRecoveryActivity
import com.deepseekharness.app.util.EnvironmentTaskGate
import com.deepseekharness.app.util.InstallTask
import com.deepseekharness.app.util.MaintenanceErrorText
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.BuildConfig
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.component.dialog.ConfirmResult
import com.luti.dshlauncher.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val STEP_TITLES = intArrayOf(
    R.string.ui2_component1, R.string.ui2_component2, R.string.ui2_component3,
    R.string.ui2_component4, R.string.ui2_component5, R.string.ui2_component6,
)

/** 安装页只展示应用级任务快照；页面销毁不影响后台任务、取消信号或结果。 */
@Composable
fun InstallPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val repository = remember { InstallRepository.get(context) }
    val maintenance = remember { BackupTask.get(context) }
    val controller = remember { HarnessController.get(context) }

    var resumed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(repository.snapshot()) }
    var environmentBusy by remember { mutableStateOf(MaintenanceCoordinator.isEnvironmentTaskBusy()) }
    var pending by remember { mutableStateOf(maintenance.pendingMaintenanceForUi()) }
    var stepDialog by remember { mutableIntStateOf(0) }

    val environmentText = remember {
        if (controller.isEnvironmentReady) UiText.choose("可用", "Available") else UiText.choose("等待检查", "Needs checking")
    }
    val identity = remember {
        try {
            controller.proot().installedRuntimeDescriptor()?.json() ?: emptyMap()
        } catch (_: java.io.IOException) {
            emptyMap<String, Any>()
        }
    }

    fun refresh() {
        state = repository.snapshot()
        environmentBusy = MaintenanceCoordinator.isEnvironmentTaskBusy()
        pending = maintenance.pendingMaintenanceForUi()
    }

    fun toast(text: String, long: Boolean = true) =
        Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    fun start(repair: Boolean, step: Int) {
        if (!repository.start(repair, step)) {
            toast(
                if (maintenance.pendingMaintenance()) UiText.text("请先恢复中断维护，再检查或修复环境")
                else UiText.text("无法开始安装任务，请稍后重试或先完成正在进行的环境任务"),
            )
        }
        refresh()
    }

    fun copyLog() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText(UiText.text("DSHA 安装日志"), repository.snapshot().log))
            toast(UiText.text("已复制脱敏日志"), long = false)
        } catch (_: RuntimeException) {
            toast(UiText.text("复制失败，请稍后重试"), long = false)
        }
    }

    fun confirmMaintenance() {
        if (repository.snapshot().busy() || maintenance.busy()) {
            toast("已有环境任务进行中，请等待完成")
            return
        }
        val recovery = maintenance.pendingMaintenance()
        scope.launch {
            val result = confirm.awaitConfirm(
                title = if (recovery) UiText.text("恢复中断维护？") else UiText.text("备份并重建环境？"),
                content = if (recovery) {
                    UiText.text("先停止 Web，再回切旧环境；安全备份和失败的新环境均保留。")
                } else {
                    UiText.text("会停止 Web 并中断正在执行的任务，完整备份并校验配置、会话和本地插件，再重建环境并恢复数据。\n\n") +
                        UiText.text("备份失败不切换环境，后续失败回切旧环境；安全备份和旧环境会保留并占用额外空间。额外安装的系统软件留在旧环境中。")
                },
                confirm = if (recovery) UiText.text("恢复原环境") else UiText.text("备份并重建"),
                dismiss = UiText.text("取消"),
            )
            if (result != ConfirmResult.Confirmed) return@launch
            try {
                // BackupTask 在发布任务前原子取得同一全局锁，旧弹窗也不能绕过互斥。
                if (!(if (recovery) maintenance.recoverMaintenance() else maintenance.rebuild())) {
                    toast(UiText.text("已有环境任务或未完成维护，请稍后重试"))
                    return@launch
                }
                context.startActivity(
                    Intent(context, ExtractActivity::class.java).putExtra("data_task_id", maintenance.snapshot().id),
                )
            } catch (error: Throwable) {
                toast(UiText.text("无法打开维护页，可到数据与备份页查看任务：") + SensitiveData.redact(error.toString()))
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumed) {
        while (resumed) {
            refresh()
            delay(1_000)
        }
    }

    val busy = state.busy()
    val statusText = when {
        busy -> state.phase
        environmentBusy -> "环境任务进行中：" + EnvironmentTaskGate.activeKind()
        pending -> UiText.text("上次环境维护尚未完成，请先恢复原环境")
        state.outcome == InstallTask.Outcome.IDLE -> UiText.text("检查环境，或按需修复缺项")
        else -> state.phase
    }
    val progressText = (if (state.repair) UiText.text("检查与按需修复") else UiText.text("仅检查")) +
        UiText.text(" · 总耗时 ") + state.elapsedSeconds + UiText.text(" 秒") +
        (if (busy) UiText.text(" · 当前阶段 ") + state.stageSeconds + UiText.text(" 秒") else "") +
        when {
            !(state.cancelRequested && busy) -> ""
            state.cancellable -> UiText.text("\n正在取消检查…")
            else -> UiText.text("\n等待当前修复到达安全点，随后停止")
        }
    val total = if (state.selected == 0) 6 else 1
    val completed = state.steps.indices.count { i ->
        (state.selected == 0 || state.selected == i + 1) &&
            state.steps[i].let { it == InstallTask.Step.OK || it == InstallTask.Step.FAILED || it == InstallTask.Step.SKIPPED }
    }
    val canRun = !busy && !environmentBusy && !pending
    val canMaintain = !busy && !environmentBusy

    DshaPageScaffold(
        title = context.getString(R.string.ui2_environment_page),
        subtitle = context.getString(R.string.ui2_environment_sub),
        onBack = onBack,
    ) {
        dshaKvCard(
            listOf(
                DshaKv(context.getString(R.string.ui2_environment_state), environmentText),
                DshaKv(context.getString(R.string.ui2_base), identity["baseVersion"]?.toString() ?: "—"),
                DshaKv(context.getString(R.string.ui2_managed), identity["dshVersion"]?.toString() ?: "—"),
                DshaKv(context.getString(R.string.ui2_app_version), BuildConfig.VERSION_NAME),
            ),
        )

        item { DshaCategory(context.getString(R.string.ui2_six_steps)) }
        dshaEntryCard(
            state.steps.indices.map { i ->
                DshaEntry(
                    title = context.getString(STEP_TITLES[i]),
                    summary = stepLabel(state.steps[i]) + " · " + context.getString(R.string.ui2_component_hint),
                ) { stepDialog = i + 1 }
            },
        )

        item {
            DshaPrimaryButton(context.getString(R.string.ui2_check_all), enabled = canRun) { start(false, 0) }
        }
        dshaEntryCard(
            listOf(
                DshaEntry(UiText.choose("任务状态", "Task status"), statusText) { BackgroundTasksActivity.open(context) },
            ),
        )
        if (state.outcome != InstallTask.Outcome.IDLE) item { DshaNote(progressText) }
        if (busy) {
            item { DshaProgressBar(completed.toFloat() / total) }
            item {
                DshaSecondaryButton(
                    when {
                        state.cancelRequested -> UiText.text("等待停止…")
                        state.cancellable -> UiText.text("取消检查")
                        else -> UiText.text("安全停止后续修复")
                    },
                    enabled = !state.cancelRequested,
                ) {
                    repository.cancel()
                    refresh()
                }
            }
        }
        if (state.failure.isNotEmpty()) {
            item {
                DshaCard {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        DshaNote(MaintenanceErrorText.render(state.failure))
                    }
                }
            }
        }

        item { DshaCategory(context.getString(R.string.ui2_maintenance)) }
        dshaEntryCard(
            listOf(
                DshaEntry(context.getString(R.string.ui2_managed_update), context.getString(R.string.ui2_managed_sub)) {
                    context.startActivity(Intent(context, ExtractActivity::class.java).putExtra("review_only", true))
                },
                DshaEntry(context.getString(R.string.ui2_repair_parts), context.getString(R.string.ui2_repair_parts_sub)) {
                    if (canRun) start(true, 0)
                    else toast(UiText.text("无法开始安装任务，请稍后重试或先完成正在进行的环境任务"))
                },
                DshaEntry(
                    context.getString(R.string.ui2_environment_recovery),
                    context.getString(R.string.ui2_environment_recovery_sub),
                ) { context.startActivity(Intent(context, StartupRecoveryActivity::class.java)) },
            ),
        )
        item {
            DshaSecondaryButton(
                if (pending) UiText.text("恢复中断维护") else UiText.text("备份并重建环境"),
                enabled = canMaintain,
            ) { confirmMaintenance() }
            DshaSecondaryButton(context.getString(R.string.ui2_factory_reset), enabled = canMaintain) {
                context.startActivity(
                    Intent(context, ExtractActivity::class.java)
                        .putExtra("review_only", true)
                        .putExtra("request_factory_reset", true),
                )
            }
        }

        item { DshaCategory(context.getString(R.string.ui2_installation_log)) }
        item {
            DshaLogBox(state.log.ifEmpty { UiText.text("执行后将在此逐行显示脱敏输出。") })
            if (state.log.isNotEmpty()) {
                DshaSecondaryButton(context.getString(R.string.ui_m0102)) { copyLog() }
            }
        }
    }

    val step = stepDialog
    if (step in 1..6) {
        val allowAction = !state.busy() && !MaintenanceCoordinator.isEnvironmentTaskBusy() && !maintenance.pendingMaintenance()
        DshaActionDialog(
            title = UiText.text(InstallTask.NAMES[step - 1]),
            message = MaintenanceErrorText.render(state.details[step - 1]),
            visible = true,
            onDismiss = { stepDialog = 0 },
            actions = buildList {
                add(DshaDialogAction(UiText.text("关闭")) {})
                if (allowAction) {
                    add(DshaDialogAction(UiText.text("重新检查")) { start(false, step) })
                    add(DshaDialogAction(UiText.text("按需修复")) { start(true, step) })
                }
            },
        )
    }
}

private fun stepLabel(step: InstallTask.Step): String = when (step) {
    InstallTask.Step.RUNNING -> UiText.text("进行中")
    InstallTask.Step.OK -> UiText.text("成功")
    InstallTask.Step.FAILED -> UiText.text("失败")
    InstallTask.Step.SKIPPED -> UiText.text("未完成")
    else -> UiText.text("未检查")
}