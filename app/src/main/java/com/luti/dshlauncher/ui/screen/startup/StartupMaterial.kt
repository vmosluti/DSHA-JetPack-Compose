package com.luti.dshlauncher.ui.screen.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luti.dshlauncher.R

@Composable
internal fun StartupMaterial(
    lines: List<String>,
    finished: Boolean,
    onDownloadLog: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.dsh_boot_title)) },
                actions = {
                    IconButton(onClick = onDownloadLog) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.dsh_boot_download_log),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101014),
                    scrolledContainerColor = Color(0xFF101014),
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
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
