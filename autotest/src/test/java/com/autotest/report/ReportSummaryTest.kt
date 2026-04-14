package com.autotest.report

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportSummaryTest {

    @Test
    fun summary_calculatesCorrectly() {
        val steps = listOf(
            StepResult("s", "a", "1", 100, true),
            StepResult("s", "b", "2", 200, true),
            StepResult("s", "c", "3", 300, false, error = "fail")
        )

        val summary = ReportSummary.from(steps)

        assertEquals(3, summary.totalSteps)
        assertEquals(2, summary.passedSteps)
        assertEquals(1, summary.failedSteps)
        assertEquals(600L, summary.totalDurationMs)
        assertEquals(66.66f, summary.passRate, 1f)
    }

    @Test
    fun summary_emptySteps() {
        val summary = ReportSummary.from(emptyList())
        assertEquals(0, summary.totalSteps)
        assertEquals(0f, summary.passRate, 0.01f)
    }

    @Test
    fun summary_allPass() {
        val steps = listOf(
            StepResult("s", "a", "1", 50, true),
            StepResult("s", "b", "2", 50, true)
        )
        val summary = ReportSummary.from(steps)
        assertEquals(100f, summary.passRate, 0.01f)
    }
}
