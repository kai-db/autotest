package com.autotest.bridge

import com.autotest.log.TestLogger
import com.google.gson.GsonBuilder
import java.io.File

/**
 * 缓存读写策略（借鉴 midscene.js）：
 * - READ_WRITE：探索期默认——读缓存、失效时更新
 * - READ_ONLY：CI 回归用——只消费缓存，保证执行确定性，禁止任何回写
 * - WRITE_ONLY：强制重新探索——忽略已有缓存，全量重写
 */
enum class CacheMode { READ_WRITE, READ_ONLY, WRITE_ONLY }

/**
 * 用例缓存库：每个用例一个 `<caseId>.cache.json`，目录可进版本库。
 */
class CaseCacheStore(
    private val dir: File,
    val mode: CacheMode = CacheMode.READ_WRITE,
    private val logger: TestLogger? = null
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(caseId: String): CachedCase? {
        if (mode == CacheMode.WRITE_ONLY) return null
        val file = fileOf(caseId)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), CachedCase::class.java)
        } catch (e: Throwable) {
            logger?.w("CaseCache", "缓存 $caseId 损坏，按未命中处理: ${e.message}")
            null
        }
    }

    /** 保存缓存；READ_ONLY 模式下拒绝写入并留痕（保证 CI 确定性） */
    fun save(case: CachedCase): Boolean {
        if (mode == CacheMode.READ_ONLY) {
            logger?.w("CaseCache", "READ_ONLY 模式拒绝写入缓存: ${case.caseId}")
            return false
        }
        return try {
            dir.mkdirs()
            fileOf(case.caseId).writeText(gson.toJson(case))
            true
        } catch (e: Throwable) {
            logger?.w("CaseCache", "缓存写入失败: ${case.caseId}: ${e.message}")
            false
        }
    }

    /** 列出已缓存的用例 ID */
    fun list(): List<String> =
        dir.listFiles { f -> f.name.endsWith(SUFFIX) }
            ?.map { it.name.removeSuffix(SUFFIX) }
            ?.sorted()
            ?: emptyList()

    private fun fileOf(caseId: String) = dir.resolve("$caseId$SUFFIX")

    companion object {
        private const val SUFFIX = ".cache.json"
    }
}
