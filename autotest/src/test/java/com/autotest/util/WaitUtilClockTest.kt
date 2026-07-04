package com.autotest.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** waitUntil 的超时判定走单调假钟（Q3） */
class WaitUtilClockTest {

    @After
    fun tearDown() {
        MonotonicTime.resetForTest()
    }

    @Test
    fun conditionTrueImmediately_neverReadsTimeout() {
        MonotonicTime.overrideSourceForTest { 0L }
        WaitUtil.waitUntil(timeoutMillis = 10, intervalMillis = 1) { true } // 不抛即过
    }

    @Test
    fun clockJump_conditionNeverTrue_throwsTimeout() {
        var now = 0L
        MonotonicTime.overrideSourceForTest { now.also { now += 500 } } // 每次读钟 +500ms
        try {
            WaitUtil.waitUntil(timeoutMillis = 100, intervalMillis = 1) { false }
            fail("应抛等待超时")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("等待超时"))
        }
    }

    @Test
    fun clockFrozen_conditionBecomesTrue_noFakeTimeout() {
        MonotonicTime.overrideSourceForTest { 1000L } // 冻结：窗口永不耗尽
        var polls = 0
        WaitUtil.waitUntil(timeoutMillis = 10, intervalMillis = 1) { ++polls >= 5 }
        assertEquals(5, polls)
    }
}
