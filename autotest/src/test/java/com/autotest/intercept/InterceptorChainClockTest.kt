package com.autotest.intercept

import com.autotest.util.MonotonicTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** intercept 的动作耗时走单调假钟（Q3）：假钟步进 → durationMs 精确等于步进值 */
class InterceptorChainClockTest {

    @After
    fun tearDown() {
        MonotonicTime.resetForTest()
    }

    @Test
    fun intercept_durationEqualsFakeClockStep() {
        var now = 100L
        MonotonicTime.overrideSourceForTest { now }
        val durations = mutableListOf<Long>()
        val chain = InterceptorChain()
        chain.add(object : Interceptor {
            override fun afterAction(actionName: String, durationMs: Long) {
                durations.add(durationMs)
            }
        })

        chain.intercept("click") { now += 42 } // 动作内假钟前进 42ms

        assertEquals(listOf(42L), durations)
    }
}
