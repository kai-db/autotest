package com.autotest.intercept

import com.autotest.log.TestLogger

/**
 * 拦截器链，管理多个拦截器的注册和触发。
 * 所有拦截器按注册顺序执行，单个拦截器异常不影响其他拦截器和主逻辑。
 */
class InterceptorChain {

    private val interceptors = mutableListOf<Interceptor>()

    /**
     * 可选日志器：设置后，被忽略的拦截器异常会留痕。
     * 默认 null 保持原有静默行为（向后兼容）；生产路径由 [com.autotest.base.BaseUiTest] 注入。
     */
    var logger: TestLogger? = null

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
     * 取某步骤的截图路径（若链中有 ScreenshotInterceptor 且该步截过图）。
     * 默认只在步骤失败时截图，所以成功步骤通常返回 null——失败才有图、成功零图。
     */
    fun stepScreenshotPath(stepNumber: String): String? =
        interceptors.filterIsInstance<ScreenshotInterceptor>().firstOrNull()?.getScreenshotPath(stepNumber)

    /** 取某步骤的 logcat 路径（若链中有 LogcatInterceptor 且该步收集过；默认仅失败步骤收集）。 */
    fun stepLogcatPath(stepNumber: String): String? =
        interceptors.filterIsInstance<LogcatInterceptor>().firstOrNull()?.getLogcatPath(stepNumber)

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
        } catch (e: Throwable) {
            // 拦截器异常不影响主流程，但留痕避免静默吞错
            logger?.w("Interceptor", "拦截器异常已忽略: ${e.message}")
        }
    }
}
