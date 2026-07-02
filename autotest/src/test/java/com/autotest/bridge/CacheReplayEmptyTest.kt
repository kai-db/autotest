package com.autotest.bridge

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** P0-3 回归测试：空步骤缓存回放 = 空场景假 PASS，必须拒绝 */
class CacheReplayEmptyTest {

    @Test
    fun `空步骤缓存拒绝回放`() {
        val case = CachedCase(caseId = "TC-EMPTY", steps = emptyList())
        val noop = object : ReplayExecutor {
            override fun execute(case: CachedCase, step: CachedStep) {}
        }
        try {
            CacheReplay.toScenario(case, noop)
            fail("空步骤缓存应拒绝回放")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("无步骤"))
        }
    }
}
