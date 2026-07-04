package com.autotest.dsl

import com.autotest.report.ReportCollector
import com.autotest.util.MonotonicTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** step 耗时走单调假钟（Q3）：假钟步进 → StepResult.durationMs 精确等于步进值 */
class ScenarioClockTest {

    @After
    fun tearDown() {
        MonotonicTime.resetForTest()
    }

    @Test
    fun stepDuration_equalsFakeClockStep() {
        var now = 1000L
        MonotonicTime.overrideSourceForTest { now }
        val collector = ReportCollector()

        scenario("clock", collector) {
            step("advance") { now += 250 } // 步骤内假钟前进 250ms
        }.run()

        val steps = collector.buildReport("com.test").steps
        assertEquals(1, steps.size)
        assertEquals(250L, steps[0].durationMs)
    }

    @Test
    fun failedStepDuration_equalsFakeClockStep() {
        var now = 0L
        MonotonicTime.overrideSourceForTest { now }
        val collector = ReportCollector()

        try {
            scenario("clock-fail", collector) {
                step("boom") { now += 30; throw AssertionError("boom") }
            }.run()
        } catch (expected: AssertionError) {
            // 失败路径同样记精确时长
        }

        val steps = collector.buildReport("com.test").steps
        assertEquals(1, steps.size)
        assertEquals(30L, steps[0].durationMs)
        assertEquals(false, steps[0].passed)
    }
}
