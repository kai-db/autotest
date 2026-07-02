package com.autotest.stability

/**
 * 统一重试预算：三层重试（flakyStep / behavior 恢复重放 / RetryRunner）共享同一个尝试次数上限，
 * 防止彼此叠乘成数十次（D1）。
 *
 * 语义：[MAX_TOTAL_ATTEMPTS] 是「同一逻辑步骤」允许的总尝试次数硬顶（含首次）。
 * 每次真正要额外重试前 [tryConsume]；返回 false 表示预算耗尽，调用方必须停止重试。
 *
 * 非线程安全（单个步骤在单线程内串行重试）；跨步骤请各自 new。
 */
class RetryBudget(private val maxTotalAttempts: Int = MAX_TOTAL_ATTEMPTS) {

    init {
        require(maxTotalAttempts >= 1) { "maxTotalAttempts 至少为 1（首次执行），当前: $maxTotalAttempts" }
    }

    /** 已消耗的尝试次数（含首次） */
    var consumed: Int = 0
        private set

    /** 记一次首次执行（不占额外重试额度，但计入总数以封顶） */
    fun markFirstAttempt() {
        consumed = maxOf(consumed, 1)
    }

    /**
     * 申请再重试一次。剩余额度 >0 时消耗并返回 true；耗尽返回 false（调用方停止重试）。
     */
    fun tryConsume(): Boolean {
        if (consumed >= maxTotalAttempts) return false
        consumed++
        return true
    }

    val remaining: Int get() = maxOf(0, maxTotalAttempts - consumed)

    companion object {
        /** 同一逻辑步骤的总尝试次数硬顶（含首次），三层重试共享 */
        const val MAX_TOTAL_ATTEMPTS = 5
    }
}
