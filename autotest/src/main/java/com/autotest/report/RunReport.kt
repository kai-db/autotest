package com.autotest.report

data class RunReport(
    val appPackage: String,
    val startTime: Long,
    val endTime: Long,
    val device: String? = null,
    val failures: List<Failure> = emptyList()
)

data class Failure(
    val className: String,
    val methodName: String,
    val message: String,
    val screenshots: List<String>? = null
)
