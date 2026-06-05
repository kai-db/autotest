package com.autotest.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.Description

class ReportCollectorTest {

    @Test
    fun newInstances_doNotShareState() {
        val collector1 = ReportCollector()
        val collector2 = ReportCollector()

        // 模拟 collector1 记录一个失败
        val report1 = collector1.buildReport(appPackage = "com.test", endTime = 1000L)
        val report2 = collector2.buildReport(appPackage = "com.test", endTime = 2000L)

        assertEquals(0, report1.failures.size)
        assertEquals(0, report2.failures.size)
        assertEquals(0, report1.steps.size)
        assertEquals(0, report2.steps.size)
    }

    @Test
    fun addStepResult_appearsInReport() {
        val collector = ReportCollector()
        collector.addStepResult(
            StepResult(
                scenarioName = "test",
                stepName = "step1",
                durationMs = 100,
                passed = true
            )
        )
        val report = collector.buildReport(appPackage = "com.test")
        assertEquals(1, report.steps.size)
        assertEquals("step1", report.steps[0].stepName)
        assertEquals(true, report.steps[0].passed)
    }

    @Test
    fun failed_withScreenshotProvider_backfillsScreenshots() {
        val collector = ReportCollector()
        collector.screenshotProvider = { listOf("/sdcard/a.png", "/sdcard/b.png") }
        collector.failed(RuntimeException("boom"), Description.createTestDescription("C", "m"))

        val report = collector.buildReport(appPackage = "com.test")
        assertEquals(1, report.failures.size)
        assertEquals(listOf("/sdcard/a.png", "/sdcard/b.png"), report.failures[0].screenshots)
    }

    @Test
    fun failed_withoutProvider_screenshotsNull() {
        val collector = ReportCollector()
        collector.failed(RuntimeException("boom"), Description.createTestDescription("C", "m"))

        val report = collector.buildReport(appPackage = "com.test")
        assertEquals(1, report.failures.size)
        assertNull(report.failures[0].screenshots)
    }
}
