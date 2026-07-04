package com.autotest.selector

import com.autotest.log.TestLogger
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 元素指纹：某个选择器最近一次成功定位到的元素快照 + 命中统计。
 *
 * @param provisional true = 自愈/AI 命中写入的**暂定**指纹（可能是误愈）——不因命中就转正/满分，
 *   只有原始 selector 确定性命中（L1）才写权威指纹（provisional=false）。见 C1。
 */
data class ElementFingerprint(
    val selectorKey: String,
    val snapshot: ElementSnapshot,
    val hits: Int = 1,
    val lastSeenMs: Long = 0,
    val provisional: Boolean = false
)

/**
 * 指纹库：选择器键 → 元素指纹，JSON 落盘。
 * 每次确定性定位成功时回写权威指纹，作为自愈降级链（HealingEngine）的数据底座。
 *
 * 文件可进版本库：AI 探索期产出，回归期只读消费。
 */
class FingerprintStore(
    private val file: File,
    private val logger: TestLogger? = null
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val entries: MutableMap<String, ElementFingerprint> by lazy { load() }

    fun find(selectorKey: String): ElementFingerprint? = entries[selectorKey]

    fun all(): Map<String, ElementFingerprint> = entries.toMap()

    /**
     * 记录一次**权威**定位（L1 原始 selector 确定性命中）：写 provisional=false。
     * 权威指纹是「原始 selector 真的匹配到该元素」的可信证据，可覆盖此前的 provisional（转正）。
     */
    // nowMs 默认墙钟：写入持久化 lastSeenMs（跨进程/重启做 TTL 比对），勿改单调钟（重启归零）
    fun record(selectorKey: String, snapshot: ElementSnapshot, nowMs: Long = System.currentTimeMillis()) {
        val prev = entries[selectorKey]
        entries[selectorKey] = ElementFingerprint(
            selectorKey = selectorKey,
            snapshot = snapshot,
            hits = (prev?.hits ?: 0) + 1,
            lastSeenMs = nowMs,
            provisional = false
        )
        save()
    }

    /**
     * 记录一次**暂定**定位（L2 自愈 / L3 AI 命中，可能误愈，C1）：写 provisional=true。
     * - 若已有**权威**指纹 → **不降级覆盖**（权威 > 暂定）。
     * - provisional 永不因「自身快照又被命中」自动转正——只有 [record]（L1）才转正。
     */
    // nowMs 默认墙钟：同 record，持久化 lastSeenMs 语义
    fun recordProvisional(selectorKey: String, snapshot: ElementSnapshot, nowMs: Long = System.currentTimeMillis()) {
        val prev = entries[selectorKey]
        if (prev != null && !prev.provisional) {
            logger?.d("FingerprintStore", "已存在权威指纹，暂定命中不降级覆盖: $selectorKey")
            return
        }
        entries[selectorKey] = ElementFingerprint(
            selectorKey = selectorKey,
            snapshot = snapshot,
            hits = (prev?.hits ?: 0) + 1,
            lastSeenMs = nowMs,
            provisional = true
        )
        save()
    }

    private fun load(): MutableMap<String, ElementFingerprint> {
        if (!file.exists()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, ElementFingerprint>>() {}.type
            val raw: MutableMap<String, ElementFingerprint> =
                gson.fromJson(file.readText(), type) ?: return mutableMapOf()
            // B1：逐条校验，Gson 注入 null 的坏条目跳过、只放结构完整数据进内存
            val clean = mutableMapOf<String, ElementFingerprint>()
            for ((k, v) in raw) {
                val err = fingerprintError(k, v)
                if (err != null) {
                    logger?.w("FingerprintStore", "指纹条目损坏已跳过 [$k]: $err")
                } else {
                    clean[k] = v
                }
            }
            clean
        } catch (e: Throwable) {
            logger?.w("FingerprintStore", "指纹库加载失败，按空库处理: ${e.message}")
            mutableMapOf()
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    private fun fingerprintError(key: String, fp: ElementFingerprint?): String? {
        if (fp == null) return "条目为 null"
        if (fp.selectorKey == null || fp.selectorKey.isBlank()) return "selectorKey 为空"
        if (fp.snapshot == null) return "snapshot 为 null"
        if (!fp.snapshot.isStructurallyValid()) return "snapshot 字段缺失"
        return null
    }

    private fun save() {
        // B2 原子写：进程中断不留半截 JSON、不静默清空指纹资产
        com.autotest.util.AtomicFileWriter.writeText(file, gson.toJson(entries), logger)
    }
}
