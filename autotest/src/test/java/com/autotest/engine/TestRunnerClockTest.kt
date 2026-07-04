package com.autotest.engine

import com.autotest.log.TestLogger
import com.autotest.util.MonotonicTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用例耗时走单调假钟（Q3）：假钟只在步骤内前进 → durationMs 精确等于步进值 */
class TestRunnerClockTest {

    private val silentLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @After
    fun tearDown() {
        MonotonicTime.resetForTest()
    }

    @Test
    fun caseDuration_equalsFakeClockStep() {
        var now = 5000L
        MonotonicTime.overrideSourceForTest { now }
        val runner = TestRunner(appPackage = "com.test", logger = silentLogger)

        val result = runner.runTest("clock-case") {
            step("advance") { now += 77 } // 步骤内假钟前进 77ms
        }

        assertTrue(result.passed)
        assertEquals(77L, result.durationMs)
    }
}
