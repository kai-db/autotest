package com.autotest.report

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReportWriterTest {

    @Test
    fun reportWriter_writesJson() {
        val report = RunReport(
            appPackage = "com.example.app",
            startTime = 1000L,
            endTime = 2000L,
            device = null,
            failures = listOf(
                Failure(
                    className = "com.example.TestClass",
                    methodName = "testMethod",
                    message = "boom",
                    screenshots = null
                )
            )
        )

        val json = ReportWriter.write(report)
        assertNotNull(json)

        val parsed = Gson().fromJson(json, RunReport::class.java)
        assertEquals("com.example.app", parsed.appPackage)
        assertEquals(1000L, parsed.startTime)
        assertEquals(2000L, parsed.endTime)
        assertEquals(1, parsed.failures.size)
        assertEquals("com.example.TestClass", parsed.failures[0].className)
        assertEquals("testMethod", parsed.failures[0].methodName)
        assertEquals("boom", parsed.failures[0].message)
    }
}
