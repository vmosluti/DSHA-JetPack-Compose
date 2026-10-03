package com.luti.dshlauncher.ui.dsha

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.DshaAccessibilityService

@Composable
fun AccessibilitySetupPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf("") }
    var enableLabel by remember { mutableStateOf(context.dshaT("去开启屏幕操作", "Enable screen control")) }
    var tipsVisible by remember { mutableStateOf(false) }

    fun refresh() {
        val connected = DshaAccessibilityService.connected()
        val allowed = DshaAccessibilityService.enabled(context)
        status = when {
            connected -> context.dshaT(
                "已连接，可以在对话中使用 Computer Use。",
                "Connected. Computer Use is available in conversations.",
            )
            allowed -> context.dshaT(
                "开关已开启，正在等待系统连接；可重新开关服务。",
                "Enabled, waiting for the system to connect. Try toggling the service.",
            )
            else -> context.dshaT("尚未开启，请完成第 1 步。", "Not enabled. Complete step 1.")
        }
        enableLabel = if (connected) {
            context.dshaT("查看或关闭系统授权", "Review or disable access")
        } else {
            context.dshaT("去开启屏幕操作", "Enable screen control")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DshaPageScaffold(
        title = context.dshaT("屏幕操作", "Screen control"),
        subtitle = context.dshaT(
            "开启一次，之后直接在对话里说出要做的事。",
            "Enable once, then describe what you want in a conversation.",
        ),
        onBack = onBack,
    ) {
        dshaKvCard(
            listOf(
                DshaKv(
                    context.dshaT("第 1 步", "Step 1"),
                    context.dshaT("开启系统服务", "Enable the system service"),
                ),
            ),
        )
        item {
            DshaNote(
                context.dshaT(
                    "点下方按钮 → 已下载的应用 / 服务 → DSHA 配对助手 → 开启「使用服务」，确认系统提示。",
                    "Tap below → Downloaded apps / services → DSHA 配对助手 → Enable the service, then confirm the system prompt.",
                ),
            )
            Spacer(Modifier.height(8.dp))
            DshaPrimaryButton(enableLabel) { openAccessibilitySettings(context) }
        }
        dshaKvCard(
            listOf(
                DshaKv(
                    context.dshaT("第 2 步", "Step 2"),
                    context.dshaT("返回这里检查", "Return here to check"),
                ),
            ),
        )
        item {
            DshaNote(status)
            Spacer(Modifier.height(8.dp))
            DshaSecondaryButton(context.dshaT("检查连接", "Check connection")) { refresh() }
        }
        item {
            DshaNote(context.dshaT("怎么使用", "How to use"))
            DshaNote(
                context.dshaT(
                    "在 DSH 对话中发送：\n“请打开系统设置，查看当前的显示设置。”\n\n首次操作按应用提示确认。你可以随时在系统无障碍设置中关闭服务。读屏与点按无需 Root、Shizuku 或 ADB。截屏需要 Android 11+。",
                    "In a DSH conversation, ask:\n“Open system settings and inspect the display settings.”\n\nConfirm the app prompt when requested. You can disable the service in Accessibility settings at any time. Observation and taps require no Root, Shizuku or ADB. Screenshots require Android 11+.",
                ),
            )
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.dshaT("找不到开关或开关是灰色", "Missing or disabled switch"),
                    context.dshaT("查看系统设置提示", "View system setup tips"),
                ) { tipsVisible = true },
            ),
        )
    }
    DshaMessageDialog(
        title = context.dshaT("开启提示", "Setup tips"),
        message = context.dshaT(
            "部分手机路径是：设置 → 更多设置 → 无障碍 → 已下载的服务。\n\nAndroid 13+ 若提示“受限设置”，在系统应用详情右上角菜单允许受限设置，再返回无障碍页面。只有你确认信任已安装的 DSHA 时才启用。\n\n开启后仍未连接，可关闭再开启服务，并允许 DSHA 后台运行。",
            "Some devices use Settings → Additional settings → Accessibility → Downloaded services.\n\nOn Android 13+, if the system reports restricted settings, use the app-info menu to allow restricted settings, then return to Accessibility. Enable only for your trusted DSHA installation.\n\nIf disconnected after enabling, toggle the service off and on and allow DSHA to run in the background.",
        ),
        visible = tipsVisible,
        onDismiss = { tipsVisible = false },
    )
}

fun openAccessibilitySettings(context: Context) {
    val activity = context as? Activity ?: return
    try {
        activity.startActivity(
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(activity, DshaAccessibilityService::class.java)),
        )
    } catch (_: RuntimeException) {
        try {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: RuntimeException) {
            Toast.makeText(
                activity,
                context.dshaT("请打开系统设置 → 无障碍 → DSHA 配对助手。", "Open Settings → Accessibility → DSHA 配对助手."),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
