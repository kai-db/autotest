package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Test

/** Q7：TestRunner 失败类型归类——envReset/before=INFRA、步骤=ASSERTION、成功=NONE。 */
class FailureKindTest {

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    private fun runner(envReset: (() -> Unit)? = null) =
        TestRunner(appPackage = "com.test", logger = fakeLogger, envReset = envReset)

    @Test
    fun `成功用例 failureKind 为 NONE`() {
        val r = runner().runTest("ok") { step("s") {} }
        assertEquals(FailureKind.NONE, r.failureKind)
    }

    @Test
    fun `envReset 抛错归类 INFRA`() {
        val r = runner(envReset = { throw IllegalStateException("设备没连上") })
            .runTest("env-fail") { step("s") {} }
        assertEquals(false, r.passed)
        assertEquals(FailureKind.INFRA, r.failureKind)
    }

    @Test
    fun `before 抛错归类 INFRA`() {
        val r = runner().runTest("before-fail", before = { throw IllegalStateException("前置失败") }) {
            step("s") {}
        }
        assertEquals(false, r.passed)
        assertEquals(FailureKind.INFRA, r.failureKind)
    }

    @Test
    fun `步骤断言失败归类 ASSERTION`() {
        val r = runner().runTest("step-fail") {
            step("断言") { throw AssertionError("元素不对") }
        }
        assertEquals(false, r.passed)
        assertEquals(FailureKind.ASSERTION, r.failureKind)
    }

    @Test
    fun `after 失败不覆盖成功用例（primary 优先，passed 不变）`() {
        val r = runner().runTest("after-fail", after = { throw RuntimeException("清理失败") }) {
            step("s") {}
        }
        // after 失败是 secondary：既有语义 catch+log 不改 passed，failureKind 保持 NONE
        assertEquals(true, r.passed)
        assertEquals(FailureKind.NONE, r.failureKind)
    }
}
