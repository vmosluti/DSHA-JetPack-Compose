package com.luti.dshlauncher.ui.dsha

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.deepseekharness.app.DeviceBridgeService
import com.deepseekharness.app.DeviceSense
import com.deepseekharness.app.DshaAccessibilityService
import com.deepseekharness.app.HttpShellService
import com.deepseekharness.app.RootShell
import com.deepseekharness.app.ShizukuManagerCompat
import com.deepseekharness.app.ShizukuShell
import com.deepseekharness.app.bridge.AdbBridge
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.core.DeviceGrants
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.ui.AccessibilitySetupActivity
import com.deepseekharness.app.ui.AdbPairActivity
import com.deepseekharness.app.ui.DshaPageActivity
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.deepseekharness.app.vscreen.VirtualScreenActivity
import com.deepseekharness.app.vscreen.VirtualScreenManager
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.component.dialog.ConfirmResult
import com.luti.dshlauncher.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设备通道、系统权限与能力预授权的唯一设置页；沿用历史偏好键（与原 DeviceGrantsFragment 一致）。 */
@Composable
fun DeviceGrantsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val prefs = remember { context.getSharedPreferences(Constants.PREFS, 0) }

    var resumed by remember { mutableStateOf(false) }
    var rootEnabled by remember { mutableStateOf(RootShell.enabled(context)) }
    var rootStatus by remember { mutableStateOf(context.getString(R.string.device_checking)) }
    var rootVerifying by remember { mutableStateOf(false) }
    var guardStatus by remember { mutableStateOf("") }
    var shizukuStatus by remember { mutableStateOf("") }
    var shizukuAuthorized by remember { mutableStateOf(ShizukuShell.hasPermission()) }
    var shizukuBusy by remember { mutableStateOf(false) }
    var adbEnabled by remember { mutableStateOf(DeviceBridgeService.isAdbEnabled(context)) }
    var adbStatus by remember { mutableStateOf(context.getString(R.string.ui_m0030)) }
    var adbRequest by remember { mutableIntStateOf(0) }
    var a11yStatus by remember { mutableStateOf(context.getString(R.string.device_checking)) }
    var allFilesStatus by remember { mutableStateOf(context.getString(R.string.device_checking)) }
    var smsAllowed by remember { mutableStateOf(DeviceGrants(context).smsReadAllowed()) }
    var location by remember { mutableStateOf(prefs.getBoolean("cap_location", false)) }
    var sensors by remember { mutableStateOf(prefs.getBoolean("cap_sensors", false)) }
    var messageTitle by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var messageVisible by remember { mutableStateOf(false) }

    fun toast(value: String) = Toast.makeText(context, value, Toast.LENGTH_LONG).show()

    fun showMessage(title: String, text: String) {
        messageTitle = title
        messageText = text
        messageVisible = true
    }

    fun savePreference(key: String, enabled: Boolean): Boolean {
        val success = prefs.edit().putBoolean(key, enabled).commit()
        if (success && !enabled) DeviceSense.revoke(context, key)
        if (!success) toast(UiText.text("设置保存失败，请重试"))
        return success
    }

    fun syncSettings() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AdbBridge.applySettings(app, HarnessController.get(app).proot())
            }
            guardStatus = UiText.text("原生授权即时生效；") + result.replaceFirst(Regex("^SETTINGS_[A-Z]+: "), "")
        }
    }

    fun refreshChannelLabels() {
        if (!shizukuBusy) shizukuStatus = UiText.text(ShizukuShell.userStatus(context))
        shizukuAuthorized = ShizukuShell.hasPermission()
        if (!rootVerifying) rootStatus = UiText.text(RootShell.status(context))
    }

    fun computeAdbStatus(): String = try {
        if (!prefs.getBoolean("adb_enabled", false)) {
            UiText.text("ADB 已关闭。开启后才会保持连接。")
        } else {
            val bridge = DeviceBridgeService.adbState
            val detail = DeviceBridgeService.adbDetail.orEmpty()
            val suffix = if (detail.isEmpty()) "" else "\n$detail"
            val controller = HarnessController.get(app)
            val status = if (controller.proot().isEnvironmentReady) AdbBridge.status(controller.proot()) else "env:not_ready"
            val portIndex = status.indexOf("port=")
            val port = if (portIndex >= 0) status.substring(portIndex + 5).trim() else "?"
            when {
                status.startsWith("ENVIRONMENT_BUSY") || bridge == "environment_busy" ->
                    "环境任务进行中，ADB 暂停检查，完成后自动重试。$suffix"
                bridge == "need_pair" -> UiText.text("配对已失效，请重新配对。")
                bridge == "reconnecting" -> UiText.text("正在重连无线调试…") + suffix
                bridge == "connected" -> UiText.text("连接已验证 · 端口 ") + port + suffix
                status.contains("key=YES") -> UiText.text("配对密钥已保存，连接尚未验证。") + suffix
                else -> UiText.text("尚未配对：点下方「ADB 无线配对」，配对后还会验证连接。")
            }
        }
    } catch (e: Throwable) {
        UiText.text("ADB 状态读取失败：") + e.message
    }

    /** 后台读 ADB 通道真实状态；环境任务进行中时 1.5s 后自动重试。 */
    fun refreshAdbStatus() {
        val request = ++adbRequest
        scope.launch {
            var text = withContext(Dispatchers.IO) { computeAdbStatus() }
            if (request != adbRequest) return@launch
            adbStatus = UiText.status(text)
            while (text.startsWith("环境任务进行中") && resumed && request == adbRequest) {
                delay(1500)
                text = withContext(Dispatchers.IO) { computeAdbStatus() }
                if (request != adbRequest) return@launch
                adbStatus = UiText.status(text)
            }
        }
    }

    fun refreshAllFilesStatus() {
        val granted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            else -> true
        }
        allFilesStatus = UiText.text(
            if (granted) "已开启：容器可读写手机存储任意文件（含 DSHA 目录外）"
            else "未开启：仅能访问 App 私有目录；去系统设置开启后可访问全部文件",
        )
    }

    fun refreshA11yStatus() {
        a11yStatus = when (DshaAccessibilityService.enabledState(context)) {
            "YES" -> UiText.text("已开启 · 读屏、点按与输入可用")
            "NO" -> UiText.text("未开启 · 点上方按两步完成设置")
            else -> UiText.text("尚未连接 · 点上方查看设置步骤")
        }
    }

    fun verify(setBusy: (Boolean) -> Unit, setStatus: (String) -> Unit, probe: () -> String) {
        setBusy(true)
        setStatus(UiText.text("正在验证设备通道…"))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    probe()
                } catch (error: Throwable) {
                    SensitiveData.redact(error.toString())
                }
            }
            setBusy(false)
            showMessage(UiText.text("设备通道验证"), UiText.text(result))
            refreshChannelLabels()
        }
    }

    fun saveSms(enabled: Boolean) {
        val saved = DeviceGrants(context).setSmsReadAllowed(enabled)
        smsAllowed = DeviceGrants(context).smsReadAllowed()
        toast(
            when {
                !saved -> UiText.text("授权保存失败，请重试")
                enabled -> UiText.text("短信查询已预授权")
                else -> UiText.choose("已关闭短信读取", "SMS reading disabled")
            },
        )
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) return@rememberLauncherForActivityResult
        savePreference("cap_location", false)
        location = false
        scope.launch {
            val result = confirm.awaitConfirm(
                title = UiText.choose("需要系统定位权限", "Location permission required"),
                content = UiText.choose(
                    "位置读取保持关闭。请在系统应用权限中允许定位，再返回这里开启；如果系统定位服务关闭，也需要先开启系统定位。",
                    "Location reading remains off. Allow location in system app permissions, then return and enable it here. System location services must also be enabled.",
                ),
                confirm = UiText.choose("打开系统权限设置", "Open app permissions"),
                dismiss = UiText.choose("暂不开启", "Not now"),
            )
            if (result != ConfirmResult.Confirmed) return@launch
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            } catch (_: RuntimeException) {
                toast(UiText.choose("请在系统设置中打开 DSHA 应用权限", "Open DSHA permissions in system settings"))
            }
        }
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshAllFilesStatus() }

    fun openAllFilesAccess() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            } catch (_: Throwable) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e: Throwable) {
                    toast(UiText.text("打开设置失败：") + e.message)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6-10：运行时请求 WRITE_EXTERNAL_STORAGE（Android 10 作用域存储下尽力而为）
                if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    toast(UiText.text("存储权限已授予，容器可访问手机存储"))
                }
            }
            else -> toast(UiText.text("当前系统无需存储权限"))
        }
    }

    fun openBatteryOptimization() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            )
        } catch (_: Exception) {
            toast(UiText.text("无法打开电池优化设置"))
        }
    }

    fun openShizuku() {
        val launch = ShizukuManagerCompat.launchIntent(context)
        if (launch == null) {
            toast(UiText.text("尚未安装 Shizuku，请安装后启动服务"))
            return
        }
        try {
            context.startActivity(launch)
        } catch (e: RuntimeException) {
            toast(UiText.text("无法打开 Shizuku：") + e.javaClass.simpleName)
        }
    }

    fun authorizeShizuku() {
        if (!ShizukuShell.isAvailable()) {
            val activity = context as? Activity ?: return
            shizukuBusy = true
            ShizukuManagerCompat.reconnect(activity) {
                shizukuBusy = false
                if (ShizukuShell.isAvailable()) {
                    ShizukuShell.requestPermission { _, _ -> refreshChannelLabels() }
                } else {
                    toast(ShizukuShell.userStatus(context))
                }
                refreshChannelLabels()
            }
            return
        }
        if (ShizukuShell.hasPermission()) {
            verify({ shizukuBusy = it }, { shizukuStatus = it }) { ShizukuShell.exec("id") }
        } else {
            ShizukuShell.requestPermission { _, _ -> refreshChannelLabels() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    ShizukuShell.ensureBound(context)
                    smsAllowed = DeviceGrants(context).smsReadAllowed()
                    refreshAllFilesStatus()
                    refreshA11yStatus()
                    refreshAdbStatus()
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adbRequest++
        }
    }

    LaunchedEffect(resumed) {
        while (resumed) {
            refreshChannelLabels()
            delay(1000)
        }
    }

    DshaPageScaffold(
        title = context.getString(R.string.ui2_device_grants),
        subtitle = context.getString(R.string.ui2_grants_sub),
        onBack = onBack,
    ) {
        item { DshaCategory(context.getString(R.string.ui2_device_channels)) }

        // Root
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0062),
                    summary = context.getString(R.string.ui134_root_hint),
                    checked = rootEnabled,
                    onCheckedChange = { enabled ->
                        rootEnabled = enabled
                        ConfigStore(context).isRootShellAllowed = enabled
                        if (!enabled) VirtualScreenManager.revoke()
                        syncSettings()
                        refreshChannelLabels()
                    },
                )
            }
        }
        dshaEntryCard(
            listOf(
                DshaEntry(UiText.choose("Root 状态", "Root status"), rootStatus) {
                    showMessage(UiText.choose("设备通道状态", "Device channel status"), rootStatus)
                },
            ),
        )
        if (guardStatus.isNotEmpty()) item { DshaNote(guardStatus) }
        item {
            DshaSecondaryButton(context.getString(R.string.ui_m0170), enabled = !rootVerifying) {
                when {
                    !RootShell.enabled(context) -> toast(UiText.text("请先启用「允许 Root Shell」"))
                    !RootShell.present() -> toast(UiText.text("未找到 su；请确认手机已 root，且 root 管理器允许 DSHA 使用"))
                    else -> verify({ rootVerifying = it }, { rootStatus = it }) {
                        RootShell.exec(app, "id", -1)
                    }
                }
            }
        }

        // Shizuku
        item { DshaNote("Shizuku · " + context.getString(R.string.ui134_shizuku_hint)) }
        dshaEntryCard(
            listOf(
                DshaEntry(UiText.choose("Shizuku 状态", "Shizuku status"), shizukuStatus) {
                    showMessage(UiText.choose("设备通道状态", "Device channel status"), shizukuStatus)
                },
            ),
        )
        item {
            DshaSecondaryButton(
                if (shizukuAuthorized) UiText.text("验证 Shizuku 连接") else UiText.text("授权 Shizuku"),
                enabled = !shizukuBusy,
            ) { authorizeShizuku() }
            DshaSecondaryButton(context.getString(R.string.ui_m0125)) { openShizuku() }
        }

        // ADB
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0090),
                    summary = context.getString(R.string.ui134_adb_hint),
                    checked = adbEnabled,
                    onCheckedChange = { enabled ->
                        if (!savePreference("adb_enabled", enabled)) return@DshaSwitchRow
                        adbEnabled = enabled
                        if (!enabled) VirtualScreenManager.revoke()
                        if (enabled) {
                            DeviceBridgeService.apply(context)
                            (context as? com.deepseekharness.app.ui.LocalNetworkRequester)?.requestLocalNetwork()
                        } else {
                            context.stopService(Intent(context, DeviceBridgeService::class.java))
                        }
                        syncSettings()
                        refreshAdbStatus()
                    },
                )
            }
        }
        dshaEntryCard(
            listOf(
                DshaEntry(UiText.choose("ADB 状态", "ADB status"), adbStatus) {
                    showMessage(UiText.choose("设备通道状态", "Device channel status"), adbStatus)
                },
                DshaEntry(context.getString(R.string.ui_m0031), UiText.choose("Android 11+ 无线调试配对码", "Android 11+ wireless debugging code")) {
                    if (Build.VERSION.SDK_INT < 30) {
                        showMessage(
                            UiText.text("当前系统没有配对码接口"),
                            UiText.text("无线调试配对码需要 Android 11+。Android 6—10 可使用 Shizuku、已授权的 root，或由电脑开启 ADB TCP 通道。"),
                        )
                    } else {
                        context.startActivity(Intent(context, AdbPairActivity::class.java))
                    }
                },
            ),
        )

        // Computer Use
        item { DshaNote("Computer Use") }
        item {
            DshaNote(
                UiText.choose(
                    "让助手读屏、点击和输入。先完成屏幕操作设置，再在对话中描述任务。",
                    "Let the assistant observe, tap and type. Set up screen control, then describe the task in a conversation.",
                ),
            )
        }
        dshaEntryCard(
            buildList {
                add(
                    DshaEntry(context.getString(R.string.ui134_screen_setup), a11yStatus) {
                        context.startActivity(Intent(context, AccessibilitySetupActivity::class.java))
                    },
                )
                add(
                    DshaEntry(
                        UiText.choose("撤销本次读屏与操作授权", "Revoke screen access for this run"),
                        UiText.choose("后续屏幕操作需要重新确认", "Future screen actions require confirmation"),
                    ) {
                        HttpShellService.revokeScreenGrant()
                        toast(UiText.choose("已撤销，后续屏幕操作需要重新确认", "Revoked. Future screen actions require confirmation"))
                    },
                )
                if (VirtualScreenManager.supported(context)) {
                    add(
                        DshaEntry(
                            UiText.choose("打开虚拟屏", "Open virtual screen"),
                            UiText.choose("在独立虚拟显示中运行应用", "Run apps on a separate virtual display"),
                        ) { context.startActivity(Intent(context, VirtualScreenActivity::class.java)) },
                    )
                }
            },
        )

        // 系统权限
        item { DshaCategory(context.getString(R.string.ui2_system_permissions)) }
        dshaEntryCard(
            listOf(
                DshaEntry(context.getString(R.string.device_open_access), allFilesStatus) { openAllFilesAccess() },
                DshaEntry(context.getString(R.string.ui_m0191), context.getString(R.string.ui_m0084)) { openBatteryOptimization() },
            ),
        )

        // 敏感能力
        item { DshaCategory(context.getString(R.string.ui2_sensitive)) }
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0171),
                    summary = if (smsAllowed) UiText.text("已预授权 · 关闭可撤销")
                    else UiText.choose("已关闭 · 查询会被阻止", "Off · queries are blocked"),
                    checked = smsAllowed,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            saveSms(false)
                            return@DshaSwitchRow
                        }
                        scope.launch {
                            val result = confirm.awaitConfirm(
                                title = UiText.text("持续允许读取短信？"),
                                content = UiText.text("当前 DSHA 环境内的助手和插件将能通过已授权的 root 或 ADB 查询短信，包括正文、号码和可能存在的验证码。\n\n后续短信查询不再逐条询问。关闭后立即阻止新查询。"),
                                confirm = UiText.text("允许短信读取"),
                                dismiss = UiText.text("取消"),
                            )
                            if (result == ConfirmResult.Confirmed) saveSms(true)
                        }
                    },
                )
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0066),
                    checked = location,
                    onCheckedChange = { enabled ->
                        if (!savePreference("cap_location", enabled)) return@DshaSwitchRow
                        location = enabled
                        if (enabled && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            locationPermission.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        }
                    },
                )
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0053),
                    checked = sensors,
                    onCheckedChange = { enabled ->
                        if (savePreference("cap_sensors", enabled)) sensors = enabled
                    },
                )
            }
        }
    }

    DshaMessageDialog(
        title = messageTitle,
        message = messageText,
        visible = messageVisible,
        onDismiss = { messageVisible = false },
        confirm = UiText.text("知道了"),
    )
}