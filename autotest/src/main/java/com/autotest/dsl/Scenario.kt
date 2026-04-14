package com.autotest.dsl

import com.autotest.report.ReportCollector
import com.autotest.report.StepResult

class Scenario(
    val name: String,
    private val steps: List<Step>,
    private val collector: ReportCollector? = null
) {
    fun run() {
        steps.forEach { step ->
            val start = System.currentTimeMillis()
            try {
                step.run()
                collector?.addStepResult(
                    StepResult(
                        scenarioName = name,
                        stepName = step.name,
                        durationMs = System.currentTimeMillis() - start,
                        passed = true
                    )
                )
            } catch (e: Throwable) {
                collector?.addStepResult(
                    StepResult(
                        scenarioName = name,
                        stepName = step.name,
                        durationMs = System.currentTimeMillis() - start,
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

    fun step(name: String, action: () -> Unit) {
        steps.add(Step(name, action))
    }

    fun build(): Scenario {
        return Scenario(name, steps.toList(), collector)
    }
}

fun scenario(
    name: String,
    collector: ReportCollector? = null,
    block: ScenarioBuilder.() -> Unit
): Scenario {
    val builder = ScenarioBuilder(name)
    builder.collector = collector
    builder.block()
    return builder.build()
}
