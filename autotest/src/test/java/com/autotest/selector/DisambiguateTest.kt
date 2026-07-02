package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Test

/** C5：findBySnapshot 多命中消歧纯逻辑——无法唯一确定则 fail-closed（返回 -1，不点第一个）。 */
class DisambiguateTest {

    private fun snap(res: String = "", text: String = "", desc: String = "", bounds: String = "") =
        ElementSnapshot(text = text, resourceId = res, contentDesc = desc, bounds = bounds)

    @Test
    fun `候选唯一直接返回 0`() {
        val target = snap(res = "a", bounds = "[0,0][1,1]")
        assertEquals(0, SelfHealingLocator.disambiguate(listOf(snap(res = "a", bounds = "[9,9][9,9]")), target))
    }

    @Test
    fun `bounds 精确匹配且唯一时选它`() {
        val target = snap(res = "a", bounds = "[0,0][1,1]")
        val cands = listOf(
            snap(res = "a", bounds = "[5,5][6,6]"),
            snap(res = "a", bounds = "[0,0][1,1]"), // bounds 精确
            snap(res = "a", bounds = "[7,7][8,8]")
        )
        assertEquals(1, SelfHealingLocator.disambiguate(cands, target))
    }

    @Test
    fun `全属性收窄到唯一时选它`() {
        val target = snap(res = "a", text = "登录", bounds = "")
        val cands = listOf(
            snap(res = "a", text = "注册"),
            snap(res = "a", text = "登录") // res+text 全匹配唯一
        )
        assertEquals(1, SelfHealingLocator.disambiguate(cands, target))
    }

    @Test
    fun `多命中且无法唯一消歧时 fail-closed 返回 -1`() {
        val target = snap(res = "a", text = "项", bounds = "[0,0][1,1]")
        // 两个候选都 res=a text=项，bounds 都不等于 target → 无法唯一确定
        val cands = listOf(
            snap(res = "a", text = "项", bounds = "[2,2][3,3]"),
            snap(res = "a", text = "项", bounds = "[4,4][5,5]")
        )
        assertEquals("列表项场景应 fail-closed，不点第一个", -1, SelfHealingLocator.disambiguate(cands, target))
    }

    @Test
    fun `空候选返回 -1`() {
        assertEquals(-1, SelfHealingLocator.disambiguate(emptyList(), snap(res = "a")))
    }
}
