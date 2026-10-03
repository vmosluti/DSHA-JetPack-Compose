package com.luti.dshlauncher.ui.dsha

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekharness.app.ui.AboutDialog
import com.deepseekharness.app.util.Constants
import com.luti.dshlauncher.BuildConfig
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val REPO_URL = "https://github.com/DSH-APP/DSHA"

@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var licenseVisible by remember { mutableStateOf(false) }
    var communityVisible by remember { mutableStateOf(false) }
    val license = remember {
        runCatching {
            context.assets.open("licenses/DSHA-MIT.txt").use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } + "\n\n" + context.dshaT(
                "Ubuntu、Node.js、pnpm、Gecko 与其他依赖遵循各自许可。完整清单位于项目 THIRD_PARTY_NOTICES.md。",
                "Ubuntu, Node.js, pnpm, Gecko and other dependencies retain their own licenses. See THIRD_PARTY_NOTICES.md in the repository.",
            )
        }.getOrElse { "MIT · $REPO_URL/blob/main/THIRD_PARTY_NOTICES.md" }
    }
    val edition = if (BuildConfig.LOW_ANDROID) {
        context.dshaT("兼容版 · Android 6+", "Compatibility · Android 6+")
    } else {
        context.dshaT("标准版 · Android 11+", "Standard · Android 11+")
    }
    DshaPageScaffold(
        title = context.dshaT("关于 DSHA", "About DSHA"),
        onBack = onBack,
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_ui2_prompt),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                )
            }
            when (LocalUiMode.current) {
                UiMode.Miuix -> {
                    MiuixText(
                        text = "DSHA",
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 10.dp),
                    )
                    MiuixText(
                        text = "DeepSeek Harness for Android\n${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    )
                }
                UiMode.Material -> {
                    Text(
                        text = "DSHA",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 10.dp),
                        fontSize = 30.sp,
                    )
                    Text(
                        text = "DeepSeek Harness for Android\n${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                    )
                }
            }
        }
        dshaKvCard(
            listOf(
                DshaKv(context.dshaT("包名", "Package"), BuildConfig.APPLICATION_ID),
                DshaKv(context.dshaT("当前版本", "Edition"), edition),
                DshaKv("DSH", Constants.DSH_VERSION),
                DshaKv(context.dshaT("架构", "Architecture"), "arm64-v8a"),
                DshaKv(context.dshaT("开源许可", "License"), "MIT"),
            ),
        )
        item {
            DshaNote(context.dshaT("了解更多", "Learn more"))
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    context.dshaT("项目仓库", "Repository"),
                    "github.com/DSH-APP/DSHA",
                ) { AboutDialog.openBrowser(context, REPO_URL) },
                DshaEntry(
                    context.dshaT("交流与反馈", "Community and feedback"),
                    context.dshaT("QQ群 975836806 · 问题反馈", "QQ group 975836806 · Issue tracker"),
                ) { communityVisible = true },
                DshaEntry(
                    context.dshaT("开源许可", "Open-source licenses"),
                    context.dshaT("DSHA 与随包第三方组件", "DSHA and bundled components"),
                ) { licenseVisible = true },
            ),
        )
    }
    DshaMessageDialog(
        title = context.dshaT("开源许可", "Licenses"),
        message = license,
        visible = licenseVisible,
        onDismiss = { licenseVisible = false },
    )
    DshaChoiceSheet(
        title = context.dshaT("交流与反馈", "Community and feedback"),
        items = listOf(
            context.dshaT("打开 QQ 群", "Open QQ group"),
            context.dshaT("打开问题反馈", "Open issue tracker"),
        ),
        visible = communityVisible,
        onDismiss = { communityVisible = false },
        onSelect = { index ->
            scope.launch {
                if (index == 0) AboutDialog.openQQGroup(context)
                else AboutDialog.openBrowser(context, "$REPO_URL/issues")
            }
        },
    )
}
