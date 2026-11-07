package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSliderItem
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn
import com.yenaly.han1meviewer.ui.preview.ComponentPreview

data class DownloadSettingsUiState(
    val downloadPathSummary: String,
    val downloadCountLimit: Int,
    val downloadCountLimitSummary: String,
    val downloadSpeedLimitIndex: Int,
    val downloadSpeedLimitSummary: String,
    /** 当前生效的文件名模板，展示在设置项副标题上 */
    val fileNameTemplate: String = "",
    /** 外部下载器状态摘要，如「已启用 · Aria2」 */
    val externalDownloaderSummary: String = "",
)

@Composable
fun DownloadSettingsScreen(
    state: DownloadSettingsUiState,
    maxDownloadCountLimit: Int,
    maxDownloadSpeedLimitIndex: Int,
    onOpenDownloadPath: () -> Unit,
    onRestoreDefaultPath: () -> Unit,
    onImportDownloadedFiles: () -> Unit,
    onDownloadCountLimitChange: (Int) -> Unit,
    onDownloadSpeedLimitChange: (Int) -> Unit,
    onEditFileNameTemplate: () -> Unit,
    onOpenExternalDownloader: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SettingNavigationItem(
                title = stringResource(R.string.download_path),
                summary = state.downloadPathSummary,
                iconRes = R.drawable.baseline_path_24,
                onClick = onOpenDownloadPath,
            )
        }

        item {
            SettingNavigationItem(
                title = stringResource(R.string.pref_export_downloads_title),
                summary = stringResource(R.string.pref_export_downloads_summary),
                iconRes = R.drawable.baseline_export_24,
                onClick = onImportDownloadedFiles,
            )
        }

        item {
            SettingNavigationItem(
                title = stringResource(R.string.download_file_name_template),
                summary = state.fileNameTemplate,
                iconRes = R.drawable.baseline_edit_24,
                onClick = onEditFileNameTemplate,
            )
        }

        item {
            SettingNavigationItem(
                title = stringResource(R.string.external_downloader),
                summary = state.externalDownloaderSummary,
                iconRes = R.drawable.baseline_add_link_24,
                onClick = onOpenExternalDownloader,
            )
        }

        item {
            SettingSliderItem(
                title = stringResource(R.string.download_count_limit),
                summary = state.downloadCountLimitSummary,
                value = state.downloadCountLimit,
                valueRange = 0..maxDownloadCountLimit,
                iconRes = R.drawable.baseline_count_24,
                onValueChange = onDownloadCountLimitChange,
            )
        }

        item {
            SettingSliderItem(
                title = stringResource(R.string.download_speed_limit),
                summary = state.downloadSpeedLimitSummary,
                value = state.downloadSpeedLimitIndex,
                valueRange = 0..maxDownloadSpeedLimitIndex,
                iconRes = R.drawable.baseline_speed2_24,
                onValueChange = onDownloadSpeedLimitChange,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadSettingsScreenPreview() {
    ComponentPreview {
        DownloadSettingsScreen(
            state = DownloadSettingsUiState(
                downloadPathSummary = "/storage/emulated/0/Android/data/.../files",
                downloadCountLimit = 2,
                downloadCountLimitSummary = "2",
                downloadSpeedLimitIndex = 0,
                downloadSpeedLimitSummary = "无限制",
            ),
            maxDownloadCountLimit = 10,
            maxDownloadSpeedLimitIndex = 5,
            onOpenDownloadPath = {},
            onRestoreDefaultPath = {},
            onImportDownloadedFiles = {},
            onDownloadCountLimitChange = {},
            onDownloadSpeedLimitChange = {},
            onEditFileNameTemplate = {},
            onOpenExternalDownloader = {},
        )
    }
}
