package com.luti.dshlauncher.ui.dsha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalUiMode
import com.luti.dshlauncher.ui.UiMode
import com.luti.dshlauncher.ui.component.material.SegmentedColumn
import com.luti.dshlauncher.ui.component.material.SegmentedListItem
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class WelcomeItem(val title: String, val summary: String, val icon: ImageVector)

@Composable
fun WelcomeScreen(onContinue: () -> Unit, onSkip: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val primary = when (pagerState.currentPage) {
        0 -> stringResource(R.string.ui2_meet)
        1 -> dshaChoose("了解数据与权限", "Data and permissions")
        else -> dshaChoose("开始准备环境", "Prepare the environment")
    }
    DshaPageScaffold(
        title = "DSHA",
        subtitle = "${pagerState.currentPage + 1} / 3",
        footer = listOf(
            DshaAction(primary, primary = true) {
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onContinue()
                }
            },
            DshaAction(stringResource(R.string.ui2_skip), primary = false, onClick = onSkip),
        ),
    ) {
        item {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
            ) { page ->
                when (page) {
                    0 -> WelcomeIntro()
                    1 -> WelcomeFeatures()
                    else -> WelcomePrivacy()
                }
            }
        }
    }
}

@Composable
private fun WelcomeIntro() {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        DshaNote("DEEPSEEK HARNESS · ANDROID")
        androidx.compose.material3.Text(
            text = stringResource(R.string.ui2_welcome_title0),
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        DshaNote(stringResource(R.string.ui2_welcome_lead0))
        DshaNote(stringResource(R.string.ui2_offline_badge))
    }
}

@Composable
private fun WelcomeFeatures() {
    val items = listOf(
        WelcomeItem(stringResource(R.string.ui2_w1_0), stringResource(R.string.ui2_ws1_0), Icons.Rounded.Chat),
        WelcomeItem(stringResource(R.string.ui2_w1_1), stringResource(R.string.ui2_ws1_1), Icons.Rounded.Terminal),
        WelcomeItem(stringResource(R.string.ui2_w1_2), stringResource(R.string.ui2_ws1_2), Icons.Rounded.Extension),
    )
    Column(modifier = Modifier.padding(top = 12.dp)) {
        DshaNote("01 / BUILT FOR YOUR FLOW")
        androidx.compose.material3.Text(
            text = stringResource(R.string.ui2_welcome_title1),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        DshaNote(stringResource(R.string.ui2_welcome_lead1))
        WelcomeItemCard(items)
    }
}

@Composable
private fun WelcomePrivacy() {
    val items = listOf(
        WelcomeItem(stringResource(R.string.ui2_w2_0), stringResource(R.string.ui2_ws2_0), Icons.Rounded.Folder),
        WelcomeItem(stringResource(R.string.ui2_w2_1), stringResource(R.string.ui2_ws2_1), Icons.Rounded.Key),
        WelcomeItem(stringResource(R.string.ui2_w2_2), stringResource(R.string.ui2_ws2_2), Icons.Rounded.Security),
    )
    Column(modifier = Modifier.padding(top = 12.dp)) {
        DshaNote("02 / YOUR DATA, YOUR CHOICE")
        androidx.compose.material3.Text(
            text = stringResource(R.string.ui2_welcome_title2),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        DshaNote(stringResource(R.string.ui2_welcome_lead2))
        WelcomeItemCard(items)
        DshaNote(stringResource(R.string.ui2_online_notice))
    }
}

@Composable
private fun WelcomeItemCard(items: List<WelcomeItem>) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            items.forEach { item ->
                BasicComponent(
                    title = item.title,
                    summary = item.summary,
                    startAction = {
                        Icon(item.icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    },
                )
            }
        }
        UiMode.Material -> SegmentedColumn(
            modifier = Modifier.padding(vertical = 8.dp),
            content = items.map { item ->
                {
                    SegmentedListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.summary) },
                    )
                }
            },
        )
    }
}

@Composable
private fun dshaChoose(zh: String, en: String): String =
    com.deepseekharness.app.util.UiText.choose(zh, en)