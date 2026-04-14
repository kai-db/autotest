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

    @Test
    fun loadFromMarkdown_registersTests() {
        val md = """
            ## P0 — 核心

            ### TC-001 冷启动
            - 步骤：启动 App
            - 验证：到达首页

            ### TC-002 导航
            - 步骤：点击 Tab
            - 验证：页面正常

            ## P1 — 功能

            ### TC-003 登录
            - 步骤：输入账号
            - 步骤：点击登录
            - 验证：登录成功
        """.trimIndent()

        val executed = mutableListOf<String>()
        suite.loadFromMarkdown(md) { testId, desc ->
            executed.add("$testId:$desc")
        }

        assertEquals(3, suite.getTestCount())
        assertEquals(2, suite.getTestsByPriority(TestSuite.Priority.P0).size)
        assertEquals(1, suite.getTestsByPriority(TestSuite.Priority.P1).size)

        // 执行并验证步骤被调用
        suite.runAll()
        assertTrue(executed.contains("TC-001:启动 App"))
        assertTrue(executed.contains("TC-001:到达首页"))
        assertTrue(executed.contains("TC-003:输入账号"))
    }
}
