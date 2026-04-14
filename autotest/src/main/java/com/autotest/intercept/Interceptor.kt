package com.autotest.intercept

/**
 * 操作拦截器接口。
 * 参考 Kaspresso 的 BehaviorInterceptor + WatcherInterceptor 设计，
 * 简化为单一接口，在操作前后插入通用逻辑。
 *
 * 使用场景：自动日志、自动截图、弹窗处理、性能埋点等。
 */
interface Interceptor {
    /** 操作执行前调用 */
    fun beforeAction(actionName: String, details: String = "") {}

    /** 操作执行成功后调用 */
    fun afterAction(actionName: String, durationMs: Long) {}

    /** 操作执行失败时调用 */
    fun onActionFailure(actionName: String, error: Throwable) {}

    /** 测试步骤开始前调用 */
    fun beforeStep(stepNumber: String, stepName: String) {}

    /** 测试步骤成功后调用 */
    fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {}

    /** 测试步骤失败时调用 */
    fun onStepFailure(stepNumber: String, stepName: String, error: Throwable) {}
}
