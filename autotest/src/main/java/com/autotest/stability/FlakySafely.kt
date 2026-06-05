package com.autotest.stability

/**
 * 全局 Flaky 安全包装器。
 * 参考 Kaspresso flakySafely 设计，在任意代码块上自动重试。
 *
 * 用法：
 * ```
 * // 在 step 内部对不稳定操作重试
 * step("验证元素可见") {
 *     flakySafely(timeoutMs = 5000) {
 *         viewByText("首页").isDisplayed()
 *     }
 * }
 *
 * // 独立使用
 * val result = flakySafely(timeoutMs = 10000, intervalMs = 1000) {
 *     device.findObject(By.text("加载完成"))
 *         ?: throw AssertionError("未找到")
 * }
 * ```
 *
 * 与 RetryRunner 的区别：
 * - RetryRunner 是 JUnit Rule，重试整个测试方法
 * - flakySafely 是代码块级别，只重试指定的操作
 */
fun <T> flakySafely(
    timeoutMs: Long = 10000,
    intervalMs: Long = 500,
    allowedExceptions: Set<Class<out Throwable>> = DEFAULT_ALLOWED_EXCEPTIONS,
    failureMessage: String? = null,
    action: () -> T
): T {
    val startTime = System.currentTimeMillis()
    var lastError: Throwable? = null

    while (System.currentTimeMillis() - startTime < timeoutMs) {
        try {
            return action()
        } catch (e: Throwable) {
            if (!isAllowed(e, allowedExceptions)) throw e
            lastError = e
            Thread.sleep(intervalMs)
        }
    }

    // 最后一次尝试
    try {
        return action()
    } catch (e: Throwable) {
        val msg = failureMessage ?: "flakySafely 超时 (${timeoutMs}ms)"
        throw AssertionError("$msg: ${lastError?.message ?: e.message}", e)
    }
}

private fun isAllowed(e: Throwable, allowed: Set<Class<out Throwable>>): Boolean {
    return allowed.any { it.isInstance(e) }
}

/**
 * 默认可重试异常：仅 UI 时序类（断言未通过、状态尚未就绪）。
 *
 * 刻意不含 [NullPointerException] / [RuntimeException]——它们几乎总是真实 bug，
 * 自动重试只会拖到超时后才报错、反而掩盖根因。确需放宽时由调用方显式传 [allowedExceptions]。
 */
val DEFAULT_ALLOWED_EXCEPTIONS: Set<Class<out Throwable>> = setOf(
    AssertionError::class.java,
    IllegalStateException::class.java
)
