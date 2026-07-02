package com.autotest.stability

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * P0-2 回归测试：flakySafely「最后一次尝试」的 catch 必须与循环体同样判白名单——
 * 否则任意真 bug（NPE 等）会被包装成含「超时」的 AssertionError，
 * 被 FlakyClassifier 按消息误判 FLAKY 进入重试洗白链。
 */
class FlakySafelyFinalAttemptTest {

    @Test
    fun `最后一次尝试抛非白名单异常时原样上抛（不包装成超时 AssertionError）`() {
        // timeoutMs=0 跳过轮询循环，直接进入「最后一次尝试」分支
        try {
            flakySafely(timeoutMs = 0, intervalMs = 1) {
                throw NullPointerException("真 bug")
            }
            fail("应抛出 NullPointerException")
        } catch (e: NullPointerException) {
            assertTrue(e.message!!.contains("真 bug")) // 原样上抛，未被包装
        }
    }

    @Test
    fun `最后一次尝试抛白名单异常时仍包装为超时 AssertionError（原语义保留）`() {
        try {
            flakySafely(timeoutMs = 0, intervalMs = 1) {
                throw AssertionError("元素未出现")
            }
            fail("应抛出 AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("flakySafely 超时"))
        }
    }

    @Test
    fun `循环期间白名单异常重试、窗口后成功仍返回结果`() {
        var attempts = 0
        val result = flakySafely(timeoutMs = 500, intervalMs = 10) {
            attempts++
            if (attempts < 3) throw IllegalStateException("尚未就绪")
            "ok"
        }
        assertTrue(result == "ok" && attempts >= 3)
    }
}
