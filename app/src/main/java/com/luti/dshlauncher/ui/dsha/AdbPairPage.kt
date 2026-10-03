package com.luti.dshlauncher.ui.dsha

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.deepseekharness.app.util.UiText

/**
 * ADB 无线配对页的可渲染状态：由宿主 ViewModel 的 LiveData 映射而来。
 *
 * 对应原 XML 的 status/host/pairPort/code 四个输入与按钮的可用性。
 */
data class AdbPairState(
    val status: String = "",
    val host: String = "",
    val pairPort: String = "",
    val code: String = "",
    val busy: Boolean = false,
)

/**
 * ADB 无线配对页（Compose 版）。
 *
 * 配对任务由宿主 ViewModel 持有；本页只渲染状态并回传用户输入与动作，重建不重跑握手。
 */
@Composable
fun AdbPairPage(
    state: AdbPairState,
    onHostChange: (String) -> Unit,
    onPairPortChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onAutoRead: () -> Unit,
    onManual: () -> Unit,
    onVerify: () -> Unit,
    onDisconnect: () -> Unit,
    onHelp: () -> Unit,
    onShowDetails: () -> Unit,
) {
    DshaPageScaffold(
        title = UiText.text("ADB 无线配对"),
        subtitle = UiText.text("在系统无线调试中取得配对码，再连接当前设备。"),
        onBack = onBack,
        footer = buildList {
            add(
                DshaAction(
                    title = if (state.busy) UiText.text("正在处理，请等待结果…") else UiText.text("开始配对"),
                    primary = true,
                    enabled = !state.busy,
                    onClick = onStart,
                ),
            )
            add(DshaAction(title = UiText.text("自动读取本次配对码"), enabled = !state.busy, onClick = onAutoRead))
            add(DshaAction(title = UiText.text("手动地址与端口"), enabled = !state.busy, onClick = onManual))
            add(DshaAction(title = UiText.text("验证已有连接"), enabled = !state.busy, onClick = onVerify))
            add(DshaAction(title = UiText.text("打开无线调试设置"), onClick = onOpenSettings))
            add(DshaAction(title = UiText.text("断开 ADB 连接"), onClick = onDisconnect))
            add(DshaAction(title = UiText.text("配对与端口说明"), onClick = onHelp))
        },
    ) {
        item { DshaNote(UiText.text("连接状态")) }
        item {
            DshaCard {
                DshaSelectableText(state.status)
                DshaSecondaryButton(title = UiText.choose("查看连接详情 ›", "Connection details ›")) { onShowDetails() }
            }
        }
        item {
            DshaTextField(
                title = UiText.text("配对地址"),
                value = state.host,
                onValueChange = onHostChange,
                hint = UiText.text("留空自动发现本机地址"),
            )
        }
        item {
            DshaTextField(
                title = UiText.text("配对端口"),
                value = state.pairPort,
                onValueChange = onPairPortChange,
                hint = UiText.text("六位码弹窗的端口，可自动发现"),
            )
        }
        item {
            DshaTextField(
                title = UiText.text("配对码"),
                value = state.code,
                onValueChange = onCodeChange,
                hint = UiText.text("6 位配对码"),
            )
        }
    }
}

/** 手动连接设置对话框：地址、配对端口与连接端口三个输入。 */
@Composable
fun AdbManualDialog(
    host: String,
    pairPort: String,
    connectPort: String,
    visible: Boolean,
    invalidMessage: String?,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
    onSave: (host: String, pairPort: String, connectPort: String) -> Unit,
) {
    if (!visible) return
    var hostDraft by remember(visible) { mutableStateOf(host) }
    var pairDraft by remember(visible) { mutableStateOf(pairPort) }
    var connectDraft by remember(visible) { mutableStateOf(connectPort) }
    DshaContentDialog(
        title = UiText.choose("手动连接设置", "Manual connection"),
        visible = true,
        onDismiss = onDismiss,
        actions = listOf(
            DshaDialogAction(UiText.choose("取消", "Cancel")) {},
            DshaDialogAction(UiText.choose("保存", "Save")) {
                onSave(hostDraft, pairDraft, connectDraft)
            },
        ),
    ) {
        DshaNote(
            UiText.choose(
                "配对端口来自六位码弹窗；连接端口来自无线调试主页面。",
                "Pairing port comes from the code dialog; connection port comes from the main Wireless debugging page.",
            ),
        )
        DshaTextField(
            title = UiText.choose("本机 IP · 可留空", "Device IP · Optional"),
            value = hostDraft,
            onValueChange = { hostDraft = it; onClearError() },
        )
        DshaTextField(
            title = UiText.choose("配对端口 · 留空自动发现", "Pairing port · Auto if blank"),
            value = pairDraft,
            onValueChange = { pairDraft = it; onClearError() },
        )
        DshaTextField(
            title = UiText.choose("连接端口 · 留空自动发现", "Connection port · Auto if blank"),
            value = connectDraft,
            onValueChange = { connectDraft = it; onClearError() },
        )
        invalidMessage?.let { DshaNote(it) }
    }
}