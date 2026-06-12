package com.autotest.report

import com.autotest.selector.LocatorEvent
import com.autotest.selector.LocatorLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiReportSectionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun baseReport() = RunReport(appPackage = "com.app", startTime = 0, endTime = 1000)

    @Test
    fun `ReportCollector 聚合 AI 断言与自愈事件`() {
        val collector = ReportCollector()
        collector.addAiAssertion(AiAssertion("页面应显示余额", AiVerdict.PASS))
        collector.healingEventsProvider = {
            listOf(LocatorEvent("text:exact:登录", LocatorLevel.HEALED, "text=立即登录", 0.85))
        }

        val report = collector.buildReport("com.app")
        assertTrue(report.aiAssertions.single().description == "页面应显示余额")
        assertTrue(report.healingEvents.single().level == LocatorLevel.HEALED)
    }

    @Test
    fun `HTML 报告包含 AI 断言独立 section`() {
        val report = baseReport().copy(
            aiAssertions = listOf(
                AiAssertion("页面应显示余额", AiVerdict.FAIL, "页面是加载态", optional = true),
                AiAssertion("无报错弹窗", AiVerdict.SKIPPED, "未注入评估器")
            )
        )
        val html = HtmlReporter.generate(report, tmp.newFile("r.html")).readText()

        assertTrue(html.contains("AI 断言"))
        assertTrue(html.contains("页面应显示余额"))
        assertTrue(html.contains("软断言"))
        assertTrue(html.contains("SKIPPED"))
    }

    @Test
    fun `HTML 报告包含定位自愈 section 并提示人工确认`() {
        val report = baseReport().copy(
            healingEvents = listOf(
                LocatorEvent("text:exact:登录", LocatorLevel.HEALED, "text=立即登录", 0.85),
                LocatorEvent("res:exact:btn", LocatorLevel.AI_FALLBACK, "text=确认")
            )
        )
        val html = HtmlReporter.generate(report, tmp.newFile("r.html")).readText()

        assertTrue(html.contains("定位自愈事件"))
        assertTrue(html.contains("HEALED"))
        assertTrue(html.contains("AI_FALLBACK"))
        assertTrue(html.contains("0.85"))
        assertTrue(html.contains("人工确认"))
    }

    @Test
    fun `无 AI 断言与自愈事件时不渲染对应 section（报告保持干净）`() {
        val html = HtmlReporter.generate(baseReport(), tmp.newFile("r.html")).readText()
        assertFalse(html.contains("AI 断言"))
        assertFalse(html.contains("定位自愈事件"))
    }
}
