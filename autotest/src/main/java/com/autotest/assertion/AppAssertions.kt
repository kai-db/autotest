package com.autotest.assertion

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
import org.junit.Assert.assertTrue

/**
 * 通用断言封装。
 */
object AppAssertions {

    /** 断言 App 在前台 */
    fun assertInForeground(
        device: UiDevice,
        packageName: String = TestConfig.packageName
    ) {
        val current = device.currentPackageName
        assertTrue("App 不在前台, 当前: $current, 期望: $packageName", current == packageName)
    }

    /** 断言屏幕上存在指定文本 */
    fun assertTextVisible(device: UiDevice, text: String) {
        val found = device.findObject(By.textContains(text))
        assertTrue("屏幕上未找到文本: \"$text\"", found != null)
    }

    /** 断言屏幕上存在指定 resource-id */
    fun assertResIdVisible(device: UiDevice, resId: String) {
        val found = device.findObject(By.res(resId))
        assertTrue("屏幕上未找到资源 ID: \"$resId\"", found != null)
    }
}
