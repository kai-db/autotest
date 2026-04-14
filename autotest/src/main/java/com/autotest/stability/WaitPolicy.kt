package com.autotest.stability

data class WaitPolicy(
    val timeoutMs: Long,
    val intervalMs: Long = 500
) {
    init {
        require(timeoutMs > 0) { "timeoutMs 必须 > 0，当前值: $timeoutMs" }
        require(intervalMs > 0) { "intervalMs 必须 > 0，当前值: $intervalMs" }
        require(intervalMs <= timeoutMs) { "intervalMs ($intervalMs) 不能大于 timeoutMs ($timeoutMs)" }
    }
}
