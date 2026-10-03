package com.luti.dshlauncher.ui.dsha

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.HarnessService
import com.deepseekharness.app.LanProxyService
import com.deepseekharness.app.OverlayController
import com.deepseekharness.app.core.ConfigStore
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.ui.DshaPageActivity
import com.deepseekharness.app.ui.ModelSetupActivity
import com.deepseekharness.app.ui.PictureInPictureActivity
import com.deepseekharness.app.util.ConfigInput
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.CredentialRead
import com.deepseekharness.app.util.EnvironmentTaskGate
import com.deepseekharness.app.util.SensitiveData
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.BuildConfig
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.component.dialog.ConfirmResult
import com.luti.dshlauncher.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.launch

// Settings 没有在所有 compileSdk stub 中暴露该常量，使用公开 action 字符串保持 API 26+ 兼容。
private const val ACTION_PICTURE_IN_PICTURE_SETTINGS = "android.settings.PICTURE_IN_PICTURE_SETTINGS"
private const val REPO_URL = "https://github.com/qiannianhuanxiang/DSHA"
private val DNS_KEYS = listOf("auto", "ipv4", "native")

/**
 * 配置子页：接口、显示与运行行为。所有控件只编辑草稿，点「保存」才整体校验并写入 ConfigStore，
 * 与原 ConfigFragment 的保存语义一致（端口先校验、凭据加密失败保留原记录、环境任务互斥）。
 */
@Composable
fun ConfigPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val openPage = rememberDshaPageOpener()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val confirm = rememberConfirmDialog()
    val config = remember { ConfigStore(context) }
    val prefs = remember { context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE) }

    var credential by remember { mutableStateOf(config.readApiKey()) }
    var apiKey by remember { mutableStateOf(credential.valueOrEmpty()) }
    var apiKeyError by remember { mutableStateOf<String?>(null) }
    var port by remember { mutableStateOf(config.port) }
    var portError by remember { mutableStateOf<String?>(null) }
    var proroot by remember { mutableStateOf(config.isProroot) }
    var dnsIndex by remember { mutableStateOf(DNS_KEYS.indexOf(config.dnsMode).coerceAtLeast(0)) }
    var lan by remember { mutableStateOf(config.isLanMode) }
    var desktop by remember { mutableStateOf(config.isDesktopMode) }
    var gecko by remember { mutableStateOf(config.isGeckoCore) }
    var checkUpdate by remember { mutableStateOf(config.isCheckUpdate) }
    var overlay by remember { mutableStateOf(prefs.getBoolean(OverlayController.K_ENABLED, false)) }
    var staticLoader by remember { mutableStateOf(config.isProrootStaticLoader) }
    var noSeccomp by remember { mutableStateOf(config.isProotSeccompDisabled) }
    var pictureSummary by remember { mutableStateOf("") }
    var pictureSupported by remember { mutableStateOf(false) }

    fun toast(message: String, long: Boolean = false) {
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun renderPicture() {
        pictureSupported = PictureInPictureActivity.supported(context)
        pictureSummary = context.getString(
            when {
                !pictureSupported -> R.string.picture_in_picture_unsupported
                PictureInPictureActivity.allowed(context) -> R.string.picture_in_picture_allowed
                else -> R.string.picture_in_picture_disabled
            },
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) renderPicture()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        renderPicture()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openPictureSettings() {
        if (!PictureInPictureActivity.supported(context)) {
            toast(context.getString(R.string.picture_in_picture_unsupported), long = true)
            return
        }
        val app = Uri.parse("package:${context.packageName}")
        try {
            context.startActivity(Intent(ACTION_PICTURE_IN_PICTURE_SETTINGS, app))
        } catch (_: RuntimeException) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
            } catch (_: RuntimeException) {
                toast(context.getString(R.string.picture_in_picture_settings_unavailable), long = true)
            }
        }
    }

    fun openOverlayPermission() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
        } catch (_: Exception) {
            toast(UiText.text("未取得悬浮窗权限，请到系统设置 → 应用 → DSHA → 悬浮窗中允许"))
        }
    }

    /** LAN 开关真正生效：开启时若 dsh 已鉴权则启动 3081 代理，关闭时停掉监听。 */
    fun applyLanMode(on: Boolean) {
        try {
            if (!on) {
                LanProxyService.stopLanListener()
                HarnessService.refreshPowerMode()
                return
            }
            HarnessService.ensureLanForeground(context)
            val controller = HarnessController.get(context)
            val generation = controller.webGeneration
            // dsh 还没起来/还没交换 cookie：等下次进入对话时 HarnessController 自动启动
            if (generation <= 0 || !LanProxyService.hasDshAuth(generation)) return
            LanProxyService.start(
                controller.proot().rootfsDir.absolutePath,
                context,
                controller.webPort,
                generation,
            )
        } catch (t: Throwable) {
            Log.w("DSHA", UiText.text("LAN 开关生效失败: ") + t.message)
        }
    }

    fun retryCredential() {
        credential = config.readApiKey()
        if (credential.usable() && apiKey.isEmpty()) apiKey = credential.valueOrEmpty()
    }

    fun removeCredential() {
        scope.launch {
            val result = confirm.awaitConfirm(
                title = UiText.text("移除不可用的凭据记录？"),
                content = UiText.text("仅移除本机保存的 API Key 记录，不删除对话或配置文件。继续使用相关接口时需要重新提供凭据。"),
                confirm = UiText.text("移除"),
                dismiss = UiText.text("取消"),
            )
            if (result != ConfirmResult.Confirmed) return@launch
            val lease = EnvironmentTaskGate.tryAcquire(UiText.text("保存配置"))
            if (lease == null) {
                toast(UiText.text("有其他操作正在进行，请稍后重试。"))
                return@launch
            }
            try {
                lease.run<Unit> {
                    val current = config.readApiKey()
                    if (current.usable()) {
                        credential = current
                        if (apiKey.isEmpty()) apiKey = current.valueOrEmpty()
                    } else if (config.saveApiKey("")) {
                        credential = config.readApiKey()
                        apiKey = ""
                    } else {
                        toast(UiText.text("凭据记录未能保存，原记录已保留。"))
                    }
                }
            } catch (_: Exception) {
                toast(UiText.text("凭据记录未能保存，原记录已保留。"))
            } finally {
                lease.close()
            }
        }
    }

    fun save() {
        val chosenPort = try {
            ConfigInput.port(port)
        } catch (e: IllegalArgumentException) {
            portError = e.message
            return
        }
        portError = null
        apiKeyError = null
        val saving = EnvironmentTaskGate.tryAcquire(UiText.text("保存配置"))
        if (saving == null) {
            toast(UiText.text("正在") + EnvironmentTaskGate.activeKind() + UiText.text("，完成后再保存配置"))
            return
        }
        try {
            saving.run<Unit> {
                val key = apiKey.trim()
                val keepUnreadable = !credential.usable() && key.isEmpty()
                if (!keepUnreadable && !config.saveApiKey(key)) {
                    apiKeyError = UiText.text("密钥加密保存失败，原配置已保留，请重试")
                    return@run
                }
                if (!keepUnreadable) credential = config.readApiKey()
                config.setPort(chosenPort.toString())
                config.isCheckUpdate = checkUpdate
                config.isDesktopMode = desktop
                if (BuildConfig.LOW_ANDROID) config.isGeckoCore = gecko
                config.isProroot = proroot
                config.isProrootStaticLoader = staticLoader
                config.isProotSeccompDisabled = noSeccomp
                config.dnsMode = DNS_KEYS[dnsIndex]
                config.isLanMode = lan
                prefs.edit().putBoolean(OverlayController.K_ENABLED, overlay).apply()
                // 设置开关同时是 WindowManager 悬浮条的生命周期开关：关闭立即撤下，开启立即套用样式。
                val app = context.applicationContext
                if (!overlay) OverlayController.teardown(app)
                else if (OverlayController.permitted(context)) OverlayController.applyStyleNow(app)
                applyLanMode(lan)
                if (lan) (context as? com.deepseekharness.app.ui.LocalNetworkRequester)?.requestLocalNetwork()
                toast(
                    UiText.text(
                        if (keepUnreadable) "其他设置已保存；无法读取的 API Key 原记录保持不变。"
                        else "已保存；网页显示选项重新进入对话生效，端口与运行时需重启 Web",
                    ),
                    long = true,
                )
                if (overlay && !OverlayController.permitted(context)) openOverlayPermission()
            }
        } catch (e: Exception) {
            toast(UiText.text("配置保存未完成：") + SensitiveData.redact(e.toString()))
        } finally {
            saving.close()
        }
    }

    val coreLabels = if (BuildConfig.LOW_ANDROID) {
        listOf(UiText.choose("自动选择内核", "Automatic engine"), "Gecko")
    } else {
        listOf("System WebView")
    }

    DshaPageScaffold(
        title = context.getString(R.string.ui2_runtime_config),
        subtitle = context.getString(R.string.ui2_config_sub),
        onBack = onBack,
        footer = listOf(
            DshaAction(context.getString(R.string.ui_m0002), primary = true) { save() },
        ),
    ) {
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.getString(R.string.ui2_models),
                    context.getString(R.string.ui2_models_full),
                ) { context.startActivity(Intent(context, ModelSetupActivity::class.java)) },
            ),
        )
        item {
            DshaPreferenceGroup {
                DshaTextField(
                    title = context.getString(R.string.ui2_legacy_key),
                    summary = context.getString(R.string.ui2_legacy_hint),
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        apiKeyError = null
                    },
                    hint = "sk-...",
                    error = apiKeyError,
                    password = true,
                    keyboardType = KeyboardType.Password,
                )
            }
            if (!credential.usable()) {
                DshaNote(ConfigStore.credentialMessage(credential))
                DshaSecondaryButton(UiText.text("重试读取 API Key")) { retryCredential() }
                DshaSecondaryButton(UiText.text("移除不可用的凭据记录")) { removeCredential() }
            }
        }

        item { DshaCategory(context.getString(R.string.ui2_network_runtime)) }
        item {
            DshaPreferenceGroup {
                DshaDropdownRow(
                    title = UiText.choose("运行方式", "Runtime"),
                    items = listOf("proroot", "proot"),
                    selectedIndex = if (proroot) 0 else 1,
                    onSelectedIndexChange = { proroot = it == 0 },
                )
                DshaTextField(
                    title = context.getString(R.string.ui2_port_preferred),
                    summary = context.getString(R.string.ui2_port_hint),
                    value = port,
                    onValueChange = {
                        port = it.filter { ch -> ch.isDigit() }
                        portError = null
                    },
                    hint = context.getString(R.string.ui_m0028),
                    error = portError,
                    keyboardType = KeyboardType.Number,
                )
                DshaDropdownRow(
                    title = UiText.choose("DNS 解析策略", "DNS policy"),
                    items = listOf(
                        UiText.choose("自动（推荐）", "Automatic (recommended)"),
                        "IPv4",
                        UiText.choose("原生", "Native"),
                    ),
                    selectedIndex = dnsIndex,
                    onSelectedIndexChange = { dnsIndex = it.coerceIn(0, DNS_KEYS.lastIndex) },
                )
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0063),
                    checked = lan,
                    onCheckedChange = { lan = it },
                )
            }
        }

        item { DshaCategory(context.getString(R.string.ui2_display)) }
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0057),
                    checked = desktop,
                    onCheckedChange = { desktop = it },
                )
                DshaDropdownRow(
                    title = UiText.choose("网页内核", "Browser engine"),
                    summary = if (BuildConfig.LOW_ANDROID) context.getString(R.string.ui_m0118) else null,
                    items = coreLabels,
                    selectedIndex = if (BuildConfig.LOW_ANDROID && gecko) 1 else 0,
                    onSelectedIndexChange = { gecko = BuildConfig.LOW_ANDROID && it == 1 },
                )
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0089),
                    checked = checkUpdate,
                    onCheckedChange = { checkUpdate = it },
                )
            }
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.getString(R.string.picture_in_picture_title),
                    pictureSummary,
                    if (pictureSupported) ({ openPictureSettings() }) else null,
                ),
            ),
        )

        item { DshaCategory(context.getString(R.string.ui2_floating)) }
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.getString(R.string.ui_m0121),
                    checked = overlay,
                    onCheckedChange = { overlay = it },
                )
            }
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.getString(R.string.ui2_floating_settings),
                    context.getString(R.string.ui2_floating_settings_sub),
                ) { openPage(DshaPageActivity.PAGE_OVERLAY) },
            ),
        )

        item { DshaCategory(context.getString(R.string.ui2_runtime_compat)) }
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.getString(R.string.alpha_static_loader),
                    checked = staticLoader,
                    onCheckedChange = { staticLoader = it },
                )
                DshaSwitchRow(
                    title = context.getString(R.string.alpha_no_seccomp),
                    checked = noSeccomp,
                    onCheckedChange = { noSeccomp = it },
                )
            }
        }

        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.getString(R.string.ui_m0126),
                    UiText.choose("备份恢复 · 文件共享", "Backup, restore and file sharing"),
                ) { openPage(DshaPageActivity.PAGE_DATA) },
                DshaEntry("DSHA", REPO_URL.removePrefix("https://")) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
                    } catch (_: Exception) {
                        toast(UiText.text("无法打开浏览器"))
                    }
                },
            ),
        )
    }
}

private fun CredentialRead.valueOrEmpty(): String = if (usable()) requireValue() ?: "" else ""