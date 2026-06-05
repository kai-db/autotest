package com.autotest.report

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import com.autotest.stability.FlakyClassifier

class ReportCollector : TestWatcher() {
    private var startTime: Long = 0L
    private val failures = mutableListOf<Failure>()
    private val stepResults = mutableListOf<StepResult>()

    /**
     * 失败截图路径提供器：由 [com.autotest.base.BaseUiTest] 接到 ScreenshotInterceptor，
     * 测试失败时回填 [Failure.screenshots]，补全证据链。默认 null（无来源时不改变原行为）。
     */
    var screenshotProvider: (() -> List<String>)? = null

    override fun starting(description: Description) {
        startTime = System.currentTimeMillis()
    }

    public override fun failed(e: Throwable?, description: Description) {
        val message = e?.message ?: e?.toString() ?: "unknown"
        val flakyType = FlakyClassifier.classify(message)
        failures.add(
            Failure(
                className = description.className ?: "",
                methodName = description.methodName ?: "",
                message = message,
                screenshots = screenshotProvider?.invoke()?.takeIf { it.isNotEmpty() },
                flakyType = flakyType
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
        val steps = stepResults.toList()
        return RunReport(
            appPackage = appPackage,
            startTime = resolvedStart,
            endTime = endTime,
            device = device,
            runnerInfo = runnerInfo,
            failures = failures.toList(),
            steps = steps,
            summary = ReportSummary.from(steps)
        )
    }
}
