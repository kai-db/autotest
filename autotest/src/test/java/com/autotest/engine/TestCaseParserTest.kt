package com.autotest.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TestCaseParserTest {

    @Test
    fun parse_singleCase() {
        val md = """
            ## P0 — 编译

            ### TC-001 框架编译
            - 步骤：执行 gradlew compileReleaseKotlin
            - 验证：编译成功，无错误
        """.trimIndent()

        val cases = TestCaseParser.parse(md)
        assertEquals(1, cases.size)
        assertEquals("TC-001", cases[0].id)
        assertEquals("框架编译", cases[0].name)
        assertEquals("P0", cases[0].priority)
        assertEquals(1, cases[0].steps.size)
        assertEquals(1, cases[0].verifications.size)
    }

    @Test
    fun parse_multipleCases() {
        val md = """
            ## P0 — 核心

            ### TC-001 冷启动
            - 步骤：启动 App
            - 验证：3秒内到达首页

            ### TC-002 Tab导航
            - 步骤：依次点击底部 Tab
            - 步骤：每次截图确认
            - 验证：每个 Tab 页面正常

            ## P1 — 功能

            ### TC-003 登录
            - 步骤：输入手机号
            - 步骤：点击登录
            - 验证：登录成功
        """.trimIndent()

        val cases = TestCaseParser.parse(md)
        assertEquals(3, cases.size)

        assertEquals("P0", cases[0].priority)
        assertEquals("P0", cases[1].priority)
        assertEquals("P1", cases[2].priority)

        assertEquals(1, cases[0].steps.size)
        assertEquals(2, cases[1].steps.size)
        assertEquals(2, cases[2].steps.size)
    }

    @Test
    fun parse_emptyMarkdown() {
        val cases = TestCaseParser.parse("")
        assertEquals(0, cases.size)
    }

    @Test
    fun parse_noCases() {
        val md = """
            # 标题
            一些描述文本
            ## 不是用例的标题
        """.trimIndent()

        val cases = TestCaseParser.parse(md)
        assertEquals(0, cases.size)
    }

    @Test
    fun parse_chineseColon() {
        val md = """
            ## P0 — 测试

            ### TC-001 测试
            - 步骤：中文冒号步骤
            - 验证：中文冒号验证
        """.trimIndent()

        val cases = TestCaseParser.parse(md)
        assertEquals("中文冒号步骤", cases[0].steps[0])
        assertEquals("中文冒号验证", cases[0].verifications[0])
    }
}
