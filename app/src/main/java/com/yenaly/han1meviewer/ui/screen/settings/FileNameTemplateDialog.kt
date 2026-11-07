package com.yenaly.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.util.DownloadFileNameTemplate

/**
 * 文件名模板编辑弹窗。
 *
 * 输入过程中实时用示例数据渲染，用户能立刻看到最终文件名长什么样，
 * 不用先下载一遍再回来改。
 */
@Composable
fun FileNameTemplateDialog(
    visible: Boolean,
    initialTemplate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!visible) return

    var value by rememberSaveable { mutableStateOf(initialTemplate) }
    LaunchedEffect(visible, initialTemplate) {
        if (visible) value = initialTemplate
    }

    val preview = DownloadFileNameTemplate.render(
        template = value,
        title = SAMPLE_TITLE,
        quality = SAMPLE_QUALITY,
        suffix = SAMPLE_EXT,
        code = SAMPLE_CODE,
        artist = SAMPLE_ARTIST,
        releaseDate = SAMPLE_DATE,
        chineseTitle = SAMPLE_TITLE,
        originalName = null,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_file_name_template)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.download_file_name_template_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(DownloadFileNameTemplate.DEFAULT_TEMPLATE) },
                )
                Text(
                    text = stringResource(R.string.download_file_name_template_preview, preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.download_file_name_template_variables),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(value.ifBlank { DownloadFileNameTemplate.DEFAULT_TEMPLATE })
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private const val SAMPLE_TITLE = "【中文字幕】Sample Title 第 01 话"
private const val SAMPLE_CODE = "HAN-0001"
private const val SAMPLE_ARTIST = "Sample Studio"
private const val SAMPLE_DATE = "2026-08-07"
private const val SAMPLE_QUALITY = "1080P"
private const val SAMPLE_EXT = "mp4"
