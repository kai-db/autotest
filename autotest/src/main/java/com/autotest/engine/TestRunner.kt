package com.autotest.engine

import com.autotest.dsl.Scenario
import com.autotest.dsl.scenario
import com.autotest.intercept.InterceptorChain
import com.autotest.lifecycle.TestLifecycleManager
import com.autotest.log.TestLogger
import com.autotest.report.ReportCollector
import com.autotest.report.ReportSummary
import com.autotest.report.ReportWriter
import com.autotest.report.RunReport
import com.autotest.report.StepResult
import com.autotest.runner.RunnerInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 测试执行引擎。
 * 编排完整的测试生命周期：环境准备 → 前置操作 → 执行用例 → 后置操作 → 生成报告。
 *
 * 参考 Kaspresso TestRunner 设计，但更轻量：
 * - 不依赖 JUnit Runner，可独立运行
 * - 集成拦截器、日志、生命周期、报告
 * - 支持单用例和批量执行
 */
class TestRunner(
    private val appPackage: String,
    private val logger: TestLogger,
    private val interceptors: InterceptorChain = InterceptorChain(),
    private val lifecycle: TestLifecycleManager = TestLifecycleManager(),
    private val reportDir: String = "/sdcard/Pictures/autotest"
) {

    private val results = mutableListOf<TestCaseResult>()

    /**
     * 执行单个测试用例。
     *
     * @param name 用例名称
     * @param before 前置操作（如启动 App、重置环境）
     * @param after 后置操作（如清理数据）
     * @param steps 测试步骤 DSL
     * @return 测试结果
     */
    fun runTest(
        name: String,
        before: (() -> Unit)? = null,
        after: (() -> Unit)? = null,
        steps: com.autotest.dsl.ScenarioBuilder.() -> Unit
    ): TestCaseResult {
        logger.i("TestRunner", "═══ 开始用例: $name ═══")
        lifecycle.fireBeforeTest(name)

        val collector = ReportCollector()
        val startTime = System.currentTimeMillis()
        var error: Throwable? = null

        try {
            // 前置操作
            before?.let {
                logger.d("TestRunner", "执行前置操作...")
                interceptors.intercept("before:$name") { it() }
            }

            // 执行步骤
            val s = scenario(name, collector, interceptors, steps)
            s.run()

            lifecycle.fireAfterTestSuccess(name)
        } catch (e: Throwable) {
            error = e
            logger.e("TestRunner", "用例失败: $name", e)
            lifecycle.fireAfterTestFailure(name, e)
        } finally {
            // 后置操作（总是执行）
            try {
                after?.let {
                    logger.d("TestRunner", "执行后置操作...")
                    it()
                }
            } catch (e: Throwable) {
                logger.e("TestRunner", "后置操作失败: ${e.message}")
            }
            lifecycle.fireAfterTestFinally(name)
        }

        val duration = System.currentTimeMillis() - startTime
        val passed = error == null
        val status = if (passed) "PASS" else "FAIL"

        logger.i("TestRunner", "═══ $status: $name (${duration}ms) ═══")

        val result = TestCaseResult(
            name = name,
            passed = passed,
            durationMs = duration,
            error = error?.message,
            steps = collector.buildReport(appPackage).steps
        )
        results.add(result)
        return result
    }

    /**
     * 获取所有已执行用例的结果。
     */
    fun getResults(): List<TestCaseResult> = results.toList()

    /**
     * 生成汇总报告。
     */
    fun generateReport(): RunReport {
        val allSteps = results.flatMap { it.steps }
        return RunReport(
            appPackage = appPackage,
            startTime = results.firstOrNull()?.let { System.currentTimeMillis() - it.durationMs } ?: 0,
            endTime = System.currentTimeMillis(),
            steps = allSteps,
            summary = ReportSummary.from(allSteps)
        )
    }

    /**
     * 将报告写入文件。
     */
    fun writeReport(): File {
        val report = generateReport()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportDir, "report_$timestamp.json")
        return ReportWriter.write(report, file)
    }

    /**
     * 生成 Markdown 格式的测试结果。
     */
    fun toMarkdown(): String {
        val sb = StringBuilder()
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        val total = results.size

        sb.appendLine("# 测试结果")
        sb.appendLine()
        sb.appendLine("| 统计项 | 值 |")
        sb.appendLine("|---|---|")
        sb.appendLine("| 总用例 | $total |")
        sb.appendLine("| 通过 | $passed |")
        sb.appendLine("| 失败 | $failed |")
        sb.appendLine("| 通过率 | ${if (total > 0) "%.1f%%".format(passed.toFloat() / total * 100) else "N/A"} |")
        sb.appendLine()

        sb.appendLine("| 用例 | 结果 | 耗时 | 失败原因 |")
        sb.appendLine("|---|---|---|---|")
        results.forEach { r ->
            val status = if (r.passed) "✅ PASS" else "❌ FAIL"
            val errorMsg = r.error?.take(50) ?: "-"
            sb.appendLine("| ${r.name} | $status | ${r.durationMs}ms | $errorMsg |")
        }

        return sb.toString()
    }

    /**
     * 重置所有结果（用于新一轮测试）。
     */
    fun reset() {
        results.clear()
    }
}

data class TestCaseResult(
    val name: String,
    val passed: Boolean,
    val durationMs: Long,
    val error: String? = null,
    val steps: List<StepResult> = emptyList()
)
