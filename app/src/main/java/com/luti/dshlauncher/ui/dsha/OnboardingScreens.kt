package com.luti.dshlauncher.ui.dsha

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.deepseekharness.app.core.HarnessController
import com.deepseekharness.app.util.Constants
import com.luti.dshlauncher.BuildConfig

@Composable
fun OnboardingPrepareScreen(
    onInstall: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller = remember { HarnessController.get(context) }
    val ready = controller.isEnvironmentReady
    val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    val edition = if (BuildConfig.LOW_ANDROID) {
        context.dshaT("兼容版", "Compatibility")
    } else {
        context.dshaT("标准版", "Standard")
    }
    DshaPageScaffold(
        title = context.dshaT("为第一次启动做准备", "Prepare your first launch"),
        subtitle = "4 / 7",
        onBack = onBack,
        footer = listOf(
            DshaAction(
                if (ready) context.dshaT("继续模型配置", "Continue to model setup")
                else context.dshaT("安装内置环境", "Install bundled runtime"),
                primary = true,
                onClick = onInstall,
            ),
            DshaAction(context.dshaT("返回介绍", "Back to introduction"), onClick = onBack),
        ),
    ) {
        dshaKvCard(
            listOf(
                DshaKv(context.dshaT("设备架构", "Architecture"), abi),
                DshaKv(context.dshaT("系统与版本", "System"), "Android ${android.os.Build.VERSION.RELEASE} · $edition"),
                DshaKv(context.dshaT("可用空间", "Free space"), Formatter.formatFileSize(context, context.filesDir.usableSpace)),
                DshaKv(context.dshaT("安装方式", "Installation"), context.dshaT("随包离线部署", "Bundled offline runtime")),
            ),
        )
        item {
            DshaNote(
                context.dshaT(
                    "环境准备会进行真实检查。现有环境和个人数据会按原有维护规则处理；设备控制权限可以稍后单独设置。",
                    "Setup performs real checks and preserves existing data through the maintenance workflow. Device permissions remain optional.",
                ),
            )
        }
    }
}

@Composable
fun OnboardingReadyScreen(onOpen: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { HarnessController.get(context) }
    val ready = controller.isEnvironmentReady
    DshaPageScaffold(
        title = if (ready) {
            context.dshaT("你的工作台，准备好了。", "Your workspace is ready.")
        } else {
            context.dshaT("运行环境需要检查", "The runtime needs attention")
        },
        subtitle = "7 / 7",
        footer = listOf(
            DshaAction(context.dshaT("进入 DSHA", "Open DSHA"), primary = true, onClick = onOpen),
        ),
    ) {
        dshaKvCard(
            listOf(
                DshaKv(
                    context.dshaT("运行环境", "Runtime"),
                    if (ready) "Ubuntu · READY" else context.dshaT("需要检查", "Needs attention"),
                ),
                DshaKv("DSH", Constants.DSH_VERSION),
            ),
        )
        item {
            DshaNote(
                context.dshaT(
                    "模型凭据由实际模型设置保存。你可以在启动页继续配置。",
                    "Credentials are saved by the real model editor. You can continue setup from the launch page.",
                ),
            )
        }
    }
}