package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestRunnerTest {

    private lateinit var runner: TestRunner
    private val log = mutableListOf<String>()

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) { log.add("D:$msg") }
        override fun i(tag: String, msg: String) { log.add("I:$msg") }
        override fun w(tag: String, msg: String) { log.add("W:$msg") }
        override fun e(tag: String, msg: String, throwable: Throwable?) { log.add("E:$msg") }
    }

    @Before
    fun setUp() {
        runner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        log.clear()
    }

    @Test
    fun runTest_passCase() {
        val result = runner.runTest("pass-test") {
            step("step1") { /* ok */ }
            step("step2") { /* ok */ }
        }

        assertTrue(result.passed)
        assertEquals("pass-test", result.name)
        assertTrue(result.durationMs >= 0)
        assertEquals(null, result.error)
    }

    @Test
    fun runTest_failCase() {
        val result = runner.runTest("fail-test") {
            step("step1") { /* ok */ }
            step("step2") { throw RuntimeException("boom") }
        }

        assertFalse(result.passed)
        assertEquals("boom", result.error)
    }

    @Test
    fun runTest_beforeAndAfterExecute() {
        val order = mutableListOf<String>()

        runner.runTest(
            "lifecycle-test",
            before = { order.add("before") },
            after = { order.add("after") }
        ) {
            step("main") { order.add("main") }
        }

        assertEquals(listOf("before", "main", "after"), order)
    }

    @Test
    fun runTest_afterExecutesOnFailure() {
        val order = mutableListOf<String>()

        runner.runTest(
            "after-on-fail",
            after = { order.add("after") }
        ) {
            step("boom") { throw RuntimeException("fail") }
        }

        assertTrue(order.contains("after"))
    }

    @Test
    fun getResults_accumulatesMultipleRuns() {
        runner.runTest("test1") { step("s") {} }
        runner.runTest("test2") { step("s") {} }

        assertEquals(2, runner.getResults().size)
    }

    @Test
    fun reset_clearsResults() {
        runner.runTest("test1") { step("s") {} }
        runner.reset()

        assertEquals(0, runner.getResults().size)
    }

    @Test
    fun toMarkdown_containsResults() {
        runner.runTest("TC-001 冷启动") { step("启动") {} }
        runner.runTest("TC-002 导航") {
            step("点击") { throw RuntimeException("找不到元素") }
        }

        val md = runner.toMarkdown()
        assertTrue(md.contains("TC-001"))
        assertTrue(md.contains("PASS"))
        assertTrue(md.contains("TC-002"))
        assertTrue(md.contains("FAIL"))
        assertTrue(md.contains("通过率"))
    }

    @Test
    fun generateReport_hasSummary() {
        runner.runTest("test1") {
            step("s1") {}
            step("s2") {}
        }

        val report = runner.generateReport()
        assertNotNull(report.summary)
        assertEquals(2, report.summary!!.totalSteps)
        assertEquals(2, report.summary!!.passedSteps)
    }
}
