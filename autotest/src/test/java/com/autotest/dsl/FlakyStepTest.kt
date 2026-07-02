package com.autotest.dsl

import com.autotest.intercept.InterceptorChain
import com.autotest.report.ReportCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** D1：flakyStep 走 classifier——只重试 FLAKY，HARD_FAIL（真 bug）立即抛不重试到耗尽；预算封顶。 */
class FlakyStepTest {

    private fun runSingle(block: ScenarioBuilder.() -> Unit) {
        scenario("s", collector = ReportCollector()) { block() }.run()
    }

    @Test
    fun `flakyStep 对 FLAKY 失败重试直到成功`() {
        var calls = 0
        runSingle {
            flakyStep("等待元素", maxRetries = 3, intervalMs = 0) {
                calls++
                if (calls < 3) throw AssertionError("Timeout waiting for view")
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun `flakyStep 对 HARD_FAIL（NPE）立即抛，不重试`() {
        var calls = 0
        try {
            runSingle {
                flakyStep("点击", maxRetries = 3, intervalMs = 0) {
                    calls++
                    throw NullPointerException("真 bug")
                }
            }
            fail("NPE 应立即抛，不重试")
        } catch (e: NullPointerException) {
            assertEquals("HARD_FAIL 不应重试", 1, calls)
        }
    }

    @Test
    fun `flakyStep 重试次数受 maxRetries 封顶`() {
        var calls = 0
        try {
            runSingle {
                flakyStep("持续 flaky", maxRetries = 2, intervalMs = 0) {
                    calls++
                    throw AssertionError("timeout")
                }
            }
            fail("持续失败应最终抛出")
        } catch (e: AssertionError) {
            assertEquals("首次 + 2 次重试 = 3", 3, calls)
        }
    }

    @Test
    fun `flakyStep 重试不超过统一预算硬顶`() {
        var calls = 0
        try {
            runSingle {
                // maxRetries 请求 100，但受 MAX_TOTAL_ATTEMPTS=5 封顶
                flakyStep("巨量重试", maxRetries = 100, intervalMs = 0) {
                    calls++
                    throw AssertionError("timeout")
                }
            }
            fail("应最终抛出")
        } catch (e: AssertionError) {
            assertTrue("总尝试不应超过硬顶 5，实际 $calls", calls <= 5)
        }
    }

    @Test
    fun `Scenario 内 flakyStep 与 behavior 重放共享同一步骤级预算，总尝试不超硬顶`() {
        // 组合：flakyStep（retriable=false，内部循环）在同一 Scenario.run 下受 per-step 共享预算封顶。
        // behavior 层不会重放 retriable=false 步骤，故组合总尝试 = flakyStep 循环 ≤ MAX_TOTAL_ATTEMPTS。
        var calls = 0
        val chain = InterceptorChain().apply {
            addBehavior(object : com.autotest.intercept.BehaviorInterceptor {
                override fun tryRecover(ctx: com.autotest.intercept.StepContext, error: Throwable) = true
            })
        }
        try {
            scenario("s", collector = ReportCollector(), interceptors = chain) {
                flakyStep("巨量重试 + behavior 在场", maxRetries = 100, intervalMs = 0) {
                    calls++
                    throw AssertionError("timeout")
                }
            }.run()
            fail("应抛出")
        } catch (e: AssertionError) {
            assertTrue("三层在场时总尝试仍不超过硬顶 5，实际 $calls", calls <= com.autotest.stability.RetryBudget.MAX_TOTAL_ATTEMPTS)
        }
    }
}
