package com.deepseekharness.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.deepseekharness.app.util.UiText
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.dsha.DshaContentDialog
import com.luti.dshlauncher.ui.dsha.DshaDialogAction
import com.luti.dshlauncher.ui.dsha.DshaNote
import com.luti.dshlauncher.ui.dsha.DshaSecondaryButton
import com.luti.dshlauncher.ui.dsha.DshaSelectableText

/**
 * 关于信息：GitHub 仓库 / QQ 交流群入口。
 *
 * 展示由 Compose 页面承担；这里只保留链接打开能力与 Compose 版对话框，
 * 不再持有任何 XML 视图。
 */
object AboutDialog {

    const val GITHUB_URL = "https://github.com/qiannianhuanxiang/DSHA"
    const val QQ_GROUP = "975836806"

    fun openBrowser(ctx: Context, url: String) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, UiText.text("打不开，请手动访问：") + url, Toast.LENGTH_SHORT).show()
        }
    }

    fun openQQGroup(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "mqqapi://card/show_pslcard?src_type=internal&version=1" +
                            "&uin=" + QQ_GROUP + "&card_type=group",
                    ),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, UiText.text("打不开 QQ，请手动搜索群号：") + QQ_GROUP, Toast.LENGTH_SHORT).show()
        }
    }

    /** 版本号；读取失败时返回 unknown，与原实现一致。 */
    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    /** Compose 版关于对话框：版本、说明与两个入口。 */
    @Composable
    fun DshaAboutDialog(visible: Boolean, onDismiss: () -> Unit) {
        if (!visible) return
        val context = LocalContext.current
        val version = remember { versionName(context) }
        DshaContentDialog(
            title = "DSHA v$version",
            visible = true,
            onDismiss = onDismiss,
            actions = listOf(DshaDialogAction(context.getString(R.string.about_close_button)) {}),
        ) {
            DshaNote(context.getString(R.string.about_tagline))
            DshaSelectableText(context.getString(R.string.edition_description))
            DshaSecondaryButton(title = "GitHub · " + Uri.parse(GITHUB_URL).path.orEmpty().removePrefix("/")) {
                openBrowser(context, GITHUB_URL)
            }
            DshaSecondaryButton(title = context.getString(R.string.about_community) + " · " + QQ_GROUP) {
                openQQGroup(context)
            }
        }
    }
}