package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToSelectorSpecTest {

    @Test
    fun `多属性组合为 Or（任一属性变化仍可命中）`() {
        val snap = ElementSnapshot(text = "登录", resourceId = "com.app:id/btn", contentDesc = "login")
        val spec = snap.toSelectorSpec()!!

        assertTrue(spec is SelectorSpec.Or)
        // res-id 优先
        assertEquals(byResId("com.app:id/btn"), (spec as SelectorSpec.Or).children[0])
        // 文案改版后 res-id 仍命中；res-id 重构后文案仍命中
        assertTrue(spec.matches(ElementSnapshot(resourceId = "com.app:id/btn", text = "立即登录")))
        assertTrue(spec.matches(ElementSnapshot(resourceId = "com.app:id/new_btn", text = "登录")))
    }

    @Test
    fun `单属性直接返回叶子`() {
        assertEquals(byText("登录"), ElementSnapshot(text = "登录").toSelectorSpec())
        assertEquals(byResId("a:id/b"), ElementSnapshot(resourceId = "a:id/b").toSelectorSpec())
    }

    @Test
    fun `无可用属性返回 null（纯图形元素）`() {
        assertNull(ElementSnapshot(className = "android.view.View", bounds = "[0,0][10,10]").toSelectorSpec())
    }
}
