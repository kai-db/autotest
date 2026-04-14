package com.autotest.intercept

import com.autotest.log.TestLogger

/**
 * 性能拦截器：记录每个步骤的耗时，超过阈值时警告。
 */
class PerformanceInterceptor(
    private val logger: TestLogger,
    private val warnThresholdMs: Long = 5000
) : Interceptor {

    private val stepDurations = mutableMapOf<String, Long>()

    override fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {
        stepDurations["$stepNumber:$stepName"] = durationMs
        if (durationMs > warnThresholdMs) {
            logger.w("Perf", "步骤 [$stepNumber] $stepName 耗时 ${durationMs}ms 超过阈值 ${warnThresholdMs}ms")
        }
    }

    fun getStepDurations(): Map<String, Long> = stepDurations.toMap()

    fun getTotalDuration(): Long = stepDurations.values.sum()
}
