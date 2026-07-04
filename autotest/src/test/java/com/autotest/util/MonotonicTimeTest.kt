package com.autotest.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotonicTimeTest {

    @After
    fun tearDown() {
        MonotonicTime.resetForTest()
    }

    @Test
    fun defaultSource_onJvm_fallsBackToNanoTimeAndIsNonDecreasing() {
        // JVM 单测中 SystemClock 是 android.jar stub（探针抛异常）→ 默认源回退 nanoTime
        val samples = LongArray(50) { MonotonicTime.nowMs() }
        for (i in 1 until samples.size) {
            assertTrue("单调源不允许回退: ${samples[i - 1]} -> ${samples[i]}", samples[i] >= samples[i - 1])
        }
        // 回退源与 nanoTime 同量级（同为进程内单调毫秒，差值应远小于任何墙钟 epoch）
        val diff = Math.abs(MonotonicTime.nowMs() - System.nanoTime() / 1_000_000)
        assertTrue("默认源应为 nanoTime 回退（差值 ${diff}ms）", diff < 60_000)
    }

    @Test
    fun overrideSourceForTest_takesEffect() {
        MonotonicTime.overrideSourceForTest { 12345L }
        assertEquals(12345L, MonotonicTime.nowMs())
    }

    @Test
    fun resetForTest_restoresProbedDefault() {
        MonotonicTime.overrideSourceForTest { -1L }
        MonotonicTime.resetForTest()
        val diff = Math.abs(MonotonicTime.nowMs() - System.nanoTime() / 1_000_000)
        assertTrue("reset 后应复原探测所得默认源", diff < 60_000)
    }
}
