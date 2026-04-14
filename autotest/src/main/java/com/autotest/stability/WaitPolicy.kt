package com.autotest.stability

data class WaitPolicy(
    val timeoutMs: Long,
    val intervalMs: Long = 500
)
