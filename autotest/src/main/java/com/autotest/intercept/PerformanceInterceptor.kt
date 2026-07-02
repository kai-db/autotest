package com.autotest.intercept

import com.autotest.log.TestLogger

/**
 * 性能拦截器：记录每个步骤的耗时，超过阈值时警告。
 *
 * 按逻辑 key（caseId#stepNumber）**聚合多样本**——同一 case+step 跨轮/重试的多次耗时都保留，
 * 百分位基于全样本（P0-8：拦截器实例跨用例复用时不再互相覆盖、也不丢样本）。
 */
class PerformanceInterceptor(
    private val logger: TestLogger,
    private val warnThresholdMs: Long = 5000
) : Interceptor {

    /** 逻辑 key(caseId#step) -> 该 step 的全部耗时样本 */
    private val stepSamples = linkedMapOf<String, MutableList<Long>>()

    override fun afterStep(ctx: StepContext, durationMs: Long) {
        stepSamples.getOrPut(ctx.logicalKey) { mutableListOf() }.add(durationMs)
        if (durationMs > warnThresholdMs) {
            logger.w("Perf", "步骤 [${ctx.stepNumber}] ${ctx.stepName} 耗时 ${durationMs}ms 超过阈值 ${warnThresholdMs}ms")
        }
    }

    /** 每个逻辑 step 的最近一次耗时（兼容旧接口语义：单值视图取该 step 最后样本） */
    fun getStepDurations(): Map<String, Long> = stepSamples.mapValues { it.value.last() }

    /** 全部样本展平 */
    private fun allSamples(): List<Long> = stepSamples.values.flatten()

    fun getTotalDuration(): Long = allSamples().sum()

    /**
     * 耗时百分位（nearest-rank 法），基于**全部样本**。趋势比单值阈值更能暴露劣化。
     * @param percentile 1-100
     */
    fun getPercentile(percentile: Int): Long {
        require(percentile in 1..100) { "percentile 必须在 1..100，当前: $percentile" }
        val sorted = allSamples().sorted()
        if (sorted.isEmpty()) return 0
        val rank = Math.ceil(percentile / 100.0 * sorted.size).toInt()
        return sorted[rank - 1]
    }

    data class PerfSummary(val count: Int, val totalMs: Long, val p50Ms: Long, val p95Ms: Long, val maxMs: Long)

    fun getSummary(): PerfSummary {
        val samples = allSamples()
        return PerfSummary(
            count = samples.size,
            totalMs = samples.sum(),
            p50Ms = getPercentile(50),
            p95Ms = getPercentile(95),
            maxMs = samples.maxOrNull() ?: 0
        )
    }
}
