package com.autotest.report

import com.google.gson.GsonBuilder
import java.io.File

object ReportWriter {
    private val gson = GsonBuilder().serializeNulls().create()

    fun write(report: RunReport): String {
        return gson.toJson(report)
    }

    fun write(report: RunReport, file: File): File {
        file.parentFile?.mkdirs()
        file.writeText(write(report))
        return file
    }
}
