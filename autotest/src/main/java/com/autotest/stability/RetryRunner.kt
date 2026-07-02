package com.autotest.stability

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit Rule：测试失败时根据 RetryPolicy 自动重试。
 * FlakyClassifier 判定为 FLAKY 的才重试，HARD_FAIL 直接抛出。
 *
 * 可选自适应模式（借鉴 Marathon）：注入 [adaptivePolicy] 后，重试预算由该用例的
 * 历史通过率计算（稳定用例 0 预算、flaky 用例足额预算）；注入 [history] 后，
 * 每轮尝试的结果都会回写历史库（每轮是独立的统计样本），供下次 run 计算预算。
 */
class RetryRunner(
    private val policy: RetryPolicy = RetryPolicy(),
    private val classifier: FlakyClassifierApi = DefaultFlakyClassifier(),
    private val adaptivePolicy: AdaptiveRetryPolicy? = null,
    private val history: TestHistoryStore? = null
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val caseId = "${description.className}#${description.methodName}"
                // 与统一预算硬顶对齐：整方法级重试也不超过 MAX_TOTAL_ATTEMPTS-1，防三层重试叠乘
                val requested = adaptivePolicy?.retryBudget(caseId) ?: policy.maxRetries
                val maxRetries = requested.coerceAtMost(RetryBudget.MAX_TOTAL_ATTEMPTS - 1)
                val intervalMs = adaptivePolicy?.intervalMs ?: policy.intervalMs

                var lastError: Throwable? = null
                for (attempt in 0..maxRetries) {
                    try {
                        base.evaluate()
                        history?.record(caseId, true)
                        return // 成功，直接返回
                    } catch (e: Throwable) {
                        history?.record(caseId, false)
                        lastError = e
                        // 按 Throwable 类型优先判定：NPE 等真 bug 不因 message 措辞被误判 FLAKY 重试
                        val flakyType = classifier.classify(e)
                        if (flakyType == FlakyType.HARD_FAIL) {
                            throw e // 非 Flaky 错误，不重试
                        }
                        if (attempt < maxRetries) {
                            Thread.sleep(intervalMs)
                        }
                    }
                }
                throw lastError!!
            }
        }
    }
}
