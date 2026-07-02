package com.autotest.intercept

import com.autotest.dsl.scenario
import com.autotest.report.ReportCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BehaviorChainTest {

    private fun ctx(n: String = "1", name: String = "step") =
        StepContext(caseId = "case", stepNumber = n, stepName = name, runId = "r0")

    private class FakeBehavior(private val canRecover: Boolean) : BehaviorInterceptor {
        var invoked = 0
        override fun tryRecover(ctx: StepContext, error: Throwable): Boolean {
            invoked++
            return canRecover
        }
    }

    /** 前 failTimes 次抛错、之后成功的 action */
    private fun flakyAction(failTimes: Int = 1): () -> Unit {
        var calls = 0
        return {
            calls++
            if (calls <= failTimes) throw AssertionError("挡路弹窗导致点击失败")
        }
    }

    @Test
    fun `无 behavior 节点时失败直接抛出`() {
        val chain = InterceptorChain()
        try {
            chain.runWithRecovery(ctx(), retriable = true) { throw AssertionError("失败") }
            fail("应抛出")
        } catch (e: AssertionError) {
            assertEquals("失败", e.message)
        }
    }

    @Test
    fun `retriable=true 时 behavior 恢复成功后重试通过`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)

        chain.runWithRecovery(ctx(), retriable = true, action = flakyAction(failTimes = 1))
        assertEquals(1, behavior.invoked)
    }

    @Test
    fun `retriable=false 时恢复动作照跑但不重放，原始失败照抛（副作用保护）`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)
        val action = flakyAction(failTimes = 1)

        try {
            chain.runWithRecovery(ctx(), retriable = false, action = action)
            fail("retriable=false 不应重放，原始失败应抛出")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("弹窗"))
        }
        assertEquals("恢复动作仍应执行一次", 1, behavior.invoked) // recovery 跑了，只是没重放
    }

    @Test
    fun `第一个节点无能为力时交给下一个`() {
        val chain = InterceptorChain()
        val helpless = FakeBehavior(canRecover = false)
        val helpful = FakeBehavior(canRecover = true)
        chain.addBehaviors(helpless, helpful)

        chain.runWithRecovery(ctx(), retriable = true, action = flakyAction(failTimes = 1))
        assertEquals(1, helpless.invoked)
        assertEquals(1, helpful.invoked)
    }

    @Test
    fun `恢复后重试仍失败则继续走后续节点，全链无解抛最后错误`() {
        val chain = InterceptorChain()
        val recoverer = FakeBehavior(canRecover = true)
        chain.addBehavior(recoverer)

        try {
            chain.runWithRecovery(ctx(), retriable = true, action = flakyAction(failTimes = 99))
            fail("应抛出")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("弹窗"))
        }
        assertEquals(1, recoverer.invoked) // 单节点只给一次恢复机会，不会死循环
    }

    @Test
    fun `onRetry 返回 false（预算耗尽）时停止重放`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)
        val action = flakyAction(failTimes = 1)

        try {
            chain.runWithRecovery(ctx(), retriable = true, onRetry = { false }, action = action)
            fail("预算耗尽应停止重放并抛原始失败")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("弹窗"))
        }
        assertEquals(1, behavior.invoked)
    }

    @Test
    fun `behavior 节点自身异常按无能为力处理`() {
        val chain = InterceptorChain()
        chain.addBehavior(object : BehaviorInterceptor {
            override fun tryRecover(ctx: StepContext, error: Throwable): Boolean =
                throw RuntimeException("behavior 自己崩了")
        })
        val helpful = FakeBehavior(canRecover = true)
        chain.addBehavior(helpful)

        chain.runWithRecovery(ctx(), retriable = true, action = flakyAction(failTimes = 1))
        assertEquals(1, helpful.invoked) // 崩溃节点被跳过，后续节点正常恢复
    }

    @Test
    fun `成功路径不触碰 behavior 链`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)

        val result = chain.runWithRecovery(ctx(), retriable = true) { 42 }
        assertEquals(42, result)
        assertEquals(0, behavior.invoked)
    }

    @Test
    fun `Scenario retriableStep 失败经 behavior 恢复后整体通过`() {
        val chain = InterceptorChain()
        chain.addBehavior(FakeBehavior(canRecover = true))
        val collector = ReportCollector()

        val action = flakyAction(failTimes = 1)
        scenario("弹窗恢复场景", collector = collector, interceptors = chain) {
            retriableStep("点击登录") { action() }
        }.run()

        val step = collector.buildReport("com.app").steps.single()
        assertTrue(step.passed)
    }

    @Test
    fun `Scenario 默认 step retriable=false 不重放，副作用步骤失败即失败`() {
        val chain = InterceptorChain()
        chain.addBehavior(FakeBehavior(canRecover = true))
        val collector = ReportCollector()
        val action = flakyAction(failTimes = 1)

        try {
            scenario("默认不重放", collector = collector, interceptors = chain) {
                step("转账确认") { action() }
            }.run()
            fail("默认 step retriable=false，恢复后不重放，应失败")
        } catch (_: AssertionError) {
        }
        assertTrue(!collector.buildReport("com.app").steps.single().passed)
    }

    @Test
    fun `clear 同时清空 watcher 与 behavior 链`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)
        chain.clear()

        try {
            chain.runWithRecovery(ctx(), retriable = true) { throw AssertionError("失败") }
            fail("应抛出")
        } catch (_: AssertionError) {
        }
        assertEquals(0, behavior.invoked)
    }
}
