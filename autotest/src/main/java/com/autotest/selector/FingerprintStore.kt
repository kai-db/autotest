package com.autotest.selector

import com.autotest.log.TestLogger
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/** 元素指纹：某个选择器最近一次成功定位到的元素快照 + 命中统计 */
data class ElementFingerprint(
    val selectorKey: String,
    val snapshot: ElementSnapshot,
    val hits: Int = 1,
    val lastSeenMs: Long = 0
)

/**
 * 指纹库：选择器键 → 元素指纹，JSON 落盘。
 * 每次确定性定位成功时回写指纹，作为自愈降级链（HealingEngine）的数据底座。
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

    /** 记录一次成功定位：更新指纹快照与命中数并落盘 */
    fun record(selectorKey: String, snapshot: ElementSnapshot, nowMs: Long = System.currentTimeMillis()) {
        val prev = entries[selectorKey]
        entries[selectorKey] = ElementFingerprint(
            selectorKey = selectorKey,
            snapshot = snapshot,
            hits = (prev?.hits ?: 0) + 1,
            lastSeenMs = nowMs
        )
        save()
    }

    private fun load(): MutableMap<String, ElementFingerprint> {
        if (!file.exists()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, ElementFingerprint>>() {}.type
            gson.fromJson<MutableMap<String, ElementFingerprint>>(file.readText(), type) ?: mutableMapOf()
        } catch (e: Throwable) {
            // 指纹库损坏按冷启动处理（自愈能力降级但不阻断测试），留痕不静默
            logger?.w("FingerprintStore", "指纹库加载失败，按空库处理: ${e.message}")
            mutableMapOf()
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(entries))
        } catch (e: Throwable) {
            logger?.w("FingerprintStore", "指纹库写入失败: ${e.message}")
        }
    }
}
