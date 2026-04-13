package com.autotest.util

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 测试失败时自动截图。
 * 截图目录由 TestConfig.screenshotDir 控制。
 */
class ScreenshotRule : TestWatcher() {

    override fun failed(e: Throwable?, description: Description) {
        if (!TestConfig.screenshotOnFailure) return

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dir = File(TestConfig.screenshotDir, "failures")
        dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${description.className}_${description.methodName}_$timestamp.png"
        device.takeScreenshot(File(dir, fileName))
    }
}
