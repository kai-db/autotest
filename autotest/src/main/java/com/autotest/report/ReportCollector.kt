package com.autotest.report

import org.junit.rules.TestWatcher
import org.junit.runner.Description

class ReportCollector : TestWatcher() {
    private var startTime: Long = 0L
    private val failures = mutableListOf<Failure>()

    override fun starting(description: Description) {
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
        }
    }

    override fun failed(e: Throwable?, description: Description) {
        val message = e?.message ?: e?.toString() ?: "unknown"
        failures.add(
            Failure(
                className = description.className ?: "",
                methodName = description.methodName ?: "",
                message = message,
                screenshots = null
            )
        )
    }

    fun buildReport(
        appPackage: String,
        endTime: Long = System.currentTimeMillis(),
        device: String? = null
    ): RunReport {
        val resolvedStart = if (startTime == 0L) endTime else startTime
        return RunReport(
            appPackage = appPackage,
            startTime = resolvedStart,
            endTime = endTime,
            device = device,
            failures = failures.toList()
        )
    }
}
