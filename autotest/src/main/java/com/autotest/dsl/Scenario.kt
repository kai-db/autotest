package com.autotest.dsl

import com.autotest.intercept.InterceptorChain
import com.autotest.intercept.StepContext
import com.autotest.report.ReportCollector
import com.autotest.report.StepResult
import com.autotest.stability.FlakyType
import com.autotest.stability.RetryBudget
import com.autotest.stability.StepRetryBudgetHolder
import com.autotest.stability.DefaultFlakyClassifier
import com.autotest.stability.FlakyClassifierApi
import com.autotest.util.MonotonicTime
import java.util.concurrent.atomic.AtomicLong

class Scenario(
    val name: String,
    private val steps: List<Step>,
    private val collector: ReportCollector? = null,
    private val interceptors: InterceptorChain? = null
) {
    fun run() {
        // runId 每次 run() 生成——同一 Scenario 对象多轮/Phase6/重跑各自不同，证据不复用（P0-8）
        val runId = RUN_SEQ.incrementAndGet().toString()
        steps.forEachIndexed { index, step ->
            var attempt = 0
            var ctx = StepContext(
                caseId = name,
                stepNumber = "${index + 1}",
                stepName = step.name,
                runId = runId,
                attempt = attempt
            )
            val start = MonotonicTime.nowMs()

            interceptors?.fireBeforeStep(ctx)

            // per-step 共享预算：flakyStep 与 behavior 重放都从它消费，统一封顶（D1）
            val budget = RetryBudget()
            budget.markFirstAttempt()
            StepRetryBudgetHolder.set(budget)
            try {
                interceptors?.runWithRecovery(
                    ctx,
                    step.retriable,
                    // 仅当共享预算尚有额度才放行重放并递增 attempt；耗尽返回 false，runWithRecovery 停止重放
                    onRetry = { if (budget.tryConsume()) { attempt++; true } else false },
                    action = { step.run() }
                ) ?: step.run()
                val duration = MonotonicTime.nowMs() - start
                ctx = ctx.copy(attempt = attempt)

                interceptors?.fireAfterStep(ctx, duration)
                collector?.addStepResult(
                    StepResult(
                        scenarioName = name,
                        stepName = step.name,
                        stepNumber = ctx.stepNumber,
                        durationMs = duration,
                        passed = true,
                        screenshotPath = interceptors?.stepScreenshotPath(ctx),
                        logcatPath = interceptors?.stepLogcatPath(ctx)
                    )
                )
            } catch (e: Throwable) {
                val duration = MonotonicTime.nowMs() - start
                ctx = ctx.copy(attempt = attempt)

                interceptors?.fireOnStepFailure(ctx, e)
                collector?.addStepResult(
                    StepResult(
                        scenarioName = name,
                        stepName = step.name,
                        stepNumber = ctx.stepNumber,
                        durationMs = duration,
                        passed = false,
                        error = e.message ?: e.toString(),
                        screenshotPath = interceptors?.stepScreenshotPath(ctx),
                        logcatPath = interceptors?.stepLogcatPath(ctx)
                    )
                )
                throw e
            } finally {
                StepRetryBudgetHolder.clear()
            }
        }
    }

    companion object {
        private val RUN_SEQ = AtomicLong(0)
    }
}

class ScenarioBuilder(private val name: String) {
    private val steps = mutableListOf<Step>()
    var collector: ReportCollector? = null
    var interceptors: InterceptorChain? = null

    /** 普通步骤（默认 retriable=false：behavior 恢复链不重放，副作用保护） */
    fun step(name: String, retriable: Boolean = false, action: () -> Unit) {
        steps.add(Step(name, retriable, action))
    }

    /** 可被 behavior 恢复链重放的步骤（只读/幂等步骤专用：dismiss 挡路弹窗后重试） */
    fun retriableStep(name: String, action: () -> Unit) {
        steps.add(Step(name, retriable = true, action))
    }

    /** 条件步骤：condition 为 true 时才执行 */
    fun stepIf(condition: Boolean, name: String, action: () -> Unit) {
        if (condition) steps.add(Step(name, action = action))
    }

    /**
     * 可重试步骤：失败后按 [classifier] 判定重试。
     * 只重试 FLAKY（时序/未就绪）；HARD_FAIL（NPE/AssertionError 等真 bug）直接抛，不再重试到耗尽（D1）。
     * 重试次数受 [maxRetries] 与统一预算 [RetryBudget.MAX_TOTAL_ATTEMPTS] 双重封顶（防三层叠乘）。
     */
    fun flakyStep(
        name: String,
        maxRetries: Int = 2,
        intervalMs: Long = 1000,
        classifier: FlakyClassifierApi = DEFAULT_CLASSIFIER,
        action: () -> Unit
    ) {
        steps.add(Step(name) {
            // 优先用步骤级共享预算（与 behavior 重放共享封顶，D1）；无共享上下文时自建局部预算
            val budget = StepRetryBudgetHolder.current() ?: RetryBudget().also { it.markFirstAttempt() }
            var localRetries = 0
            var lastError: Throwable
            try {
                action()
                return@Step
            } catch (e: Throwable) {
                lastError = e
            }
            while (true) {
                if (classifier.classify(lastError) == FlakyType.HARD_FAIL) throw lastError
                if (localRetries >= maxRetries) throw lastError       // 本步自身重试上限
                if (!budget.tryConsume()) throw lastError             // 共享预算硬顶（三层不叠乘）
                localRetries++
                Thread.sleep(intervalMs)
                try {
                    action()
                    return@Step
                } catch (e: Throwable) {
                    lastError = e
                }
            }
        })
    }

    /** 带异常恢复的步骤：失败时执行 recovery 后重试一次；recovery 自身抛错不吞掉原始失败 */
    fun stepWithRecovery(name: String, action: () -> Unit, recovery: (Throwable) -> Unit) {
        steps.add(Step(name) {
            try {
                action()
            } catch (e: Throwable) {
                try {
                    recovery(e)
                } catch (re: Throwable) {
                    re.addSuppressed(e) // 不丢原始失败：排障时既能看到恢复错、也能看到原始错
                    throw re
                }
                action() // 恢复后重试一次
            }
        })
    }

    /** 循环步骤 */
    fun repeat(times: Int, name: String, action: (Int) -> Unit) {
        for (i in 0 until times) {
            steps.add(Step("$name (${i + 1}/$times)") { action(i) })
        }
    }

    /**
     * 数据驱动步骤：数据集 × 同一操作展开为多个独立步骤，
     * 每条数据单独计步/计时/留证据，失败时能直接看出挂在哪条数据上。
     *
     * ```
     * forEach(listOf("BTC", "ETH", "USDT"), "搜索代币") { symbol ->
     *     searchToken(symbol)
     * }
     * ```
     */
    fun <T> forEach(data: Iterable<T>, name: String, action: (T) -> Unit) {
        data.forEachIndexed { i, item ->
            steps.add(Step("$name [${i + 1}: $item]") { action(item) })
        }
    }

    fun build(): Scenario {
        return Scenario(name, steps.toList(), collector, interceptors)
    }

    companion object {
        private val DEFAULT_CLASSIFIER = DefaultFlakyClassifier()
    }
}

fun scenario(
    name: String,
    collector: ReportCollector? = null,
    interceptors: InterceptorChain? = null,
    block: ScenarioBuilder.() -> Unit
): Scenario {
    val builder = ScenarioBuilder(name)
    builder.collector = collector
    builder.interceptors = interceptors
    builder.block()
    return builder.build()
}
