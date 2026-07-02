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
    /**
     * 日志目录。**null（默认）= 走 [com.autotest.config.TestConfig.screenshotDir] 单一来源**
     * （targetContext 私有外部目录，可写）。不再硬编码 `/sdcard/Pictures/autotest`（scoped storage 必死）。
     * lazy 取值，避免类构造即触发 TestConfig。
     */
    private val logDir: String? = null
) : TestLogger {

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logFile: File by lazy {
        val dir = File(logDir ?: com.autotest.config.TestConfig.screenshotDir)
        if (!dir.exists() && !dir.mkdirs()) {
            // mkdirs 失败（如公共目录 scoped-storage 拒写）：留痕到 logcat，别静默每条日志写失败刷屏
            Log.w("AutoTest", "日志目录创建失败，后续文件日志可能写不进: ${dir.absolutePath}")
        }
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
            } catch (e: Exception) {
                // 文件写入失败不影响测试；留痕到 logcat（不可再走本 logger，避免递归）
                Log.w("AutoTest", "日志文件写入失败: ${e.message}")
            }
        }
    }
}
