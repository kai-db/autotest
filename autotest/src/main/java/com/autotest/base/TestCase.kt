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

        var primary: Throwable? = null
        try {
            before?.invoke(this)

            val s = scenario(name, reportCollector, interceptors, steps)
            s.run()
        } catch (e: Throwable) {
            primary = e
            throw e
        } finally {
            try {
                after?.invoke(this)
            } catch (e: Throwable) {
                logger.e("TestCase", "after 执行失败: ${e.message}")
                resolveAfterFailure(primary, e)?.let { throw it }
            }
        }
    }

    companion object {
        /**
         * E2 异常保真：after 失败不再静默吞掉（脏状态会传染后续用例），同时不掩盖主失败根因。
         * - 主流程已失败：after 异常 addSuppressed 附加到主失败（正在传播中），返回 null 不另抛
         * - 主流程成功：after 异常就是测试失败，返回它由调用方抛出（对齐 JUnit @After 语义）
         */
        internal fun resolveAfterFailure(primary: Throwable?, afterError: Throwable): Throwable? =
            if (primary != null) {
                primary.addSuppressed(afterError)
                null
            } else {
                afterError
            }
    }
}
