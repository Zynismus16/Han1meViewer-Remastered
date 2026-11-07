package com.yenaly.han1meviewer.logic.download

import com.yenaly.han1meviewer.HFileManager
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.model.HanimeVideo
import com.yenaly.han1meviewer.logic.network.ExternalDownloadTask
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderClient
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderConfig
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderResult
import com.yenaly.han1meviewer.logic.network.ExternalDownloaderType

/**
 * 把「下载」动作转交给外部下载器的桥接层。
 *
 * 之所以单独抽一层，是为了让 UI 层不需要知道 Aria2/qBittorrent 的差异，
 * 也方便以后换下载器时只改这里。
 */
object ExternalDownloaderBridge {

    /**
     * 从设置里读出当前配置。
     */
    fun currentConfig(): ExternalDownloaderConfig = ExternalDownloaderConfig(
        type = ExternalDownloaderType.fromId(Preferences.externalDownloaderType),
        aria2Url = Preferences.aria2RpcUrl,
        aria2Secret = Preferences.aria2RpcSecret,
        aria2Dir = Preferences.aria2SaveDir,
        qbUrl = Preferences.qbBaseUrl,
        qbUsername = Preferences.qbUsername,
        qbPassword = Preferences.qbPassword,
        qbSavePath = Preferences.qbSavePath,
    )

    /**
     * 是否应该把下载交给外部下载器：开关打开且地址已经填好。
     * 地址没填时继续走本地下载，避免用户开了开关却什么都下不了。
     */
    fun isAvailable(): Boolean = Preferences.externalDownloaderEnabled && currentConfig().isConfigured

    /**
     * 把某个视频按指定画质推送到外部下载器。
     *
     * 文件名与本地下载共用同一套模板，保证两边命名一致。
     */
    suspend fun submit(
        videoData: HanimeVideo,
        videoCode: String,
        quality: String,
    ): ExternalDownloaderResult {
        val link = videoData.videoUrls[quality]?.link
            ?: return ExternalDownloaderResult.Failure("当前画质没有可用的下载直链")
        val fileName = HFileManager.createDownloadFileName(
            title = videoData.title,
            quality = quality,
            suffix = videoData.videoUrls[quality]?.suffix ?: HFileManager.DEF_VIDEO_TYPE,
            videoCode = videoCode,
            artist = videoData.artist?.name,
            releaseDate = videoData.uploadTime?.toString(),
            chineseTitle = videoData.chineseTitle,
            originalName = link.substringAfterLast('/').substringBefore('?').ifBlank { null },
        )
        return ExternalDownloaderClient.submit(
            config = currentConfig(),
            task = ExternalDownloadTask(
                url = link,
                fileName = fileName,
                referer = Preferences.homeUrl,
                userAgent = Preferences.effectiveUserAgent,
                cookie = buildCookieHeader(),
            ),
        )
    }

    /**
     * 合并登录与 Cloudflare 两处 Cookie；两者可能包含同一个键，这里只做去重拼接，
     * 由服务端按先后顺序取第一个匹配项。
     */
    private fun buildCookieHeader(): String? {
        val merged = listOf(
            Preferences.loginCookie.cookie,
            Preferences.cloudFlareCookie.cookie,
        ).filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
        return merged.ifBlank { null }
    }
}
