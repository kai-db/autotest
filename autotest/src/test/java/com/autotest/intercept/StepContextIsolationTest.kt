package com.autotest.intercept

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-8：StepContext 双 key——证据唯一（不覆盖）+ 性能逻辑聚合（不串味）。 */
class StepContextIsolationTest {

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Test
    fun `证据 key 跨 case 同 stepNumber 不碰撞`() {
        val a = StepContext(caseId = "TC-A", stepNumber = "1", stepName = "s", runId = "r1")
        val b = StepContext(caseId = "TC-B", stepNumber = "1", stepName = "s", runId = "r2")
        assertNotEquals(a.evidenceKey, b.evidenceKey)
        // 逻辑 key 也不同（不同 case）
        assertNotEquals(a.logicalKey, b.logicalKey)
    }

    @Test
    fun `同 case 同 step 多轮 runId 不同 → 证据 key 唯一但逻辑 key 相同（可聚合）`() {
        val r1 = StepContext(caseId = "TC-A", stepNumber = "1", stepName = "s", runId = "r1")
        val r2 = StepContext(caseId = "TC-A", stepNumber = "1", stepName = "s", runId = "r2")
        assertNotEquals("多轮证据不应互相覆盖", r1.evidenceKey, r2.evidenceKey)
        assertEquals("同 case+step 逻辑 key 相同，供性能聚合", r1.logicalKey, r2.logicalKey)
    }

    @Test
    fun `Performance 按逻辑 key 聚合多样本，不因跨用例覆盖丢样本`() {
        val p = PerformanceInterceptor(fakeLogger)
        // 两个不同 case 的 step1 + 同 case 多轮
        p.afterStep(StepContext("TC-A", "1", "s", "r1"), 100)
        p.afterStep(StepContext("TC-A", "1", "s", "r2"), 300) // 同逻辑 key，第二样本
        p.afterStep(StepContext("TC-B", "1", "s", "r3"), 500) // 不同 case
        val summary = p.getSummary()
        assertEquals("三条样本都应保留（旧实现会因 key 碰撞覆盖成 1-2 条）", 3, summary.count)
        assertEquals(900, summary.totalMs)
    }

    @Test
    fun `Screenshot 证据按唯一 key 存取`() {
        val s = ScreenshotInterceptor(logger = fakeLogger)
        val ctx = StepContext("TC-A", "1", "s", "r1")
        // 未截图时取不到；构造上验证 key 通路（真实截图依赖设备）
        assertEquals(null, s.getScreenshotPath(ctx))
    }

    @Test
    fun `fileSafeKey 同秒多 case 同 stepNumber 不碰撞且文件名安全`() {
        val a = StepContext("TC-A", "1", "s", "r1").fileSafeKey
        val b = StepContext("TC-B", "1", "s", "r2").fileSafeKey
        assertNotEquals("底层文件名片段必须不同，否则同秒落盘互相覆盖", a, b)
        // 仅含文件名安全字符
        assertTrue(a.matches(Regex("[A-Za-z0-9._-]+")))
    }

    @Test
    fun `fileSafeKey 清洗非法字符（含中文 caseId 时）`() {
        val key = StepContext("TC 中文/名", "1", "s", "r1").fileSafeKey
        assertTrue("不应含空格或斜杠", key.matches(Regex("[A-Za-z0-9._-]+")))
    }
}
