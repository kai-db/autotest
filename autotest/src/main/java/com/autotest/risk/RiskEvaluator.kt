package com.autotest.risk

object RiskEvaluator {

    fun evaluate(changeSummary: String): RiskLevel {
        val lowered = changeSummary.lowercase()
        return if (lowered.contains("wait")) {
            RiskLevel.L1
        } else {
            RiskLevel.L2
        }
    }
}
