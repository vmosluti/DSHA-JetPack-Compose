package com.luti.dshlauncher.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.luti.dshlauncher.R
import com.luti.dshlauncher.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial() {
    val mainPagerState = LocalMainPagerState.current

    val items = listOf(
        Triple(R.string.nav_home, Icons.Filled.Cottage, Icons.Outlined.Cottage),
        Triple(R.string.nav_plugins, Icons.Filled.Extension, Icons.Outlined.Extension),
        Triple(R.string.nav_terminal, Icons.Filled.Terminal, Icons.Outlined.Terminal),
        Triple(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    FlexibleBottomAppBar(
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        if (selected) selectedIcon else unselectedIcon,
                        stringResource(label)
                    )
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
