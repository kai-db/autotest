package com.autotest.bridge

import com.autotest.selector.ElementSnapshot
import com.google.gson.Gson
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** B1：CachedCase/CachedStep 反序列化后递归校验——Gson Unsafe 注入的坏数据不进内存。 */
class CachedCaseValidationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val validSnap = ElementSnapshot(text = "登录", resourceId = "com.app:id/btn")

    @Test
    fun `结构完整的用例校验通过`() {
        val case = CachedCase(
            caseId = "TC-S-001",
            steps = listOf(CachedStep("点登录", CachedActionType.CLICK, target = validSnap))
        )
        assertNull(case.validationError())
    }

    @Test
    fun `steps 为空报错`() {
        val case = CachedCase(caseId = "TC-1", steps = emptyList())
        assertNotNull(case.validationError())
    }

    @Test
    fun `CLICK 缺 target 报错`() {
        val step = CachedStep("点", CachedActionType.CLICK, target = null)
        assertTrue(step.validationError()!!.contains("target"))
    }

    @Test
    fun `INPUT_TEXT 缺 payload 报错`() {
        val step = CachedStep("输入", CachedActionType.INPUT_TEXT, target = validSnap, payload = null)
        assertTrue(step.validationError()!!.contains("payload"))
    }

    @Test
    fun `Gson 注入 null 到非空字段的坏缓存被 load 拒绝`() {
        // 手编 JSON：缺 steps（Gson Unsafe 让非空 List 为 null）
        val file = tmp.root.resolve("TC-BAD.cache.json")
        file.writeText("""{"caseId":"TC-BAD"}""")
        val store = CaseCacheStore(tmp.root)
        assertNull("缺 steps 的坏缓存应按未命中处理", store.load("TC-BAD"))
    }

    @Test
    fun `caseId 逃逸文件名约束的坏缓存被拒绝`() {
        // caseId 含路径分隔（Gson 绕过 init 的 caseId 正则）
        val file = tmp.root.resolve("evil.cache.json")
        file.writeText(Gson().toJson(mapOf(
            "caseId" to "../evil",
            "steps" to listOf(mapOf("name" to "s", "action" to "CLICK", "target" to validSnap))
        )))
        val store = CaseCacheStore(tmp.root)
        assertNull("caseId 非法应被 validator 拒绝", store.load("evil"))
    }

    @Test
    fun `正常缓存 save 后 load 回来一致`() {
        val store = CaseCacheStore(tmp.root)
        val case = CachedCase("TC-OK", steps = listOf(CachedStep("点", CachedActionType.CLICK, target = validSnap)))
        assertTrue(store.save(case))
        assertNotNull(store.load("TC-OK"))
    }
}
