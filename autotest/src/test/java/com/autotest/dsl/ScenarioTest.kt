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
}
