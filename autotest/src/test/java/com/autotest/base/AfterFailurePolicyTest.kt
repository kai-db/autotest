package com.autotest.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** E2：TestCase.after 失败的异常保真策略（不吞、不掩盖主失败根因） */
class AfterFailurePolicyTest {

    @Test
    fun `主流程成功时 after 异常作为主失败抛出`() {
        val afterError = IllegalStateException("after 挂了")
        assertSame(afterError, TestCase.resolveAfterFailure(primary = null, afterError = afterError))
    }

    @Test
    fun `主流程已失败时 after 异常附加为 suppressed 不另抛`() {
        val primary = AssertionError("步骤断言失败")
        val afterError = IllegalStateException("after 挂了")
        assertNull(TestCase.resolveAfterFailure(primary, afterError))
        assertEquals(listOf<Throwable>(afterError), primary.suppressed.toList())
    }

    @Test
    fun `多次 after 异常全部累积到主失败`() {
        val primary = AssertionError("根因")
        val e1 = RuntimeException("清理1")
        val e2 = RuntimeException("清理2")
        TestCase.resolveAfterFailure(primary, e1)
        TestCase.resolveAfterFailure(primary, e2)
        assertEquals(listOf<Throwable>(e1, e2), primary.suppressed.toList())
    }
}
