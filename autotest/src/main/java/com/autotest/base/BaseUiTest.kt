package com.autotest.base

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.config.TestConfig
import com.autotest.util.allowPermission
import com.autotest.util.waitForApp
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import java.io.File

/**
 * UI 自动化测试基类，整合 Espresso + UiAutomator。
 * 与具体项目零耦合，所有项目相关信息从 TestConfig 读取。
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    lateinit var device: UiDevice
    lateinit var context: Context

    val idlingResource = CountingIdlingResource("BaseUiTest")

    @Before
    open fun setUp() {
        TestConfig.init()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        IdlingRegistry.getInstance().register(idlingResource)
    }

    @After
    open fun tearDown() {
        IdlingRegistry.getInstance().unregister(idlingResource)
        Espresso.onIdle()
    }

    // ==================== App 生命周期 ====================

    /** 通过包名启动被测 App */
    fun launchApp(
        packageName: String = TestConfig.packageName,
        timeout: Long = TestConfig.launchTimeout
    ) {
        pressHome()
        sleep(300)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("找不到启动 Intent，请确认包名: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.waitForApp(packageName, timeout)
    }

    /** 启动 App 并自动处理权限弹窗 */
    fun launchAppAndDismissDialogs(
        packageName: String = TestConfig.packageName,
        timeout: Long = TestConfig.launchTimeout,
        settleTime: Long = 2000
    ) {
        launchApp(packageName, timeout)
        sleep(settleTime)
        device.allowPermission()
    }

    /** 启动指定 Activity */
    fun launchActivity(cls: Class<*>) {
        val intent = Intent(context, cls).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()
    }

    /** 验证当前前台 App 是否为目标包名 */
    fun assertAppInForeground(packageName: String = TestConfig.packageName) {
        val current = device.currentPackageName
        assert(current == packageName) { "App 不在前台, 当前: $current, 期望: $packageName" }
    }

    // ==================== 设备操作 ====================

    fun pressBack() = device.pressBack()
    fun pressHome() = device.pressHome()
    fun sleep(millis: Long = 1000) = Thread.sleep(millis)

    /** 截图保存 */
    fun takeScreenshot(name: String) {
        val dir = File(TestConfig.screenshotDir)
        dir.mkdirs()
        device.takeScreenshot(File(dir, "${name}.png"))
    }
}
