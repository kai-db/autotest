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

/**
 * 默认日志器。
 *
 * **线程安全（Q9）**：`SimpleDateFormat` 非线程安全，用 [ThreadLocal] 每线程独立（无锁）；
 * 文件写入段 `synchronized(this)`。**假设：一个 DefaultTestLogger 实例对应一个日志文件**
 * （现用法=每测试一实例、文件名带秒级时间戳）；跨实例写同一文件不在设计目标内（需文件级锁，超范围）。
 */
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

    // ThreadLocal：SimpleDateFormat 非线程安全，每线程独立避免并发抛 ArrayIndexOutOfBounds / 时间戳错乱。
    // 用匿名 ThreadLocal 覆写 initialValue，而非 ThreadLocal.withInitial（后者是 API 26+，minSdk24 会 NoSuchMethodError）。
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

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

        val time = timeFormat.get().format(Date())
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
            // 写文件段同步：多线程共写同一文件时避免行交错（单实例/单文件假设见类 KDoc）
            try {
                synchronized(this) { logFile.appendText("$formatted\n") }
            } catch (e: Exception) {
                // 文件写入失败不影响测试；留痕到 logcat（不可再走本 logger，避免递归）
                Log.w("AutoTest", "日志文件写入失败: ${e.message}")
            }
        }
    }
}
