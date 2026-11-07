package com.yenaly.han1meviewer
import android.content.Context
import android.os.Environment
import android.util.Log
import com.yenaly.han1meviewer.util.DownloadFileNameTemplate
import java.io.File

object HFileManager {

    const val HANIME_DOWNLOAD_FOLDER = "hanime_download"
    const val DEF_VIDEO_TYPE = "mp4"
    const val DEF_VIDEO_COVER_TYPE = "png"
    val illegalCharsRegex = Regex("""["*/:<>?\\|\x00-\x1F\x7F]""")


    /**
     * 获取 App 的下载主目录，如写入失败则切换为私有目录，
     * 使用 [com.yenaly.han1meviewer.util.SafFileManager] 可自定义目录
     */
    fun getAppDownloadFolder(context: Context): File {
        return if (Preferences.isUsePrivateStorage) {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APP_NAME
            )
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APP_NAME
            )
        }
    }


    /**
     * 获取某视频的下载目录
     */
    fun getDownloadVideoFolder(context: Context, videoCode: String): File {
        val folder = File(getAppDownloadFolder(context), "$HANIME_DOWNLOAD_FOLDER/$videoCode")
        return folder
    }

    /**
     * 获取视频文件
     */
    fun getDownloadVideoFile(
        context: Context,
        videoCode: String,
        title: String,
        quality: String,
        suffix: String = DEF_VIDEO_TYPE
    ): File {
        return File(
            getDownloadVideoFolder(context, videoCode),
            createVideoName(title, quality, suffix)
        )
    }

    /**
     * 按已渲染好的文件名获取视频文件。
     *
     * 与 [getDownloadVideoFile] 的区别在于文件名完全由调用方决定，
     * 供「文件名模板」功能使用，避免重复渲染导致命名不一致。
     */
    fun getDownloadVideoFileByName(
        context: Context,
        videoCode: String,
        fileName: String,
    ): File {
        return File(getDownloadVideoFolder(context, videoCode), fileName)
    }

    /**
     * 获取视频封面文件
     */
    fun getDownloadVideoCoverFile(
        context: Context,
        videoCode: String,
        title: String,
        suffix: String = DEF_VIDEO_COVER_TYPE
    ): File {
        return File(
            getDownloadVideoFolder(context, videoCode),
            createVideoCoverName(title, suffix)
        )
    }

    /**
     * 替换非法文件名字符
     */
    private fun String.replaceAllIllegalChars(): String =
        illegalCharsRegex.replace(this, "_")

    fun createVideoName(title: String, quality: String, suffix: String): String =
        "${title.replaceAllIllegalChars()}_${quality}.$suffix"

    /**
     * 按用户在「下载设置 → 文件名模板」里配置的模板生成文件名（含扩展名）。
     *
     * 模板为空或渲染结果非法时会自动回退到 [DownloadFileNameTemplate.DEFAULT_TEMPLATE]。
     */
    fun createDownloadFileName(
        title: String,
        quality: String,
        suffix: String = DEF_VIDEO_TYPE,
        videoCode: String = "",
        artist: String? = null,
        releaseDate: String? = null,
        chineseTitle: String? = null,
        originalName: String? = null,
    ): String = DownloadFileNameTemplate.render(
        template = Preferences.downloadFileNameTemplate,
        title = title,
        quality = quality,
        suffix = suffix,
        code = videoCode,
        artist = artist,
        releaseDate = releaseDate,
        chineseTitle = chineseTitle,
        originalName = originalName,
    )

    fun createVideoCoverName(title: String, suffix: String): String =
        "${title.replaceAllIllegalChars()}.$suffix"

    /**
     * 创建目录并在其中写入 .nomedia 文件，防止被媒体扫描器扫描到让你尴尬
     */
    @Deprecated("下载工具已经创建了nomedia，没必要重复创建")
    private fun File.makeFolderNoMedia() {
        if (!exists() && !mkdirs()) {
            Log.w("HFileManager", "⚠️ 目录创建失败: $absolutePath")
            return
        }

        if (!isDirectory) {
            Log.w("HFileManager", "⚠️ 已存在但不是文件夹: $absolutePath")
            return
        }

        val noMedia = File(this, ".nomedia")
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
                .onFailure { Log.w("HFileManager", "⚠️ 创建 .nomedia 失败: ${it.message}") }
        }
    }
}
