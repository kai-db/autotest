package com.autotest.stability

enum class FlakyType {
    FLAKY,
    HARD_FAIL
}

/**
 * 自定义分类规则：返回 null 表示不认识该消息，交给下一个规则/内置规则判定。
 * 用于项目特化的 flaky 模式（如某 App 特有的弱网报错文案）。
 */
fun interface FlakyRule {
    fun classify(message: String): FlakyType?
}

/**
 * Flaky 测试分类器。
 * 根据错误消息判定失败类型：可重试的 FLAKY 还是确定性的 HARD_FAIL。
 *
 * 判定顺序：自定义规则链（注册序）→ 内置关键词 → 内置正则 → HARD_FAIL（保守兜底）。
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

    /**
     * 内置正则模式：关键词 contains 表达不了的结构化消息。
     * 收得很窄，宁可漏判 FLAKY（多跑一次人工看），不可把真 bug 误判成 FLAKY。
     */
    private val FLAKY_REGEXES = listOf(
        // 输入注入失败（UiAutomator 偶发）
        Regex("(?i)failed to inject input event"),
        // "Waited 5000ms for X" 类等待超时（Espresso/Compose/自定义等待器的常见格式）
        Regex("""(?i)waited \d+\s*ms"""),
        // instrumentation/adb 通道抖动
        Regex("(?i)connection (reset|refused) ")
    )

    private val customRules = mutableListOf<FlakyRule>()

    /** 注册项目特化规则，优先于内置规则判定 */
    fun registerRule(rule: FlakyRule) {
        customRules.add(rule)
    }

    /** 清空自定义规则（测试隔离用） */
    fun clearRules() {
        customRules.clear()
    }

    fun classify(message: String?): FlakyType {
        if (message.isNullOrEmpty()) return FlakyType.HARD_FAIL
        for (rule in customRules) {
            rule.classify(message)?.let { return it }
        }
        val lowered = message.lowercase()
        if (FLAKY_PATTERNS.any { lowered.contains(it) }) return FlakyType.FLAKY
        if (FLAKY_REGEXES.any { it.containsMatchIn(message) }) return FlakyType.FLAKY
        return FlakyType.HARD_FAIL
    }
}
