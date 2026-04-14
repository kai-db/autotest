package com.autotest.dsl

class Scenario(
    val name: String,
    private val steps: List<Step>
) {
    fun run() {
        steps.forEach { it.run() }
    }
}

class ScenarioBuilder(private val name: String) {
    private val steps = mutableListOf<Step>()

    fun step(name: String, action: () -> Unit) {
        steps.add(Step(name, action))
    }

    fun build(): Scenario {
        return Scenario(name, steps.toList())
    }
}

fun scenario(name: String, block: ScenarioBuilder.() -> Unit): Scenario {
    val builder = ScenarioBuilder(name)
    builder.block()
    return builder.build()
}
