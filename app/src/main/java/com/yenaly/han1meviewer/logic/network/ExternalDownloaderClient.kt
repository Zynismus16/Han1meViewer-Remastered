package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 外部下载器类型。
 *
 * - [ARIA2]：Aria2 JSON-RPC，支持 HTTP 直链、自定义文件名与请求头，
 *   Motrix、群晖 Download Station 等都以它为准；
 * - [QBITTORRENT]：qBittorrent Web API，只接受磁力链或 `.torrent` 链接，
 *   本站的 mp4/HLS 直链无法直接交给它，仅作为扩展保留。
 */
enum class ExternalDownloaderType(val id: String) {
    ARIA2("aria2"),
    QBITTORRENT("qbittorrent");

    companion object {
        fun fromId(id: String): ExternalDownloaderType =
            if (id == QBITTORRENT.id) QBITTORRENT else ARIA2
    }
}

/**
 * 外部下载器连接配置，字段按类型各取所需。
 */
data class ExternalDownloaderConfig(
    val type: ExternalDownloaderType = ExternalDownloaderType.ARIA2,
    val aria2Url: String = "",
    val aria2Secret: String = "",
    val aria2Dir: String = "",
    val qbUrl: String = "",
    val qbUsername: String = "",
    val qbPassword: String = "",
    val qbSavePath: String = "",
) {
    /**
     * 当前类型的服务地址是否已经填写，用于设置页与下载入口的前置校验。
     */
    val isConfigured: Boolean
        get() = when (type) {
            ExternalDownloaderType.ARIA2 -> aria2Url.isNotBlank()
            ExternalDownloaderType.QBITTORRENT -> qbUrl.isNotBlank()
        }
}

/**
 * 要推送给外部下载器的任务。
 *
 * @param url 视频直链
 * @param fileName 已经按文件名模板渲染好的文件名（含扩展名）
 * @param referer 防盗链所需的 Referer
 * @param userAgent 生成链接时使用的 UA，部分站点校验
 * @param cookie 站点 Cookie，可能为空
 */
data class ExternalDownloadTask(
    val url: String,
    val fileName: String,
    val referer: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null,
)

sealed interface ExternalDownloaderResult {
    data class Success(val message: String) : ExternalDownloaderResult
    data class Failure(val message: String) : ExternalDownloaderResult
}

/**
 * 外部下载器客户端。
 *
 * 独立使用一个不走系统代理的 OkHttp 实例：下载器通常部署在局域网，
 * 走代理反而连不上。
 */
object ExternalDownloaderClient {

    private const val JSON_RPC_ID = "han1meviewer"

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .build()

    /**
     * 测试连接，成功时返回一句可展示的版本信息。
     */
    suspend fun testConnection(config: ExternalDownloaderConfig): ExternalDownloaderResult {
        return try {
            when (config.type) {
                ExternalDownloaderType.ARIA2 -> testAria2(config)
                ExternalDownloaderType.QBITTORRENT -> testQbittorrent(config)
            }
        } catch (e: Exception) {
            ExternalDownloaderResult.Failure(e.readableMessage())
        }
    }

    /**
     * 把任务提交给外部下载器。
     */
    suspend fun submit(
        config: ExternalDownloaderConfig,
        task: ExternalDownloadTask,
    ): ExternalDownloaderResult {
        if (task.url.isBlank()) {
            return ExternalDownloaderResult.Failure("下载直链为空，无法提交")
        }
        return try {
            when (config.type) {
                ExternalDownloaderType.ARIA2 -> submitToAria2(config, task)
                ExternalDownloaderType.QBITTORRENT -> submitToQbittorrent(config, task)
            }
        } catch (e: Exception) {
            ExternalDownloaderResult.Failure(e.readableMessage())
        }
    }

    // region Aria2

    private suspend fun submitToAria2(
        config: ExternalDownloaderConfig,
        task: ExternalDownloadTask,
    ): ExternalDownloaderResult = withContext(Dispatchers.IO) {
        if (config.aria2Url.isBlank()) {
            return@withContext ExternalDownloaderResult.Failure("请先填写 Aria2 RPC 地址")
        }
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", JSON_RPC_ID)
            put("method", "aria2.addUri")
            putJsonArray("params") {
                if (config.aria2Secret.isNotBlank()) {
                    add("token:${config.aria2Secret}")
                }
                addJsonArray { add(task.url) }
                addJsonObject {
                    put("out", task.fileName)
                    if (config.aria2Dir.isNotBlank()) {
                        put("dir", config.aria2Dir.trim())
                    }
                    putJsonArray("header") {
                        task.userAgent?.takeIf { it.isNotBlank() }
                            ?.let { add("User-Agent: $it") }
                        task.referer?.takeIf { it.isNotBlank() }
                            ?.let { add("Referer: $it") }
                        task.cookie?.takeIf { it.isNotBlank() }
                            ?.let { add("Cookie: $it") }
                    }
                }
            }
        }
        val response = executeRpc(aria2Endpoint(config.aria2Url), payload.toString())
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return@withContext ExternalDownloaderResult.Failure(
                "Aria2 返回 HTTP ${response.code}"
            )
        }
        val gid = parseAria2Result(body)
        ExternalDownloaderResult.Success("已提交到 Aria2（GID: $gid）")
    }

    private suspend fun testAria2(config: ExternalDownloaderConfig): ExternalDownloaderResult =
        withContext(Dispatchers.IO) {
            if (config.aria2Url.isBlank()) {
                return@withContext ExternalDownloaderResult.Failure("请先填写 Aria2 RPC 地址")
            }
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", JSON_RPC_ID)
                put("method", "aria2.getVersion")
                putJsonArray("params") {
                    if (config.aria2Secret.isNotBlank()) {
                        add("token:${config.aria2Secret}")
                    }
                }
            }
            val response = executeRpc(aria2Endpoint(config.aria2Url), payload.toString())
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext ExternalDownloaderResult.Failure(
                    "Aria2 返回 HTTP ${response.code}"
                )
            }
            val version = runCatching {
                json.parseToJsonElement(body).jsonObject["result"]
                    ?.jsonObject?.get("version")?.jsonPrimitive?.content
            }.getOrNull()
            ExternalDownloaderResult.Success(
                if (version.isNullOrBlank()) "Aria2 连接成功" else "Aria2 连接成功（v$version）"
            )
        }

    /**
     * 解析 `aria2.addUri` 返回值，成功时返回 GID，失败时抛异常把原因交给调用方。
     */
    private fun parseAria2Result(body: String): String {
        val element = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IOException("Aria2 返回了无法解析的内容") }
        element["error"]?.let { error ->
            val message = runCatching {
                error.jsonObject["message"]?.jsonPrimitive?.content
            }.getOrNull()
            throw IOException(message ?: "Aria2 拒绝了该任务")
        }
        return element["result"]?.jsonPrimitive?.content ?: "unknown"
    }

    private fun aria2Endpoint(raw: String): String {
        val base = raw.trim().trimEnd('/')
        return if (base.endsWith("/jsonrpc")) base else "$base/jsonrpc"
    }

    private suspend fun executeRpc(url: String, payload: String) =
        client.newCall(
            Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        ).await()

    // endregion

    // region qBittorrent

    private suspend fun submitToQbittorrent(
        config: ExternalDownloaderConfig,
        task: ExternalDownloadTask,
    ): ExternalDownloaderResult = withContext(Dispatchers.IO) {
        if (!task.url.startsWith("magnet:") && !task.url.substringBefore('?').endsWith(".torrent")) {
            return@withContext ExternalDownloaderResult.Failure(
                "qBittorrent 只接受磁力链或 .torrent 链接，" +
                    "本站提供的是 HTTP 视频直链，请改用 Aria2 后端"
            )
        }
        if (config.qbUrl.isBlank()) {
            return@withContext ExternalDownloaderResult.Failure("请先填写 qBittorrent 地址")
        }
        val base = config.qbUrl.trim().trimEnd('/')
        val sid = qbLogin(base, config.qbUsername, config.qbPassword)
        val form = FormBody.Builder()
            .add("urls", task.url)
            .apply {
                if (config.qbSavePath.isNotBlank()) add("savepath", config.qbSavePath.trim())
            }
            .build()
        val response = client.newCall(
            Request.Builder()
                .url("$base/api/v2/torrents/add")
                .header("Cookie", "SID=$sid")
                .header("Referer", base)
                .post(form)
                .build()
        ).await()
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful && text.contains("Ok", ignoreCase = true)) {
            ExternalDownloaderResult.Success("已提交到 qBittorrent")
        } else {
            ExternalDownloaderResult.Failure(
                "qBittorrent 拒绝了该任务（HTTP ${response.code}${
                    text.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
                }）"
            )
        }
    }

    private suspend fun testQbittorrent(
        config: ExternalDownloaderConfig,
    ): ExternalDownloaderResult = withContext(Dispatchers.IO) {
        if (config.qbUrl.isBlank()) {
            return@withContext ExternalDownloaderResult.Failure("请先填写 qBittorrent 地址")
        }
        val base = config.qbUrl.trim().trimEnd('/')
        val sid = qbLogin(base, config.qbUsername, config.qbPassword)
        val response = client.newCall(
            Request.Builder()
                .url("$base/api/v2/app/version")
                .header("Cookie", "SID=$sid")
                .header("Referer", base)
                .build()
        ).await()
        val version = response.body?.string().orEmpty().trim()
        if (response.isSuccessful) {
            ExternalDownloaderResult.Success(
                if (version.isBlank()) "qBittorrent 连接成功" else "qBittorrent 连接成功（v$version）"
            )
        } else {
            ExternalDownloaderResult.Failure("qBittorrent 返回 HTTP ${response.code}")
        }
    }

    /**
     * 登录 qBittorrent 并取回 SID。
     *
     * 部分版本（如启用“对本地主机绕过身份验证”）允许空账号密码直接访问，
     * 此时登录接口会返回失败，但 SID 仍可能已经在 Cookie 里，这里一并兜底。
     */
    private suspend fun qbLogin(base: String, username: String, password: String): String {
        base.toHttpUrlOrNull() ?: throw IOException("qBittorrent 地址格式不正确")
        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val response = client.newCall(
            Request.Builder()
                .url("$base/api/v2/auth/login")
                .header("Referer", base)
                .post(form)
                .build()
        ).await()
        val body = response.body?.string().orEmpty()
        val sid = response.headers("Set-Cookie")
            .firstOrNull { it.startsWith("SID=", ignoreCase = true) }
            ?.substringAfter("SID=")
            ?.substringBefore(';')
            .orEmpty()
        if (sid.isBlank()) {
            throw IOException(
                "qBittorrent 登录失败：${body.take(80).ifBlank { "HTTP ${response.code}" }}"
            )
        }
        if (body.contains("Fails", ignoreCase = true)) {
            throw IOException("qBittorrent 登录失败，请检查用户名与密码")
        }
        return sid
    }

    // endregion

    private fun Throwable.readableMessage(): String = when (this) {
        is IOException -> message ?: "网络请求失败"
        else -> message ?: this::class.java.simpleName
    }
}
