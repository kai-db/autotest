package com.autotest.risk

object ApprovalGate {

    fun shouldApply(
        risk: RiskLevel,
        changeSummary: String,
        approvalCallback: (RiskLevel, String) -> Boolean
    ): Boolean {
        return if (risk == RiskLevel.L1) {
            true
        } else {
            approvalCallback(risk, changeSummary)
        }
    }
}
