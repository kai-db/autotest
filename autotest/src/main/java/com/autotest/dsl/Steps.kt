package com.autotest.dsl

/**
 * @param retriable 该步骤是否允许被 behavior 恢复链重放。默认 **false**（副作用保护，D1）：
 *   钱包 App 的转账/签名等已生效操作重放会造成二次执行。只读/幂等步骤显式置 true 换回
 *   「dismiss 弹窗后重试」行为（或用 ScenarioBuilder.retriableStep）。
 */
class Step(
    val name: String,
    val retriable: Boolean = false,
    private val action: () -> Unit
) {
    fun run() {
        action()
    }
}
