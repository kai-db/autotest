package com.autotest.intercept

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
import com.autotest.log.TestLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logcat 收集拦截器。
 * 参考 Kaspresso DumpLogcatInterceptor。
 *
 * 步骤失败时自动收集设备 logcat 日志，保存到文件，关联到失败用例。
 * 可选：每个步骤结束后都收集（用于全量日志分析）。
 */
class LogcatInterceptor(
    private val logger: TestLogger,
    private val collectOnFailure: Boolean = true,
    private val collectOnSuccess: Boolean = false,
    private val logLines: Int = 300
) : Interceptor {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private val dateFormat = SimpleDateFormat("HHmmss", Locale.getDefault())
    private val logcatFiles = mutableMapOf<String, String>()

    override fun beforeStep(stepNumber: String, stepName: String) {
        // 每个步骤开始前清空 logcat，只收集本步骤的日志
        try {
            device.executeShellCommand("logcat -c")
        } catch (_: Throwable) {}
    }

    override fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {
        if (collectOnSuccess) {
            dump("step_${stepNumber}_pass", stepNumber)
        }
    }

    override fun onStepFailure(stepNumber: String, stepName: String, error: Throwable) {
        if (collectOnFailure) {
            dump("step_${stepNumber}_fail", stepNumber)
        }
    }

    fun getLogcatPath(stepNumber: String): String? = logcatFiles[stepNumber]

    fun getAllLogcats(): Map<String, String> = logcatFiles.toMap()

    private fun dump(name: String, stepNumber: String) {
        try {
            val dir = File(TestConfig.screenshotDir, "logcat")
            dir.mkdirs()
            val timestamp = dateFormat.format(Date())
            val file = File(dir, "${name}_$timestamp.txt")

            val logcat = device.executeShellCommand("logcat -d -t $logLines")
            file.writeText(logcat)

            logcatFiles[stepNumber] = file.absolutePath
            logger.d("Logcat", "日志已收集: ${file.absolutePath}")
        } catch (e: Throwable) {
            logger.e("Logcat", "日志收集失败: ${e.message}")
        }
    }
}
