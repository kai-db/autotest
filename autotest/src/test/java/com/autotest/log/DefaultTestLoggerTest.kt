package com.autotest.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Q1/Q9：log 包首个单测——分级过滤 + 文件写入 + 并发写不抛/不丢行。 */
class DefaultTestLoggerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun logFileText(dir: java.io.File): String =
        dir.listFiles { f -> f.name.startsWith("test_") && f.name.endsWith(".log") }
            ?.firstOrNull()?.readText() ?: ""

    @Test
    fun `分级过滤：minLevel=WARN 时 DEBUG_INFO 不落文件`() {
        val logger = DefaultTestLogger(logToLogcat = false, minLevel = DefaultTestLogger.LogLevel.WARN, logDir = tmp.root.absolutePath)
        logger.d("T", "debug-msg")
        logger.i("T", "info-msg")
        logger.w("T", "warn-msg")
        val text = logFileText(tmp.root)
        assertFalse(text.contains("debug-msg"))
        assertFalse(text.contains("info-msg"))
        assertTrue(text.contains("warn-msg"))
    }

    @Test
    fun `写入格式含时间_级别_tag`() {
        val logger = DefaultTestLogger(logToLogcat = false, logDir = tmp.root.absolutePath)
        logger.e("Tag1", "boom")
        val text = logFileText(tmp.root)
        assertTrue(text.contains("[Tag1]"))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("E ")) // ERROR 级别首字母
    }

    @Test
    fun `多线程并发写不抛异常且不丢行`() {
        val logger = DefaultTestLogger(logToLogcat = false, logDir = tmp.root.absolutePath)
        val threads = (1..8).map { t ->
            Thread {
                repeat(50) { i -> logger.i("T$t", "line-$t-$i") }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val lines = logFileText(tmp.root).lines().filter { it.contains("line-") }
        assertEquals("8 线程 × 50 行都应落盘（synchronized 写不丢行）", 400, lines.size)
    }
}
