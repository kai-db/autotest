package com.autotest.stability

data class RetryPolicy(
    val maxRetries: Int = 1,
    val intervalMs: Long = 1000
)
