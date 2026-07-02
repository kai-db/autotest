package com.autotest.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DangerousOpsGuardTest {

    private fun target(
        text: String = "",
        resId: String = "",
        desc: String = ""
    ) = ClickTarget(text = text, resourceId = resId, contentDesc = desc)

    // ==================== 危险词命中 ====================

    @Test
    fun `text 命中危险词默认阻断并记 DANGEROUS_BLOCKED`() {
        val guard = DangerousOpsGuard()
        try {
            guard.checkClick(target(text = "确认转账"))
            fail("应抛 DangerousOperationException")
        } catch (e: DangerousOperationException) {
            assertTrue(e.message!!.contains("转账"))
        }
        val event = guard.events.single()
        assertEquals(GuardEventType.DANGEROUS_BLOCKED, event.type)
        assertEquals("转账", event.matchedWord)
    }

    @Test
    fun `contentDesc 与 resourceId 同样参与比对`() {
        val guard = DangerousOpsGuard()
        try {
            guard.checkClick(target(desc = "删除钱包按钮"))
            fail("desc 命中应阻断")
        } catch (_: DangerousOperationException) {
        }
        try {
            guard.checkClick(target(resId = "com.app:id/btn_transfer_confirm", text = "OK"))
            fail("res-id 命中 Transfer 应阻断（大小写不敏感）")
        } catch (_: DangerousOperationException) {
        }
    }

    @Test
    fun `allowDangerous 显式放行并记 DANGEROUS_ALLOWED 留痕`() {
        val guard = DangerousOpsGuard()
        guard.checkClick(target(text = "确认转账"), allowDangerous = true) // 不抛
        val event = guard.events.single()
        assertEquals(GuardEventType.DANGEROUS_ALLOWED, event.type)
        assertEquals("转账", event.matchedWord)
    }

    @Test
    fun `普通文本不命中直接放行且不记事件`() {
        val guard = DangerousOpsGuard()
        guard.checkClick(target(text = "查看详情"))
        assertTrue(guard.events.isEmpty())
    }

    // ==================== 不可审计目标（fail-closed） ====================

    @Test
    fun `全空字段目标默认阻断（fail-closed）`() {
        val guard = DangerousOpsGuard()
        try {
            guard.checkClick(target())
            fail("不可审计目标应默认阻断")
        } catch (e: DangerousOperationException) {
            assertTrue(e.message!!.contains("不可审计"))
        }
        assertEquals(GuardEventType.UNVERIFIABLE_BLOCKED, guard.events.single().type)
    }

    @Test
    fun `allowUnverifiable 显式放行不可审计目标并留痕`() {
        val guard = DangerousOpsGuard()
        guard.checkClick(target(), allowUnverifiable = true)
        assertEquals(GuardEventType.UNVERIFIABLE_ALLOWED, guard.events.single().type)
    }

    @Test
    fun `allowDangerous 不能替代 allowUnverifiable（两开关语义独立）`() {
        val guard = DangerousOpsGuard()
        try {
            guard.checkClick(target(), allowDangerous = true)
            fail("allowDangerous 不应放行不可审计目标")
        } catch (_: DangerousOperationException) {
        }
    }

    @Test
    fun `有 res-id 无文本的目标可审计，不按 UNVERIFIABLE 处理`() {
        val guard = DangerousOpsGuard()
        guard.checkClick(target(resId = "com.app:id/btn_next")) // 可审计且未命中 → 放行
        assertTrue(guard.events.isEmpty())
    }

    // ==================== 词表组装 ====================

    @Test
    fun `词表默认合并内置与项目词表`() {
        val words = DangerousOpsGuard.buildWordlist(listOf("项目专属危险词"))
        assertTrue(words.containsAll(DangerousOpsGuard.DEFAULT_DANGEROUS_TEXTS))
        assertTrue(words.contains("项目专属危险词"))
    }

    @Test
    fun `replace=true 才完全替换内置词表`() {
        val words = DangerousOpsGuard.buildWordlist(listOf("仅此一词"), replace = true)
        assertEquals(listOf("仅此一词"), words)
    }

    @Test
    fun `replace=true 但项目词表为空时回退内置（防守卫被掏空）`() {
        val words = DangerousOpsGuard.buildWordlist(emptyList(), replace = true)
        assertEquals(DangerousOpsGuard.DEFAULT_DANGEROUS_TEXTS, words)
    }

    @Test
    fun `词长小于 2 的词条被剔除（单字 contains 误命中面过大）`() {
        val guard = DangerousOpsGuard(dangerousTexts = listOf("删", "转账"))
        assertNull(guard.matchWord(target(text = "删除草稿"))) // 单字「删」不生效
        assertEquals("转账", guard.matchWord(target(text = "确认转账")))
    }

    // ==================== 非抛出式查询与禁用留痕 ====================

    @Test
    fun `matchWord 非抛出式查询供弹窗拦截器筛候选`() {
        val guard = DangerousOpsGuard()
        assertEquals("登出", guard.matchWord(target(text = "登出")))
        assertNull(guard.matchWord(target(text = "取消")))
        assertTrue(guard.events.isEmpty()) // 查询不记事件
    }

    @Test
    fun `recordDisabled 留 GUARD_DISABLED 审计事件`() {
        val guard = DangerousOpsGuard()
        guard.recordDisabled()
        assertEquals(GuardEventType.GUARD_DISABLED, guard.events.single().type)
    }

    // ==================== 留证 ====================

    @Test
    fun `阻断时调用 evidenceCapture 并把截图路径写进事件`() {
        var captured: String? = null
        val guard = DangerousOpsGuard(evidenceCapture = { prefix ->
            captured = prefix
            "/artifacts/$prefix.png"
        })
        try {
            guard.checkClick(target(text = "确认转账"))
            fail()
        } catch (_: DangerousOperationException) {
        }
        assertEquals("guard_dangerous", captured)
        assertEquals("/artifacts/guard_dangerous.png", guard.events.single().screenshotPath)
    }

    @Test
    fun `evidenceCapture 自身异常不影响阻断（守卫必须先于截图可靠）`() {
        val guard = DangerousOpsGuard(evidenceCapture = { throw RuntimeException("截图失败") })
        try {
            guard.checkClick(target(text = "确认转账"))
            fail("截图失败也必须阻断")
        } catch (_: DangerousOperationException) {
        }
        assertNull(guard.events.single().screenshotPath)
    }
}
