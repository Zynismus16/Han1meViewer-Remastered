package com.yenaly.han1meviewer.ui.navigation.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.download.ExternalDownloaderBridge
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderClient
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderResult
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderType
import com.yenaly.han1meviewer.ui.screen.settings.ExternalDownloaderField
import com.yenaly.han1meviewer.ui.screen.settings.ExternalDownloaderSettingsScreen
import com.yenaly.han1meviewer.ui.screen.settings.ExternalDownloaderUiState
import kotlinx.coroutines.launch

/**
 * 外部下载器设置页。
 *
 * 这里只做「读写偏好 + 触发测试连接」，具体展示交给
 * [ExternalDownloaderSettingsScreen]。
 */
@Composable
fun ExternalDownloaderSettingsRouteScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val state = remember(refreshKey, testing, testResult) {
        buildExternalDownloaderUiState(context, testing, testResult)
    }

    ExternalDownloaderSettingsScreen(
        state = state,
        onEnabledChange = { enabled ->
            Preferences.preferenceSp.edit {
                putBoolean(SettingsPreferenceKeys.EXTERNAL_DOWNLOADER_ENABLED, enabled)
            }
            testResult = null
            refreshKey++
        },
        onTypeChange = { typeId ->
            Preferences.preferenceSp.edit {
                putString(SettingsPreferenceKeys.EXTERNAL_DOWNLOADER_TYPE, typeId)
            }
            testResult = null
            refreshKey++
        },
        onFieldChange = { field, value ->
            val key = when (field) {
                ExternalDownloaderField.ARIA2_URL -> SettingsPreferenceKeys.ARIA2_RPC_URL
                ExternalDownloaderField.ARIA2_SECRET -> SettingsPreferenceKeys.ARIA2_RPC_SECRET
                ExternalDownloaderField.ARIA2_DIR -> SettingsPreferenceKeys.ARIA2_SAVE_DIR
                ExternalDownloaderField.QB_URL -> SettingsPreferenceKeys.QB_BASE_URL
                ExternalDownloaderField.QB_USERNAME -> SettingsPreferenceKeys.QB_USERNAME
                ExternalDownloaderField.QB_PASSWORD -> SettingsPreferenceKeys.QB_PASSWORD
                ExternalDownloaderField.QB_SAVE_PATH -> SettingsPreferenceKeys.QB_SAVE_PATH
            }
            Preferences.preferenceSp.edit { putString(key, value) }
            testResult = null
            refreshKey++
        },
        onTestConnection = {
            if (!testing) {
                testing = true
                testResult = null
                scope.launch {
                    val result = ExternalDownloaderClient.testConnection(
                        ExternalDownloaderBridge.currentConfig()
                    )
                    testResult = when (result) {
                        is ExternalDownloaderResult.Success -> result.message
                        is ExternalDownloaderResult.Failure -> "连接失败：${result.message}"
                    }
                    testing = false
                }
            }
        },
    )
}

private fun buildExternalDownloaderUiState(
    context: Context,
    testing: Boolean,
    testResult: String?,
): ExternalDownloaderUiState {
    val type = ExternalDownloaderType.fromId(Preferences.externalDownloaderType)
    return ExternalDownloaderUiState(
        enabled = Preferences.externalDownloaderEnabled,
        typeId = type.id,
        typeLabel = when (type) {
            ExternalDownloaderType.ARIA2 -> "Aria2"
            ExternalDownloaderType.QBITTORRENT -> "qBittorrent"
        },
        aria2Url = Preferences.aria2RpcUrl,
        aria2Secret = Preferences.aria2RpcSecret,
        aria2Dir = Preferences.aria2SaveDir,
        qbUrl = Preferences.qbBaseUrl,
        qbUsername = Preferences.qbUsername,
        qbPassword = Preferences.qbPassword,
        qbSavePath = Preferences.qbSavePath,
        testing = testing,
        testResult = testResult ?: if (testing) {
            context.getString(com.yenaly.han1meviewer.R.string.testing_connection)
        } else {
            null
        },
    )
}
