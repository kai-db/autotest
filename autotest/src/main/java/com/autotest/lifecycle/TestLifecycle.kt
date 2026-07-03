package com.autotest.lifecycle

/**
 * 测试生命周期钩子接口。
 * 参考 Kaspresso 的 TestRunWatcherInterceptor 设计，
 * 提供测试执行各阶段的回调，支持自定义扩展。
 *
 * 使用场景：
 * - 每次测试前重置 App 状态
 * - 测试失败后收集日志
 * - 测试完成后上传报告
 */
interface TestLifecycleHook {

    /** 测试方法开始前调用 */
    fun beforeTest(testName: String) {}

    /** 测试方法成功后调用 */
    fun afterTestSuccess(testName: String) {}

    /** 测试方法失败后调用 */
    fun afterTestFailure(testName: String, error: Throwable) {}

    /** 测试方法结束后调用（无论成功失败） */
    fun afterTestFinally(testName: String) {}
}

/**
 * 生命周期管理器，管理多个 Hook 的注册和触发。
 */
class TestLifecycleManager {

    /**
     * 钩子异常留痕通道（E1）：由宿主注入（BaseUiTest/TestRunner 的 setUp 期，与
     * [com.autotest.intercept.InterceptorChain.logger] 同模式）；未注入时仅隔离不留痕（纯 JVM 场景）。
     */
    var logger: com.autotest.log.TestLogger? = null

    private val hooks = mutableListOf<TestLifecycleHook>()

    fun register(hook: TestLifecycleHook) {
        hooks.add(hook)
    }

    fun register(vararg hookList: TestLifecycleHook) {
        hooks.addAll(hookList)
    }

    fun clear() {
        hooks.clear()
    }

    fun fireBeforeTest(testName: String) {
        hooks.forEach { safely("beforeTest", it) { it.beforeTest(testName) } }
    }

    fun fireAfterTestSuccess(testName: String) {
        hooks.forEach { safely("afterTestSuccess", it) { it.afterTestSuccess(testName) } }
    }

    fun fireAfterTestFailure(testName: String, error: Throwable) {
        hooks.forEach { safely("afterTestFailure", it) { it.afterTestFailure(testName, error) } }
    }

    fun fireAfterTestFinally(testName: String) {
        hooks.forEach { safely("afterTestFinally", it) { it.afterTestFinally(testName) } }
    }

    private inline fun safely(phase: String, hook: TestLifecycleHook, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            // Hook 异常不影响测试执行，但拒绝静默吞掉（E1）：留痕方便排查"钩子悄悄没生效"
            logger?.w(
                "Lifecycle",
                "$phase hook 异常被隔离（${hook.javaClass.simpleName}）: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}
