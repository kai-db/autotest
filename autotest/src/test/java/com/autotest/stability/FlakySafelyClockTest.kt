package com.autotest.stability

import com.autotest.util.MonotonicTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * flakySafely 的重试窗口判定走单调假钟（Q3）：
 * 墙钟跳变不再导致「瞬间假超时」或「永不超时」。
 */
class FlakySafelyClockTest {

    @After
    fun tearDown() {
        MonotonicTime.resetForTest()
    }

    @Test
    fun clockFrozen_doesNotFakeTimeout_retriesUntilSuccess() {
        // 假钟冻结（模拟：墙钟即便回拨也不影响单调源）→ 窗口永不耗尽，按失败次数重试到成功
        MonotonicTime.overrideSourceForTest { 1000L }
        var attempts = 0
        val result = flakySafely(timeoutMs = 50, intervalMs = 1) {
            attempts++
            if (attempts < 4) throw AssertionError("not ready")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(4, attempts)
    }

    @Test
    fun clockJumpsPastTimeout_goesToFinalAttemptThenFails() {
        // 假钟一步跳过窗口（模拟前跳）→ 循环体不进入，走「最后一次尝试」后抛 AssertionError
        var now = 0L
        MonotonicTime.overrideSourceForTest { now.also { now += 100 } } // 每次读钟 +100ms，首查即超 50ms 窗口
        var attempts = 0
        try {
            flakySafely(timeoutMs = 50, intervalMs = 1) {
                attempts++
                throw AssertionError("never ready")
            }
            fail("应抛 AssertionError")
        } catch (e: AssertionError) {
            assertEquals("超时窗口耗尽后只走最后一次尝试", 1, attempts)
        }
    }
}
