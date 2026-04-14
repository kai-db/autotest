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
        hooks.forEach { safely { it.beforeTest(testName) } }
    }

    fun fireAfterTestSuccess(testName: String) {
        hooks.forEach { safely { it.afterTestSuccess(testName) } }
    }

    fun fireAfterTestFailure(testName: String, error: Throwable) {
        hooks.forEach { safely { it.afterTestFailure(testName, error) } }
    }

    fun fireAfterTestFinally(testName: String) {
        hooks.forEach { safely { it.afterTestFinally(testName) } }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Hook 异常不影响测试执行
        }
    }
}
