package com.autotest.intercept

/**
 * 步骤执行上下文：给拦截器足够信息把证据/统计按「用例 + 步骤 + 执行次 + 尝试次」隔离，
 * 消除跨用例串味（P0-8）。
 *
 * 两种 key（由拦截器按用途选用）：
 * - 证据唯一 key [evidenceKey]：`runId#caseId#stepNumber#attempt`——每次执行唯一，绝不覆盖历史证据。
 * - 性能聚合逻辑 key [logicalKey]：`caseId#stepNumber`——同一 case+step 的多次样本聚合，用于百分位趋势。
 *
 * @param runId 单次 Scenario 执行的进程内唯一号（同名 scenario 多轮/Phase6/重跑各自不同）
 * @param attempt 同一步骤的第几次尝试（0 起；flakyStep/behavior 重放时递增）
 */
data class StepContext(
    val caseId: String,
    val stepNumber: String,
    val stepName: String,
    val runId: String,
    val attempt: Int = 0
) {
    val evidenceKey: String get() = "$runId#$caseId#$stepNumber#$attempt"
    val logicalKey: String get() = "$caseId#$stepNumber"

    /**
     * 文件名安全的唯一片段：证据落盘文件名必须带它，否则同秒/同 stepNumber/多 case 的文件会互相覆盖
     * （map key 唯一挡不住底层文件路径碰撞）。非 [A-Za-z0-9._-] 一律替换为 `_`。
     */
    val fileSafeKey: String get() = evidenceKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
