package com.autotest.stability

import kotlin.math.ceil
import kotlin.math.ln

/**
 * 自适应重试预算（借鉴 Marathon 概率模型）：
 * 按用例历史通过率 p 计算达到目标成功率 T 所需的尝试次数 n（1-(1-p)^n ≥ T），
 * 代替"所有用例固定重试 N 次"——稳定用例不浪费重试，flaky 用例拿到足额预算。
 *
 * 边界语义：
 * - 无历史 → defaultRetries（冷启动按固定策略）
 * - p=1.0（从未失败）→ 0 次重试
 * - p=0.0（从未通过）→ maxRetries（大概率是真 bug，重试只为排除环境因素，封顶不浪费）
 */
class AdaptiveRetryPolicy(
    private val history: TestHistoryStore,
    /** 期望的用例最终成功率 */
    private val targetSuccessRate: Double = 0.95,
    /** 重试次数硬上限（防 flaky 用例吃掉整个 run 的时长） */
    private val maxRetries: Int = 3,
    /** 无历史数据时的默认重试次数 */
    private val defaultRetries: Int = 1,
    val intervalMs: Long = 1000
) {

    init {
        require(targetSuccessRate > 0.0 && targetSuccessRate < 1.0) {
            "targetSuccessRate 必须在 (0,1) 开区间，当前: $targetSuccessRate"
        }
        require(maxRetries >= 0) { "maxRetries 必须 >= 0" }
        require(defaultRetries in 0..maxRetries) { "defaultRetries 必须在 0..maxRetries" }
    }

    /** 计算某用例本轮的重试预算（不含首次执行） */
    fun retryBudget(caseId: String): Int {
        val p = history.passRate(caseId) ?: return defaultRetries
        if (p >= 1.0) return 0
        if (p <= 0.0) return maxRetries
        val attempts = ceil(ln(1 - targetSuccessRate) / ln(1 - p)).toInt()
        return (attempts - 1).coerceIn(0, maxRetries)
    }
}
