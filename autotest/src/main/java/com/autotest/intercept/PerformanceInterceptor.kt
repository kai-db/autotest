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

    /**
     * 耗时百分位（nearest-rank 法）。趋势比单值阈值更能暴露劣化：
     * P95 连续多轮上涨通常意味着真实的性能回归。
     * @param percentile 1-100
     */
    fun getPercentile(percentile: Int): Long {
        require(percentile in 1..100) { "percentile 必须在 1..100，当前: $percentile" }
        val sorted = stepDurations.values.sorted()
        if (sorted.isEmpty()) return 0
        val rank = Math.ceil(percentile / 100.0 * sorted.size).toInt()
        return sorted[rank - 1]
    }

    data class PerfSummary(val count: Int, val totalMs: Long, val p50Ms: Long, val p95Ms: Long, val maxMs: Long)

    fun getSummary(): PerfSummary = PerfSummary(
        count = stepDurations.size,
        totalMs = getTotalDuration(),
        p50Ms = getPercentile(50),
        p95Ms = getPercentile(95),
        maxMs = stepDurations.values.maxOrNull() ?: 0
    )
}
