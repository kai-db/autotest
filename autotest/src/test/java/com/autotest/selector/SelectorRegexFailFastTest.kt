package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Q4：REGEX 选择器构造期校验（fail-fast）+ 预编译缓存复用 */
class SelectorRegexFailFastTest {

    private val snapshot = ElementSnapshot(
        text = "确认订单",
        resourceId = "com.demo:id/btn",
        contentDesc = "",
        className = "android.widget.Button"
    )

    @Test
    fun `非法正则在构造期抛 IllegalArgumentException 而非定位期`() {
        val e = assertThrows(IllegalArgumentException::class.java) { byTextRegex("[未闭合") }
        assertTrue("异常信息应含原始 pattern", e.message!!.contains("[未闭合"))
    }

    @Test
    fun `合法正则构造成功且预编译 pattern 可复用`() {
        val leaf = byTextRegex("确认.*")
        assertEquals("确认.*", leaf.pattern!!.pattern())
        assertTrue(leaf.matches(snapshot))
        assertFalse(leaf.matches(snapshot.copy(text = "取消")))
    }

    @Test
    fun `非 REGEX 模式不产生预编译 pattern`() {
        assertEquals(null, byText("确认订单").pattern)
        assertEquals(null, byTextContains("确认").pattern)
    }

    @Test
    fun `REGEX 的 key 不受预编译字段影响保持稳定`() {
        assertEquals(byTextRegex("确认.*").key(), byTextRegex("确认.*").key())
    }
}
