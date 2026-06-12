package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** RetryRunner 自适应模式（注入 AdaptiveRetryPolicy + TestHistoryStore）的行为验证 */
class AdaptiveRetryRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val description: Description =
        Description.createTestDescription("com.app.SmokeTest", "testLogin")

    private fun statement(failTimes: Int, counter: MutableList<Int>): Statement =
        object : Statement() {
            var calls = 0
            override fun evaluate() {
                calls++
                counter.add(calls)
                if (calls <= failTimes) throw AssertionError("timeout waiting for view")
            }
        }

    @Test
    fun `flaky 历史的用例按预算重试并最终通过`() {
        val history = TestHistoryStore(tmp.root.resolve("h.json"))
        val caseId = "com.app.SmokeTest#testLogin"
        repeat(2) { history.record(caseId, true) }
        repeat(2) { history.record(caseId, false) } // p=0.5 → 预算封顶 3

        val calls = mutableListOf<Int>()
        RetryRunner(
            adaptivePolicy = AdaptiveRetryPolicy(history, maxRetries = 3, intervalMs = 0),
            history = history
        ).apply(statement(failTimes = 2, calls), description).evaluate()

        assertEquals(3, calls.size) // 失败 2 次 + 第 3 次成功
        // 每轮尝试都回写历史：原 4 条 + 新 3 条（false,false,true）
        assertEquals(7, history.history(caseId)!!.outcomes.size)
    }

    @Test
    fun `稳定历史的用例零预算，失败直接抛`() {
        val history = TestHistoryStore(tmp.root.resolve("h.json"))
        val caseId = "com.app.SmokeTest#testLogin"
        repeat(5) { history.record(caseId, true) } // p=1.0 → 0 预算

        val calls = mutableListOf<Int>()
        try {
            RetryRunner(
                adaptivePolicy = AdaptiveRetryPolicy(history, intervalMs = 0),
                history = history
            ).apply(statement(failTimes = 9, calls), description).evaluate()
        } catch (_: AssertionError) {
        }
        assertEquals(1, calls.size) // 没有重试
    }

    @Test
    fun `无历史用例用默认预算`() {
        val history = TestHistoryStore(tmp.root.resolve("h.json"))
        val calls = mutableListOf<Int>()
        RetryRunner(
            adaptivePolicy = AdaptiveRetryPolicy(history, defaultRetries = 1, intervalMs = 0),
            history = history
        ).apply(statement(failTimes = 1, calls), description).evaluate()

        assertEquals(2, calls.size) // 1 次失败 + 1 次重试成功
    }
}
