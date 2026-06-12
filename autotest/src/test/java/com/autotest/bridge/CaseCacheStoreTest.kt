package com.autotest.bridge

import com.autotest.selector.ElementSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaseCacheStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val case = CachedCase(
        caseId = "TC-S-001",
        description = "冷启动进入首页",
        appPackage = "com.tm.security.wallet",
        createdAtMs = 1000,
        steps = listOf(
            CachedStep("启动 App", CachedActionType.LAUNCH_APP),
            CachedStep(
                "点击消息 Tab", CachedActionType.CLICK,
                target = ElementSnapshot(text = "消息", resourceId = "com.app:id/tab_msg")
            ),
            CachedStep("等待消息页加载", CachedActionType.WAIT_TEXT, payload = "消息")
        )
    )

    @Test
    fun `保存后可加载（roundtrip）`() {
        val store = CaseCacheStore(tmp.root)
        assertTrue(store.save(case))
        assertEquals(case, store.load("TC-S-001"))
    }

    @Test
    fun `未缓存的用例返回 null`() {
        assertNull(CaseCacheStore(tmp.root).load("TC-NOT-EXIST"))
    }

    @Test
    fun `READ_ONLY 模式拒绝写入（CI 确定性）`() {
        CaseCacheStore(tmp.root).save(case) // 先用 READ_WRITE 写入

        val readonly = CaseCacheStore(tmp.root, CacheMode.READ_ONLY)
        assertFalse(readonly.save(case.copy(description = "改动")))
        assertEquals("冷启动进入首页", readonly.load("TC-S-001")!!.description) // 未被覆盖
    }

    @Test
    fun `WRITE_ONLY 模式忽略已有缓存（强制重新探索）`() {
        CaseCacheStore(tmp.root).save(case)

        val writeOnly = CaseCacheStore(tmp.root, CacheMode.WRITE_ONLY)
        assertNull(writeOnly.load("TC-S-001"))
        assertTrue(writeOnly.save(case.copy(description = "新探索")))
    }

    @Test
    fun `缓存文件损坏按未命中处理不抛异常`() {
        tmp.root.resolve("TC-S-001.cache.json").writeText("{ 损坏的 JSON")
        assertNull(CaseCacheStore(tmp.root).load("TC-S-001"))
    }

    @Test
    fun `list 列出全部缓存用例`() {
        val store = CaseCacheStore(tmp.root)
        store.save(case)
        store.save(case.copy(caseId = "TC-S-002"))
        assertEquals(listOf("TC-S-001", "TC-S-002"), store.list())
    }

    @Test
    fun `空目录 list 返回空列表`() {
        assertEquals(emptyList<String>(), CaseCacheStore(tmp.root.resolve("not-exist")).list())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `caseId 含路径分隔符等非法字符时拒绝构造`() {
        CachedCase(caseId = "../escape", steps = emptyList())
    }
}
