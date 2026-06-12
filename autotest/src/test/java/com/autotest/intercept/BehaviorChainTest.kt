package com.autotest.intercept

import com.autotest.dsl.scenario
import com.autotest.report.ReportCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BehaviorChainTest {

    private class FakeBehavior(private val canRecover: Boolean) : BehaviorInterceptor {
        var invoked = 0
        override fun tryRecover(stepNumber: String, stepName: String, error: Throwable): Boolean {
            invoked++
            return canRecover
        }
    }

    /** 第一次抛错、恢复后第二次成功的 action */
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
            chain.runWithRecovery("1", "step") { throw AssertionError("失败") }
            fail("应抛出")
        } catch (e: AssertionError) {
            assertEquals("失败", e.message)
        }
    }

    @Test
    fun `behavior 恢复成功后重试通过`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)

        chain.runWithRecovery("1", "step", flakyAction(failTimes = 1))
        assertEquals(1, behavior.invoked)
    }

    @Test
    fun `第一个节点无能为力时交给下一个`() {
        val chain = InterceptorChain()
        val helpless = FakeBehavior(canRecover = false)
        val helpful = FakeBehavior(canRecover = true)
        chain.addBehaviors(helpless, helpful)

        chain.runWithRecovery("1", "step", flakyAction(failTimes = 1))
        assertEquals(1, helpless.invoked)
        assertEquals(1, helpful.invoked)
    }

    @Test
    fun `恢复后重试仍失败则继续走后续节点，全链无解抛最后错误`() {
        val chain = InterceptorChain()
        val recoverer = FakeBehavior(canRecover = true)
        chain.addBehavior(recoverer)

        try {
            chain.runWithRecovery("1", "step", flakyAction(failTimes = 99))
            fail("应抛出")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("弹窗"))
        }
        assertEquals(1, recoverer.invoked) // 单节点只给一次恢复机会，不会死循环
    }

    @Test
    fun `behavior 节点自身异常按无能为力处理`() {
        val chain = InterceptorChain()
        chain.addBehavior(object : BehaviorInterceptor {
            override fun tryRecover(stepNumber: String, stepName: String, error: Throwable): Boolean =
                throw RuntimeException("behavior 自己崩了")
        })
        val helpful = FakeBehavior(canRecover = true)
        chain.addBehavior(helpful)

        chain.runWithRecovery("1", "step", flakyAction(failTimes = 1))
        assertEquals(1, helpful.invoked) // 崩溃节点被跳过，后续节点正常恢复
    }

    @Test
    fun `成功路径不触碰 behavior 链`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)

        val result = chain.runWithRecovery("1", "step") { 42 }
        assertEquals(42, result)
        assertEquals(0, behavior.invoked)
    }

    @Test
    fun `Scenario 步骤失败经 behavior 恢复后整体通过`() {
        val chain = InterceptorChain()
        chain.addBehavior(FakeBehavior(canRecover = true))
        val collector = ReportCollector()

        val action = flakyAction(failTimes = 1)
        scenario("弹窗恢复场景", collector = collector, interceptors = chain) {
            step("点击登录") { action() }
        }.run()

        val step = collector.buildReport("com.app").steps.single()
        assertTrue(step.passed)
    }

    @Test
    fun `clear 同时清空 watcher 与 behavior 链`() {
        val chain = InterceptorChain()
        val behavior = FakeBehavior(canRecover = true)
        chain.addBehavior(behavior)
        chain.clear()

        try {
            chain.runWithRecovery("1", "step") { throw AssertionError("失败") }
            fail("应抛出")
        } catch (_: AssertionError) {
        }
        assertEquals(0, behavior.invoked)
    }
}
