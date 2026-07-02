package com.autotest.intercept

import com.autotest.log.TestLogger

/**
 * 拦截器链，管理多个拦截器的注册和触发。
 *
 * 双链设计（对齐 Kaspresso）：
 * - watcher 链（[Interceptor]）：纯旁路观察（日志/截图/性能），按注册序执行，异常不影响主逻辑
 * - behavior 链（[BehaviorInterceptor]）：失败恢复（弹窗关闭等），按注册序尝试，恢复成功重试一次
 */
class InterceptorChain {

    private val interceptors = mutableListOf<Interceptor>()
    private val behaviors = mutableListOf<BehaviorInterceptor>()

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
        behaviors.clear()
    }

    fun addBehavior(behavior: BehaviorInterceptor) {
        behaviors.add(behavior)
    }

    fun addBehaviors(vararg behaviors: BehaviorInterceptor) {
        this.behaviors.addAll(behaviors)
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

    fun fireBeforeStep(ctx: StepContext) {
        interceptors.forEach { safely { it.beforeStep(ctx) } }
    }

    fun fireAfterStep(ctx: StepContext, durationMs: Long) {
        interceptors.forEach { safely { it.afterStep(ctx, durationMs) } }
    }

    fun fireOnStepFailure(ctx: StepContext, error: Throwable) {
        interceptors.forEach { safely { it.onStepFailure(ctx, error) } }
    }

    /**
     * 取某步骤的截图路径（按证据唯一 key，若链中有 ScreenshotInterceptor 且该步截过图）。
     * 默认只在步骤失败时截图，所以成功步骤通常返回 null——失败才有图、成功零图。
     */
    fun stepScreenshotPath(ctx: StepContext): String? =
        interceptors.filterIsInstance<ScreenshotInterceptor>().firstOrNull()?.getScreenshotPath(ctx)

    /** 取某步骤的 logcat 路径（按证据唯一 key，默认仅失败步骤收集）。 */
    fun stepLogcatPath(ctx: StepContext): String? =
        interceptors.filterIsInstance<LogcatInterceptor>().firstOrNull()?.getLogcatPath(ctx)

    /**
     * 带恢复链的执行：失败时按注册序让 behavior 节点尝试恢复。
     *
     * 副作用保护（D1）：恢复动作（如关弹窗）照常执行，但**是否重放步骤取决于 [retriable]**——
     * - retriable=true：任一节点恢复成功则重试一次（重试再失败的新错误交给后续节点）；
     * - retriable=false（默认）：恢复动作执行完也**不重放**，原始失败照抛（避免转账/签名等已生效副作用被重复执行）。
     * 全链无能为力（或不可重放）抛出最后一次错误。behavior 节点自身异常按"无能为力"处理并留痕。
     *
     * @param onRetry 每次真正重放前回调（供上层递增 attempt / 消费重试预算），返回 false 表示预算耗尽、停止重放。
     */
    fun <T> runWithRecovery(
        ctx: StepContext,
        retriable: Boolean,
        onRetry: () -> Boolean = { true },
        action: () -> T
    ): T {
        var lastError: Throwable
        try {
            return action()
        } catch (e: Throwable) {
            lastError = e
        }
        for (behavior in behaviors) {
            val recovered = try {
                behavior.tryRecover(ctx, lastError)
            } catch (e: Throwable) {
                logger?.w("Interceptor", "behavior 节点异常已忽略(${behavior.javaClass.simpleName}): ${e.message}")
                false
            }
            if (!recovered) continue
            if (!retriable) {
                logger?.w(
                    "Interceptor",
                    "步骤[${ctx.stepNumber}]经 ${behavior.javaClass.simpleName} 做了恢复动作，但该步骤 retriable=false（副作用保护），不重放，原始失败照抛"
                )
                continue
            }
            if (!onRetry()) {
                logger?.w("Interceptor", "步骤[${ctx.stepNumber}]重试预算耗尽，停止重放")
                break
            }
            logger?.i("Interceptor", "步骤[${ctx.stepNumber}]经 ${behavior.javaClass.simpleName} 恢复，重试")
            try {
                return action()
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError
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
        } catch (e: Throwable) {
            // 拦截器异常不影响主流程，但留痕避免静默吞错
            logger?.w("Interceptor", "拦截器异常已忽略: ${e.message}")
        }
    }
}
