package com.autotest.bridge

import com.autotest.report.ReportCollector
import com.autotest.selector.ElementSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CacheReplayTest {

    private class RecordingExecutor : ReplayExecutor {
        val executed = mutableListOf<String>()
        var failOn: String? = null

        override fun execute(case: CachedCase, step: CachedStep) {
            if (step.name == failOn) throw AssertionError("模拟步骤失败: ${step.name}")
            executed.add(step.name)
        }
    }

    private val case = CachedCase(
        caseId = "TC-S-001",
        description = "冷启动",
        steps = listOf(
            CachedStep("启动 App", CachedActionType.LAUNCH_APP),
            CachedStep("点击消息", CachedActionType.CLICK, target = ElementSnapshot(text = "消息")),
            CachedStep("校验消息页", CachedActionType.WAIT_TEXT, payload = "消息")
        )
    )

    @Test
    fun `按缓存步骤顺序回放`() {
        val executor = RecordingExecutor()
        CacheReplay.toScenario(case, executor).run()

        assertEquals(listOf("启动 App", "点击消息", "校验消息页"), executor.executed)
    }

    @Test
    fun `步骤失败中断回放并向上抛`() {
        val executor = RecordingExecutor().apply { failOn = "点击消息" }
        try {
            CacheReplay.toScenario(case, executor).run()
            fail("应抛出 AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("点击消息"))
        }
        assertEquals(listOf("启动 App"), executor.executed) // 后续步骤未执行
    }

    @Test
    fun `回放结果进入既有报告链路`() {
        val collector = ReportCollector()
        CacheReplay.toScenario(case, RecordingExecutor(), collector = collector).run()

        val steps = collector.buildReport("com.app").steps
        assertEquals(3, steps.size)
        assertTrue(steps.all { it.passed })
        assertTrue(steps.all { it.scenarioName.startsWith("TC-S-001") })
    }
}
