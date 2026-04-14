package com.autotest.report

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HtmlReporterTest {

    @Test
    fun generate_producesValidHtml() {
        val report = RunReport(
            appPackage = "com.test",
            startTime = 1000L,
            endTime = 5000L,
            steps = listOf(
                StepResult("场景A", "步骤1", "1", 100, true),
                StepResult("场景A", "步骤2", "2", 200, false, error = "找不到元素")
            ),
            failures = listOf(
                Failure("TestClass", "testMethod", "找不到元素")
            )
        )

        val file = File(System.getProperty("java.io.tmpdir"), "test_report.html")
        HtmlReporter.generate(report, file)

        assertTrue(file.exists())
        val html = file.readText()
        assertTrue(html.contains("AutoTest Report"))
        assertTrue(html.contains("com.test"))
        assertTrue(html.contains("步骤1"))
        assertTrue(html.contains("PASS"))
        assertTrue(html.contains("FAIL"))
        assertTrue(html.contains("找不到元素"))
        assertTrue(html.contains("50.0%"))

        file.delete()
    }

    @Test
    fun generate_emptyReport() {
        val report = RunReport(
            appPackage = "com.test",
            startTime = 0,
            endTime = 0
        )

        val file = File(System.getProperty("java.io.tmpdir"), "empty_report.html")
        HtmlReporter.generate(report, file)

        assertTrue(file.exists())
        assertTrue(file.readText().contains("AutoTest Report"))

        file.delete()
    }

    @Test
    fun generate_escapesHtml() {
        val report = RunReport(
            appPackage = "com.test",
            startTime = 0,
            endTime = 0,
            steps = listOf(
                StepResult("s", "<script>alert(1)</script>", "1", 0, true)
            )
        )

        val file = File(System.getProperty("java.io.tmpdir"), "escape_report.html")
        HtmlReporter.generate(report, file)

        val html = file.readText()
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(!html.contains("<script>alert"))

        file.delete()
    }
}
