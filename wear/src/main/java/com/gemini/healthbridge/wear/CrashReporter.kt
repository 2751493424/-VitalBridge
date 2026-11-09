package com.gemini.healthbridge.wear

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录器：未捕获异常写入 filesDir/last_crash.txt，下次启动首页显示，
 * 便于用户拍屏反馈真实崩溃堆栈（三星 Sensor SDK 的原生崩溃 Java 层接不住，但 Java 崩溃都能记到）。
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val time = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())
                File(appContext.filesDir, FILE).writeText(
                    "$time  Thread:${t.name}\n${e.stackTraceToString().take(3000)}"
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun last(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
