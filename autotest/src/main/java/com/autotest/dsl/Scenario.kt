package com.autotest.dsl

import com.autotest.intercept.InterceptorChain
import com.autotest.report.ReportCollector
import com.autotest.report.StepResult

class Scenario(
    val name: String,
    private val steps: List<Step>,
    private val collector: ReportCollector? = null,
    private val interceptors: InterceptorChain? = null
) {
    fun run() {
        steps.forEachIndexed { index, step ->
            val stepNumber = "${index + 1}"
            val start = System.currentTimeMillis()

            interceptors?.fireBeforeStep(stepNumber, step.name)

            try {
                step.run()
                val duration = System.currentTimeMillis() - start

                interceptors?.fireAfterStep(stepNumber, step.name, duration)
                collector?.addStepResult(
                    StepResult(
                        scenarioName = name,
                        stepName = step.name,
                        stepNumber = stepNumber,
                        durationMs = duration,
                        passed = true
                    )
                )
            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - start

                interceptors?.fireOnStepFailure(stepNumber, step.name, e)
                collector?.addStepResult(
                    StepResult(
                        scenarioName = name,
                        stepName = step.name,
                        stepNumber = stepNumber,
                        durationMs = duration,
                        passed = false,
                        error = e.message
                    )
                )
                throw e
            }
        }
    }
}

class ScenarioBuilder(private val name: String) {
    private val steps = mutableListOf<Step>()
    var collector: ReportCollector? = null
    var interceptors: InterceptorChain? = null

    /** 普通步骤 */
    fun step(name: String, action: () -> Unit) {
        steps.add(Step(name, action))
    }

    /** 条件步骤：condition 为 true 时才执行 */
    fun stepIf(condition: Boolean, name: String, action: () -> Unit) {
        if (condition) steps.add(Step(name, action))
    }

    /** 可重试步骤：失败后重试指定次数 */
    fun flakyStep(name: String, maxRetries: Int = 2, intervalMs: Long = 1000, action: () -> Unit) {
        steps.add(Step(name) {
            var lastError: Throwable? = null
            for (attempt in 0..maxRetries) {
                try {
                    action()
                    return@Step
                } catch (e: Throwable) {
                    lastError = e
                    if (attempt < maxRetries) Thread.sleep(intervalMs)
                }
            }
            throw lastError!!
        })
    }

    /** 带异常恢复的步骤：失败时执行 recovery 后重试一次 */
    fun stepWithRecovery(name: String, action: () -> Unit, recovery: (Throwable) -> Unit) {
        steps.add(Step(name) {
            try {
                action()
            } catch (e: Throwable) {
                recovery(e)
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

    fun build(): Scenario {
        return Scenario(name, steps.toList(), collector, interceptors)
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
