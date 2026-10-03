package com.deepseekharness.app.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.luti.dshlauncher.ui.dsha.observeAsState
import com.deepseekharness.app.DeviceBridgeService
import com.deepseekharness.app.DshaAccessibilityService
import com.deepseekharness.app.bridge.AdbBridge
import com.deepseekharness.app.bridge.LocalNetworkAccess
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.core.RuntimeTasks
import com.deepseekharness.app.runtime.ProotBootstrap
import com.deepseekharness.app.util.AdbEnvironmentTask
import com.deepseekharness.app.util.AdbResult
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.ui.dsha.AdbManualDialog
import com.luti.dshlauncher.ui.dsha.AdbPairPage
import com.luti.dshlauncher.ui.dsha.AdbPairState
import com.luti.dshlauncher.ui.dsha.DshaMessageDialog
import com.luti.dshlauncher.ui.dsha.setDshaContent

/**
 * ADB 无线配对宿主：保留原类名与全部握手语义，界面改为 Compose 页面。
 *
 * 配对任务由 ViewModel 持有；页面重建不重跑握手，不丢进度与结果。
 */
class AdbPairActivity : androidx.fragment.app.FragmentActivity() {

    private lateinit var model: PairModel

    private val networkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        model.message(
            if (granted) {
                UiText.text("局域网权限已允许，请输入本次配对码后开始配对")
            } else {
                UiText.text("未允许局域网访问，请在系统 DSHA 权限设置中允许后重试")
            },
        )
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        model = ViewModelProvider(this)[PairModel::class.java]
        model.restore(saved)

        setDshaContent {
            val status by model.status.observeAsState("")
            var hostDraft by remember { mutableStateOf(model.host) }
            var portDraft by remember { mutableStateOf(model.pairPort) }
            var codeDraft by remember { mutableStateOf(model.code) }
            var details by remember { mutableStateOf<String?>(null) }
            var helpVisible by remember { mutableStateOf(false) }
            var manualVisible by remember { mutableStateOf(false) }
            var manualError by remember { mutableStateOf<String?>(null) }

            // 与 ViewModel 同步外部变更：配对码被清空或自动读码写入时刷新输入框；
            // 端口只在空闲时回填，避免覆盖用户正在输入的内容。
            LaunchedEffect(status, model.busy) {
                if (codeDraft != model.code) codeDraft = model.code
                if (!model.busy) portDraft = model.pairPort
                hostDraft = model.host
            }

            val full = status.orEmpty()
            AdbPairPage(
                state = AdbPairState(
                    status = full.split("\n\n", limit = 2)[0],
                    host = hostDraft,
                    pairPort = portDraft,
                    code = codeDraft,
                    busy = model.busy,
                ),
                onHostChange = { hostDraft = it; model.host = it },
                onPairPortChange = { portDraft = it; model.pairPort = it },
                onCodeChange = { value ->
                    val digits = value.filter { ch -> ch.isDigit() }.take(6)
                    codeDraft = digits
                    model.code = digits
                },
                onBack = { finish() },
                onOpenSettings = { openWirelessSettings() },
                onStart = { startPair(verify = false) },
                onVerify = { startPair(verify = true) },
                onAutoRead = { autoRead() },
                onManual = { manualVisible = true; manualError = null },
                onDisconnect = { disconnectAdb() },
                onHelp = { helpVisible = true },
                onShowDetails = { details = full.ifEmpty { null } },
            )

            details?.let { text ->
                DshaMessageDialog(
                    title = UiText.choose("连接详情", "Connection details"),
                    message = text,
                    visible = true,
                    onDismiss = { details = null },
                )
            }
            if (helpVisible) {
                DshaMessageDialog(
                    title = UiText.text("配对与端口说明"),
                    message = UiText.text("找不到开发者选项时，到关于手机连续点按系统版本号。系统或厂商未提供无线调试时，填写端口无法启用它。\n\n") +
                        UiText.text("通常让地址与端口留空即可自动发现。配对端口来自 6 位码弹窗，连接端口来自无线调试主页面，两者不能互换。\n\n") +
                        UiText.text("首次准备依赖可能较久；配对码失效时请重新获取。自动读码需要屏幕操作权限，手动输入无需此权限。"),
                    visible = true,
                    onDismiss = { helpVisible = false },
                )
            }
            AdbManualDialog(
                host = model.host,
                pairPort = model.pairPort,
                connectPort = model.connectPort,
                visible = manualVisible,
                invalidMessage = manualError,
                onDismiss = { manualVisible = false },
                onClearError = { manualError = null },
                onSave = { host, pairPort, connectPort ->
                    manualError = saveManual(host, pairPort, connectPort)
                    if (manualError == null) manualVisible = false
                },
            )
        }

        if (!LocalNetworkAccess.granted(this)) requestNetworkPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        model.save(outState)
        super.onSaveInstanceState(outState)
    }

    private fun requestNetworkPermission() {
        model.message(UiText.text("无线 ADB 需要局域网权限，以发现配对端口并连接设备"))
        try {
            networkPermission.launch(LocalNetworkAccess.PERMISSION)
        } catch (_: RuntimeException) {
            // 无法发起授权请求时保持原状态提示，用户可在系统设置中手动允许。
        }
    }

    private fun startPair(verify: Boolean) {
        if (!LocalNetworkAccess.granted(this)) {
            requestNetworkPermission()
            return
        }
        if (readAddress()) model.start(verify, "", 0)
    }

    private fun readAddress(): Boolean {
        val host = model.host.trim()
        val port = model.pairPort.trim()
        return try {
            AdbResult.port(port)
            if (host.isNotEmpty() && !AdbBridge.localAddresses().contains(host)) {
                model.message(UiText.choose("请填写本机无线调试显示的 IP", "Enter this device's wireless debugging IP"))
                false
            } else {
                model.host = host
                model.pairPort = port
                true
            }
        } catch (_: IllegalArgumentException) {
            model.message(UiText.choose("端口应为 1–65535，或留空自动发现", "Use a port from 1–65535, or leave blank"))
            false
        }
    }

    /** 返回错误文案；null 表示保存成功。 */
    private fun saveManual(host: String, pairPort: String, connectPort: String): String? {
        return try {
            AdbResult.port(pairPort)
            AdbResult.port(connectPort)
            val address = host.trim()
            if (address.isNotEmpty() && !AdbBridge.localAddresses().contains(address)) {
                UiText.choose("请填写本机无线调试页面的 IP", "Enter this device's wireless debugging IP")
            } else {
                model.host = address
                model.pairPort = pairPort.trim()
                model.connectPort = connectPort.trim()
                model.message(UiText.choose("连接设置已保存，可开始配对或验证已有连接。", "Connection settings saved. Pair or verify the existing connection."))
                null
            }
        } catch (_: IllegalArgumentException) {
            UiText.choose("端口应为 1–65535，或留空自动发现。", "Use a port from 1–65535, or leave blank.")
        }
    }

    private fun autoRead() {
        if (!LocalNetworkAccess.granted(this)) {
            requestNetworkPermission()
            return
        }
        if (!DshaAccessibilityService.enabled(this)) {
            startActivity(Intent(this, AccessibilitySetupActivity::class.java))
            return
        }
        model.watch()
        openWirelessSettings()
    }

    private fun disconnectAdb() {
        getSharedPreferences(Constants.PREFS, 0).edit().putBoolean("adb_enabled", false).apply()
        stopService(Intent(this, DeviceBridgeService::class.java))
        model.message(UiText.choose("ADB 已关闭，配对密钥保留。需要时可验证连接重新启用。", "ADB disabled. Pairing keys are retained; verify the connection to enable it again."))
    }

    /** 直接打开与自动读码共用回退，兼容没有无线调试独立 Activity 的 MIUI。 */
    private fun openWirelessSettings() {
        try {
            startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))
        } catch (_: RuntimeException) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: RuntimeException) {
                model.message(UiText.text("请手动进入开发者选项 → 无线调试 → 使用配对码配对设备"))
            }
        }
    }

    /** 只持有 Application；自动读码及后台任务不会捕获旧 Activity。 */
    class PairModel(app: Application) : AndroidViewModel(app) {
        val status = MutableLiveData(UiText.text("输入配对码后开始；端口会重新发现。"))
        private val main = Handler(Looper.getMainLooper())

        @Volatile
        var busy = false

        @Volatile
        private var cleared = false

        var code = ""
        var host = ""
        var pairPort = ""
        var connectPort = ""

        private var task: Thread? = null
        private var restored = false
        private var watchEpoch = 0L

        fun message(text: String) {
            if (!cleared) status.postValue(SensitiveData.redact(text))
        }

        fun save(out: Bundle) {
            out.putBoolean("adb-pair-busy", busy)
            out.putString("adb-pair-status", status.value)
            out.putString("adb-pair-host", host)
            out.putString("adb-pair-port", pairPort)
            out.putString("adb-connect-port", connectPort)
        }

        fun restore(saved: Bundle?) {
            if (restored) return
            restored = true
            if (saved == null) return
            host = saved.getString("adb-pair-host", "").orEmpty()
            pairPort = saved.getString("adb-pair-port", "").orEmpty()
            connectPort = saved.getString("adb-connect-port", "").orEmpty()
            status.value = if (saved.getBoolean("adb-pair-busy")) {
                UiText.text("配对任务被系统中断，结果尚不确定。请先验证已有连接；若仍未配对，再获取新配对码。")
            } else {
                saved.getString("adb-pair-status", UiText.text("请输入本次配对码"))
            }
        }

        fun watch() {
            val epoch = ++watchEpoch
            message(UiText.text("已开始监听，两分钟内有效。请打开系统的「使用配对码配对设备」弹窗。"))
            DshaAccessibilityService.startWatch { value, address, port, connectionPort ->
                main.post {
                    if (cleared || busy || epoch != watchEpoch) return@post
                    code = value
                    if (connectionPort.isNotEmpty()) connectPort = connectionPort
                    val p = try {
                        AdbResult.port(port)
                    } catch (_: IllegalArgumentException) {
                        0
                    }
                    start(false, address, p)
                }
            }
            main.postDelayed({
                if (!cleared && !busy && epoch == watchEpoch) {
                    watchEpoch++
                    DshaAccessibilityService.stopWatch()
                    message(UiText.text("自动读码已到期；请重新点自动读取，或手动输入本次配对码。"))
                }
            }, 120_000)
        }

        fun start(verify: Boolean, readHost: String, readPort: Int) {
            if (busy || cleared) return
            // 仅拦截 Android 11+ 明确关闭的无线调试；读不到或旧系统仍走真实连接验证。
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                var wireless = -1
                try {
                    wireless = android.provider.Settings.Global.getInt(
                        getApplication<Application>().contentResolver,
                        "adb_wifi_enabled",
                        -1,
                    )
                } catch (_: RuntimeException) {
                    // 读不到系统开关时按旧系统处理，交由真实连接验证给出结论。
                }
                if (wireless == 0) {
                    watchEpoch++
                    DshaAccessibilityService.stopWatch()
                    val detail = UiText.text("无线调试已关闭，请先开启")
                    DeviceBridgeService.adbDetail = detail
                    DeviceBridgeService.adbState = "need_manual"
                    status.value = detail
                    return
                }
            }
            if (!verify && !AdbResult.code(code.trim())) {
                message(UiText.text("配对码必须恰好为 6 位数字"))
                return
            }
            if (!LocalNetworkAccess.granted(getApplication())) {
                message(UiText.text("局域网权限未允许，请先在 DSHA 权限设置中允许"))
                return
            }
            val value = code.trim()
            val savedPairPort = pairPort
            val pp = if (readPort > 0) readPort.toString() else pairPort
            val cp = connectPort
            val selectedHost = if (readPort > 0) readHost else host
            pairPort = "" // 一次性配对端口不跨请求复用，旧弹窗关闭后必须重新发现。
            busy = true
            watchEpoch++
            status.value = if (verify) {
                UiText.text("正在准备已有连接验证…")
            } else {
                UiText.text("正在准备配对环境…")
            }
            DshaAccessibilityService.stopWatch()
            val worker = Thread({
                var result: String
                try {
                    result = AdbBridge.runEnvironmentTask(
                        getApplication(),
                        if (verify) UiText.text("ADB 验证完整任务") else UiText.text("ADB 配对完整任务"),
                    ) {
                        perform(verify, value, pp, cp, selectedHost)
                    }
                } catch (e: AdbEnvironmentTask.Busy) {
                    if (!cleared) {
                        DeviceBridgeService.adbDetail = e.message
                        DeviceBridgeService.adbState = "environment_busy"
                    }
                    result = "ENVIRONMENT_BUSY: " + e.message
                } catch (e: Throwable) {
                    result = UiText.text("ADB 操作未完成：") + SensitiveData.redact(e.toString())
                } finally {
                    // finally 只处理取消路径；完成路径统一在主线程更新所有 UI 状态。
                    if (cleared) busy = false
                }
                val finished = result
                main.post {
                    if (cleared) return@post
                    if (AdbResult.marker(finished, "ENVIRONMENT_BUSY")) pairPort = savedPairPort else code = ""
                    busy = false
                    status.value = SensitiveData.redact(finished)
                }
            }, "adb-pair-task")
            task = worker
            worker.start()
        }

        /** 整段由同一 Lease.run 执行，准备、发现、配对、验证及授权期间不允许移动环境。 */
        private fun perform(
            verify: Boolean,
            value: String,
            pp: String,
            cp: String,
            selectedHost: String,
        ): String {
            if (cleared || Thread.currentThread().isInterrupted) return UiText.text("ADB 操作已取消")
            RuntimeTasks.begin().use {
                val proot: ProotBootstrap = HarnessController.get(getApplication()).proot()
                if (!proot.isEnvironmentReady()) throw IllegalStateException(UiText.text("环境未就绪，请先完成环境安装"))
                val prep = AdbBridge.ensureReady(getApplication(), proot) { message(it) }
                if (AdbResult.marker(prep, "ENVIRONMENT_BUSY")) throw AdbEnvironmentTask.Busy(prep)
                if (!AdbResult.marker(prep, "SETUP_DONE")) throw IllegalStateException(prep)
                if (cleared || Thread.currentThread().isInterrupted) return UiText.text("ADB 操作已取消")
                var address = selectedHost
                var port = pp
                if (!verify && port.isEmpty()) {
                    message(UiText.text("正在重新发现本机配对端口（最多 6 秒）…"))
                    val endpoint = AdbBridge.discover(getApplication(), "_adb-tls-pairing._tcp.", 6000) { message(it) }
                    if (endpoint != null) {
                        address = endpoint.host
                        port = endpoint.port.toString()
                    }
                }
                if (cleared || Thread.currentThread().isInterrupted) return UiText.text("ADB 操作已取消")
                message(
                    if (verify) {
                        UiText.text("正在验证已配对的设备连接，最多约 1 分钟…")
                    } else {
                        UiText.text("正在完成一次配对握手并验证连接，最多约 2 分钟；随后检查自动恢复授权…")
                    },
                )
                var connectionPort = cp
                if (connectionPort.isEmpty()) {
                    val endpoint = AdbBridge.discover(getApplication(), "_adb-tls-connect._tcp.", 5000) { message(it) }
                    if (endpoint != null) {
                        connectionPort = endpoint.port.toString()
                        if (address.isEmpty()) address = endpoint.host
                    }
                }
                val out = if (verify) {
                    AdbBridge.verify(proot, connectionPort, address)
                } else {
                    AdbBridge.pair(proot, value, port, connectionPort, address)
                }
                if (AdbResult.marker(out, "ENVIRONMENT_BUSY")) throw AdbEnvironmentTask.Busy(out)
                val state = AdbResult.pairState(out)
                DeviceBridgeService.recordPairResult(getApplication(), state, out)
                return when (state) {
                    AdbResult.PairState.CONNECTED ->
                        UiText.text("连接已验证，ADB 设备命令可用。\n\n") + out
                    AdbResult.PairState.PAIRED ->
                        UiText.text("配对已完成，连接尚未验证。请检查连接端口后点「验证已有连接」，无需再次配对。\n\n") + out
                    else ->
                        (if (verify) UiText.text("连接验证未通过。\n\n") else UiText.text("配对未完成，请按以下原因处理。\n\n")) + out
                }
            }
        }

        override fun onCleared() {
            cleared = true
            watchEpoch++
            code = ""
            DshaAccessibilityService.stopWatch()
            main.removeCallbacksAndMessages(null)
            task?.interrupt()
        }
    }
}