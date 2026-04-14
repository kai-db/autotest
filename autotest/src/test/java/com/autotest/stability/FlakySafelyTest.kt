package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FlakySafelyTest {

    @Test
    fun flakySafely_returnsOnSuccess() {
        val result = flakySafely(timeoutMs = 1000) { 42 }
        assertEquals(42, result)
    }

    @Test
    fun flakySafely_retriesUntilSuccess() {
        var attempts = 0
        val result = flakySafely(timeoutMs = 5000, intervalMs = 50) {
            attempts++
            if (attempts < 3) throw AssertionError("not yet")
            "done"
        }
        assertEquals("done", result)
        assertTrue(attempts >= 3)
    }

    @Test
    fun flakySafely_throwsOnTimeout() {
        try {
            flakySafely(timeoutMs = 200, intervalMs = 50) {
                throw AssertionError("always fails")
            }
            fail("Should have thrown")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("flakySafely 超时"))
        }
    }

    @Test
    fun flakySafely_doesNotRetryDisallowedExceptions() {
        try {
            flakySafely(
                timeoutMs = 5000,
                allowedExceptions = setOf(AssertionError::class.java)
            ) {
                throw UnsupportedOperationException("not retryable")
            }
            fail("Should have thrown")
        } catch (e: UnsupportedOperationException) {
            assertEquals("not retryable", e.message)
        }
    }

    @Test
    fun flakySafely_customMessage() {
        try {
            flakySafely(
                timeoutMs = 100,
                intervalMs = 20,
                failureMessage = "自定义超时消息"
            ) {
                throw AssertionError("fail")
            }
            fail("Should have thrown")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("自定义超时消息"))
        }
    }
}
