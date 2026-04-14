package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MonitorModeTest {

    private lateinit var runner: TestRunner
    private lateinit var suite: TestSuite
    private lateinit var monitor: MonitorMode
    private val tempDir = System.getProperty("java.io.tmpdir")

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
        monitor = MonitorMode(suite, fakeLogger, resultsDir = tempDir)
    }

    @Test
    fun runRound_allPass() {
        suite.addTest("TC-001", "通过A") { step("s") {} }
        suite.addTest("TC-002", "通过B") { step("s") {} }

        val round = monitor.runRound()
        assertTrue(round.allPassed)
        assertEquals(2, round.passed)
        assertEquals(0, round.failed)
        assertEquals(1, round.round)
    }

    @Test
    fun runRound_withFailures() {
        suite.addTest("TC-001", "通过") { step("s") {} }
        suite.addTest("TC-002", "失败") {
            step("s") { throw RuntimeException("error") }
        }

        val round = monitor.runRound()
        assertFalse(round.allPassed)
        assertEquals(1, round.passed)
        assertEquals(1, round.failed)
    }

    @Test
    fun multipleRounds_accumulate() {
        suite.addTest("TC-001", "通过") { step("s") {} }

        monitor.runRound()
        monitor.runRound()

        assertEquals(2, monitor.getRounds().size)
        assertEquals(1, monitor.getRounds()[0].round)
        assertEquals(2, monitor.getRounds()[1].round)
    }

    @Test
    fun writeResults_createsFile() {
        suite.addTest("TC-001", "通过") { step("s") {} }
        monitor.runRound()

        val file = monitor.writeResults()
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("TC-001"))
        assertTrue(content.contains("PASS"))

        file.delete() // cleanup
    }

    @Test
    fun toMarkdown_containsAllRounds() {
        suite.addTest("TC-001", "通过") { step("s") {} }
        monitor.runRound()
        monitor.runRound()

        val md = monitor.toMarkdown()
        assertTrue(md.contains("第 1 轮"))
        assertTrue(md.contains("第 2 轮"))
    }

    @Test
    fun finalVerification_passesWhenAllPass() {
        suite.addTest("TC-001", "通过") { step("s") {} }

        val result = monitor.runFinalVerification()
        assertTrue(result)
    }

    @Test
    fun reset_clearsRounds() {
        suite.addTest("TC-001", "通过") { step("s") {} }
        monitor.runRound()
        monitor.reset()

        assertEquals(0, monitor.getRounds().size)
    }

    @Test
    fun recordFix_appearsInMarkdown() {
        suite.addTest("TC-001", "失败") {
            step("s") { throw RuntimeException("err") }
        }
        monitor.runRound()
        monitor.recordFix("TC-001", "选择器失效", "更新 resource-id")

        val md = monitor.toMarkdown()
        assertTrue(md.contains("修复记录"))
        assertTrue(md.contains("TC-001"))
        assertTrue(md.contains("选择器失效"))
        assertTrue(md.contains("更新 resource-id"))
    }

    @Test
    fun getLastFailures_returnsFailedCases() {
        suite.addTest("TC-001", "通过") { step("s") {} }
        suite.addTest("TC-002", "失败") {
            step("s") { throw RuntimeException("err") }
        }
        monitor.runRound()

        val failures = monitor.getLastFailures()
        assertEquals(1, failures.size)
        assertEquals("TC-002 失败", failures[0].name)
    }
}
