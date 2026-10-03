package com.luti.dshlauncher.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.luti.dshlauncher.templateApp
import com.luti.dshlauncher.ui.screen.home.HomeUiState
import com.luti.dshlauncher.ui.screen.home.SystemInfo
import com.luti.dshlauncher.ui.screen.home.getAppVersion
import com.luti.dshlauncher.ui.util.LatestVersionInfo

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // 更新检测已改由 UpdateEngine（dsha.cc 清单）负责，这里不再请求模板遗留的 GitHub 接口。
            val baseState = withContext(Dispatchers.IO) { buildState() }
            _uiState.update { baseState }
        }
    }

    private fun buildState(): HomeUiState {
        val appVersion = getAppVersion(templateApp)

        return HomeUiState(
            checkUpdateEnabled = templateApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("check_update", true),
            latestVersionInfo = LatestVersionInfo(),
            currentAppVersionCode = appVersion.versionCode,
            systemInfo = SystemInfo(
                appVersion = "${appVersion.versionName} (${appVersion.versionCode})",
            ),
        )
    }
}