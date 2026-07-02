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
 * 截图拦截器：步骤失败时自动截图，并记录截图路径。
 * 铁律8：验证必须有截图证据。
 */
class ScreenshotInterceptor(
    private val screenshotOnFailure: Boolean = true,
    private val screenshotOnSuccess: Boolean = false,
    private val logger: TestLogger? = null,
    /** 截图缩放（0-1]：证据截图不需要原始分辨率，0.5 可省约 75% 存储 */
    private val scale: Float = 0.5f,
    /** PNG 压缩质量 1-100 */
    private val quality: Int = 80
) : Interceptor {

    init {
        require(scale > 0f && scale <= 1f) { "scale 必须在 (0,1]，当前: $scale" }
        require(quality in 1..100) { "quality 必须在 1..100，当前: $quality" }
    }

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private val dateFormat = SimpleDateFormat("HHmmss", Locale.getDefault())

    /** 最近一次截图的路径，供外部读取写入报告 */
    var lastScreenshotPath: String? = null
        private set

    /** 所有截图路径，key = StepContext.evidenceKey（跨用例/多轮/attempt 唯一，不覆盖） */
    private val screenshotMap = mutableMapOf<String, String>()

    override fun afterStep(ctx: StepContext, durationMs: Long) {
        if (screenshotOnSuccess) {
            // 文件名带 fileSafeKey：同秒/同 stepNumber/多 case 不会覆盖底层文件（P0-8）
            val path = take("step_${ctx.fileSafeKey}_pass")
            path?.let { screenshotMap[ctx.evidenceKey] = it }
        }
    }

    override fun onStepFailure(ctx: StepContext, error: Throwable) {
        if (screenshotOnFailure) {
            val path = take("step_${ctx.fileSafeKey}_fail")
            path?.let { screenshotMap[ctx.evidenceKey] = it }
        }
    }

    override fun onActionFailure(actionName: String, error: Throwable) {
        if (screenshotOnFailure) {
            take("action_fail_${actionName.replace(" ", "_")}")
        }
    }

    fun getScreenshotPath(ctx: StepContext): String? = screenshotMap[ctx.evidenceKey]

    fun getAllScreenshots(): Map<String, String> = screenshotMap.toMap()

    private fun take(name: String): String? {
        return try {
            val dir = File(TestConfig.screenshotDir, "auto")
            dir.mkdirs()
            val timestamp = dateFormat.format(Date())
            val file = File(dir, "${name}_$timestamp.png")
            device.takeScreenshot(file, scale, quality)
            lastScreenshotPath = file.absolutePath
            file.absolutePath
        } catch (e: Throwable) {
            logger?.e("Screenshot", "截图失败: $name", e)
            null
        }
    }
}
