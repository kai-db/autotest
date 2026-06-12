package com.autotest.stability

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FlakyClassifierRulesTest {

    @After
    fun cleanup() {
        FlakyClassifier.clearRules()
    }

    @Test
    fun `内置正则识别等待超时格式`() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("Waited 5000ms for view to appear"))
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("failed to inject input event"))
    }

    @Test
    fun `正则不误伤普通消息`() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify("NullPointerException at LoginActivity"))
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify("waited for nothing")) // 无数字不匹配
    }

    @Test
    fun `自定义规则优先于内置规则`() {
        // 内置会把 "超时" 判 FLAKY；自定义规则把支付类超时强制判 HARD_FAIL（资金安全宁可人工看）
        FlakyClassifier.registerRule { message ->
            if (message.contains("支付")) FlakyType.HARD_FAIL else null
        }
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify("支付确认超时"))
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("页面加载超时")) // 不影响其他消息
    }

    @Test
    fun `自定义规则返回 null 时落到内置判定`() {
        FlakyClassifier.registerRule { null }
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("timeout"))
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify("crash"))
    }

    @Test
    fun `多条自定义规则按注册序生效`() {
        FlakyClassifier.registerRule { if (it.contains("A")) FlakyType.FLAKY else null }
        FlakyClassifier.registerRule { if (it.contains("A")) FlakyType.HARD_FAIL else null }
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("错误 A")) // 先注册的先命中
    }
}
