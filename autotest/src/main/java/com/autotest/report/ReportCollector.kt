package com.autotest.report

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import com.autotest.stability.FlakyClassifier
import com.autotest.stability.RetryPolicy

class ReportCollector : TestWatcher() {
    private var startTime: Long = 0L
    private val failures = mutableListOf<Failure>()
    private val stepResults = mutableListOf<StepResult>()
    private var retryPolicy: RetryPolicy? = null
    private var flakyClassifier: FlakyClassifier = FlakyClassifier

    fun setStability(
        retryPolicy: RetryPolicy?,
        flakyClassifier: FlakyClassifier = FlakyClassifier
    ) {
        this.retryPolicy = retryPolicy
        this.flakyClassifier = flakyClassifier
    }

    override fun starting(description: Description) {
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
        }
    }

    override fun failed(e: Throwable?, description: Description) {
        val message = e?.message ?: e?.toString() ?: "unknown"
        val flakyType = flakyClassifier.classify(message)
        failures.add(
            Failure(
                className = description.className ?: "",
                methodName = description.methodName ?: "",
                message = message,
                screenshots = null,
                flakyType = flakyType,
                retryPolicy = retryPolicy
            )
        )
    }

    fun addStepResult(result: StepResult) {
        stepResults.add(result)
    }

    fun buildReport(
        appPackage: String,
        endTime: Long = System.currentTimeMillis(),
        device: String? = null,
        runnerInfo: com.autotest.runner.RunnerInfo? = null
    ): RunReport {
        val resolvedStart = if (startTime == 0L) endTime else startTime
        return RunReport(
            appPackage = appPackage,
            startTime = resolvedStart,
            endTime = endTime,
            device = device,
            runnerInfo = runnerInfo,
            failures = failures.toList(),
            steps = stepResults.toList()
        )
    }
}

object ReportCollectorHolder {
    val shared = ReportCollector()
}
