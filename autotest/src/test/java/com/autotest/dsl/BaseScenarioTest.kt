package com.autotest.dsl

import org.junit.Assert.assertEquals
import org.junit.Test

class BaseScenarioTest {

    class FakeScenario(private val log: MutableList<String>) : BaseScenario("fake") {
        override fun ScenarioBuilder.steps() {
            step("步骤A") { log.add("A") }
            step("步骤B") { log.add("B") }
        }
    }

    @Test
    fun include_injectsSteps() {
        val log = mutableListOf<String>()
        val s = scenario("主流程") {
            step("前置") { log.add("before") }
            include(FakeScenario(log))
            step("后续") { log.add("after") }
        }
        s.run()
        assertEquals(listOf("before", "A", "B", "after"), log)
    }

    @Test
    fun include_multipleScenarios() {
        val log = mutableListOf<String>()

        class ScenarioX(private val l: MutableList<String>) : BaseScenario("X") {
            override fun ScenarioBuilder.steps() { step("x") { l.add("X") } }
        }
        class ScenarioY(private val l: MutableList<String>) : BaseScenario("Y") {
            override fun ScenarioBuilder.steps() { step("y") { l.add("Y") } }
        }

        val s = scenario("组合") {
            include(ScenarioX(log))
            include(ScenarioY(log))
        }
        s.run()
        assertEquals(listOf("X", "Y"), log)
    }
}
