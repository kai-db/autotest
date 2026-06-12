package com.autotest.assertion

import com.autotest.report.AiVerdict
import com.autotest.report.ReportCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiAsserterTest {

    private val collector = ReportCollector()

    private fun recorded() = collector.buildReport("com.app").aiAssertions

    private class FixedEvaluator(private val result: AiEvaluation) : AiAssertionEvaluator {
        override fun evaluate(assertion: String, screenshotPath: String?) = result
    }

    private class ThrowingEvaluator : AiAssertionEvaluator {
        override fun evaluate(assertion: String, screenshotPath: String?): AiEvaluation =
            throw RuntimeException("LLM 服务不可用")
    }

    @Test
    fun `无评估器时记 SKIPPED 不抛错（CI 确定性执行）`() {
        AiAsserter(evaluator = null, collector = collector).assertWithAi("页面应显示余额")

        val a = recorded().single()
        assertEquals(AiVerdict.SKIPPED, a.verdict)
        assertTrue(a.explanation.contains("未注入"))
    }

    @Test
    fun `断言通过记 PASS`() {
        AiAsserter(FixedEvaluator(AiEvaluation(true, "余额可见")), collector)
            .assertWithAi("页面应显示余额")

        val a = recorded().single()
        assertEquals(AiVerdict.PASS, a.verdict)
        assertEquals("余额可见", a.explanation)
    }

    @Test
    fun `软断言失败只记录不挂测试（Maestro optional 语义）`() {
        AiAsserter(FixedEvaluator(AiEvaluation(false, "页面是加载态")), collector)
            .assertWithAi("页面应显示余额") // optional 默认 true，不应抛

        val a = recorded().single()
        assertEquals(AiVerdict.FAIL, a.verdict)
        assertTrue(a.optional)
    }

    @Test
    fun `硬断言失败抛 AssertionError`() {
        try {
            AiAsserter(FixedEvaluator(AiEvaluation(false, "余额未显示")), collector)
                .assertWithAi("页面应显示余额", optional = false)
            fail("应抛出 AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("余额未显示"))
        }
        assertEquals(AiVerdict.FAIL, recorded().single().verdict) // 失败也要先记录
    }

    @Test
    fun `评估器异常时软断言记 SKIPPED 不挂测试`() {
        AiAsserter(ThrowingEvaluator(), collector).assertWithAi("页面应显示余额")

        val a = recorded().single()
        assertEquals(AiVerdict.SKIPPED, a.verdict)
        assertTrue(a.explanation.contains("LLM 服务不可用"))
    }

    @Test
    fun `评估器异常时硬断言抛错（不允许静默降级）`() {
        try {
            AiAsserter(ThrowingEvaluator(), collector).assertWithAi("页面应显示余额", optional = false)
            fail("应抛出 AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("评估器异常"))
        }
    }

    @Test
    fun `截图路径传递给评估器`() {
        var received: String? = null
        val evaluator = object : AiAssertionEvaluator {
            override fun evaluate(assertion: String, screenshotPath: String?): AiEvaluation {
                received = screenshotPath
                return AiEvaluation(true)
            }
        }
        AiAsserter(evaluator, collector, screenshotProvider = { "/sdcard/shot.png" })
            .assertWithAi("断言")
        assertEquals("/sdcard/shot.png", received)
    }
}
