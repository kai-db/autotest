package com.autotest.stability

/**
 * 步骤级重试预算的线程内桥（D1 三层共享预算）。
 *
 * 由 [com.autotest.dsl.Scenario] 在执行每个步骤前 [set] 一个 per-step [RetryBudget]、步骤结束 [clear]；
 * 步骤内部的 flakyStep 与 behavior 恢复重放都从 [current] 取同一个 budget 消费，
 * 从而「同一逻辑步骤」的总尝试次数被 [RetryBudget.MAX_TOTAL_ATTEMPTS] 统一封顶，不再各层各自 5 次叠乘。
 *
 * （RetryRunner 是方法级重试——重跑整个 Scenario，属独立的最外层，自身另有 MAX_TOTAL_ATTEMPTS-1 封顶。）
 */
object StepRetryBudgetHolder {
    private val holder = ThreadLocal<RetryBudget?>()

    fun set(budget: RetryBudget) = holder.set(budget)
    fun clear() = holder.remove()

    /** 当前步骤的共享预算；未在步骤上下文中时返回 null（调用方自建局部预算） */
    fun current(): RetryBudget? = holder.get()
}
