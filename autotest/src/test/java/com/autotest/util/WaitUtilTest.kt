package com.autotest.util

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WaitUtilTest {

    @Test
    fun waitUntil_throwsOnTimeout() {
        try {
            WaitUtil.waitUntil(timeoutMillis = 500, intervalMillis = 100) { false }
            fail("Should have thrown AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("超时"))
        }
    }

    @Test
    fun waitUntil_returnsImmediatelyWhenTrue() {
        val start = System.currentTimeMillis()
        WaitUtil.waitUntil(timeoutMillis = 5000, intervalMillis = 100) { true }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Should return immediately, took ${elapsed}ms", elapsed < 200)
    }

    @Test
    fun waitUntil_retriesUntilConditionMet() {
        var count = 0
        WaitUtil.waitUntil(timeoutMillis = 5000, intervalMillis = 50) {
            count++
            count >= 3
        }
        assertTrue("Should have retried at least 3 times, was $count", count >= 3)
    }
}
