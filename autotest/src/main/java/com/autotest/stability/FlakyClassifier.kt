package com.autotest.stability

enum class FlakyType {
    FLAKY,
    HARD_FAIL
}

object FlakyClassifier {

    fun classify(message: String?): FlakyType {
        if (message.isNullOrEmpty()) return FlakyType.HARD_FAIL
        val lowered = message.lowercase()
        return if (
            lowered.contains("timeout") ||
            lowered.contains("timed out") ||
            message.contains("超时")
        ) {
            FlakyType.FLAKY
        } else {
            FlakyType.HARD_FAIL
        }
    }
}
