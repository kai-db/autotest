package com.autotest.ai

import com.autotest.risk.RiskLevel

object AIHooks {
    var approvalCallback: ((RiskLevel, String) -> Boolean)? = null
}
