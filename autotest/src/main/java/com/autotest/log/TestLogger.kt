package com.autotest.log

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface TestLogger {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}

class DefaultTestLogger(
    private val logToFile: Boolean = true,
    private val logToLogcat: Boolean = true,
    private val minLevel: LogLevel = LogLevel.DEBUG,
    logDir: String = "/sdcard/Pictures/autotest"
) : TestLogger {

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logFile: File by lazy {
        val dir = File(logDir)
        dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        File(dir, "test_$timestamp.log")
    }

    override fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    override fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    override fun w(tag: String, msg: String) = log(LogLevel.WARN, tag, msg)

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        log(LogLevel.ERROR, tag, msg)
        throwable?.let {
            log(LogLevel.ERROR, tag, it.stackTraceToString())
        }
    }

    private fun log(level: LogLevel, tag: String, msg: String) {
        if (level.ordinal < minLevel.ordinal) return

        val time = dateFormat.format(Date())
        val formatted = "[$time] ${level.name.first()} [$tag] $msg"

        if (logToLogcat) {
            when (level) {
                LogLevel.DEBUG -> Log.d("AutoTest", "[$tag] $msg")
                LogLevel.INFO -> Log.i("AutoTest", "[$tag] $msg")
                LogLevel.WARN -> Log.w("AutoTest", "[$tag] $msg")
                LogLevel.ERROR -> Log.e("AutoTest", "[$tag] $msg")
            }
        }

        if (logToFile) {
            try {
                logFile.appendText("$formatted\n")
            } catch (_: Exception) {
                // 文件写入失败不影响测试
            }
        }
    }
}
