package com.luti.dshlauncher.data.repository

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.deepseekharness.app.HarnessService
import com.deepseekharness.app.core.EnvironmentAccess
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.MaintenanceCoordinator
import com.deepseekharness.app.ui.ExtractActivity
import com.deepseekharness.app.ui.WebPreviewActivity
import com.deepseekharness.app.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.luti.dshlauncher.templateApp

/**
 * Compose 外壳与 DSHA 引擎（[HarnessController]）之间的适配层。
 *
 * 引擎没有状态回调，原 LaunchFragment 也是轮询共享状态；这里在 IO 线程轮询
 * （isWebRunning / getWebAuthUrl 可能触发文件或进程检查，不能放主线程），
 * 只在快照变化时更新 StateFlow。
 */
object DshRuntime {

    private const val TAG = "DSHA"

    /** 一次轮询得到的引擎快照，字段语义与 LaunchFragment.refreshRunState 一致。 */
    data class State(
        val starting: Boolean = false,
        val stopping: Boolean = false,
        /** 鉴权链接已就绪，可进入 Web。 */
        val ready: Boolean = false,
        val safeMode: Boolean = false,
        val restartBlocked: Boolean = false,
        val compatibilityFallback: Boolean = false,
        val port: Int = 0,
        val stage: String = "",
        val stageElapsedMs: Long = 0,
        val elapsedMs: Long = 0,
        val log: String = "",
        val logRevision: Long = -1,
        val issues: Map<String, String> = emptyMap(),
        /** 最近一次 start/stop 回调给出的状态文案。 */
        val status: String = "",
    ) {
        val busy: Boolean get() = starting || stopping
    }

    enum class StartResult { Accepted, Busy, NeedsSetup, Rejected }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private var poller: Job? = null
    private var lastStatus = ""

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _running = MutableStateFlow(false)
    /** 首页卡片用：Web 已就绪或正在启动都视为「运行中」。 */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val controller: HarnessController
        get() = HarnessController.get(templateApp)

    init {
        ensurePolling()
    }

    /** 轮询常驻：进程级单例，开销是每 500ms 读一次内存状态。 */
    fun ensurePolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                runCatching { refreshNow() }.onFailure { Log.w(TAG, "DshRuntime poll: $it") }
                // 启动 / 停止中 300ms；空闲且有界面在看 800ms；没人订阅（后台、子活动）放慢到 3s，
                // 避免每轮 startupDiagnostics 快照在后台持续占 IO 与 GC。
                val observed = _state.subscriptionCount.value > 0 || _running.subscriptionCount.value > 0
                when {
                    _state.value.busy -> delay(300)
                    observed -> delay(800)
                    // 后台慢轮询，但界面一订阅就立刻刷新，回到首页不会看到旧状态。
                    else -> withTimeoutOrNull(3000) {
                        _state.subscriptionCount.first { it > 0 }
                    }
                }
            }
        }
    }

    private fun refreshNow() {
        val c = controller
        val starting = c.isStarting
        val stopping = c.isStopping
        val url = c.webAuthUrl ?: ""
        val ready = !starting && !stopping && url.isNotEmpty()
        val trace = c.startupDiagnostics().snapshot()
        val next = State(
            starting = starting,
            stopping = stopping,
            ready = ready,
            safeMode = trace.safe,
            restartBlocked = c.isRestartBlocked,
            compatibilityFallback = c.isWebCompatibilityFallback,
            port = if (ready) c.webPort else 0,
            stage = trace.stage ?: "",
            stageElapsedMs = trace.stageElapsedMs,
            elapsedMs = trace.elapsedMs,
            log = trace.log ?: "",
            logRevision = trace.revision,
            issues = trace.issues,
            status = lastStatus,
        )
        if (next != _state.value) _state.value = next
        val run = ready || starting
        if (run != _running.value) _running.value = run
    }

    private val statusSink = java.util.function.Consumer<String> { msg ->
        lastStatus = UiText.text(msg ?: "")
        scope.launch { runCatching { refreshNow() } }
    }

    /**
     * 是否需要先走安装 / 修复（与 ui.MainActivity.gate 的 Extract 分支同一判据）。
     * 只在 UI 线程短时调用；原版同样在 onCreate 主线程判断。
     */
    fun needsSetup(context: Context): Boolean {
        val c = HarnessController.get(context)
        return MaintenanceCoordinator.pending(c) || !c.isEnvironmentReady
                || EnvironmentAccess.shouldAttemptRuntimeUpdate(c)
    }

    /** 打开 DSHA 的安装 / 修复流程。 */
    fun openSetup(context: Context) {
        val i = Intent(context, ExtractActivity::class.java)
        if (context !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** 启动 DSH Web；safe=true 时走安全启动（独立基础配置，原插件和配置保留）。 */
    fun start(context: Context, safe: Boolean = false): StartResult {
        val c = HarnessController.get(context)
        if (!safe && (c.isStarting || c.isStopping)) return StartResult.Busy
        if (needsSetup(context)) return StartResult.NeedsSetup
        val accepted = if (safe) {
            c.recoverWeb(true, null, statusSink); true
        } else {
            c.startWeb(statusSink)
        }
        if (!accepted) return StartResult.Rejected
        startKeepAlive(context)
        ensurePolling()
        return StartResult.Accepted
    }

    /** 重启：停止当前任务后在同一 generation 上重新排入启动（不复用旧进程）。 */
    fun restart(context: Context): StartResult {
        if (needsSetup(context)) return StartResult.NeedsSetup
        HarnessController.get(context).recoverWeb(false, null, statusSink)
        startKeepAlive(context)
        ensurePolling()
        return StartResult.Accepted
    }

    /** 停止 Web。停止依赖 pid 文件 + 出生身份，由引擎负责，不做端口反查。 */
    fun stop(context: Context) {
        HarnessController.get(context).stopWeb(statusSink)
        ensurePolling()
    }

    /**
     * 进入 Web：后台换取鉴权 cookie，成功后打开 WebPreviewActivity。
     * [onResult] 在主线程回调，null 表示已打开，否则为失败原因。
     */
    fun enter(activity: Activity, onResult: (String?) -> Unit = {}) {
        val c = HarnessController.get(activity)
        val url = c.webAuthUrl ?: ""
        if (url.isEmpty()) {
            onResult(UiText.text("先点「启动」，等鉴权链接就绪后再进入"))
            return
        }
        val generation = c.webGeneration
        scope.launch {
            var cookie: String? = null
            var failure: String? = null
            try {
                cookie = c.exchangeDshAuthCookie()
                if (cookie.isNullOrEmpty()) failure = c.webAuthFailure
            } catch (e: Exception) {
                failure = UiText.text("Web 鉴权失败，请点「进入」重试（") + e.javaClass.simpleName + UiText.text("）")
            }
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (generation != c.webGeneration || url != c.webAuthUrl) {
                    onResult(UiText.text("Web 状态已变化，请等待就绪后重新进入"))
                    return@post
                }
                if (failure != null) {
                    onResult(failure)
                    return@post
                }
                try {
                    activity.startActivity(WebPreviewActivity.intent(activity, url, cookie))
                    onResult(null)
                } catch (e: RuntimeException) {
                    onResult(UiText.text("无法打开 Web 页面，请重试（") + e.javaClass.simpleName + UiText.text("）"))
                }
            }
        }
    }

    /** 前台保活服务：dsh 后台常驻 + 看门狗自动重启（与 LaunchFragment.doStart 一致）。 */
    fun startKeepAlive(context: Context) {
        try {
            val svc = Intent(context.applicationContext, HarnessService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(svc)
            } else {
                context.applicationContext.startService(svc)
            }
        } catch (t: Throwable) {
            Log.w(TAG, UiText.text("拉起保活服务失败: ") + t.message)
        }
    }

    /** 关闭保活服务：服务自身会先 stopWeb 再退出。 */
    fun stopKeepAlive(context: Context) {
        try {
            context.applicationContext.startService(
                Intent(context.applicationContext, HarnessService::class.java)
                    .setAction(HarnessService.ACTION_STOP)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "stop keep-alive: ${t.message}")
        }
    }
}