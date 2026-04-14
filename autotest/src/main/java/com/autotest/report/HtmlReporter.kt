package com.autotest.report

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HTML 报告生成器。
 * 将 RunReport 转为可视化 HTML 页面，支持浏览器直接打开。
 */
object HtmlReporter {

    fun generate(report: RunReport, outputFile: File): File {
        val summary = report.summary ?: ReportSummary.from(report.steps)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val startStr = dateFormat.format(Date(report.startTime))
        val endStr = dateFormat.format(Date(report.endTime))
        val duration = (report.endTime - report.startTime) / 1000

        val stepsHtml = report.steps.joinToString("\n") { step ->
            val statusClass = if (step.passed) "pass" else "fail"
            val statusText = if (step.passed) "PASS" else "FAIL"
            val errorCell = step.error?.let { "<span class=\"error-msg\">${escapeHtml(it)}</span>" } ?: "-"
            """<tr class="$statusClass">
                <td>${escapeHtml(step.stepNumber)}</td>
                <td>${escapeHtml(step.scenarioName)}</td>
                <td>${escapeHtml(step.stepName)}</td>
                <td><span class="badge $statusClass">$statusText</span></td>
                <td>${step.durationMs}ms</td>
                <td>$errorCell</td>
            </tr>"""
        }

        val failuresHtml = report.failures.joinToString("\n") { f ->
            """<tr>
                <td>${escapeHtml(f.className)}</td>
                <td>${escapeHtml(f.methodName)}</td>
                <td>${escapeHtml(f.message)}</td>
                <td>${f.flakyType?.name ?: "-"}</td>
            </tr>"""
        }

        val deviceInfo = report.runnerInfo?.let {
            "${it.deviceManufacturer} ${it.deviceModel} (Android ${it.androidVersion}, SDK ${it.sdkVersion})"
        } ?: report.device ?: "Unknown"

        val html = """<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AutoTest Report - ${escapeHtml(report.appPackage)}</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; background: #f5f5f5; color: #333; padding: 20px; }
.container { max-width: 1200px; margin: 0 auto; }
h1 { font-size: 24px; margin-bottom: 20px; color: #1a1a1a; }
h2 { font-size: 18px; margin: 30px 0 15px; color: #333; border-bottom: 2px solid #eee; padding-bottom: 8px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 30px; }
.summary-card { background: white; border-radius: 8px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.summary-card .label { font-size: 13px; color: #888; margin-bottom: 5px; }
.summary-card .value { font-size: 28px; font-weight: 700; }
.summary-card .value.pass { color: #22c55e; }
.summary-card .value.fail { color: #ef4444; }
.meta { background: white; border-radius: 8px; padding: 15px 20px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); font-size: 14px; color: #666; }
.meta span { margin-right: 20px; }
table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
th { background: #f9fafb; text-align: left; padding: 12px 15px; font-size: 13px; color: #666; font-weight: 600; border-bottom: 1px solid #eee; }
td { padding: 10px 15px; font-size: 14px; border-bottom: 1px solid #f0f0f0; }
tr:last-child td { border-bottom: none; }
tr.fail td { background: #fef2f2; }
.badge { display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.badge.pass { background: #dcfce7; color: #166534; }
.badge.fail { background: #fee2e2; color: #991b1b; }
.error-msg { color: #dc2626; font-size: 13px; }
.footer { margin-top: 30px; text-align: center; font-size: 12px; color: #aaa; }
</style>
</head>
<body>
<div class="container">
<h1>AutoTest Report</h1>

<div class="meta">
<span>App: <strong>${escapeHtml(report.appPackage)}</strong></span>
<span>Device: <strong>$deviceInfo</strong></span>
<span>Time: $startStr ~ $endStr (${duration}s)</span>
</div>

<div class="summary-grid">
<div class="summary-card">
    <div class="label">Total Steps</div>
    <div class="value">${summary.totalSteps}</div>
</div>
<div class="summary-card">
    <div class="label">Passed</div>
    <div class="value pass">${summary.passedSteps}</div>
</div>
<div class="summary-card">
    <div class="label">Failed</div>
    <div class="value fail">${summary.failedSteps}</div>
</div>
<div class="summary-card">
    <div class="label">Pass Rate</div>
    <div class="value">${"%.1f".format(summary.passRate)}%</div>
</div>
</div>

<h2>Steps</h2>
<table>
<tr><th>#</th><th>Scenario</th><th>Step</th><th>Status</th><th>Duration</th><th>Error</th></tr>
$stepsHtml
</table>

${if (report.failures.isNotEmpty()) """
<h2>Failures</h2>
<table>
<tr><th>Class</th><th>Method</th><th>Message</th><th>Type</th></tr>
$failuresHtml
</table>
""" else ""}

<div class="footer">Generated by Android AutoTest Framework v1.2.0</div>
</div>
</body>
</html>"""

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(html)
        return outputFile
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
