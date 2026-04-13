package com.autotest.action

import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
import com.autotest.util.clickText
import com.autotest.util.waitForText

/**
 * 通用 App 操作封装。
 * 换项目时只需改 test-config.properties 里的配置。
 */
object AppActions {

    /** 遍历底部 Tab 并执行回调 */
    fun switchBottomTabs(
        device: UiDevice,
        tabs: List<String> = TestConfig.bottomTabs,
        onTab: ((String) -> Unit)? = null
    ) {
        for (tab in tabs) {
            device.clickText(tab)
            Thread.sleep(1000)
            onTab?.invoke(tab)
        }
    }

    /** 等待引导页/闪屏消失 */
    fun waitForSplashDismiss(device: UiDevice, timeout: Long = 10000) {
        val skipTexts = listOf("跳过", "Skip", "进入", "Enter")
        for (text in skipTexts) {
            if (device.waitForText(text, 2000)) {
                device.clickText(text)
                return
            }
        }
        // 没找到跳过按钮，等待超时自动消失
        Thread.sleep(timeout.coerceAtMost(5000))
    }
}
