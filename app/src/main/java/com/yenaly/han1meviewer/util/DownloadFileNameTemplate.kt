package com.yenaly.han1meviewer.util

import java.time.LocalDate

/**
 * 下载文件名模板渲染。
 *
 * 用户可在「下载设置 → 文件名模板」里自定义命名规则，模板中用 `{变量名}` 占位，
 * 例如 `{artist}_{date}_{title}_{quality}`。渲染时会依次：
 *
 * 1. 替换已知变量，未知变量原样保留（方便用户发现自己拼错了变量名）；
 * 2. 把 `/`、`\`、`:` 等文件系统非法字符替换成 `_`；
 * 3. 合并因变量为空而留下的重复分隔符，并去掉首尾的空白与分隔符；
 * 4. 限制基础名长度，避免触发文件系统上限；
 * 5. 保证结果以真实扩展名结尾，不会出现 `xxx.mp4.mp4`。
 *
 * @project Han1meViewer
 */
object DownloadFileNameTemplate {

    /**
     * 默认模板，与旧版命名 ``${title}_${quality}.${suffix}`` 保持一致。
     */
    const val DEFAULT_TEMPLATE = "{title}_{quality}"

    /**
     * 基础名（不含扩展名）的最大长度，留出余量给扩展名与文件系统开销。
     */
    const val MAX_BASE_NAME_LENGTH = 120

    /**
     * 模板支持的变量名，顺序与设置页里的说明保持一致。
     */
    val VARIABLE_NAMES = listOf(
        "title",
        "chineseTitle",
        "code",
        "artist",
        "date",
        "quality",
        "ext",
        "downloadDate",
        "original",
    )

    private val ILLEGAL_CHARS = Regex("""["*/:<>?\\|\x00-\x1F\x7F]""")
    private val PLACEHOLDER = Regex("""\{([A-Za-z][A-Za-z0-9_]*)}""")
    private val MULTI_UNDERSCORE = Regex("""_{2,}""")
    private val MULTI_SPACE = Regex("""\s{2,}""")
    private val MULTI_DASH = Regex("""-{2,}""")

    /**
     * 渲染最终文件名（含扩展名）。
     *
     * @param template 用户设置的模板，为空时回退到 [DEFAULT_TEMPLATE]
     * @param title 网页视频标题
     * @param quality 画质，如 `1080P`
     * @param suffix 扩展名，如 `mp4`
     * @param code 视频编号
     * @param artist 发布商 / 作者
     * @param releaseDate 发行日期，建议 ISO 格式（`2026-08-07`）
     * @param chineseTitle 中文标题，为空时回退到 [title]
     * @param originalName 原始文件名（从下载直链推导），可能为空
     * @param downloadDate 下载日期，默认取当天
     */
    fun render(
        template: String,
        title: String,
        quality: String,
        suffix: String,
        code: String = "",
        artist: String? = null,
        releaseDate: String? = null,
        chineseTitle: String? = null,
        originalName: String? = null,
        downloadDate: String = LocalDate.now().toString(),
    ): String {
        val safeSuffix = suffix.trim().trimStart('.')
        val values = mapOf(
            "title" to title,
            "chineseTitle" to chineseTitle.orEmpty().ifBlank { title },
            "code" to code,
            "artist" to artist.orEmpty(),
            "date" to releaseDate.orEmpty(),
            "quality" to quality,
            "ext" to safeSuffix,
            "downloadDate" to downloadDate,
            "original" to originalName.orEmpty(),
        )

        val rendered = PLACEHOLDER.replace(template.ifBlank { DEFAULT_TEMPLATE }) { match ->
            values[match.groupValues[1]] ?: match.value
        }

        var baseName = rendered
            .replace(ILLEGAL_CHARS, "_")
            .replace(MULTI_SPACE, " ")
            .replace(MULTI_UNDERSCORE, "_")
            .replace(MULTI_DASH, "-")
            .trim()
            .trim('_', '-', '.', ' ')

        val suffixPart = if (safeSuffix.isEmpty()) "" else ".$safeSuffix"
        if (suffixPart.isNotEmpty() && baseName.endsWith(suffixPart, ignoreCase = true)) {
            baseName = baseName.dropLast(suffixPart.length).trim('_', '-', '.', ' ')
        }
        if (baseName.isBlank()) {
            baseName = "hanime_${System.currentTimeMillis()}"
        }
        if (baseName.length > MAX_BASE_NAME_LENGTH) {
            baseName = baseName.take(MAX_BASE_NAME_LENGTH).trim('_', '-', '.', ' ')
        }
        return if (suffixPart.isEmpty()) baseName else baseName + suffixPart
    }
}
