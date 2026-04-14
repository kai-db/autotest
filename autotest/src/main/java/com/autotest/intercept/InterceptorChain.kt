package com.autotest.intercept

/**
 * 拦截器链，管理多个拦截器的注册和触发。
 * 所有拦截器按注册顺序执行，单个拦截器异常不影响其他拦截器和主逻辑。
 */
class InterceptorChain {

    private val interceptors = mutableListOf<Interceptor>()

    fun add(interceptor: Interceptor) {
        interceptors.add(interceptor)
    }

    fun addAll(vararg interceptors: Interceptor) {
        this.interceptors.addAll(interceptors)
    }

    fun clear() {
        interceptors.clear()
    }

    fun fireBeforeAction(actionName: String, details: String = "") {
        interceptors.forEach { safely { it.beforeAction(actionName, details) } }
    }

    fun fireAfterAction(actionName: String, durationMs: Long) {
        interceptors.forEach { safely { it.afterAction(actionName, durationMs) } }
    }

    fun fireOnActionFailure(actionName: String, error: Throwable) {
        interceptors.forEach { safely { it.onActionFailure(actionName, error) } }
    }

    fun fireBeforeStep(stepNumber: String, stepName: String) {
        interceptors.forEach { safely { it.beforeStep(stepNumber, stepName) } }
    }

    fun fireAfterStep(stepNumber: String, stepName: String, durationMs: Long) {
        interceptors.forEach { safely { it.afterStep(stepNumber, stepName, durationMs) } }
    }

    fun fireOnStepFailure(stepNumber: String, stepName: String, error: Throwable) {
        interceptors.forEach { safely { it.onStepFailure(stepNumber, stepName, error) } }
    }

    /**
     * 带拦截器的操作执行。自动触发 before/after/onFailure。
     */
    fun <T> intercept(actionName: String, details: String = "", action: () -> T): T {
        fireBeforeAction(actionName, details)
        val start = System.currentTimeMillis()
        return try {
            val result = action()
            fireAfterAction(actionName, System.currentTimeMillis() - start)
            result
        } catch (e: Throwable) {
            fireOnActionFailure(actionName, e)
            throw e
        }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // 拦截器异常不影响主流程
        }
    }
}
