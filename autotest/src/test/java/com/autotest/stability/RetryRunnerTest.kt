package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RetryRunnerTest {

    @Test
    fun retryRunner_retriesFlakyFailures() {
        var attempts = 0
        val statement = object : Statement() {
            override fun evaluate() {
                attempts++
                if (attempts < 3) {
                    throw AssertionError("Timeout waiting for element")
                }
            }
        }

        val runner = RetryRunner(
            policy = RetryPolicy(maxRetries = 3, intervalMs = 0)
        )
        val wrapped = runner.apply(statement, Description.EMPTY)
        wrapped.evaluate()

        assertEquals(3, attempts)
    }

    @Test
    fun retryRunner_doesNotRetryHardFail() {
        var attempts = 0
        val statement = object : Statement() {
            override fun evaluate() {
                attempts++
                throw AssertionError("Element not found: button_submit")
            }
        }

        val runner = RetryRunner(
            policy = RetryPolicy(maxRetries = 3, intervalMs = 0)
        )
        val wrapped = runner.apply(statement, Description.EMPTY)

        try {
            wrapped.evaluate()
            fail("Should have thrown")
        } catch (e: AssertionError) {
            assertEquals(1, attempts)
        }
    }

    @Test
    fun retryRunner_passesOnFirstTry() {
        var attempts = 0
        val statement = object : Statement() {
            override fun evaluate() {
                attempts++
            }
        }

        val runner = RetryRunner(policy = RetryPolicy(maxRetries = 2, intervalMs = 0))
        val wrapped = runner.apply(statement, Description.EMPTY)
        wrapped.evaluate()

        assertEquals(1, attempts)
    }
}
