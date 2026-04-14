package com.autotest.intercept

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 截图拦截器：步骤失败时自动截图。
 * 可选：步骤成功后也截图（用于建立基线）。
 */
class ScreenshotInterceptor(
    private val screenshotOnFailure: Boolean = true,
    private val screenshotOnSuccess: Boolean = false
) : Interceptor {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private val dateFormat = SimpleDateFormat("HHmmss", Locale.getDefault())

    override fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {
        if (screenshotOnSuccess) {
            take("step_${stepNumber}_pass")
        }
    }

    override fun onStepFailure(stepNumber: String, stepName: String, error: Throwable) {
        if (screenshotOnFailure) {
            take("step_${stepNumber}_fail")
        }
    }

    override fun onActionFailure(actionName: String, error: Throwable) {
        if (screenshotOnFailure) {
            take("action_fail_${actionName.replace(" ", "_")}")
        }
    }

    private fun take(name: String) {
        val dir = File(TestConfig.screenshotDir, "auto")
        dir.mkdirs()
        val timestamp = dateFormat.format(Date())
        device.takeScreenshot(File(dir, "${name}_$timestamp.png"))
    }
}
