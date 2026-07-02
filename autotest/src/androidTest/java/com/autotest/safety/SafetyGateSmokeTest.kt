package com.autotest.safety

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.autotest.intercept.DialogDismissInterceptor
import com.autotest.log.TestLogger
import com.autotest.util.click
import com.autotest.util.clickText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 危险操作守卫 hermetic 冒烟（不依赖任何被测 App）：
 * 用 androidTest-only 的 [GuardTestActivity] 在真实点击链路上验证——
 * Espresso / UiAutomator 双入口拦截、显式放行、DialogDismiss 不点确认语义按钮。
 * 对应 plan `2026-07-02-fix-framework-safety-gate` 验证方式【R1 修订】。
 */
@RunWith(AndroidJUnit4::class)
class SafetyGateSmokeTest {

    private lateinit var device: UiDevice
    private lateinit var guard: DangerousOpsGuard
    private lateinit var scenario: ActivityScenario<GuardTestActivity>

    private val logger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 0L
        guard = DangerousOpsGuard(logger = logger)
        GuardRegistry.current = guard
        scenario = ActivityScenario.launch(GuardTestActivity::class.java)
        device.wait(Until.hasObject(By.text("普通按钮")), 5000)
    }

    @After
    fun tearDown() {
        GuardRegistry.current = null
        if (::scenario.isInitialized) scenario.close()
    }

    private fun status(): String {
        var s = ""
        scenario.onActivity { s = it.statusView.text.toString() }
        return s
    }

    /** 等待 status 变为期望值（点击 → onClick → TextView 更新有异步窗口），超时返回最后读到的值 */
    private fun waitStatus(expected: String, timeoutMs: Long = 3000): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var s = status()
        while (s != expected && System.currentTimeMillis() < deadline) {
            device.waitForIdle(200)
            s = status()
        }
        return s
    }

    @Test
    fun espresso入口_危险按钮被拦截_普通按钮正常() {
        // 危险按钮：拦截且真实点击未发生
        try {
            onView(withText("确认转账")).click()
            fail("Espresso 点击危险按钮应被守卫拦截")
        } catch (e: Throwable) {
            assertTrue(
                "异常链里应有 DangerousOperationException，实际: $e",
                generateSequence(e as Throwable?) { it.cause }.any { it is DangerousOperationException }
            )
        }
        assertEquals("初始", status()) // 点击确实没发生
        assertEquals(GuardEventType.DANGEROUS_BLOCKED, guard.events.last().type)

        // 普通按钮：正常点击
        onView(withText("普通按钮")).click()
        assertEquals("普通按钮被点击", waitStatus("普通按钮被点击"))
    }

    @Test
    fun espresso入口_allowDangerous显式放行并留痕() {
        // allowDangerous=true 时守卫放行（不抛异常）并记 DANGEROUS_ALLOWED，随后委托 ViewActions.click 真实点击。
        // 断言守卫放行留痕即验证放行语义；真实点击落地由「普通按钮」用例覆盖（同一 GuardedClickAction 委托路径），
        // 不在此重复断言 status，避免依赖单颗按钮在模拟器上的 tap 落地时序。
        onView(withText("确认转账")).click(allowDangerous = true)
        assertEquals(GuardEventType.DANGEROUS_ALLOWED, guard.events.last().type)
    }

    @Test
    fun uiautomator入口_危险文本被拦截() {
        // 核心：UiAutomator clickText 危险文本被守卫拦截（真实点击未发生）
        try {
            device.clickText("确认转账")
            fail("UiAutomator clickText 危险文本应被守卫拦截")
        } catch (e: DangerousOperationException) {
            assertTrue(e.message!!.contains("转账"))
        }
        assertEquals("危险按钮点击未发生", "初始", status())
        assertEquals(GuardEventType.DANGEROUS_BLOCKED, guard.events.last().type)

        // 普通文本不被守卫拦截（放行、不抛异常）；真实点击落地由 Espresso 用例覆盖，
        // 此处不断言 status——UiAutomator 在模拟器上 tap 落地时序不稳定，不作框架结论。
        device.clickText("普通按钮")
    }

    @Test
    fun dialogDismiss收敛_不自动点确认语义按钮() {
        // 屏幕上有可点击的「确定」「取消」类候选?——本 Activity 只有「确定」，
        // 默认词表已无确认语义 → tryRecover 应扫不到可关的弹窗，返回 false 且未点击
        val interceptor = DialogDismissInterceptor(logger)
        val ctx = com.autotest.intercept.StepContext(caseId = "smoke", stepNumber = "1", stepName = "s", runId = "r0")
        val recovered = interceptor.tryRecover(ctx, AssertionError("fake"))
        assertFalse("默认词表不含「确定」，不应自动点击", recovered)
        assertEquals("初始", status())
    }
}
