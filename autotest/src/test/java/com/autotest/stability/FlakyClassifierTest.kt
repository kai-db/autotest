package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Test

class FlakyClassifierTest {

    @Test
    fun flakyClassifier_marksTimeoutAsFlaky() {
        val type = FlakyClassifier.classify("Timeout while waiting")
        assertEquals(FlakyType.FLAKY, type)
    }

    @Test
    fun flakyClassifier_marksChineseTimeoutAsFlaky() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("等待超时 (10000ms)"))
    }

    @Test
    fun flakyClassifier_marksTimedOutAsFlaky() {
        assertEquals(FlakyType.FLAKY, FlakyClassifier.classify("Timed out waiting for view"))
    }

    @Test
    fun flakyClassifier_marksNullAsHardFail() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify(null))
    }

    @Test
    fun flakyClassifier_marksEmptyAsHardFail() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify(""))
    }

    @Test
    fun flakyClassifier_marksOtherAsHardFail() {
        assertEquals(FlakyType.HARD_FAIL, FlakyClassifier.classify("Element not found"))
    }
}
