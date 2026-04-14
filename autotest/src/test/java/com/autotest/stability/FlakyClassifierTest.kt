package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Test

class FlakyClassifierTest {

    @Test
    fun classify_timeout() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("Timeout while waiting"))
    }

    @Test
    fun classify_timedOut() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("Timed out waiting for view"))
    }

    @Test
    fun classify_chineseTimeout() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("等待超时 (10000ms)"))
    }

    @Test
    fun classify_noMatchingView() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("NoMatchingViewException: No views match"))
    }

    @Test
    fun classify_notFoundAfter() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("View with id 'xxx' not found after 10 seconds"))
    }

    @Test
    fun classify_appNotIdle() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("AppNotIdleException: app is busy"))
    }

    @Test
    fun classify_performException() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("PerformException: Error performing click"))
    }

    @Test
    fun classify_staleObject() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("StaleObjectException: UI object gone"))
    }

    @Test
    fun classify_chineseNotFound() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("找不到包含文本 \"提交\" 的元素"))
    }

    @Test
    fun classify_null() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify(null))
    }

    @Test
    fun classify_empty() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify(""))
    }

    @Test
    fun classify_hardFail() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify("NullPointerException at line 42"))
    }
}
