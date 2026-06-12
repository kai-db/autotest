package com.autotest.diagnosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatAnalyzerTest {

    @Test
    fun `识别 Java 崩溃并提取异常签名`() {
        val logcat = """
            06-12 10:00:01.123  1234  1234 D SomeTag: normal log
            06-12 10:00:02.456  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main
            06-12 10:00:02.456  1234  1234 E AndroidRuntime: Process: com.tm.security.wallet, PID: 1234
            06-12 10:00:02.456  1234  1234 E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method on null
            06-12 10:00:02.456  1234  1234 E AndroidRuntime: 	at com.app.LoginActivity.onCreate(LoginActivity.kt:42)
        """.trimIndent()

        val cause = LogcatAnalyzer.analyze(logcat)!!
        assertEquals(RootCauseType.CRASH, cause.type)
        assertTrue(cause.signature.contains("NullPointerException"))
        assertTrue(cause.signature.contains("Attempt to invoke"))
    }

    @Test
    fun `识别本包 ANR`() {
        val logcat = "06-12 10:00:02.456  1234  1234 E ActivityManager: ANR in com.tm.security.wallet (com.tm.security.wallet/.MainActivity)"
        val cause = LogcatAnalyzer.analyze(logcat, packageName = "com.tm.security.wallet")!!
        assertEquals(RootCauseType.ANR, cause.type)
        assertEquals("ANR in com.tm.security.wallet", cause.signature)
    }

    @Test
    fun `他包 ANR 不误判（传入包名过滤时）`() {
        val logcat = "06-12 10:00:02.456 E ActivityManager: ANR in com.other.app"
        assertNull(LogcatAnalyzer.analyze(logcat, packageName = "com.tm.security.wallet"))
    }

    @Test
    fun `OOM 优先于普通崩溃（根因是内存）`() {
        val logcat = """
            E AndroidRuntime: FATAL EXCEPTION: main
            E AndroidRuntime: java.lang.OutOfMemoryError: Failed to allocate a 104857616 byte allocation
        """.trimIndent()
        assertEquals(RootCauseType.OOM, LogcatAnalyzer.analyze(logcat)!!.type)
    }

    @Test
    fun `识别 Native 崩溃`() {
        val logcat = "F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 1234"
        val cause = LogcatAnalyzer.analyze(logcat)!!
        assertEquals(RootCauseType.NATIVE_CRASH, cause.type)
        assertEquals("Fatal signal SIGSEGV", cause.signature)
    }

    @Test
    fun `普通日志无根因`() {
        assertNull(LogcatAnalyzer.analyze("D Tag: clicked button\nI Tag: page loaded"))
        assertNull(LogcatAnalyzer.analyze(""))
    }
}
