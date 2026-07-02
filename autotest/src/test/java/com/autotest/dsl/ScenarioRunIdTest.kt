package com.autotest.dsl

import com.autotest.intercept.BehaviorInterceptor
import com.autotest.intercept.Interceptor
import com.autotest.intercept.InterceptorChain
import com.autotest.intercept.StepContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-8 修复回归：runId 每次 run() 变化；attempt 随 behavior 重放递增。 */
class ScenarioRunIdTest {

    private class CapturingInterceptor : Interceptor {
        val evidenceKeys = mutableListOf<String>()
        val runIds = mutableListOf<String>()
        override fun afterStep(ctx: StepContext, durationMs: Long) {
            evidenceKeys.add(ctx.evidenceKey); runIds.add(ctx.runId)
        }
        override fun onStepFailure(ctx: StepContext, error: Throwable) {
            evidenceKeys.add(ctx.evidenceKey); runIds.add(ctx.runId)
        }
    }

    @Test
    fun `同一 Scenario 对象多次 run 各自 runId 不同`() {
        val cap = CapturingInterceptor()
        val chain = InterceptorChain().apply { add(cap) }
        val s = scenario("TC-A", interceptors = chain) { step("s") {} }

        s.run()
        s.run()
        s.run()

        assertEquals(3, cap.runIds.size)
        assertEquals("三次执行 runId 应全不同", 3, cap.runIds.toSet().size)
        assertEquals("证据 key 也应全不同", 3, cap.evidenceKeys.toSet().size)
    }

    @Test
    fun `behavior 重放使 attempt 递增，体现在证据 key`() {
        val cap = CapturingInterceptor()
        val chain = InterceptorChain().apply {
            add(cap)
            addBehavior(object : BehaviorInterceptor {
                override fun tryRecover(ctx: StepContext, error: Throwable) = true // 总是"恢复成功"，触发重放
            })
        }
        var calls = 0
        // retriableStep 允许重放；前 1 次失败、重放后成功
        scenario("TC-A", interceptors = chain) {
            retriableStep("可重试") {
                calls++
                if (calls < 2) throw AssertionError("timeout")
            }
        }.run()

        // afterStep 用最终 ctx（attempt 已因一次重放 = 1）
        assertTrue("发生过至少一次重放", calls >= 2)
        assertTrue("证据 key 含 attempt=1", cap.evidenceKeys.single().endsWith("#1"))
    }
}
