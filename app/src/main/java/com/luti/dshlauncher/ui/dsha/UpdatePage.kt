package com.luti.dshlauncher.ui.dsha

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deepseekharness.app.core.UpdateRepository
import com.deepseekharness.app.util.Constants
import com.deepseekharness.app.util.UiStateText
import com.deepseekharness.app.util.UiText
import com.deepseekharness.app.util.UpdatePolicy
import com.luti.dshlauncher.BuildConfig
import com.luti.dshlauncher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 版本更新页（Compose 版）：通道选择、下载进度、候选详情与安装调度。
 *
 * 下载、校验和安装结果仍由应用范围的 [UpdateRepository] 承接，页面销毁不影响前台下载；
 * 安装调度（权限、FileProvider、系统安装器）由宿主 Activity 负责，本页只发信号。
 *
 * @param repository 更新仓库；由宿主经 ViewModelProvider 取用
 * @param onBack 返回
 * @param onOpenExtract 跳转环境检查/修复页（对应原 ExtractActivity，带 review_only）
 * @param onOpenRollback 展示兼容运行时回退（对应原 RuntimeRecoveryUi）
 * @param onRequestNotifications 申请通知权限（对应原 requestCode 104）
 * @param onInstall 请求安装已校验的安装包
 * @param onCancel 取消当前任务
 */
@Composable
fun UpdatePage(
    repository: UpdateRepository,
    onBack: () -> Unit,
    onOpenExtract: () -> Unit,
    onOpenRollback: () -> Unit,
    onRequestNotifications: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var state by remember { mutableStateOf(repository.state().value) }
    var install by remember { mutableStateOf(repository.installation().value) }
    var bundledBase by remember { mutableStateOf("—") }
    var detailDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    DisposableEffect(repository, lifecycleOwner) {
        val stateObserver = Observer<UpdateRepository.State> { state = it }
        val installObserver = Observer<UpdateRepository.InstallState> { install = it }
        repository.state().observe(lifecycleOwner, stateObserver)
        repository.installation().observe(lifecycleOwner, installObserver)
        onDispose {
            repository.state().removeObserver(stateObserver)
            repository.installation().removeObserver(installObserver)
        }
    }

    // 包内基础运行时版本（原 bundledBase()），IO 读一次即可
    LaunchedEffect(Unit) {
        bundledBase = withContext(Dispatchers.IO) {
            try {
                context.assets.open("offline-rootfs.version").use { input ->
                    val bytes = ByteArray(64)
                    val count = input.read(bytes)
                    if (count > 0) String(bytes, 0, count, Charsets.UTF_8).trim() else "—"
                }
            } catch (_: Exception) {
                "—"
            }
        }
    }

    val current = state
    val installState = install
    val installing = installState?.pending() == true
    val verifyingInstall = installState?.verifying == true
    val busy = current?.busy == true || installing
    val release = current?.release
    val apk = current?.apk
    val downloadedBytes = current?.downloaded ?: 0L
    val totalBytes = current?.total ?: 0L
    val downloading = current?.stage == UpdateRepository.Stage.DOWNLOADING
    val channelLocked = busy || repository.installationPending()

    val status = buildString {
        append(UiStateText.render(current?.message.orEmpty()))
        val error = installState?.error
        if (error != null) {
            append("\n")
            append(UiStateText.render(error))
        } else if (installing) {
            append("\n")
            append(
                if (verifyingInstall) UiText.text("正在重新校验安装包…")
                else UiText.text("校验完成，返回此页面后继续安装"),
            )
        }
    }

    val edition = if (BuildConfig.LOW_ANDROID) {
        UiText.text("兼容版")
    } else {
        UiText.text("标准版")
    }
    val versionLine = release?.let {
        it.version + " · " + String.format(Locale.ROOT, "%.2f MiB", it.bytes / 1048576.0) + "\n\n" + it.notes
    }.orEmpty()
    val progress: Float? = when {
        !busy -> null
        verifyingInstall || !downloading || totalBytes <= 0L -> null
        else -> downloadedBytes.toFloat() / totalBytes.toFloat()
    }

    DshaPageScaffold(
        title = context.getString(R.string.ui2_updates),
        subtitle = context.getString(R.string.ui2_updates_intro),
        onBack = onBack,
        footer = buildList {
            add(
                DshaAction(
                    title = if (release == null) context.getString(R.string.ui_m0018) else UiText.text("重新检查"),
                    primary = release == null,
                    enabled = !busy,
                    onClick = { repository.check() },
                ),
            )
            if (release != null && apk == null) {
                add(
                    DshaAction(
                        title = if (downloadedBytes > 0L) UiText.text("继续下载") else context.getString(R.string.ui_m0001),
                        primary = true,
                        enabled = !busy,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                onRequestNotifications()
                            } else {
                                repository.download()
                            }
                        },
                    ),
                )
            }
            if (apk != null) {
                add(
                    DshaAction(
                        title = context.getString(R.string.ui_m0107),
                        primary = true,
                        enabled = !busy,
                        onClick = onInstall,
                    ),
                )
            }
            if (current?.busy == true && !installing) {
                add(
                    DshaAction(
                        title = context.getString(R.string.ui_m0078),
                        enabled = true,
                        onClick = onCancel,
                    ),
                )
            }
            add(
                DshaAction(
                    title = context.getString(R.string.ui_m0094),
                    onClick = {
                        com.deepseekharness.app.ui.AboutDialog.openBrowser(
                            context,
                            release?.pageUrl ?: "https://github.com/DSH-APP/DSHA/releases",
                        )
                    },
                ),
            )
        },
    ) {
        dshaKvCard(
            listOf(
                DshaKv(context.getString(R.string.ui134_current), BuildConfig.VERSION_NAME),
                DshaKv(context.getString(R.string.ui134_code), BuildConfig.VERSION_CODE.toString()),
                DshaKv(context.getString(R.string.ui134_edition), edition),
                DshaKv("DSH", Constants.DSH_VERSION),
            ),
        )
        item {
            DshaDropdownRow(
                title = context.getString(R.string.ui2_update_channel),
                items = listOf(context.getString(R.string.ui_m0173), context.getString(R.string.ui_m0215)),
                selectedIndex = if (UpdatePolicy.PREVIEW == repository.channel()) 1 else 0,
                onSelectedIndexChange = { index ->
                    if (!channelLocked) {
                        repository.setChannel(if (index == 1) UpdatePolicy.PREVIEW else UpdatePolicy.STABLE)
                    }
                },
                summary = if (channelLocked) UiText.text("任务进行中，暂不可切换通道") else null,
            )
        }
        if (status.isNotEmpty()) item { DshaCard { DshaSelectableText(status) } }
        if (progress != null) item { DshaProgressBar(progress) }
        if (release != null && apk == null && downloading && totalBytes > 0L) {
            item {
                DshaNote(
                    String.format(
                        Locale.ROOT,
                        "%.1f / %.1f MiB",
                        downloadedBytes / 1048576.0,
                        totalBytes / 1048576.0,
                    ),
                )
            }
        }
        if (release != null) {
            dshaEntryCard(
                listOf(
                    DshaEntry(
                        title = context.getString(R.string.ui134_candidate),
                        summary = context.getString(R.string.ui134_candidate_sub),
                        onClick = { detailDialog = context.getString(R.string.ui134_candidate) to versionLine },
                    ),
                ),
            )
        }
        dshaEntryCard(
            listOf(
                DshaEntry(
                    title = context.getString(R.string.ui2_managed_update),
                    summary = context.getString(R.string.ui2_managed_sub),
                    onClick = {
                        detailDialog = context.getString(R.string.ui2_managed_update) to buildString {
                            append("Ubuntu: ")
                            append(bundledBase)
                            append("\nDSH: ")
                            append(Constants.DSH_VERSION)
                            append("\n")
                            append(UiText.text("个人数据"))
                            append(": ")
                            append(UiText.text("保持原位"))
                            append("\n\n")
                            append(
                                UiText.text(
                                    "检查后展示实际差异；确认更新时停止 Web 与终端，准备候选并完成隔离试运行。",
                                ),
                            )
                        }
                    },
                ),
                DshaEntry(
                    title = context.getString(R.string.ui2_rollback),
                    summary = context.getString(R.string.ui2_rollback_sub),
                    onClick = onOpenRollback,
                ),
                DshaEntry(
                    title = context.getString(R.string.ui134_changelog),
                    summary = context.getString(R.string.ui134_changelog_sub),
                    onClick = { detailDialog = context.getString(R.string.ui134_changelog) to changelogText() },
                ),
            ),
        )
        item {
            DshaSecondaryButton(UiText.text("检查更新计划")) { onOpenExtract() }
        }
    }

    detailDialog?.let { (title, body) ->
        DshaMessageDialog(
            title = title,
            message = body,
            visible = true,
            onDismiss = { detailDialog = null },
        )
    }
}

/** 本版本更新记录正文（与原 CardSheet 内联文案一致，中英由 UiText 选择）。 */
private fun changelogText(): String = UiText.choose(
    "DSHA 0.1.7-rc2\n\n" +
        "• 修复旧 Web PID 被复用时的启动阻塞，以及应急对话请求扩展失败。\n" +
        "• 补齐录音与音频设备权限，支持 WebView、Gecko 和应急页面的按需麦克风授权。\n" +
        "• 相同应急归档在安装包内只存一份，保留独立离线恢复能力并减少约 208 MB。\n" +
        "• 新增独立应急 DSH：正式环境维护失败时仍可准备空白修复工作台。\n" +
        "• AI 通过受控工具诊断、提出候选；原生页面逐次确认，核验停止屏障和原件后执行修复。\n" +
        "• 应急运行时单独锁定，启动、鉴权、进程与正式环境分开记录。\n" +
        "• 内置移动插件同步 3.0.3 源码修订 a094288，保留手机快捷键搜索，改善插件返回、弹层焦点与短屏操作。\n" +
        "• DSH 继续为 0.1.7-rc.2，保留既有迁移和数据保护流程。",
    "DSHA 0.1.7-rc2\n\n" +
        "• Fixed startup blocking after a stale Web PID is reused and an emergency chat request-extension failure.\n" +
        "• Added recording and audio-device permissions for on-demand microphone access in WebView, Gecko and emergency pages.\n" +
        "• Store identical emergency archives once, saving about 208 MB while retaining independent offline recovery.\n" +
        "• Added an independent emergency DSH workspace for failures in the regular environment.\n" +
        "• Controlled AI tools diagnose and propose repairs. Each write requires native confirmation, verified process shutdown and original-data protection.\n" +
        "• Emergency runtime assets, authentication and process identities are tracked separately.\n" +
        "• Updated mobile plugin 3.0.3 to source revision a094288, retaining phone shortcut search and improving plugin navigation, focus and short-screen controls.\n" +
        "• DSH remains 0.1.7-rc.2 with existing migration and data protection.",
)