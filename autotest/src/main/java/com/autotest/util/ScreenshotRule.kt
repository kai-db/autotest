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

    /** 留痕通道（E6）：由 BaseUiTest setUp 注入；未注入时降级 logcat */
    var logger: com.autotest.log.TestLogger? = null

    override fun failed(e: Throwable?, description: Description) {
        if (!TestConfig.screenshotOnFailure) return

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dir = File(TestConfig.screenshotDir, "failures")
        if (!dir.exists() && !dir.mkdirs()) {
            warn("失败截图目录创建失败，截图可能丢失: ${dir.absolutePath}")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${description.className}_${description.methodName}_$timestamp.png"
        val file = File(dir, fileName)
        // E6：takeScreenshot 返回 false = 失败证据静默丢失，必须留痕（证据链断裂要可见）
        if (!device.takeScreenshot(file)) {
            warn("失败截图保存失败（takeScreenshot=false）: ${file.absolutePath}")
        }
    }

    private fun warn(msg: String) {
        val l = logger
        if (l != null) l.w("Screenshot", msg) else android.util.Log.w("AutoTest", "[Screenshot] $msg")
    }
}
