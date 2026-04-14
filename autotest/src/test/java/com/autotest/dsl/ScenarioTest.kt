package com.autotest.dsl

import org.junit.Assert.assertEquals
import org.junit.Test

class ScenarioTest {
    @Test
    fun scenario_runsSteps() {
        val calls = mutableListOf<String>()

        val scenario = scenario("demo") {
            step("first") { calls.add("first") }
            step("second") { calls.add("second") }
        }

        scenario.run()

        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun scenario_runsWithoutCollector() {
        var count = 0
        val s = scenario("no-collector") {
            step("a") { count++ }
            step("b") { count++ }
        }
        s.run()
        assertEquals(2, count)
    }
}
