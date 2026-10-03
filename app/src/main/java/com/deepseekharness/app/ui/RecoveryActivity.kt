package com.deepseekharness.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepseekharness.app.recovery.RecoveryController
import com.deepseekharness.app.recovery.RecoveryRepairBroker
import com.deepseekharness.app.recovery.RecoveryService
import com.deepseekharness.app.util.RecoveryStatusText
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.DshaCard
import com.luti.dshlauncher.ui.dsha.DshaCardButton
import com.luti.dshlauncher.ui.dsha.DshaCardTitle
import com.luti.dshlauncher.ui.dsha.DshaChoiceSheet
import com.luti.dshlauncher.ui.dsha.DshaContentDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaPageScaffold
import com.luti.dshlauncher.ui.dsha.DshaSelectableText
import com.luti.dshlauncher.ui.dsha.DshaTextField
import com.luti.dshlauncher.ui.dsha.setDshaContent
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 无正式环境门禁的恢复入口（Compose 版）；原生页面负责用户确认，应急网页没有确认能力。
 * 轮询、提案确认/拒绝、临时密钥只在内存中保存等语义与原 Java 版一致。
 */
class RecoveryActivity : androidx.fragment.app.FragmentActivity() {

    private sealed interface Dialog {
        data class Text(val title: String, val value: String) : Dialog
        data class RepairChoice(
            val broker: RecoveryRepairBroker,
            val labels: List<String>,
            val ids: List<String>,
            val actions: List<String>,
        ) : Dialog
        data class Preview(val id: String, val proposal: JSONObject) : Dialog
    }

    private val main = Handler(Looper.getMainLooper())
    private val reading = AtomicBoolean()
    private lateinit var controller: RecoveryController
    private var lastState = ""
    private var lastPlans = ""
    private var lastPoll = 0L
    private var resumed = false

    private var dialog by mutableStateOf<Dialog?>(null)
    private var status by mutableStateOf("")
    private var operation by mutableStateOf("")
    /** 临时密钥只放在内存状态里，不进入 saved state，也不参与自动填充。 */
    private var temporaryKey by mutableStateOf("")
    private var plans by mutableStateOf(JSONArray())
    private var applying by mutableStateOf(false)
    private var canStart by mutableStateOf(false)
    private var canOpen by mutableStateOf(false)
    private var canStop by mutableStateOf(false)
    private var keyEditable by mutableStateOf(false)

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        controller = RecoveryController.get(this)
        setDshaContent { Page() }
    }

    // ---------------- 页面 ----------------

    @Composable
    private fun Page() {
        DshaPageScaffold(
            title = t("应急 DSH", "Emergency DSH"),
            subtitle = t(
                "正式环境维护失败时，可在这里启动独立的空白 DSH。它可以读取相关故障文件并向当前模型服务发送内容，提出修复方案；每次正式数据写入都需在此确认。",
                "Start a separate, empty DSH when the regular environment fails. It can read affected files and send their contents to your model service to propose repairs. Confirm each write to regular data here.",
            ),
            onBack = { finish() },
        ) {
            if (status.isNotEmpty()) item { DshaCard { DshaSelectableText(status) } }

            item {
                DshaCard {
                    DshaTextField(
                        title = t("临时 API Key（可选，仅本次应急会话使用）", "Temporary API key (optional, this emergency session only)"),
                        value = temporaryKey,
                        onValueChange = { if (keyEditable) temporaryKey = it },
                        hint = t("留空时尝试已有可读凭据", "Leave blank to try an existing readable key"),
                        password = true,
                    )
                    DshaCardButton(t("启动应急 DSH", "Start emergency DSH"), primary = true, enabled = canStart) { start() }
                    DshaCardButton(t("进入空白应急 Web", "Open emergency Web"), primary = true, enabled = canOpen) {
                        startActivity(Intent(this@RecoveryActivity, RecoveryWebActivity::class.java))
                    }
                    DshaCardButton(t("停止应急 DSH", "Stop emergency DSH"), enabled = canStop) {
                        controller.stop()
                        main.post(refresh)
                    }
                    DshaNote(t("需要更换临时密钥时，请先停止应急 DSH，再填写并重新启动。", "To change the temporary key, stop emergency DSH, enter the key, then start it again."))
                    DshaCardButton(t("查看原生诊断", "View native diagnostics")) { diagnostics() }
                    DshaCardButton(t("查看无需模型的修复选项", "Review repairs without a model")) { repairOptions() }
                }
            }

            if (operation.isNotEmpty()) item { DshaCard { DshaSelectableText(operation) } }

            item { PlansCard() }

            item {
                DshaCard {
                    DshaCardButton(t("查看并下载错误日志", "View and export error logs")) {
                        startActivity(DiagnosticActivity.downloadLogs(this@RecoveryActivity))
                    }
                    DshaCardButton(t("备份与保留数据", "Backups and retained data")) {
                        startActivity(Intent(this@RecoveryActivity, NativeDataActivity::class.java))
                    }
                    DshaCardButton(t("返回正式环境恢复页", "Return to regular environment recovery")) {
                        startActivity(Intent(this@RecoveryActivity, ExtractActivity::class.java).putExtra("review_only", true))
                    }
                }
            }
        }
        Dialogs()
    }

    @Composable
    private fun PlansCard() {
        val rows = plans
        DshaCard {
            DshaCardTitle(t("修复提案", "Repair proposals"))
            if (rows.length() == 0) {
                DshaNote(t(
                    "应急 DSH 提出方案后在此查看和确认。尚未确认的提案不会修改正式数据。",
                    "Review and confirm proposals here after emergency DSH creates them. Unconfirmed proposals do not change regular data.",
                ))
            }
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                DshaCardButton(
                    row.optString("kind") + " · " + row.optString("targetId") + "\n" + statusLabel(row.optString("status")),
                    enabled = !applying,
                ) { preview(id) }
                val reason = row.optString("reason")
                if (reason.isNotEmpty()) DshaNote(reason)
            }
        }
    }

    @Composable
    private fun Dialogs() {
        val close = { dialog = null }
        when (val current = dialog) {
            null -> Unit
            is Dialog.Text -> DshaContentDialog(
                title = current.title,
                visible = true,
                onDismiss = close,
                actions = listOf(DshaDialogAction(t("关闭", "Close")) {}),
            ) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    DshaSelectableText(current.value)
                }
            }
            is Dialog.RepairChoice -> DshaChoiceSheet(
                title = t("选择要预览的修复", "Choose a repair to preview"),
                items = current.labels,
                visible = true,
                onDismiss = close,
                onSelect = { index ->
                    dialog = null
                    background {
                        val source = current.broker.read(current.ids[index])
                        val request = JSONObject()
                            .put("action", current.actions[index])
                            .put("targetId", current.ids[index])
                            .put("sourceSha256", source.getString("sourceSha256"))
                            .put("dataGeneration", source.getString("dataGeneration"))
                        val proposal = current.broker.propose(request)
                        ui { lastPlans = ""; preview(proposal.optString("id")) }
                    }
                },
            )
            is Dialog.Preview -> PreviewDialog(current.id, current.proposal)
        }
    }

    @Composable
    private fun PreviewDialog(id: String, proposal: JSONObject) {
        val blocked = proposal.optBoolean("writeBlocked")
        val pending = "PENDING" == proposal.optString("status") && !blocked
        val actions = buildList {
            add(DshaDialogAction(t("返回", "Back")) {})
            if (pending) {
                add(DshaDialogAction(t("拒绝提案", "Reject")) {
                    val broker = controller.broker() ?: return@DshaDialogAction
                    background { broker.reject(id); ui { lastPlans = "" } }
                })
                add(DshaDialogAction(t("保存原件并执行", "Save original and apply")) { apply(id) })
            }
        }
        DshaContentDialog(
            title = t("确认这一次修复", "Confirm this repair"),
            visible = true,
            onDismiss = { dialog = null },
            actions = actions,
        ) {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                listOf(
                    t("操作", "Operation") to proposal.optString("description"),
                    t("目标", "Target") to proposal.optString("targetId"),
                    t("原件 SHA-256", "Original SHA-256") to proposal.optString("sourceSha256"),
                    t("修改前", "Before") to proposal.optString("before"),
                    t("候选修改后", "Proposed after") to proposal.optString("after"),
                ).forEach { (heading, value) ->
                    DshaCardTitle(heading)
                    DshaSelectableText(value)
                }
                if (blocked) DshaNote(proposal.optString("writeBlockedReason"))
            }
        }
    }

    // ---------------- 逻辑（与原 Java 版一致） ----------------

    private fun start() {
        try {
            val key = temporaryKey
            RecoveryService.start(this, key.ifEmpty { null })
            temporaryKey = ""
            status = t("正在准备应急服务…", "Preparing the emergency service…")
        } catch (error: Exception) {
            message(error)
        }
        main.postDelayed(refresh, 250)
    }

    private fun diagnostics() = background {
        val broker = controller.broker()
            ?: throw IOException(t("应急服务尚未启动；可先查看错误日志。", "The emergency service has not started. You can view error logs first."))
        val data = broker.diagnostics().toString(2)
        ui { dialog = Dialog.Text(t("诊断结果", "Diagnostics"), data) }
    }

    private fun repairOptions() {
        val broker = controller.broker()
        if (broker == null) {
            operation = t("请先启动应急服务；无需配置模型。", "Start the emergency service first. A model is not required.")
            return
        }
        background {
            val targets = broker.targets()
            val labels = ArrayList<String>()
            val ids = ArrayList<String>()
            val actions = ArrayList<String>()
            for (i in 0 until targets.length()) {
                val target = targets.getJSONObject(i)
                val choices = target.optJSONArray("actions") ?: continue
                for (j in 0 until choices.length()) {
                    val action = choices.getString(j)
                    if (action == "profile-settings") continue
                    labels.add(
                        when (action) {
                            "recover-maintenance" -> t("恢复中断维护", "Recover interrupted maintenance")
                            "repair-runtime" -> t("修复受管运行时", "Repair managed runtime")
                            "new-web-profile" -> t("重建 Web 基础配置（原件留存）", "Rebuild basic Web configuration (retain originals)")
                            "new-global-patch" -> t("重建全局配置补丁（影响所有 Profile）", "Rebuild global patch (all profiles)")
                            else -> action
                        },
                    )
                    ids.add(target.getString("id"))
                    actions.add(action)
                }
            }
            ui { dialog = Dialog.RepairChoice(broker, labels, ids, actions) }
        }
    }

    private fun preview(id: String) {
        val broker = controller.broker() ?: return
        background {
            val proposal = broker.preview(id)
            ui { dialog = Dialog.Preview(id, proposal) }
        }
    }

    private fun apply(id: String) {
        val broker = controller.broker() ?: return
        applying = true
        operation = t("正在核验停止屏障、源摘要并执行修复…", "Verifying stopped processes and source hashes, then applying the repair…")
        background {
            try {
                val result = broker.confirm(id)
                ui { operation = result.optString("message") }
            } finally {
                ui { applying = false; lastPlans = ""; main.post(refresh) }
            }
        }
    }

    private fun statusLabel(value: String): String = when (value) {
        "PENDING" -> t("待确认", "Awaiting confirmation")
        "APPLYING" -> t("正在执行", "Applying")
        "APPLIED" -> t("已执行并核验", "Applied and verified")
        "REJECTED" -> t("已拒绝", "Rejected")
        "EXPIRED" -> t("已失效", "Expired")
        "FAILED" -> t("未完成", "Incomplete")
        else -> value
    }

    private fun ui(work: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed) work() }
    }

    private fun message(error: Throwable) {
        ui { operation = SensitiveData.redact(error.message ?: error.javaClass.simpleName) }
    }

    private fun background(work: () -> Unit) {
        Thread({
            try { work() } catch (error: Exception) { message(error) }
        }, "emergency-native-review").start()
    }

    private val refresh: Runnable = object : Runnable {
        override fun run() {
            if (!resumed || isFinishing || isDestroyed) return
            val state = controller.snapshot()
            val nativeRepair = RecoveryRepairBroker.activeNativeRepairs() > 0
            val key = "${state.state}:${state.detail}:${state.generation}:$applying:$nativeRepair:${UiText.language()}"
            if (key != lastState) {
                lastState = key
                status = RecoveryStatusText.render(state.state, state.detail) + if (state.errorCode.isEmpty()) "" else "\n" + state.errorCode
                canStart = !state.busy && !state.ready && !applying && !nativeRepair
                canOpen = state.ready
                canStop = (state.ready || state.busy) && !applying && !nativeRepair
                keyEditable = !state.busy && !state.ready && !nativeRepair
            }
            val broker = controller.broker()
            val now = SystemClock.elapsedRealtime()
            if (broker == null && lastPlans.isNotEmpty()) {
                lastPlans = ""
                plans = JSONArray()
            }
            if (broker != null && now - lastPoll > 2000 && reading.compareAndSet(false, true)) {
                lastPoll = now
                Thread({
                    try {
                        val rows = broker.plans()
                        val serial = rows.toString()
                        ui {
                            if (broker === controller.broker() && serial != lastPlans) {
                                lastPlans = serial
                                plans = rows
                            }
                        }
                    } catch (error: Exception) {
                        message(error)
                    } finally {
                        reading.set(false)
                    }
                }, "emergency-proposal-list").start()
            }
            main.removeCallbacks(this)
            main.postDelayed(this, 700)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        main.post(refresh)
    }

    override fun onPause() {
        resumed = false
        main.removeCallbacks(refresh)
        super.onPause()
    }

    private companion object {
        fun t(zh: String, en: String): String = UiText.choose(zh, en)
    }
}