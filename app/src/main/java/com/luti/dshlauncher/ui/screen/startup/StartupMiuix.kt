package com.luti.dshlauncher.ui.screen.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
internal fun StartupMiuix(
    lines: List<String>,
    finished: Boolean,
    onDownloadLog: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.dsh_boot_title),
                actions = {
                    IconButton(onClick = onDownloadLog) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.dsh_boot_download_log),
                        )
                    }
                },
            )
        },
        popupHost = { },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101014))
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.dsh_boot_subtitle),
                    color = Color(0xFF9A9AA0),
                    fontSize = 13.sp,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            color = Color(0xFF7BE38B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    if (finished) {
                        Text(
                            text = stringResource(R.string.dsh_boot_done),
                            color = Color(0xFFE6E6E6),
                            fontSize = 13.sp,
                        )
                    } else {
                        CircularProgressIndicator(color = Color(0xFF7BE38B))
                    }
                }
            }
        }
    }
}
