package com.autotest.dsl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScenarioTest {

    @Test
    fun scenario_runsSteps() {
        val calls = mutableListOf<String>()
        val s = scenario("demo") {
            step("first") { calls.add("first") }
            step("second") { calls.add("second") }
        }
        s.run()
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

    @Test
    fun stepIf_skipsWhenFalse() {
        val calls = mutableListOf<String>()
        val s = scenario("cond") {
            step("always") { calls.add("always") }
            stepIf(false, "skipped") { calls.add("skipped") }
            stepIf(true, "included") { calls.add("included") }
        }
        s.run()
        assertEquals(listOf("always", "included"), calls)
    }

    @Test
    fun flakyStep_retriesOnFailure() {
        var attempts = 0
        val s = scenario("flaky") {
            flakyStep("retry-me", maxRetries = 2, intervalMs = 0) {
                attempts++
                if (attempts < 3) throw RuntimeException("not yet")
            }
        }
        s.run()
        assertEquals(3, attempts)
    }

    @Test
    fun stepWithRecovery_recoversAndRetries() {
        var mainAttempts = 0
        var recovered = false
        val s = scenario("recovery") {
            stepWithRecovery(
                "recoverable",
                action = {
                    mainAttempts++
                    if (mainAttempts == 1) throw RuntimeException("first fail")
                },
                recovery = { recovered = true }
            )
        }
        s.run()
        assertEquals(2, mainAttempts)
        assertTrue(recovered)
    }

    @Test
    fun repeat_executesNTimes() {
        val indices = mutableListOf<Int>()
        val s = scenario("loop") {
            repeat(3, "iteration") { i -> indices.add(i) }
        }
        s.run()
        assertEquals(listOf(0, 1, 2), indices)
    }
}
