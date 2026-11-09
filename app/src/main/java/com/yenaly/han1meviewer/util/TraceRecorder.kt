package com.yenaly.han1meviewer.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 无 adb 环境下的启动轨迹记录。
 *
 * 把 Application / Activity 的关键生命周期和初始化异常写到「下载」目录的
 * `hanime-trace.txt`，用户装完跑十几秒，直接把文件发回来即可定位问题。
 *
 * 只在测试包里启用（BuildConfig.VERSION_SOURCE == "ci"），正式包不会写文件。
 */
object TraceRecorder {

    private const val FILE_NAME = "hanime-trace.txt"
    private const val MAX_LINES = 80

    private var appContext: Context? = null
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context, enabled: Boolean) {
        appContext = if (enabled) context.applicationContext else null
    }

    @Synchronized
    fun record(tag: String) {
        val context = appContext ?: return
        lines.addLast("${timeFormat.format(Date())} $tag")
        while (lines.size > MAX_LINES) lines.removeFirst()
        flush(context)
    }

    @Synchronized
    private fun flush(context: Context) {
        val text = lines.joinToString("\n")
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val existing = resolver.query(
                    collection,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME}=?",
                    arrayOf(FILE_NAME),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        ContentUris.withAppendedId(collection, cursor.getLong(0))
                    } else {
                        null
                    }
                }
                if (existing != null) {
                    resolver.openOutputStream(existing, "wt")?.use { it.write(text.toByteArray()) }
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    }
                    resolver.insert(collection, values)?.let { uri ->
                        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    }
                }
            } else {
                val dir = context.getExternalFilesDir("trace") ?: context.filesDir
                dir.mkdirs()
                File(dir, FILE_NAME).writeText(text)
            }
        }
    }
}
