package com.autotest.assertion

import com.autotest.log.TestLogger
import com.autotest.report.AiAssertion
import com.autotest.report.AiVerdict
import com.autotest.report.ReportCollector

/** AI 断言评估结果 */
data class AiEvaluation(val passed: Boolean, val explanation: String = "")

/**
 * AI 断言评估器接口：框架只定义接口不实现 LLM 调用。
 * AI 在环时（Claude Code + mobile-mcp）由外部注入实现；CI 回归不注入，
 * 断言自动记为 SKIPPED（留痕呈现在报告，不静默）。
 */
interface AiAssertionEvaluator {
    /**
     * 评估自然语言断言是否成立。
     * @param assertion 断言描述
     * @param screenshotPath 当前屏幕截图路径（可为 null）
     */
    fun evaluate(assertion: String, screenshotPath: String?): AiEvaluation
}

/**
 * AI 软断言执行器（借鉴 Maestro assertWithAI 的 optional 设计）：
 * - optional=true（默认）：FAIL/评估异常只告警 + 记录，不挂测试
 * - optional=false：FAIL 或评估异常抛 AssertionError（硬断言不允许静默降级）
 * - 无评估器：记 SKIPPED + 告警（CI 场景的正常路径）
 *
 * 所有结果都写入 ReportCollector 的独立 AI 断言通道。
 */
class AiAsserter(
    private val evaluator: AiAssertionEvaluator? = null,
    private val collector: ReportCollector? = null,
    private val logger: TestLogger? = null,
    /** 断言时提供当前屏幕截图（如 BaseUiTest 现场截一张）；null 则不带图评估 */
    private val screenshotProvider: (() -> String?)? = null
) {

    fun assertWithAi(description: String, optional: Boolean = true) {
        val now = System.currentTimeMillis()

        if (evaluator == null) {
            record(AiAssertion(description, AiVerdict.SKIPPED, "未注入 AiAssertionEvaluator（确定性执行模式），断言未评估", optional, now))
            logger?.let {
                val msg = "AI 断言跳过（无评估器）: $description"
                if (optional) it.i(TAG, msg) else it.w(TAG, "$msg —— 这是硬断言，建议固化为确定性断言")
            }
            return
        }

        val evaluation = try {
            evaluator.evaluate(description, screenshotProvider?.invoke())
        } catch (e: Throwable) {
            record(AiAssertion(description, AiVerdict.SKIPPED, "评估器异常: ${e.message}", optional, now))
            logger?.w(TAG, "AI 断言评估器异常: $description — ${e.message}")
            if (!optional) {
                throw AssertionError("AI 硬断言评估失败（评估器异常）: $description — ${e.message}", e)
            }
            return
        }

        val verdict = if (evaluation.passed) AiVerdict.PASS else AiVerdict.FAIL
        record(AiAssertion(description, verdict, evaluation.explanation, optional, now))

        if (!evaluation.passed) {
            logger?.w(TAG, "AI 断言失败${if (optional) "（软断言，不挂测试）" else ""}: $description — ${evaluation.explanation}")
            if (!optional) {
                throw AssertionError("AI 断言失败: $description — ${evaluation.explanation}")
            }
        }
    }

    private fun record(assertion: AiAssertion) {
        collector?.addAiAssertion(assertion)
    }

    private companion object {
        const val TAG = "AiAssert"
    }
}
