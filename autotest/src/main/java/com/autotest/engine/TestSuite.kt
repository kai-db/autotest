package com.autotest.engine

import com.autotest.log.TestLogger

/**
 * 测试套件：批量管理和执行多个测试用例。
 * 支持按优先级排序、全量执行、结果汇总。
 *
 * 用法：
 * ```
 * val suite = TestSuite("DeBox 冒烟测试", runner, logger)
 * suite.addTest("TC-001", "冷启动", Priority.P0) { ... }
 * suite.addTest("TC-002", "Tab导航", Priority.P0) { ... }
 * val report = suite.runAll()
 * ```
 */
class TestSuite(
    val name: String,
    private val runner: TestRunner,
    private val logger: TestLogger
) {

    enum class Priority { P0, P1, P2 }

    data class TestDefinition(
        val id: String,
        val name: String,
        val priority: Priority,
        val before: (() -> Unit)? = null,
        val after: (() -> Unit)? = null,
        val steps: com.autotest.dsl.ScenarioBuilder.() -> Unit
    )

    private val tests = mutableListOf<TestDefinition>()

    fun addTest(
        id: String,
        name: String,
        priority: Priority = Priority.P0,
        before: (() -> Unit)? = null,
        after: (() -> Unit)? = null,
        steps: com.autotest.dsl.ScenarioBuilder.() -> Unit
    ) {
        tests.add(TestDefinition(id, name, priority, before, after, steps))
    }

    /**
     * 按优先级 P0→P1→P2 执行所有用例。
     * @return 所有用例的结果列表
     */
    fun runAll(): List<TestCaseResult> {
        logger.i("TestSuite", "══════ 开始测试套件: $name（${tests.size} 条用例）══════")

        runner.reset()
        val sorted = tests.sortedBy { it.priority }
        val results = mutableListOf<TestCaseResult>()

        sorted.forEachIndexed { index, test ->
            logger.i("TestSuite", "进度: ${index + 1}/${sorted.size} [${test.priority}] ${test.id} ${test.name}")
            val result = runner.runTest(
                name = "${test.id} ${test.name}",
                before = test.before,
                after = test.after,
                steps = test.steps
            )
            results.add(result)
        }

        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        logger.i("TestSuite", "══════ 套件完成: $name | PASS: $passed | FAIL: $failed ══════")

        return results
    }

    /**
     * 只运行失败的用例（用于调试，注意：正式回归应全量跑）。
     */
    fun runFailed(previousResults: List<TestCaseResult>): List<TestCaseResult> {
        val failedNames = previousResults.filter { !it.passed }.map { it.name }.toSet()
        logger.i("TestSuite", "重跑失败用例: ${failedNames.size} 条")

        val results = mutableListOf<TestCaseResult>()
        tests.filter { "${it.id} ${it.name}" in failedNames }
            .forEach { test ->
                val result = runner.runTest(
                    name = "${test.id} ${test.name}",
                    before = test.before,
                    after = test.after,
                    steps = test.steps
                )
                results.add(result)
            }
        return results
    }

    fun getTestCount(): Int = tests.size

    fun getTestsByPriority(priority: Priority): List<TestDefinition> =
        tests.filter { it.priority == priority }
}
