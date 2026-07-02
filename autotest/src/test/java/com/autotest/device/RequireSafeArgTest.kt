package com.autotest.device

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Q1/Q5：shell 参数与 logcat tag 注入防护（安全关键纯逻辑，补首个回归）。 */
class RequireSafeArgTest {

    // ---- requireSafeArg（包名/权限名）----

    @Test
    fun `合法包名通过`() {
        assertEquals("com.tm.security.wallet", requireSafeArg("com.tm.security.wallet"))
        assertEquals("a_b.c1", requireSafeArg("a_b.c1"))
    }

    @Test
    fun `含 shell 元字符的参数被拒`() {
        for (bad in listOf("a;rm -rf /", "a|b", "a\$(x)", "a`x`", "a b", "a&b", "a>b", "../x", "a/b")) {
            try {
                requireSafeArg(bad)
                fail("应拒绝: $bad")
            } catch (e: IllegalArgumentException) {
                // ok
            }
        }
    }

    // ---- requireSafeTag（logcat tag）----

    @Test
    fun `合法 tag 与受控 Tag冒号Priority 通过`() {
        assertEquals("MyTag", requireSafeTag("MyTag"))
        assertEquals("a.b_c1", requireSafeTag("a.b_c1"))
        assertEquals("Tag:V", requireSafeTag("Tag:V"))
        assertEquals("Tag:E", requireSafeTag("Tag:E"))
    }

    @Test
    fun `option-like 与 filter-spec 与非法优先级被拒`() {
        for (bad in listOf("-v", "-s", "*:S", "*", "tag:bad", "tag:X", "tag ", "a b", "a;b", "a|b", "tag:VV", ":V")) {
            try {
                requireSafeTag(bad)
                fail("应拒绝 tag: $bad")
            } catch (e: IllegalArgumentException) {
                // ok
            }
        }
    }
}
