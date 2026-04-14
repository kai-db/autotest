package com.autotest.dsl

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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

    @Test
    fun scenario_stopsOnFailure() {
        val calls = mutableListOf<String>()

        val s = scenario("fail-test") {
            step("ok") { calls.add("ok") }
            step("boom") { throw RuntimeException("步骤失败") }
            step("should-not-run") { calls.add("nope") }
        }

        try {
            s.run()
            fail("Should have thrown")
        } catch (e: RuntimeException) {
            assertEquals("步骤失败", e.message)
        }

        assertEquals(listOf("ok"), calls)
    }
}
