package com.autotest.stability

import com.autotest.log.TestLogger
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/** 单个用例的执行历史（滚动窗口） */
data class CaseHistory(
    val outcomes: List<Boolean> = emptyList(),
    val updatedAtMs: Long = 0
) {
    /** 历史通过率；无记录返回 null */
    fun passRate(): Double? =
        outcomes.takeIf { it.isNotEmpty() }?.let { it.count { o -> o } .toDouble() / it.size }
}

/**
 * 用例执行历史库：caseId → 最近 N 次的 pass/fail，JSON 落盘。
 * 是自适应重试预算（AdaptiveRetryPolicy）的数据底座，借鉴 Marathon
 * "flakiness 是统计问题，用历史数据而非固定次数解决" 的理念。
 *
 * 文件可进版本库或随产物目录走，跨 run 积累。
 */
class TestHistoryStore(
    private val file: File,
    /** 每个用例保留的最近记录数（滚动窗口，太老的历史会失真） */
    private val windowSize: Int = 50,
    private val logger: TestLogger? = null
) {

    init {
        require(windowSize > 0) { "windowSize 必须 > 0" }
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val entries: MutableMap<String, CaseHistory> by lazy { load() }

    /** 记录一次执行结果（每次「尝试」都记，重试的每一轮是独立样本） */
    // nowMs 默认墙钟：持久化历史时间戳（跨进程/重启可读），勿改单调钟
    fun record(caseId: String, passed: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val prev = entries[caseId] ?: CaseHistory()
        entries[caseId] = CaseHistory(
            outcomes = (prev.outcomes + passed).takeLast(windowSize),
            updatedAtMs = nowMs
        )
        save()
    }

    /** 用例历史通过率；无历史返回 null */
    fun passRate(caseId: String): Double? = entries[caseId]?.passRate()

    fun history(caseId: String): CaseHistory? = entries[caseId]

    fun all(): Map<String, CaseHistory> = entries.toMap()

    private fun load(): MutableMap<String, CaseHistory> {
        if (!file.exists()) return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, CaseHistory>>() {}.type
            val raw: MutableMap<String, CaseHistory> =
                gson.fromJson(file.readText(), type) ?: return mutableMapOf()
            // B1：逐条校验，坏条目跳过。outcomes 在 Gson 下元素是 boxed Boolean，可注入 [true,null] → 后续 count NPE
            val clean = mutableMapOf<String, CaseHistory>()
            for ((k, v) in raw) {
                val err = historyError(v)
                if (err != null) {
                    logger?.w("TestHistory", "历史条目损坏已跳过 [$k]: $err")
                } else {
                    clean[k] = v
                }
            }
            clean
        } catch (e: Throwable) {
            logger?.w("TestHistory", "历史库加载失败，按空库处理: ${e.message}")
            mutableMapOf()
        }
    }

    @Suppress("SENSELESS_COMPARISON", "USELESS_CAST")
    private fun historyError(h: CaseHistory?): String? {
        if (h == null) return "条目为 null"
        if (h.outcomes == null) return "outcomes 为 null"
        // 转 List<Boolean?> 再判 null：直接 `it == null`（it: Boolean）会在拆箱时对 null 元素抛 NPE
        if ((h.outcomes as List<Boolean?>).any { it == null }) return "outcomes 含 null 元素"
        return null
    }

    private fun save() {
        // B2 原子写：进程中断不留半截 JSON、不静默清空历史资产
        com.autotest.util.AtomicFileWriter.writeText(file, gson.toJson(entries), logger)
    }
}
