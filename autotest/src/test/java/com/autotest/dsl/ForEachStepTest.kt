package com.autotest.dsl

import com.autotest.report.ReportCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ForEachStepTest {

    @Test
    fun `数据集展开为独立步骤并按序执行`() {
        val visited = mutableListOf<String>()
        val collector = ReportCollector()

        scenario("代币搜索", collector = collector) {
            forEach(listOf("BTC", "ETH", "USDT"), "搜索代币") { visited.add(it) }
        }.run()

        assertEquals(listOf("BTC", "ETH", "USDT"), visited)
        val steps = collector.buildReport("com.app").steps
        assertEquals(3, steps.size)
        assertEquals("搜索代币 [1: BTC]", steps[0].stepName)
        assertEquals("搜索代币 [3: USDT]", steps[2].stepName)
    }

    @Test
    fun `某条数据失败时步骤名直接指认该数据`() {
        val collector = ReportCollector()
        try {
            scenario("代币搜索", collector = collector) {
                forEach(listOf("BTC", "BAD", "ETH"), "搜索代币") {
                    if (it == "BAD") throw AssertionError("搜索无结果")
                }
            }.run()
            fail("应抛出")
        } catch (_: AssertionError) {
        }

        val steps = collector.buildReport("com.app").steps
        val failedStep = steps.single { !it.passed }
        assertTrue(failedStep.stepName.contains("BAD"))
        assertEquals(2, steps.size) // BTC 通过 + BAD 失败，ETH 未执行
    }

    @Test
    fun `空数据集不生成步骤`() {
        val collector = ReportCollector()
        scenario("空场景", collector = collector) {
            forEach(emptyList<String>(), "不会执行") { fail("不应被调用") }
        }.run()
        assertEquals(0, collector.buildReport("com.app").steps.size)
    }
}
