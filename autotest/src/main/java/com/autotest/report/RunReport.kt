package com.autotest.report

import com.autotest.runner.RunnerInfo

data class RunReport(
    val appPackage: String,
    val startTime: Long,
    val endTime: Long,
    val device: String? = null,
    val runnerInfo: RunnerInfo? = null,
    val failures: List<Failure> = emptyList(),
    val steps: List<StepResult> = emptyList()
)

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
    val durationMs: Long,
    val passed: Boolean,
    val error: String? = null
)
