package com.autotest.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** E6：SLEEP payload 非法/缺失不再静默回退 1000ms——显式失败该步骤 */
class SleepPayloadTest {

    @Test
    fun `合法毫秒数正常解析`() {
        assertEquals(1500L, UiReplayExecutor.parseSleepMs("1500", "step"))
        assertEquals(0L, UiReplayExecutor.parseSleepMs("0", "step"))
    }

    @Test
    fun `缺失 payload 显式失败`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            UiReplayExecutor.parseSleepMs(null, "等待首页")
        }
        assertTrue(e.message!!.contains("等待首页"))
    }

    @Test
    fun `非数字 payload 显式失败`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            UiReplayExecutor.parseSleepMs("abc", "step")
        }
        assertTrue(e.message!!.contains("abc"))
    }

    @Test
    fun `负数 payload 显式失败`() {
        assertThrows(IllegalArgumentException::class.java) {
            UiReplayExecutor.parseSleepMs("-100", "step")
        }
    }
}
