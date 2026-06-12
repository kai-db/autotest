package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MonitorModeTimeoutTest {

    private lateinit var suite: TestSuite
    private lateinit var monitor: MonitorMode

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Before
    fun setUp() {
        val runner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        suite = TestSuite("测试", runner, fakeLogger)
        monitor = MonitorMode(suite, fakeLogger, resultsDir = System.getProperty("java.io.tmpdir"))
    }

    @Test
    fun `卡死的轮次被超时强制收尾为 FAIL`() {
        suite.addTest("TC-001", "卡死用例") {
            step("永远不结束") { Thread.sleep(60_000) }
        }

        val round = monitor.runRound(timeoutMs = 300)

        assertFalse(round.allPassed)
        assertEquals("ROUND_TIMEOUT", round.results.single().name)
        assertTrue(round.results.single().error!!.contains("超时"))
    }

    @Test
    fun `正常轮次不受超时影响`() {
        suite.addTest("TC-001", "快用例") { step("s") {} }

        val round = monitor.runRound(timeoutMs = 10_000)
        assertTrue(round.allPassed)
    }

    @Test
    fun `timeoutMs 为 0 保持原有不限时行为`() {
        suite.addTest("TC-001", "快用例") { step("s") {} }
        assertTrue(monitor.runRound().allPassed)
    }
}
