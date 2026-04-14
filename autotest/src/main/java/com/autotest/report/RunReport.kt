package com.autotest.report

import com.autotest.runner.RunnerInfo

data class RunReport(
    val appPackage: String,
    val startTime: Long,
    val endTime: Long,
    val device: String? = null,
    val runnerInfo: RunnerInfo? = null,
    val failures: List<Failure> = emptyList(),
    val steps: List<StepResult> = emptyList(),
    val summary: ReportSummary? = null
)

data class ReportSummary(
    val totalSteps: Int,
    val passedSteps: Int,
    val failedSteps: Int,
    val passRate: Float,
    val totalDurationMs: Long
) {
    companion object {
        fun from(steps: List<StepResult>): ReportSummary {
            val passed = steps.count { it.passed }
            val failed = steps.count { !it.passed }
            val total = steps.size
            return ReportSummary(
                totalSteps = total,
                passedSteps = passed,
                failedSteps = failed,
                passRate = if (total > 0) passed.toFloat() / total * 100 else 0f,
                totalDurationMs = steps.sumOf { it.durationMs }
            )
        }
    }
}

data class Failure(
    val className: String,
    val methodName: String,
    val message: String,
    val screenshots: List<String>? = null,
    val flakyType: com.autotest.stability.FlakyType? = null
)

data class StepResult(
    val scenarioName: String,
    val stepName: String,
    val stepNumber: String = "",
    val durationMs: Long,
    val passed: Boolean,
    val error: String? = null,
    val screenshotPath: String? = null
)
