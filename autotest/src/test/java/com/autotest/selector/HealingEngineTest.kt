package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealingEngineTest {

    private val engine = HealingEngine()

    private val fingerprint = ElementSnapshot(
        text = "登录",
        resourceId = "com.app:id/btn_login",
        contentDesc = "login",
        className = "android.widget.Button"
    )

    @Test
    fun `完全一致的候选得满分`() {
        assertEquals(1.0, engine.score(fingerprint, fingerprint), 1e-9)
    }

    @Test
    fun `文案微调但 resource-id 不变时自愈命中`() {
        val renamed = fingerprint.copy(text = "立即登录") // UI 文案改版
        val noise = ElementSnapshot(text = "注册", resourceId = "com.app:id/btn_register")
        val result = engine.heal(fingerprint, listOf(noise, renamed))
        assertNotNull(result)
        assertEquals(renamed, result!!.candidate)
        assertTrue(result.confidence >= 0.75)
    }

    @Test
    fun `候选都不相似时不自愈`() {
        val candidates = listOf(
            ElementSnapshot(text = "首页", resourceId = "com.app:id/tab_home"),
            ElementSnapshot(text = "我的", resourceId = "com.app:id/tab_mine")
        )
        assertNull(engine.heal(fingerprint, candidates))
    }

    @Test
    fun `空候选列表不自愈`() {
        assertNull(engine.heal(fingerprint, emptyList()))
    }

    @Test
    fun `多候选时选相似度最高的`() {
        val similar = fingerprint.copy(text = "登录中")
        val identical = fingerprint
        val result = engine.heal(fingerprint, listOf(similar, identical))
        assertEquals(identical, result!!.candidate)
    }

    @Test
    fun `指纹空属性不参与计分`() {
        val sparseFp = ElementSnapshot(resourceId = "com.app:id/btn_login")
        val candidate = ElementSnapshot(resourceId = "com.app:id/btn_login", text = "随便什么文本")
        // 指纹只有 resourceId，候选 resourceId 一致 → 满分
        assertEquals(1.0, engine.score(sparseFp, candidate), 1e-9)
    }

    @Test
    fun `全空指纹得零分`() {
        assertEquals(0.0, engine.score(ElementSnapshot(), ElementSnapshot(text = "x")), 1e-9)
    }

    @Test
    fun `阈值可配置`() {
        val strict = HealingEngine(threshold = 0.99)
        val renamed = fingerprint.copy(text = "立即登录")
        assertNull(strict.heal(fingerprint, listOf(renamed)))
    }

    @Test
    fun `字符串相似度语义`() {
        assertEquals(1.0, HealingEngine.similarity("登录", "登录"), 1e-9)
        assertEquals(0.8, HealingEngine.similarity("登录", "立即登录"), 1e-9)
        assertEquals(0.0, HealingEngine.similarity("", "登录"), 1e-9)
        assertTrue(HealingEngine.similarity("abcd", "abce") in 0.1..0.6)
    }
}
