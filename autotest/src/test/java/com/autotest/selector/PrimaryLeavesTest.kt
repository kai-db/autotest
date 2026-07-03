package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C3：同属性多条件 conjunction 驱动 BySelector 前的主 Leaf 决策。
 * BySelector 对同属性二次 set 抛 IllegalStateException——primaryLeaves 每属性只留首个 Leaf，
 * 被略过的条件由完整 conjunction 后置过滤兜底（不丢条件，见 deterministicFind）。
 */
class PrimaryLeavesTest {

    @Test
    fun `同属性多条件只保留首个驱动查找`() {
        val exact = byText("确认订单")
        val contains = byTextContains("订单")
        val primaries = SelfHealingLocator.primaryLeaves(listOf(exact, contains))
        assertEquals(listOf(exact), primaries)
    }

    @Test
    fun `跨属性条件全部保留`() {
        val text = byText("确认订单")
        val resId = byResId("com.demo:id/btn")
        val clazz = byClass("android.widget.Button")
        assertEquals(listOf(text, resId, clazz), SelfHealingLocator.primaryLeaves(listOf(text, resId, clazz)))
    }

    @Test
    fun `混合场景每属性各留首个且保序`() {
        val t1 = byText("确认")
        val r1 = byResId("id/a")
        val t2 = byTextContains("确")
        val r2 = byResIdContains("id")
        assertEquals(listOf(t1, r1), SelfHealingLocator.primaryLeaves(listOf(t1, r1, t2, r2)))
    }

    @Test
    fun `被略过的同属性条件仍由 conjunction 谓词后置兜底`() {
        // 语义回归钉子：主 Leaf 命中但完整条件不满足时，matches 必须为 false（deterministicFind 会滤掉该候选）
        val spec = byText("确认订单") and byTextContains("不存在的片段")
        val e = ElementSnapshot(text = "确认订单", resourceId = "", contentDesc = "", className = "")
        assertTrue(!spec.matches(e))
    }
}
