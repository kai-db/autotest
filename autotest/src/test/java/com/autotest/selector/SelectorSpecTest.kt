package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorSpecTest {

    private val loginButton = ElementSnapshot(
        text = "登录",
        resourceId = "com.app:id/btn_login",
        contentDesc = "login button",
        className = "android.widget.Button"
    )

    @Test
    fun `EXACT 匹配文本`() {
        assertTrue(byText("登录").matches(loginButton))
        assertFalse(byText("登").matches(loginButton))
    }

    @Test
    fun `CONTAINS 匹配文本`() {
        assertTrue(byTextContains("登").matches(loginButton))
        assertFalse(byTextContains("注册").matches(loginButton))
    }

    @Test
    fun `STARTS_WITH 匹配文本前缀`() {
        assertTrue(byTextStartsWith("登").matches(loginButton))
        assertFalse(byTextStartsWith("录").matches(loginButton))
    }

    @Test
    fun `REGEX 为全串匹配`() {
        assertTrue(byTextRegex("登.").matches(loginButton))
        assertFalse(byTextRegex("登").matches(loginButton)) // 非全串不命中
    }

    @Test
    fun `and 组合要求全部条件命中`() {
        val spec = byText("登录") and byResId("com.app:id/btn_login")
        assertTrue(spec.matches(loginButton))
        assertFalse((byText("登录") and byResId("wrong")).matches(loginButton))
    }

    @Test
    fun `or 组合任一条件命中即可`() {
        val spec = byText("Sign in") or byText("登录")
        assertTrue(spec.matches(loginButton))
        assertFalse((byText("Sign in") or byText("Login")).matches(loginButton))
    }

    @Test
    fun `not 反转叶子条件`() {
        assertTrue(not(byText("注册")).matches(loginButton))
        assertFalse(not(byText("登录")).matches(loginButton))
    }

    @Test
    fun `同一写法产生稳定的规范化键`() {
        val a = (byText("登录") or byDesc("login")).key()
        val b = (byText("登录") or byDesc("login")).key()
        assertEquals(a, b)
        // 长度前缀编码（C4）：value 前带长度，防含分隔符的 value 碰撞
        assertEquals("or(text:exact:2:登录,desc:exact:5:login)", a)
    }

    @Test
    fun `不同写法产生不同键`() {
        assertTrue(byText("登录").key() != byTextContains("登录").key())
        assertTrue(byText("登录").key() != byDesc("登录").key())
    }

    @Test
    fun `含分隔符的 value 不与其它写法碰撞（C4 长度前缀）`() {
        // 裸拼时 Leaf("a,text:exact:b") 可能与 Or(Leaf("a"),Leaf("b")) 撞键；长度前缀消除碰撞
        val tricky = byText("a,text:exact:b").key()
        val twoLeaves = (byText("a") or byText("b")).key()
        assertTrue(tricky != twoLeaves)
    }

    @Test
    fun `叶子节点的 DNF 是单一备选项`() {
        assertEquals(listOf(listOf(byText("a"))), byText("a").toDnf())
    }

    @Test
    fun `Or 的 DNF 展开为多个备选项`() {
        val dnf = (byText("a") or byText("b")).toDnf()
        assertEquals(2, dnf.size)
    }

    @Test
    fun `And 嵌套 Or 的 DNF 做笛卡尔积展开`() {
        // (a or b) and c  =>  [a,c] | [b,c]
        val spec = (byText("a") or byText("b")) and byResId("c")
        val dnf = spec.toDnf()
        assertEquals(2, dnf.size)
        assertEquals(listOf(byText("a"), byResId("c")), dnf[0])
        assertEquals(listOf(byText("b"), byResId("c")), dnf[1])
    }

    @Test
    fun `DNF 保留 Not 条件供后置过滤`() {
        val spec = byTextContains("确认") and not(byResId("com.app:id/cancel"))
        val dnf = spec.toDnf()
        assertEquals(1, dnf.size)
        assertEquals(2, dnf[0].size)
        assertTrue(dnf[0][1] is SelectorSpec.Not)
    }
}
