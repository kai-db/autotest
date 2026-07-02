package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D1：统一重试预算——三层重试共享的总尝试次数硬顶。 */
class RetryBudgetTest {

    @Test
    fun `预算耗尽后 tryConsume 返回 false`() {
        val b = RetryBudget(maxTotalAttempts = 3)
        b.markFirstAttempt()          // consumed=1（首次）
        assertTrue(b.tryConsume())    // consumed=2
        assertTrue(b.tryConsume())    // consumed=3
        assertFalse(b.tryConsume())   // 到顶
        assertEquals(0, b.remaining)
    }

    @Test
    fun `markFirstAttempt 幂等，不重复占额度`() {
        val b = RetryBudget(maxTotalAttempts = 2)
        b.markFirstAttempt()
        b.markFirstAttempt()
        assertEquals(1, b.consumed)
        assertTrue(b.tryConsume())
        assertFalse(b.tryConsume())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `上限小于 1 拒绝`() {
        RetryBudget(maxTotalAttempts = 0)
    }

    @Test
    fun `默认硬顶为 MAX_TOTAL_ATTEMPTS`() {
        assertEquals(5, RetryBudget.MAX_TOTAL_ATTEMPTS)
        val b = RetryBudget()
        b.markFirstAttempt()
        var extra = 0
        while (b.tryConsume()) extra++
        assertEquals(4, extra) // 首次 + 4 次重试 = 5 总尝试
    }
}
