package com.autotest.stability

enum class FlakyType {
    FLAKY,
    HARD_FAIL
}

/**
 * Flaky 测试分类器。
 * 根据错误消息判定失败类型：可重试的 FLAKY 还是确定性的 HARD_FAIL。
 *
 * FLAKY 关键词覆盖：
 * - 超时类：timeout/timed out/超时
 * - Espresso 类：no matching view/not found after/not idle/app is busy
 * - UiAutomator 类：stale object/unfounded ui object
 * - 通用类：找不到/未找到
 */
object FlakyClassifier {

    private val FLAKY_PATTERNS = listOf(
        // 超时
        "timeout",
        "timed out",
        "超时",
        // Espresso
        "no matching view",
        "nomatchingviewexception",
        "not found after",
        "not idle",
        "app is busy",
        "appnotidleexception",
        "performexception",
        // UiAutomator
        "stale object",
        "staleobjectexception",
        "unfounded ui object",
        // 通用
        "找不到",
        "未找到"
    )

    fun classify(message: String?): FlakyType {
        if (message.isNullOrEmpty()) return FlakyType.HARD_FAIL
        val lowered = message.lowercase()
        return if (FLAKY_PATTERNS.any { lowered.contains(it) }) {
            FlakyType.FLAKY
        } else {
            FlakyType.HARD_FAIL
        }
    }
}
