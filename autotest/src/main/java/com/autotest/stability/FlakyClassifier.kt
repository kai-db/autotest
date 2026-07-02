package com.autotest.stability

enum class FlakyType {
    FLAKY,
    HARD_FAIL
}

/**
 * 自定义分类规则：返回 null 表示不认识该消息，交给下一个规则/内置规则判定。
 * 用于项目特化的 flaky 模式（如某 App 特有的弱网报错文案）。
 * 入参非空（classifier 已对 null/空消息提前判 HARD_FAIL），规则内可直接 `message.contains(...)`。
 */
fun interface FlakyRule {
    fun classify(message: String): FlakyType?
}

/**
 * Flaky 分类器接口（可注入，替代原来直接依赖全局单例的假注入，D2）。
 */
interface FlakyClassifierApi {
    /**
     * 按异常**类型**优先判定，再回落消息规则。
     * 类型级判定不受 message 措辞影响——NPE/IllegalArgument 等归 HARD_FAIL，
     * 避免被包装出的「超时」文案误判成 FLAKY（配合 flakySafely P0-2）。
     */
    fun classify(t: Throwable): FlakyType

    /** 仅按消息判定（兼容旧调用点，如 ReportCollector 只有 message） */
    fun classify(message: String?): FlakyType
}

/**
 * 默认 Flaky 分类器：**不可变实例，规则构造注入**（不再有全局可变状态）。
 *
 * 判定顺序：类型级 HARD_FAIL 白名单 → 自定义规则（注册序）→ 内置关键词/正则 → HARD_FAIL（保守兜底）。
 */
class DefaultFlakyClassifier(
    private val rules: List<FlakyRule> = emptyList()
) : FlakyClassifierApi {

    override fun classify(t: Throwable): FlakyType {
        // 类型级：这些几乎总是真 bug，不因 message 含 "timeout/找不到" 等字样被误判 FLAKY
        if (HARD_FAIL_TYPES.any { it.isInstance(t) }) return FlakyType.HARD_FAIL
        return classify(t.message)
    }

    override fun classify(message: String?): FlakyType {
        if (message.isNullOrEmpty()) return FlakyType.HARD_FAIL
        for (rule in rules) {
            rule.classify(message)?.let { return it }
        }
        val lowered = message.lowercase()
        if (FLAKY_PATTERNS.any { lowered.contains(it) }) return FlakyType.FLAKY
        if (FLAKY_REGEXES.any { it.containsMatchIn(message) }) return FlakyType.FLAKY
        return FlakyType.HARD_FAIL
    }

    companion object {
        /** 类型级 HARD_FAIL：真 bug 家族，判定不受 message 影响 */
        private val HARD_FAIL_TYPES: List<Class<out Throwable>> = listOf(
            NullPointerException::class.java,
            IllegalArgumentException::class.java,
            IndexOutOfBoundsException::class.java,
            ClassCastException::class.java,
            ConcurrentModificationException::class.java
        )

        private val FLAKY_PATTERNS = listOf(
            // 超时
            "timeout", "timed out", "超时",
            // Espresso
            "no matching view", "nomatchingviewexception", "not found after",
            "not idle", "app is busy", "appnotidleexception", "performexception",
            // UiAutomator
            "stale object", "staleobjectexception", "unfounded ui object",
            // 通用
            "找不到", "未找到"
        )

        /**
         * 内置正则：关键词 contains 表达不了的结构化消息。收得很窄，宁可漏判 FLAKY，不把真 bug 误判 FLAKY。
         */
        private val FLAKY_REGEXES = listOf(
            Regex("(?i)failed to inject input event"),
            Regex("""(?i)waited \d+\s*ms"""),
            Regex("(?i)connection (reset|refused) ")
        )
    }
}

/**
 * 全局默认分类器（**兼容入口**）：内置规则委托给一个不可变 [DefaultFlakyClassifier]。
 *
 * 历史上通过 [registerRule]/[clearRules] 维护全局可变规则链——已 @Deprecated（跨 suite 泄漏）。
 * 新代码请构造 `DefaultFlakyClassifier(rules)` 实例并注入 RetryRunner，而非改全局状态。
 */
object FlakyClassifier : FlakyClassifierApi {

    private val builtin = DefaultFlakyClassifier()
    private val customRules = mutableListOf<FlakyRule>()

    @Deprecated(
        "全局可变规则链会跨 suite 泄漏；改用 DefaultFlakyClassifier(rules) 实例并注入 RetryRunner",
        ReplaceWith("DefaultFlakyClassifier(listOf(rule))")
    )
    fun registerRule(rule: FlakyRule) {
        customRules.add(rule)
    }

    @Deprecated("配合已废弃的 registerRule；改用不可变 DefaultFlakyClassifier 实例")
    fun clearRules() {
        customRules.clear()
    }

    override fun classify(t: Throwable): FlakyType {
        if (customRules.isEmpty()) return builtin.classify(t)
        return DefaultFlakyClassifier(customRules).classify(t)
    }

    override fun classify(message: String?): FlakyType {
        if (customRules.isEmpty()) return builtin.classify(message)
        return DefaultFlakyClassifier(customRules).classify(message)
    }
}
