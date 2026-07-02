package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** P0-6：协作式取消——shouldStop 在用例间停下；InterruptedException 向上传播不被当普通 FAIL 吞。 */
class TestSuiteCancelTest {

    private lateinit var runner: TestRunner
    private lateinit var suite: TestSuite
    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Before
    fun setUp() {
        runner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        suite = TestSuite("测试", runner, fakeLogger)
    }

    @Test
    fun `shouldStop 在用例间停下，已跑结果照常返回`() {
        val ran = mutableListOf<String>()
        repeat(5) { i -> suite.addTest("TC-00$i", "用例$i") { step("s") { ran.add("$i") } } }

        // 跑满 2 条后请求停止
        val results = suite.runAll(shouldStop = { ran.size >= 2 })
        assertEquals("停止后不再跑后续用例", 2, results.size)
        assertTrue(results.all { it.passed })
    }

    @Test
    fun `用例抛 InterruptedException 时向上传播，不被当普通 FAIL 继续`() {
        suite.addTest("TC-001", "被中断") { step("hang") { throw InterruptedException("cancel") } }
        suite.addTest("TC-002", "后续用例") { step("s") {} }

        try {
            suite.runAll()
            fail("InterruptedException 应向上传播，而非当普通 FAIL 后继续跑 TC-002")
        } catch (e: InterruptedException) {
            // 传播成功
        } finally {
            Thread.interrupted() // 清中断位，避免污染后续测试
        }
    }
}
