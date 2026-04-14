package com.autotest.stability

data class RetryPolicy(
    val maxRetries: Int = 1,
    val intervalMs: Long = 1000
) {
    init {
        require(maxRetries >= 0) { "maxRetries 必须 >= 0，当前值: $maxRetries" }
        require(intervalMs >= 0) { "intervalMs 必须 >= 0，当前值: $intervalMs" }
    }
}
