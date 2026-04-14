package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestSuiteTest {

    private lateinit var runner: TestRunner
    private lateinit var suite: TestSuite

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {}
        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Before
    fun setUp() {
        runner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        suite = TestSuite("测试套件", runner, fakeLogger)
    }

    @Test
    fun runAll_executesAllTests() {
        suite.addTest("TC-001", "测试A", TestSuite.Priority.P0) {
            step("s") {}
        }
        suite.addTest("TC-002", "测试B", TestSuite.Priority.P1) {
            step("s") {}
        }

        val results = suite.runAll()
        assertEquals(2, results.size)
        assertTrue(results.all { it.passed })
    }

    @Test
    fun runAll_sortsByPriority() {
        val order = mutableListOf<String>()

        suite.addTest("TC-003", "P2测试", TestSuite.Priority.P2) {
            step("s") { order.add("P2") }
        }
        suite.addTest("TC-001", "P0测试", TestSuite.Priority.P0) {
            step("s") { order.add("P0") }
        }
        suite.addTest("TC-002", "P1测试", TestSuite.Priority.P1) {
            step("s") { order.add("P1") }
        }

        suite.runAll()
        assertEquals(listOf("P0", "P1", "P2"), order)
    }

    @Test
    fun runAll_capturesFailures() {
        suite.addTest("TC-001", "通过", TestSuite.Priority.P0) {
            step("ok") {}
        }
        suite.addTest("TC-002", "失败", TestSuite.Priority.P0) {
            step("boom") { throw RuntimeException("error") }
        }

        val results = suite.runAll()
        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertFalse(results[1].passed)
    }

    @Test
    fun getTestCount() {
        suite.addTest("TC-001", "A") { step("s") {} }
        suite.addTest("TC-002", "B") { step("s") {} }
        assertEquals(2, suite.getTestCount())
    }

    @Test
    fun getTestsByPriority() {
        suite.addTest("TC-001", "A", TestSuite.Priority.P0) { step("s") {} }
        suite.addTest("TC-002", "B", TestSuite.Priority.P1) { step("s") {} }
        suite.addTest("TC-003", "C", TestSuite.Priority.P0) { step("s") {} }

        assertEquals(2, suite.getTestsByPriority(TestSuite.Priority.P0).size)
        assertEquals(1, suite.getTestsByPriority(TestSuite.Priority.P1).size)
    }
}
