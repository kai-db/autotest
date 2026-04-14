package com.autotest.stability

import org.junit.Assert.assertEquals
import org.junit.Test

class FlakyClassifierTest {

    @Test
    fun flakyClassifier_marksTimeoutAsFlaky() {
        val type = FlakyClassifier.classify("Timeout while waiting")
        assertEquals(FlakyType.FLAKY, type)
    }
}
