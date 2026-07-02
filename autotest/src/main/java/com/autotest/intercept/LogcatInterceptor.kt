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
    /** key = StepContext.evidenceKey（跨用例/多轮/attempt 唯一，不覆盖） */
    private val logcatFiles = mutableMapOf<String, String>()

    override fun beforeStep(ctx: StepContext) {
        // 每个步骤开始前清空 logcat，只收集本步骤的日志
        // （logcat -c 抹掉步骤前 crash 痕迹的问题属审计 D3，留后续批次处理，本批仅改 key 隔离）
        try {
            device.executeShellCommand("logcat -c")
        } catch (e: Throwable) {
            logger.w("Logcat", "清空 logcat 失败: ${e.message}")
        }
    }

    override fun afterStep(ctx: StepContext, durationMs: Long) {
        if (collectOnSuccess) {
            // 文件名带 fileSafeKey：同秒/同 stepNumber/多 case 不会覆盖底层文件（P0-8）
            dump("step_${ctx.fileSafeKey}_pass", ctx.evidenceKey)
        }
    }

    override fun onStepFailure(ctx: StepContext, error: Throwable) {
        if (collectOnFailure) {
            dump("step_${ctx.fileSafeKey}_fail", ctx.evidenceKey)
        }
    }

    fun getLogcatPath(ctx: StepContext): String? = logcatFiles[ctx.evidenceKey]

    fun getAllLogcats(): Map<String, String> = logcatFiles.toMap()

    private fun dump(name: String, evidenceKey: String) {
        try {
            val dir = File(TestConfig.screenshotDir, "logcat")
            dir.mkdirs()
            val timestamp = dateFormat.format(Date())
            val file = File(dir, "${name}_$timestamp.txt")

            val logcat = device.executeShellCommand("logcat -d -t $logLines")
            file.writeText(logcat)

            logcatFiles[evidenceKey] = file.absolutePath
            logger.d("Logcat", "日志已收集: ${file.absolutePath}")
        } catch (e: Throwable) {
            logger.e("Logcat", "日志收集失败: ${e.message}")
        }
    }
}
