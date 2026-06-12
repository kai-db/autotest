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
            gson.fromJson<MutableMap<String, CaseHistory>>(file.readText(), type) ?: mutableMapOf()
        } catch (e: Throwable) {
            logger?.w("TestHistory", "历史库加载失败，按空库处理: ${e.message}")
            mutableMapOf()
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(entries))
        } catch (e: Throwable) {
            logger?.w("TestHistory", "历史库写入失败: ${e.message}")
        }
    }
}
