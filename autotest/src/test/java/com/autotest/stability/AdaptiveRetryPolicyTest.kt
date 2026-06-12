package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdaptiveRetryPolicyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun historyWith(caseId: String, vararg outcomes: Boolean): TestHistoryStore {
        val store = TestHistoryStore(tmp.root.resolve("h.json"))
        outcomes.forEach { store.record(caseId, it) }
        return store
    }

    @Test
    fun `无历史用默认预算（冷启动）`() {
        val policy = AdaptiveRetryPolicy(historyWith("other", true), defaultRetries = 1)
        assertEquals(1, policy.retryBudget("TC-001"))
    }

    @Test
    fun `从未失败的稳定用例零预算`() {
        val history = historyWith("TC-001", true, true, true, true)
        assertEquals(0, AdaptiveRetryPolicy(history).retryBudget("TC-001"))
    }

    @Test
    fun `高通过率用例预算小`() {
        // p=0.9, T=0.95: n = ceil(ln0.05/ln0.1) = 2 次尝试 → 1 次重试
        val history = historyWith("TC-001", true, true, true, true, true, true, true, true, true, false)
        assertEquals(1, AdaptiveRetryPolicy(history, targetSuccessRate = 0.95).retryBudget("TC-001"))
    }

    @Test
    fun `五五开的 flaky 用例拿到足额预算但被上限封顶`() {
        // p=0.5, T=0.95: n = ceil(4.32) = 5 次尝试 → 4 次重试 → 封顶 3
        val history = historyWith("TC-001", true, false, true, false)
        assertEquals(3, AdaptiveRetryPolicy(history, maxRetries = 3).retryBudget("TC-001"))
    }

    @Test
    fun `从未通过的用例给满预算（排除环境因素）`() {
        val history = historyWith("TC-001", false, false, false)
        assertEquals(3, AdaptiveRetryPolicy(history, maxRetries = 3).retryBudget("TC-001"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `目标成功率必须小于 1`() {
        AdaptiveRetryPolicy(historyWith("x", true), targetSuccessRate = 1.0)
    }
}
