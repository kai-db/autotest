package com.autotest.ai

import com.autotest.risk.RiskEvaluator
import com.autotest.risk.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskEvaluatorTest {

    @Test
    fun riskEvaluator_lowRiskForWaitOnly() {
        val level = RiskEvaluator.evaluate("Please WAIT for the element")
        assertEquals(RiskLevel.L1, level)
    }
}
