package com.autotest.safety

import com.autotest.selector.ElementSnapshot
import com.autotest.selector.toSelectorSpec
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住关键不变量（回应 impl-review R1 Important）：
 * 「不可审计目标」（text/res-id/desc 全空）等价于「无法产生选择器」——
 * 因此缓存回放对这类 target 在 toSelectorSpec()==null 处 fail-fast（比守卫更早一道闸），
 * 不会出现「点击了但守卫没看到」的漏洞。守卫的 UNVERIFIABLE 分支则覆盖「已定位到、但实时快照不可审计」的元素。
 */
class UnverifiableInvariantTest {

    @Test
    fun `全空快照既 isUnverifiable 又无法产生选择器`() {
        val empty = ElementSnapshot(className = "android.view.View", bounds = "[0,0][10,10]")
        assertTrue(ClickTarget(className = empty.className, bounds = empty.bounds).isUnverifiable)
        assertNull("全空定位属性应无法产生选择器 → 回放定位阶段 fail-fast", empty.toSelectorSpec())
    }

    @Test
    fun `有任一定位属性即可审计且能产生选择器`() {
        val withRes = ElementSnapshot(resourceId = "com.app:id/ok")
        assertNotNull(withRes.toSelectorSpec())
        val target = withRes.toClickTarget()
        assertTrue(!target.isUnverifiable)
    }
}
