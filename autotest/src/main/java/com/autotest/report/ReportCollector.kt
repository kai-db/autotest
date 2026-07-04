package com.autotest.report

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import com.autotest.stability.FlakyClassifier

class ReportCollector : TestWatcher() {
    private var startTime: Long = 0L
    private val failures = mutableListOf<Failure>()
    private val stepResults = mutableListOf<StepResult>()
    private val aiAssertions = mutableListOf<AiAssertion>()

    /**
     * 失败截图路径提供器：由 [com.autotest.base.BaseUiTest] 接到 ScreenshotInterceptor，
     * 测试失败时回填 [Failure.screenshots]，补全证据链。默认 null（无来源时不改变原行为）。
     */
    var screenshotProvider: (() -> List<String>)? = null

    /**
     * 定位自愈事件提供器：由 [com.autotest.base.BaseUiTest] 接到 SelfHealingLocator，
     * 报告生成时回填 [RunReport.healingEvents]。默认 null。
     */
    var healingEventsProvider: (() -> List<com.autotest.selector.LocatorEvent>)? = null

    /**
     * logcat 提供器：测试失败时取设备日志做根因分析（crash/ANR/OOM 签名），
     * 回填 [Failure.rootCause]。默认 null（不分析）。
     */
    var logcatProvider: (() -> String)? = null

    /**
     * 危险操作守卫事件提供器：由 BaseUiTest 接到 DangerousOpsGuard，
     * 报告生成时回填 [RunReport.guardEvents]。默认 null。
     */
    var guardEventsProvider: (() -> List<com.autotest.safety.GuardEvent>)? = null

    /** 根因分析时的包名过滤（ANR 判定用），由 BaseUiTest 注入 */
    var rootCausePackage: String? = null

    /** 留痕通道（E6）：内部降级（如根因分析失败）必须可见；由 BaseUiTest 注入 */
    var logger: com.autotest.log.TestLogger? = null

    override fun starting(description: Description) {
        startTime = System.currentTimeMillis() // 墙钟 epoch：RunReport.startTime 展示语义（HtmlReporter 按 Date 格式化），勿改单调钟
    }

    public override fun failed(e: Throwable?, description: Description) {
        val message = e?.message ?: e?.toString() ?: "unknown"
        val flakyType = FlakyClassifier.classify(message)
        val rootCause = try {
            logcatProvider?.invoke()?.let { com.autotest.diagnosis.LogcatAnalyzer.analyze(it, rootCausePackage) }
        } catch (ex: Throwable) {
            // 根因分析失败不能影响失败记录本身（logcat 不可得时降级为无根因），但降级必须留痕（E6）
            logger?.w("Report", "根因分析失败，降级为无根因: ${ex.javaClass.simpleName}: ${ex.message}")
            null
        }
        failures.add(
            Failure(
                className = description.className ?: "",
                methodName = description.methodName ?: "",
                message = message,
                screenshots = screenshotProvider?.invoke()?.takeIf { it.isNotEmpty() },
                flakyType = flakyType,
                rootCause = rootCause
            )
        )
    }

    fun addStepResult(result: StepResult) {
        stepResults.add(result)
    }

    fun addAiAssertion(assertion: AiAssertion) {
        aiAssertions.add(assertion)
    }

    fun buildReport(
        appPackage: String,
        endTime: Long = System.currentTimeMillis(), // 墙钟 epoch：RunReport.endTime 展示语义，勿改单调钟
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
            summary = ReportSummary.from(steps),
            aiAssertions = aiAssertions.toList(),
            healingEvents = healingEventsProvider?.invoke() ?: emptyList(),
            guardEvents = guardEventsProvider?.invoke() ?: emptyList()
        )
    }
}
