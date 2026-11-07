package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderType
import com.yenaly.han1meviewer.ui.component.ChoiceDialog
import com.yenaly.han1meviewer.ui.component.SettingNavigationItem
import com.yenaly.han1meviewer.ui.component.SettingSwitchItem
import com.yenaly.han1meviewer.ui.component.TextInputDialog
import com.yenaly.han1meviewer.ui.component.lazy.LazyColumn

/**
 * 可以编辑的外部下载器字段，Route 层按它映射到对应的 Preference key。
 */
enum class ExternalDownloaderField {
    ARIA2_URL,
    ARIA2_SECRET,
    ARIA2_DIR,
    QB_URL,
    QB_USERNAME,
    QB_PASSWORD,
    QB_SAVE_PATH,
}

data class ExternalDownloaderUiState(
    val enabled: Boolean = false,
    val typeId: String = ExternalDownloaderType.ARIA2.id,
    val typeLabel: String = "Aria2",
    val aria2Url: String = "",
    val aria2Secret: String = "",
    val aria2Dir: String = "",
    val qbUrl: String = "",
    val qbUsername: String = "",
    val qbPassword: String = "",
    val qbSavePath: String = "",
    val testing: Boolean = false,
    val testResult: String? = null,
)

@Composable
fun ExternalDownloaderSettingsScreen(
    state: ExternalDownloaderUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTypeChange: (String) -> Unit,
    onFieldChange: (ExternalDownloaderField, String) -> Unit,
    onTestConnection: () -> Unit,
) {
    var editingField by remember { mutableStateOf<ExternalDownloaderField?>(null) }
    var showTypeDialog by remember { mutableStateOf(false) }

    ChoiceDialog(
        visible = showTypeDialog,
        title = stringResource(R.string.external_downloader_type),
        options = listOf(
            "Aria2 (JSON-RPC)" to ExternalDownloaderType.ARIA2.id,
            "qBittorrent (Web API)" to ExternalDownloaderType.QBITTORRENT.id,
        ),
        selectedValue = state.typeId,
        onDismiss = { showTypeDialog = false },
        onSelect = {
            showTypeDialog = false
            onTypeChange(it)
        },
    )

    editingField?.let { field ->
        TextInputDialog(
            visible = true,
            title = stringResource(field.titleRes()),
            initialValue = state.valueFor(field),
            onDismiss = { editingField = null },
            onConfirm = { value ->
                editingField = null
                onFieldChange(field, value)
            },
            isPassword = field == ExternalDownloaderField.ARIA2_SECRET ||
                field == ExternalDownloaderField.QB_PASSWORD,
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            SettingSwitchItem(
                title = stringResource(R.string.external_downloader_enable),
                summary = stringResource(R.string.external_downloader_enable_summary),
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                iconRes = R.drawable.baseline_add_link_24,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.external_downloader_type),
                valueText = state.typeLabel,
                iconRes = R.drawable.baseline_online_manage_24,
                onClick = { showTypeDialog = true },
                enabled = state.enabled,
            )
        }

        if (state.enabled && state.typeId == ExternalDownloaderType.QBITTORRENT.id) {
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.qb_base_url),
                    summary = if (state.qbUrl.isBlank()) stringResource(R.string.not_set) else state.qbUrl,
                    iconRes = R.drawable.baseline_domain_24,
                    onClick = { editingField = ExternalDownloaderField.QB_URL },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.qb_username),
                    summary = if (state.qbUsername.isBlank()) {
                        stringResource(R.string.not_set)
                    } else {
                        state.qbUsername
                    },
                    iconRes = R.drawable.baseline_manage_24,
                    onClick = { editingField = ExternalDownloaderField.QB_USERNAME },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.qb_password),
                    summary = if (state.qbPassword.isBlank()) {
                        stringResource(R.string.not_set)
                    } else {
                        "••••••"
                    },
                    iconRes = R.drawable.baseline_cert_24,
                    onClick = { editingField = ExternalDownloaderField.QB_PASSWORD },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.qb_save_path),
                    summary = if (state.qbSavePath.isBlank()) {
                        stringResource(R.string.qb_save_path_default_summary)
                    } else {
                        state.qbSavePath
                    },
                    iconRes = R.drawable.baseline_path_24,
                    onClick = { editingField = ExternalDownloaderField.QB_SAVE_PATH },
                )
            }
        }

        if (state.enabled && state.typeId == ExternalDownloaderType.ARIA2.id) {
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.aria2_rpc_url),
                    summary = if (state.aria2Url.isBlank()) {
                        stringResource(R.string.aria2_rpc_url_default_summary)
                    } else {
                        state.aria2Url
                    },
                    iconRes = R.drawable.baseline_domain_24,
                    onClick = { editingField = ExternalDownloaderField.ARIA2_URL },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.aria2_rpc_secret),
                    summary = if (state.aria2Secret.isBlank()) {
                        stringResource(R.string.aria2_rpc_secret_default_summary)
                    } else {
                        "••••••"
                    },
                    iconRes = R.drawable.baseline_cert_24,
                    onClick = { editingField = ExternalDownloaderField.ARIA2_SECRET },
                )
            }
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.aria2_save_dir),
                    summary = if (state.aria2Dir.isBlank()) {
                        stringResource(R.string.aria2_save_dir_default_summary)
                    } else {
                        state.aria2Dir
                    },
                    iconRes = R.drawable.baseline_path_24,
                    onClick = { editingField = ExternalDownloaderField.ARIA2_DIR },
                )
            }
        }

        if (state.enabled) {
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.test_connection),
                    summary = state.testResult,
                    iconRes = R.drawable.baseline_online_manage_24,
                    onClick = onTestConnection,
                    enabled = !state.testing,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.external_downloader_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

private fun ExternalDownloaderField.titleRes(): Int = when (this) {
    ExternalDownloaderField.ARIA2_URL -> R.string.aria2_rpc_url
    ExternalDownloaderField.ARIA2_SECRET -> R.string.aria2_rpc_secret
    ExternalDownloaderField.ARIA2_DIR -> R.string.aria2_save_dir
    ExternalDownloaderField.QB_URL -> R.string.qb_base_url
    ExternalDownloaderField.QB_USERNAME -> R.string.qb_username
    ExternalDownloaderField.QB_PASSWORD -> R.string.qb_password
    ExternalDownloaderField.QB_SAVE_PATH -> R.string.qb_save_path
}

private fun ExternalDownloaderUiState.valueFor(field: ExternalDownloaderField): String =
    when (field) {
        ExternalDownloaderField.ARIA2_URL -> aria2Url
        ExternalDownloaderField.ARIA2_SECRET -> aria2Secret
        ExternalDownloaderField.ARIA2_DIR -> aria2Dir
        ExternalDownloaderField.QB_URL -> qbUrl
        ExternalDownloaderField.QB_USERNAME -> qbUsername
        ExternalDownloaderField.QB_PASSWORD -> qbPassword
        ExternalDownloaderField.QB_SAVE_PATH -> qbSavePath
    }
