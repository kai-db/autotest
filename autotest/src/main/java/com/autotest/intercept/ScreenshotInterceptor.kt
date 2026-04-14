package com.autotest.intercept

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
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
    private val screenshotOnSuccess: Boolean = false
) : Interceptor {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private val dateFormat = SimpleDateFormat("HHmmss", Locale.getDefault())

    /** 最近一次截图的路径，供外部读取写入报告 */
    var lastScreenshotPath: String? = null
        private set

    /** 所有截图路径，key = stepNumber */
    private val screenshotMap = mutableMapOf<String, String>()

    override fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {
        if (screenshotOnSuccess) {
            val path = take("step_${stepNumber}_pass")
            path?.let { screenshotMap[stepNumber] = it }
        }
    }

    override fun onStepFailure(stepNumber: String, stepName: String, error: Throwable) {
        if (screenshotOnFailure) {
            val path = take("step_${stepNumber}_fail")
            path?.let { screenshotMap[stepNumber] = it }
        }
    }

    override fun onActionFailure(actionName: String, error: Throwable) {
        if (screenshotOnFailure) {
            take("action_fail_${actionName.replace(" ", "_")}")
        }
    }

    fun getScreenshotPath(stepNumber: String): String? = screenshotMap[stepNumber]

    fun getAllScreenshots(): Map<String, String> = screenshotMap.toMap()

    private fun take(name: String): String? {
        return try {
            val dir = File(TestConfig.screenshotDir, "auto")
            dir.mkdirs()
            val timestamp = dateFormat.format(Date())
            val file = File(dir, "${name}_$timestamp.png")
            device.takeScreenshot(file)
            lastScreenshotPath = file.absolutePath
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
