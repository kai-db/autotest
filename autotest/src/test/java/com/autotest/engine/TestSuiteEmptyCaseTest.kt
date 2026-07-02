package com.autotest.engine

import com.autotest.log.TestLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P0-3 回归测试：Markdown 解析失败的零步骤用例不得静默假 PASS——
 * 注册为必 FAIL 用例显式暴露格式漂移；未匹配的 bullet 行计数供加载方告警。
 */
class TestSuiteEmptyCaseTest {

    private lateinit var runner: TestRunner
    private lateinit var suite: TestSuite
    private val warnings = mutableListOf<String>()

    private val fakeLogger = object : TestLogger {
        override fun d(tag: String, msg: String) {}
        override fun i(tag: String, msg: String) {}
        override fun w(tag: String, msg: String) {
            warnings.add(msg)
        }

        override fun e(tag: String, msg: String, throwable: Throwable?) {}
    }

    @Before
    fun setUp() {
        runner = TestRunner(appPackage = "com.test", logger = fakeLogger)
        suite = TestSuite("测试套件", runner, fakeLogger)
    }

    @Test
    fun `零步骤用例注册为必 FAIL 而非假 PASS`() {
        val md = """
            ## P0 — 核心

            ### TC-001 格式漂移的用例
            - 操作：这行用了不支持的「操作」前缀
            - 检查：这行也不匹配

            ### TC-002 正常用例
            - 步骤：启动 App
            - 验证：到达首页
        """.trimIndent()

        suite.loadFromMarkdown(md) { _, _ -> }
        assertEquals(2, suite.getTestCount()) // 空用例不被跳过，而是注册为必 FAIL

        val results = suite.runAll()
        val empty = results.first { it.name.startsWith("TC-001") }
        val normal = results.first { it.name.startsWith("TC-002") }
        assertFalse("零步骤用例必须 FAIL", empty.passed)
        assertTrue(empty.error!!.contains("0 步骤 0 验证"))
        assertTrue(normal.passed)
    }

    @Test
    fun `未匹配 bullet 行计数并告警`() {
        val md = """
            ## P0 — 核心

            ### TC-001 混用格式
            - 步骤：启动 App
            - 操作：未匹配行一
            * 步骤：星号 bullet 未匹配
            - 验证：到达首页
        """.trimIndent()

        val parsed = TestCaseParser.parse(md)
        assertEquals(2, parsed.single().unmatchedBullets)

        suite.loadFromMarkdown(md) { _, _ -> }
        assertTrue(warnings.any { it.contains("未被解析") })
    }
}
