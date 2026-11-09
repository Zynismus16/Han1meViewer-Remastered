package com.yenaly.han1meviewer

import android.content.ComponentName
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.color.DynamicColors
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.database.database
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.ui.viewmodel.AppViewModel
import com.yenaly.han1meviewer.util.AnimeShaders
import com.yenaly.han1meviewer.util.TraceRecorder
import com.yenaly.han1meviewer.util.ThemeUtils
import com.yenaly.yenaly_libs.base.YenalyApplication
import com.yenaly.yenaly_libs.utils.LanguageHelper
import `is`.xyz.mpv.MPVLib
import java.net.ProxySelector

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/08 008 17:32
 */
class HanimeApplication : YenalyApplication() {

    companion object {
        const val TAG = "HanimeApplication"
    }

    /**
     * 已经在 [HInitializer] 中处理了
     */
    override val isDefaultCrashHandlerEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        TraceRecorder.init(this, BuildConfig.VERSION_SOURCE == "ci")
        TraceRecorder.record("app.onCreate")
        installCrashLogger()
        runCatching { ThemeUtils.applyDarkModeFromPreferences(this) }
            .onFailure { TraceRecorder.record("theme.err ${it.javaClass.simpleName}: ${it.message}") }
        if (Preferences.useDynamicColor){
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        runCatching {
            ProxySelector.setDefault(HProxySelector())
            HProxySelector.rebuildNetwork()
        }.onFailure { TraceRecorder.record("proxy.err ${it.message}") }
        initFirebase()
        runCatching { initNotificationChannel() }
            .onFailure { TraceRecorder.record("notif.err ${it.message}") }
        runCatching {
            MPVLib.create(applicationContext)
            MPVLib.init()
        }.onFailure { TraceRecorder.record("mpv.err ${it.javaClass.simpleName}: ${it.message}") }

        if (AnimeShaders.copyShaderAssets(applicationContext) <= 0) {
            Log.w(TAG, "Shader 复制失败")
        }
        if (AnimeShaders.copyCertAssets(applicationContext) <= 0) {
            Log.w(TAG, "cert 复制失败")
        }
        runCatching { switchLauncher(Preferences.fakeLauncherIcon) }
            .onFailure { TraceRecorder.record("launcher.err ${it.message}") }
        TraceRecorder.record("app.onCreate.done")
    }

    /**
     * 没有 adb 时的排障手段：把未捕获异常的堆栈写到「下载」目录，
     * 用户装完直接打开文件管理器就能把文件发回来。
     *
     * Android 10+ 走 MediaStore，落到公共下载目录；更低版本退回应用外部私有目录。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val text = buildString {
            appendLine("time: " + java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
            ).format(java.util.Date()))
            appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("thread: ${thread.name}")
            appendLine(Log.getStackTraceString(throwable))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "hanime-crash.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = getExternalFilesDir("crash") ?: filesDir
            dir.mkdirs()
            java.io.File(dir, "last-crash.txt").writeText(text)
        }
    }

    private fun initFirebase() {
        // 测试构建可能拿不到真实的 google-services.json（例如 fork 中的占位配置）。
        // 这类环境下 Firebase 初始化会抛异常，不应让整个应用起不来。
        runCatching { initFirebaseComponents() }
            .onFailure {
                Log.w(TAG, "Firebase 初始化失败，已跳过", it)
                TraceRecorder.record("firebase.err ${it.javaClass.simpleName}: ${it.message}")
            }
    }

    private fun initFirebaseComponents() {
        // 用于处理 Firebase Analytics 初始化
        Firebase.analytics.setAnalyticsCollectionEnabled(Preferences.isAnalyticsEnabled)
        // 用于处理 Firebase Crashlytics 初始化
        Firebase.crashlytics.apply {
            isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            setCustomKeys {
                key(
                    FirebaseConstants.APP_LANGUAGE,
                    LanguageHelper.preferredLanguage.toLanguageTag()
                )
                key(
                    FirebaseConstants.VERSION_SOURCE,
                    BuildConfig.VERSION_SOURCE
                )
            }
        }
        // 用于处理 Firebase Remote Config 初始化
        Firebase.remoteConfig.apply {
            setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3 * 60 * 60
                fetchTimeoutInSeconds = 10
            })
            setDefaultsAsync(FirebaseConstants.remoteConfigDefaults)
            fetchAndActivate().addOnCompleteListener {
                AppViewModel.getLatestVersion(delayMillis = 200)
            }
        }
        Firebase.database.setPersistenceEnabled(true)
    }

    private fun initNotificationChannel() {
        val nm = NotificationManagerCompat.from(this)

        val hanimeDownloadChannel = NotificationChannelCompat.Builder(
            DOWNLOAD_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("Hanime Download").build()
        nm.createNotificationChannel(hanimeDownloadChannel)

        val appUpdateChannel = NotificationChannelCompat.Builder(
            UPDATE_NOTIFICATION_CHANNEL,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName("App Update").build()
        nm.createNotificationChannel(appUpdateChannel)
    }
    fun switchLauncher(alias: String) {
        val pm = packageManager

        val allAliases = listOf(
            "com.yenaly.han1meviewer.LauncherAliasDefault",
            "com.yenaly.han1meviewer.LauncherFakeCalc",
            "com.yenaly.han1meviewer.LauncherFakeCornhub",
            "com.yenaly.han1meviewer.LauncherFakeXxt"
        )

        allAliases.forEach { a ->
            val state = if (a == alias)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            pm.setComponentEnabledSetting(
                ComponentName(this, a),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}