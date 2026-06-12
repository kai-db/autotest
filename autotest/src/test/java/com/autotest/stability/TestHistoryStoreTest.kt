package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(windowSize: Int = 50) =
        TestHistoryStore(tmp.root.resolve("history.json"), windowSize)

    @Test
    fun `无历史时通过率为 null`() {
        assertNull(store().passRate("TC-001"))
    }

    @Test
    fun `通过率按记录计算`() {
        val s = store()
        s.record("TC-001", true)
        s.record("TC-001", true)
        s.record("TC-001", false)
        s.record("TC-001", true)
        assertEquals(0.75, s.passRate("TC-001")!!, 1e-9)
    }

    @Test
    fun `滚动窗口丢弃最老的记录`() {
        val s = store(windowSize = 3)
        s.record("TC-001", false) // 会被挤出窗口
        s.record("TC-001", true)
        s.record("TC-001", true)
        s.record("TC-001", true)
        assertEquals(1.0, s.passRate("TC-001")!!, 1e-9)
        assertEquals(3, s.history("TC-001")!!.outcomes.size)
    }

    @Test
    fun `跨实例持久化（历史是跨 run 资产）`() {
        val file = tmp.root.resolve("history.json")
        TestHistoryStore(file).record("TC-001", true, nowMs = 1000)

        val reloaded = TestHistoryStore(file)
        assertEquals(1.0, reloaded.passRate("TC-001")!!, 1e-9)
        assertEquals(1000L, reloaded.history("TC-001")!!.updatedAtMs)
    }

    @Test
    fun `历史库损坏按空库处理不抛异常`() {
        val file = tmp.root.resolve("history.json")
        file.writeText("损坏")
        val s = TestHistoryStore(file)
        assertNull(s.passRate("TC-001"))
        s.record("TC-001", true) // 仍可恢复写入
        assertEquals(1.0, s.passRate("TC-001")!!, 1e-9)
    }

    @Test
    fun `多用例独立统计`() {
        val s = store()
        s.record("TC-001", true)
        s.record("TC-002", false)
        assertEquals(1.0, s.passRate("TC-001")!!, 1e-9)
        assertEquals(0.0, s.passRate("TC-002")!!, 1e-9)
    }
}
