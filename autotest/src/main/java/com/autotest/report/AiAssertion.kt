package com.autotest.report

/** AI 断言结果 */
enum class AiVerdict {
    PASS,
    FAIL,

    /** 未注入评估器（CI 确定性执行）或评估器异常时跳过——跳过必须留痕，不静默 */
    SKIPPED
}

/**
 * AI 自然语言断言记录（借鉴 Maestro assertWithAI）：
 * - 默认软断言（optional=true）：FAIL 只告警不挂测试，防 LLM 抖动打爆 CI
 * - 结果进独立报告通道，与确定性断言分开呈现
 * - 只用于确定性断言写不出来的场景（视觉状态/canvas/动态文案），是兜底而非主路径
 */
data class AiAssertion(
    /** 自然语言断言描述，如"当前页面应显示钱包余额" */
    val description: String,
    val verdict: AiVerdict,
    /** LLM 给出的解释或跳过原因 */
    val explanation: String = "",
    /** true=软断言（默认）；false=硬断言，FAIL 会抛错挂测试 */
    val optional: Boolean = true,
    val timestampMs: Long = 0
)
