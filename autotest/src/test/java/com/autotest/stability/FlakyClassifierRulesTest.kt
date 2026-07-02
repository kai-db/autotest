package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 规则注入测试：改用不可变 [DefaultFlakyClassifier] 实例（D2）——规则构造注入，无全局可变状态、无需 @After 清理。
 */
class FlakyClassifierRulesTest {

    @Test
    fun `内置正则识别等待超时格式`() {
        val c = DefaultFlakyClassifier()
        assertEquals(FlakyType.FLAKY, c.classify("Waited 5000ms for view to appear"))
        assertEquals(FlakyType.FLAKY, c.classify("failed to inject input event"))
    }

    @Test
    fun `正则不误伤普通消息`() {
        val c = DefaultFlakyClassifier()
        assertEquals(FlakyType.HARD_FAIL, c.classify("NullPointerException at LoginActivity"))
        assertEquals(FlakyType.HARD_FAIL, c.classify("waited for nothing")) // 无数字不匹配
    }

    @Test
    fun `自定义规则优先于内置规则`() {
        // 内置会把 "超时" 判 FLAKY；自定义规则把支付类超时强制判 HARD_FAIL（资金安全宁可人工看）
        val c = DefaultFlakyClassifier(listOf(FlakyRule { m -> if (m.contains("支付")) FlakyType.HARD_FAIL else null }))
        assertEquals(FlakyType.HARD_FAIL, c.classify("支付确认超时"))
        assertEquals(FlakyType.FLAKY, c.classify("页面加载超时")) // 不影响其他消息
    }

    @Test
    fun `自定义规则返回 null 时落到内置判定`() {
        val c = DefaultFlakyClassifier(listOf(FlakyRule { null }))
        assertEquals(FlakyType.FLAKY, c.classify("timeout"))
        assertEquals(FlakyType.HARD_FAIL, c.classify("crash"))
    }

    @Test
    fun `多条自定义规则按注册序生效`() {
        val c = DefaultFlakyClassifier(
            listOf(
                FlakyRule { if (it.contains("A")) FlakyType.FLAKY else null },
                FlakyRule { if (it.contains("A")) FlakyType.HARD_FAIL else null }
            )
        )
        assertEquals(FlakyType.FLAKY, c.classify("错误 A")) // 先注册的先命中
    }

    @Test
    fun `不同实例的规则互不泄漏（不再有全局可变状态）`() {
        val withRule = DefaultFlakyClassifier(listOf(FlakyRule { m -> if (m.contains("支付")) FlakyType.HARD_FAIL else null }))
        val plain = DefaultFlakyClassifier()
        assertEquals(FlakyType.HARD_FAIL, withRule.classify("支付超时"))
        assertEquals(FlakyType.FLAKY, plain.classify("支付超时")) // plain 实例不受另一实例规则影响
    }

    // ---- Throwable 级分类（P0-2 / D1）：类型优先，不被 message 措辞误判 ----

    @Test
    fun `NPE 按类型判 HARD_FAIL，即便 message 含 flaky 字样`() {
        val c = DefaultFlakyClassifier()
        assertEquals(FlakyType.HARD_FAIL, c.classify(NullPointerException("timeout 找不到 not idle")))
    }

    @Test
    fun `IllegalArgument 等真 bug 家族按类型判 HARD_FAIL`() {
        val c = DefaultFlakyClassifier()
        assertEquals(FlakyType.HARD_FAIL, c.classify(IllegalArgumentException("waited 5000ms")))
        assertEquals(FlakyType.HARD_FAIL, c.classify(IndexOutOfBoundsException("timeout")))
    }

    @Test
    fun `AssertionError 含 flaky 文案时仍回落 message 规则判 FLAKY`() {
        val c = DefaultFlakyClassifier()
        // 断言类不在类型 HARD 白名单，按 message 判定
        assertEquals(FlakyType.FLAKY, c.classify(AssertionError("timed out waiting")))
        assertEquals(FlakyType.HARD_FAIL, c.classify(AssertionError("余额不对")))
    }
}
