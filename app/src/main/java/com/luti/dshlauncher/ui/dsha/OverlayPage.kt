package com.luti.dshlauncher.ui.dsha

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.OverlayController
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.UiText

private val HOLD_DURATIONS = intArrayOf(2, 5, 6, 10, 15, 30, 60)

@Composable
fun OverlayPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS, 0) }

    var enabled by rememberSaveable { mutableStateOf(prefs.getBoolean(OverlayController.K_ENABLED, false)) }
    var alpha by rememberSaveable { mutableIntStateOf(prefs.getInt(OverlayController.K_ALPHA, OverlayController.DEF_ALPHA).coerceIn(20, 100)) }
    var lines by rememberSaveable { mutableIntStateOf(prefs.getInt(OverlayController.K_LINES, OverlayController.DEF_LINES).coerceIn(1, 6)) }
    var hold by rememberSaveable { mutableIntStateOf(prefs.getInt(OverlayController.K_HOLD, OverlayController.DEF_HOLD).coerceIn(2, 60)) }
    var background by rememberSaveable { mutableIntStateOf(prefs.getInt(OverlayController.K_BG, 0).coerceIn(0, OverlayController.BG_PRESETS.lastIndex)) }
    var font by rememberSaveable { mutableIntStateOf(prefs.getInt(OverlayController.K_TEXT_SP, OverlayController.DEF_TEXT_SP).coerceIn(6, 28)) }
    var reasoning by rememberSaveable { mutableStateOf(prefs.getBoolean(OverlayController.K_REASONING, false)) }
    var commands by rememberSaveable { mutableStateOf(prefs.getBoolean(OverlayController.K_COMMAND, true)) }
    var confirmation by rememberSaveable { mutableStateOf(prefs.getBoolean(OverlayController.K_CONFIRM, true)) }
    var livePreview by rememberSaveable { mutableStateOf(false) }
    var permitted by remember { mutableStateOf(OverlayController.permitted(context)) }
    var permissionText by remember { mutableStateOf("") }

    fun renderPermission() {
        permitted = OverlayController.permitted(context)
        permissionText = if (permitted) {
            context.dshaT("悬浮窗权限已开启", "Overlay permission is enabled")
        } else {
            context.dshaT(
                "需要悬浮窗权限，点击“预览悬浮条”即可授权。",
                "Overlay permission is required; tap “Preview floating status” to grant it.",
            )
        }
    }

    fun openOverlayPermission() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
        } catch (_: RuntimeException) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            } catch (_: RuntimeException) {
                Toast.makeText(
                    context,
                    context.dshaT("请在系统设置中允许 DSHA 显示在其他应用上层。", "Allow DSHA to display over other apps in system settings."),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun previewText(): String {
        val text = StringBuilder(context.dshaT("DSHA · 样式预览\n", "DSHA · Style preview\n"))
        if (reasoning) text.append(context.dshaT("正在思考：先检查文件内容。\n", "Thinking: checking the files first.\n"))
        if (commands) text.append(context.dshaT("正在执行命令：ls -la\n", "Running command: ls -la\n"))
        text.append(context.dshaT("文件已整理完成，可以继续下一步。", "Files are ready; continue to the next step."))
        return text.toString()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) renderPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        renderPermission()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            OverlayController.hideStylePreview(app)
        }
    }

    LaunchedEffect(livePreview, permitted, background, alpha, lines, font, reasoning, commands) {
        if (livePreview && permitted) {
            OverlayController.showStylePreview(context, background, alpha, lines, font, reasoning, commands)
        }
    }

    val holdOptions = remember(hold) {
        if (hold in HOLD_DURATIONS) HOLD_DURATIONS.toList() else HOLD_DURATIONS.toList() + hold
    }
    val holdLabels = holdOptions.mapIndexed { index, seconds ->
        if (index < HOLD_DURATIONS.size) {
            "$seconds ${context.dshaT("秒", "seconds")}"
        } else {
            "$seconds ${context.dshaT("秒（当前）", "seconds (current)")}"
        }
    }
    val holdIndex = holdOptions.indexOf(hold).coerceAtLeast(0)
    val lineLabels = (1..6).map { "$it ${context.dshaT("行", "lines")}" }
    val backgroundLabels = OverlayController.BG_NAMES.map { UiText.text(it) }

    DshaPageScaffold(
        title = context.dshaT("悬浮条", "Floating status"),
        subtitle = context.dshaT("在其他应用中，轻量查看任务进展。", "Follow progress while using other apps."),
        onBack = onBack,
        footer = listOf(
            DshaAction(context.dshaT("保存样式", "Save style"), primary = true) {
                prefs.edit()
                    .putBoolean(OverlayController.K_ENABLED, enabled)
                    .putInt(OverlayController.K_ALPHA, alpha)
                    .putInt(OverlayController.K_LINES, lines)
                    .putInt(OverlayController.K_HOLD, hold)
                    .putInt(OverlayController.K_BG, background)
                    .putInt(OverlayController.K_TEXT_SP, font)
                    .putBoolean(OverlayController.K_REASONING, reasoning)
                    .putBoolean(OverlayController.K_COMMAND, commands)
                    .putBoolean(OverlayController.K_CONFIRM, confirmation)
                    .apply()
                if (!enabled) OverlayController.teardown(app) else OverlayController.applyStyleNow(app)
                livePreview = false
                OverlayController.hideStylePreview(app)
                Toast.makeText(
                    context,
                    context.dshaT("悬浮条设置已保存", "Overlay settings saved"),
                    Toast.LENGTH_SHORT,
                ).show()
                if (enabled && !OverlayController.permitted(context)) openOverlayPermission()
            },
        ),
    ) {
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.dshaT("显示悬浮条", "Show floating status"),
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
            DshaNote(permissionText)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Color(((alpha * 255 / 100) shl 24) or (OverlayController.BG_PRESETS[background] and 0xFFFFFF)),
                    )
                    .padding(14.dp),
            ) {
                Text(
                    text = previewText(),
                    color = Color.White,
                    fontSize = font.sp,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            DshaSecondaryButton(context.dshaT("预览悬浮条", "Preview floating status")) {
                livePreview = true
                if (!OverlayController.permitted(context)) {
                    permissionText = context.dshaT(
                        "需要悬浮窗权限，正在打开系统设置…",
                        "Overlay permission is required; opening system settings…",
                    )
                    openOverlayPermission()
                } else {
                    permissionText = context.dshaT(
                        "系统悬浮条预览已显示，调整下面的选项会立即刷新。",
                        "System overlay preview is shown; changes below update it immediately.",
                    )
                }
            }
        }
        item {
            DshaSliderRow(
                title = context.dshaT("不透明度", "Opacity"),
                value = alpha.toFloat(),
                onValueChange = { alpha = it.toInt().coerceIn(20, 100) },
                valueRange = 20f..100f,
                valueLabel = "$alpha%  ·  $lines ${context.dshaT("行", "lines")}  ·  ${font}sp",
            )
        }
        item {
            DshaDropdownRow(
                title = context.dshaT("最多显示行数", "Maximum lines"),
                items = lineLabels,
                selectedIndex = lines - 1,
                onSelectedIndexChange = { lines = (it + 1).coerceIn(1, 6) },
            )
        }
        item {
            DshaDropdownRow(
                title = context.dshaT("停留时间", "Display duration"),
                items = holdLabels,
                selectedIndex = holdIndex,
                onSelectedIndexChange = { index -> hold = holdOptions.getOrElse(index) { hold } },
            )
        }
        item {
            DshaDropdownRow(
                title = context.dshaT("背景样式", "Background style"),
                items = backgroundLabels,
                selectedIndex = background,
                onSelectedIndexChange = { background = it.coerceIn(0, OverlayController.BG_PRESETS.lastIndex) },
            )
        }
        item {
            DshaSliderRow(
                title = context.dshaT("文字大小", "Text size"),
                value = font.toFloat(),
                onValueChange = { font = it.toInt().coerceIn(6, 28) },
                valueRange = 6f..28f,
                valueLabel = "${font}sp",
            )
        }
        item {
            DshaPreferenceGroup {
                DshaSwitchRow(
                    title = context.dshaT("显示思考过程", "Show reasoning"),
                    checked = reasoning,
                    onCheckedChange = { reasoning = it },
                )
                DshaSwitchRow(
                    title = context.dshaT("显示执行命令", "Show executed commands"),
                    checked = commands,
                    onCheckedChange = { commands = it },
                )
                DshaSwitchRow(
                    title = context.dshaT("在悬浮条上确认命令", "Confirm commands in the overlay"),
                    checked = confirmation,
                    onCheckedChange = { confirmation = it },
                )
            }
        }
    }
}