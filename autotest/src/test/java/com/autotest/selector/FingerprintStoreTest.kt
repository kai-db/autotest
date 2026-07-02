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

    // ---- C1 provisional 语义 ----

    @Test
    fun `record 写权威指纹 provisional=false`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        store.record("k", snapshot)
        assertEquals(false, store.find("k")!!.provisional)
    }

    @Test
    fun `recordProvisional 写暂定指纹 provisional=true`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        store.recordProvisional("k", snapshot)
        assertEquals(true, store.find("k")!!.provisional)
    }

    @Test
    fun `已有权威指纹时 recordProvisional 不降级覆盖`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        store.record("k", snapshot)                       // 权威
        store.recordProvisional("k", snapshot.copy(text = "误愈")) // 尝试用暂定覆盖
        val fp = store.find("k")!!
        assertEquals("权威不应被暂定降级覆盖", false, fp.provisional)
        assertEquals("登录", fp.snapshot.text)
    }

    @Test
    fun `权威命中可覆盖此前的暂定（转正）`() {
        val store = FingerprintStore(tmp.root.resolve("fp.json"))
        store.recordProvisional("k", snapshot)  // 暂定
        store.record("k", snapshot)             // L1 原始 selector 命中 → 转正
        assertEquals(false, store.find("k")!!.provisional)
    }

    // ---- B1 反序列化坏数据过滤 ----

    @Test
    fun `snapshot 字段显式为 null 的坏指纹条目被跳过`() {
        val file = tmp.root.resolve("fp.json")
        // 手编 JSON：snapshot 的 String 字段显式 null（Gson 会把 null 写进非空字段）
        file.writeText(
            """{"k":{"selectorKey":"k","snapshot":{"text":"x","resourceId":null,"contentDesc":null,"className":null,"packageName":null,"bounds":null},"hits":1,"lastSeenMs":0}}"""
        )
        val store = FingerprintStore(file)
        // snapshot 有 null 字段 → 结构不完整 → 跳过
        assertNull(store.find("k"))
    }

    @Test
    fun `snapshot 为 null 的坏条目被跳过`() {
        val file = tmp.root.resolve("fp.json")
        file.writeText("""{"k":{"selectorKey":"k","hits":1,"lastSeenMs":0}}""")
        val store = FingerprintStore(file)
        assertNull(store.find("k"))
    }
}
