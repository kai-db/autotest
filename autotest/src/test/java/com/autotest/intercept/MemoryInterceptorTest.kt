package com.autotest.intercept

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryInterceptorTest {

    @Test
    fun `解析 App Summary 格式的 TOTAL PSS`() {
        val out = """
            App Summary
                               Pss(KB)                        Rss(KB)
                Java Heap:        10240                          20480
               Native Heap:        5120                           8192
                TOTAL PSS:       123456            TOTAL RSS:    234567
        """.trimIndent()
        assertEquals(123456L, MemoryInterceptor.parseTotalPssKb(out))
    }

    @Test
    fun `解析详情表格行首 TOTAL 格式（旧版本 dumpsys）`() {
        val out = """
                                 Pss  Private  Private  SwapPss
                               Total    Dirty    Clean    Dirty
                              ------   ------   ------   ------
              Native Heap     8192     8000      100        0
                     TOTAL    98765    50000     2000      300
        """.trimIndent()
        assertEquals(98765L, MemoryInterceptor.parseTotalPssKb(out))
    }

    @Test
    fun `无法解析时返回 -1`() {
        assertEquals(-1L, MemoryInterceptor.parseTotalPssKb("No process found for: com.foo"))
        assertEquals(-1L, MemoryInterceptor.parseTotalPssKb(""))
    }

    @Test
    fun `同时存在两种格式时优先 App Summary 标签`() {
        val out = """
                     TOTAL    98765    50000     2000      300
                TOTAL PSS:       123456            TOTAL RSS:    234567
        """.trimIndent()
        assertEquals(123456L, MemoryInterceptor.parseTotalPssKb(out))
    }
}
