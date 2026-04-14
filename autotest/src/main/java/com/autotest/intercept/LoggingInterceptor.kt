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

    override fun beforeStep(stepNumber: String, stepName: String) {
        logger.i("Step", "[$stepNumber] $stepName")
    }

    override fun afterStep(stepNumber: String, stepName: String, durationMs: Long) {
        logger.i("Step", "[$stepNumber] ✓ $stepName (${durationMs}ms)")
    }

    override fun onStepFailure(stepNumber: String, stepName: String, error: Throwable) {
        logger.e("Step", "[$stepNumber] ✗ $stepName: ${error.message}")
    }
}
