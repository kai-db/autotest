package com.autotest.ai

interface AIPlugin {
    fun generateTests(prompt: String): AIResult
    fun fixFlaky(report: String): AIResult
    fun generateData(schema: String): AIResult
    fun summarizeRun(runLog: String): AIResult
}
