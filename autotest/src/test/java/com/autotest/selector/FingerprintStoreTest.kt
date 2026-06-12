package com.autotest.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FingerprintStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val snapshot = ElementSnapshot(text = "登录", resourceId = "com.app:id/btn_login")

    @Test
    fun `记录后可查到指纹`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        store.record("text:exact:登录", snapshot, nowMs = 1000)

        val fp = store.find("text:exact:登录")!!
        assertEquals(snapshot, fp.snapshot)
        assertEquals(1, fp.hits)
        assertEquals(1000L, fp.lastSeenMs)
    }

    @Test
    fun `未记录的键返回 null`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        assertNull(store.find("text:exact:不存在"))
    }

    @Test
    fun `重复记录累计命中数并更新快照`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        store.record("k", snapshot, nowMs = 1000)
        store.record("k", snapshot.copy(text = "立即登录"), nowMs = 2000)

        val fp = store.find("k")!!
        assertEquals(2, fp.hits)
        assertEquals("立即登录", fp.snapshot.text)
        assertEquals(2000L, fp.lastSeenMs)
    }

    @Test
    fun `落盘后新实例可加载（跨 run 持久化）`() {
        val file = tmp.root.resolve("fp.json")
        FingerprintStore(file).record("k", snapshot, nowMs = 1000)

        val reloaded = FingerprintStore(file)
        assertEquals(snapshot, reloaded.find("k")!!.snapshot)
    }

    @Test
    fun `指纹库文件损坏时按空库处理不抛异常`() {
        val file = tmp.root.resolve("fp.json")
        file.writeText("{ 这不是合法 JSON")

        val store = FingerprintStore(file)
        assertNull(store.find("k"))
        // 仍可正常写入恢复
        store.record("k", snapshot)
        assertEquals(1, store.find("k")!!.hits)
    }
}
