package com.autotest.ai

data class AIResult(
    val content: String,
    val changeSummary: String? = null,
    val patch: String? = null
)
