package com.autotest.base

import com.autotest.dsl.ScenarioBuilder
import com.autotest.dsl.scenario
import com.autotest.intercept.InterceptorChain
import com.autotest.log.TestLogger
import com.autotest.report.ReportCollector

/**
 * 声明式测试用例基类。
 * 参考 Kaspresso TestCase 设计，提供 before/run/after 四段式 API。
 *
 * 用法（比直接继承 BaseUiTest 更简洁）：
 * ```
 * class LoginTest : TestCase() {
 *
 *     @Test
 *     fun testLogin() = execute(
 *         before = {
 *             launchAppAndDismissDialogs()
 *         },
 *         after = {
 *             pressHome()
 *         }
 *     ) {
 *         step("进入登录页") {
 *             device.clickText("登录")
 *         }
 *         step("输入账号") {
 *             viewById(R.id.phone).typeText("138...")
 *         }
 *         step("验证成功") {
 *             AppAssertions.assertTextVisible(device, "首页")
 *         }
 *     }
 * }
 * ```
 */
abstract class TestCase : BaseUiTest() {

    /**
     * 执行测试用例（声明式 API）。
     *
     * @param testName 测试名称（默认取方法名）
     * @param before 前置操作（在步骤之前执行）
     * @param after 后置操作（在步骤之后执行，无论成功失败）
     * @param steps DSL 步骤定义
     */
    fun execute(
        testName: String = "",
        before: (BaseUiTest.() -> Unit)? = null,
        after: (BaseUiTest.() -> Unit)? = null,
        steps: ScenarioBuilder.() -> Unit
    ) {
        val name = testName.ifEmpty { Thread.currentThread().stackTrace[2].methodName }

        logger.i("TestCase", "═══ $name ═══")

        try {
            before?.invoke(this)

            val s = scenario(name, reportCollector, interceptors, steps)
            s.run()
        } finally {
            try {
                after?.invoke(this)
            } catch (e: Throwable) {
                logger.e("TestCase", "after 执行失败: ${e.message}")
            }
        }
    }
}
