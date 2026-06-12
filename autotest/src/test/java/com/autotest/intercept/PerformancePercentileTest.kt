package com.autotest.intercept

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformancePercentileTest {

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    private fun interceptorWith(vararg durations: Long): PerformanceInterceptor {
        val p = PerformanceInterceptor(fakeLogger)
        durations.forEachIndexed { i, d -> p.afterStep("${i + 1}", "step$i", d) }
        return p
    }

    @Test
    fun `百分位按 nearest-rank 计算`() {
        val p = interceptorWith(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
        assertEquals(500, p.getPercentile(50))
        assertEquals(1000, p.getPercentile(95))
        assertEquals(100, p.getPercentile(1))
        assertEquals(1000, p.getPercentile(100))
    }

    @Test
    fun `无数据时百分位为 0`() {
        assertEquals(0, interceptorWith().getPercentile(50))
    }

    @Test
    fun `summary 汇总`() {
        val s = interceptorWith(100, 300, 200).getSummary()
        assertEquals(3, s.count)
        assertEquals(600, s.totalMs)
        assertEquals(200, s.p50Ms)
        assertEquals(300, s.maxMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法百分位拒绝`() {
        interceptorWith(100).getPercentile(0)
    }
}
