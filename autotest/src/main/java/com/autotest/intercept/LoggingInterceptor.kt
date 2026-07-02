package com.autotest.intercept

import com.autotest.log.TestLogger

/**
 * 日志拦截器：自动记录每个操作和步骤的执行情况。
 */
class LoggingInterceptor(private val logger: TestLogger) : Interceptor {

    override fun beforeAction(actionName: String, details: String) {
        val msg = if (details.isNotEmpty()) "$actionName ($details)" else actionName
        logger.d("Action", "→ $msg")
    }

    override fun afterAction(actionName: String, durationMs: Long) {
        logger.d("Action", "✓ $actionName (${durationMs}ms)")
    }

    override fun onActionFailure(actionName: String, error: Throwable) {
        logger.e("Action", "✗ $actionName: ${error.message}", error)
    }

    override fun beforeStep(ctx: StepContext) {
        logger.i("Step", "[${ctx.stepNumber}] ${ctx.stepName}")
    }

    override fun afterStep(ctx: StepContext, durationMs: Long) {
        logger.i("Step", "[${ctx.stepNumber}] ✓ ${ctx.stepName} (${durationMs}ms)")
    }

    override fun onStepFailure(ctx: StepContext, error: Throwable) {
        logger.e("Step", "[${ctx.stepNumber}] ✗ ${ctx.stepName}: ${error.message}")
    }
}
