package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Q7：MonitorMode.toMarkdown 失败行标注 [INFRA]/[ASSERTION]，环境失败与用例失败一眼可分。 */
class MonitorModeFailureKindMarkdownTest {

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
        val runner = TestRunner(appPackage = "com.test", logger = fakeLogger,
            envReset = null)
        suite = TestSuite("测试", runner, fakeLogger)
        monitor = MonitorMode(suite, fakeLogger, resultsDir = System.getProperty("java.io.tmpdir"))
    }

    @Test
    fun `ASSERTION 失败行标注 ASSERTION`() {
        suite.addTest("TC-A", "断言失败") { step("s") { throw AssertionError("元素不对") } }
        monitor.runRound()
        val md = monitor.toMarkdown()
        assertTrue("断言失败应标 [ASSERTION]", md.contains("[ASSERTION]"))
    }

    @Test
    fun `INFRA 失败行标注 INFRA`() {
        val runner = TestRunner(appPackage = "com.test", logger = fakeLogger,
            envReset = { throw IllegalStateException("设备没连") })
        val s = TestSuite("infra", runner, fakeLogger).apply {
            addTest("TC-I", "环境失败") { step("s") {} }
        }
        val m = MonitorMode(s, fakeLogger, resultsDir = System.getProperty("java.io.tmpdir"))
        m.runRound()
        val md = m.toMarkdown()
        assertTrue("环境失败应标 [INFRA]", md.contains("[INFRA]"))
    }

    @Test
    fun `全 PASS 不出现失败标注`() {
        suite.addTest("TC-OK", "通过") { step("s") {} }
        monitor.runRound()
        val md = monitor.toMarkdown()
        assertTrue(!md.contains("[INFRA]") && !md.contains("[ASSERTION]"))
    }
}
