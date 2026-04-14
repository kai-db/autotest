package com.autotest.stability

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit Rule：测试失败时根据 RetryPolicy 自动重试。
 * FlakyClassifier 判定为 FLAKY 的才重试，HARD_FAIL 直接抛出。
 */
class RetryRunner(
    private val policy: RetryPolicy = RetryPolicy(),
    private val classifier: FlakyClassifier = FlakyClassifier
) : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                var lastError: Throwable? = null
                for (attempt in 0..policy.maxRetries) {
                    try {
                        base.evaluate()
                        return // 成功，直接返回
                    } catch (e: Throwable) {
                        lastError = e
                        val flakyType = classifier.classify(e.message)
                        if (flakyType == FlakyType.HARD_FAIL) {
                            throw e // 非 Flaky 错误，不重试
                        }
                        if (attempt < policy.maxRetries) {
                            Thread.sleep(policy.intervalMs)
                        }
                    }
                }
                throw lastError!!
            }
        }
    }
}
