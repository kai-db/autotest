package com.autotest.engine

import com.autotest.log.TestLogger

/**
 * 测试套件：批量管理和执行多个测试用例。
 * 支持按优先级排序、全量执行、结果汇总。
 *
 * 铁律约束：
 * - 只暴露 runAll()，不暴露 runFailed()（铁律7：回归必须全量跑）
 * - 支持从 TEST_CASES.md 加载用例（铁律2：按用例执行）
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
     * 从 TEST_CASES.md 解析用例并注册。
     * 解析出的用例只有描述信息（步骤文本），实际执行逻辑需要通过 stepAction 映射。
     *
     * @param markdown TEST_CASES.md 内容
     * @param stepAction 将步骤描述文本映射为可执行的操作
     */
    fun loadFromMarkdown(
        markdown: String,
        stepAction: (testId: String, stepDescription: String) -> Unit
    ) {
        val parsed = TestCaseParser.parse(markdown)
        for (tc in parsed) {
            val priority = when (tc.priority) {
                "P0" -> Priority.P0
                "P1" -> Priority.P1
                else -> Priority.P2
            }
            if (tc.unmatchedBullets > 0) {
                logger.w(
                    "TestSuite",
                    "用例 ${tc.id} 有 ${tc.unmatchedBullets} 行 bullet 未被解析（仅支持「- 步骤：/- 验证：」），可能格式漂移"
                )
            }
            // 零步骤用例注册为必 FAIL（P0-3）：解析失败静默变空场景会「假 PASS」——
            // TEST_CASES.md 是唯一用例来源（铁律2），格式漂移必须显式暴露而非跳过
            if (tc.steps.isEmpty() && tc.verifications.isEmpty()) {
                logger.w("TestSuite", "用例 ${tc.id} 解析为空（0 步骤 0 验证），注册为必 FAIL 用例")
                addTest(tc.id, tc.name, priority) {
                    step("用例解析为空") {
                        throw AssertionError(
                            "用例 ${tc.id} 从 Markdown 解析出 0 步骤 0 验证——" +
                                "可能是格式漂移（仅支持「- 步骤：/- 验证：」bullet），拒绝按空场景假 PASS"
                        )
                    }
                }
                continue
            }
            val capturedSteps = tc.steps
            val capturedVerifications = tc.verifications
            val capturedId = tc.id

            addTest(tc.id, tc.name, priority) {
                capturedSteps.forEachIndexed { i, desc ->
                    step("步骤${i + 1}: $desc") { stepAction(capturedId, desc) }
                }
                capturedVerifications.forEachIndexed { i, desc ->
                    step("验证${i + 1}: $desc") { stepAction(capturedId, desc) }
                }
            }
        }

        logger.i("TestSuite", "从 Markdown 加载 ${parsed.size} 条用例")
    }

    /**
     * 按优先级 P0→P1→P2 全量执行所有用例。
     * 铁律7：只有全量执行，没有部分执行。
     *
     * @param shouldStop 协作式取消检查点：每条用例执行**前**求值，返回 true 则停止后续用例
     *   （已跑结果照常返回）。用于超时取消，避免僵尸线程跑完全部剩余用例（P0-6）。
     */
    fun runAll(shouldStop: () -> Boolean = { false }): List<TestCaseResult> {
        logger.i("TestSuite", "══════ 开始测试套件: $name（${tests.size} 条用例）══════")

        runner.reset()
        val sorted = tests.sortedBy { it.priority }
        val results = mutableListOf<TestCaseResult>()

        for ((index, test) in sorted.withIndex()) {
            if (shouldStop()) {
                logger.w("TestSuite", "收到取消信号，停止后续用例（已跑 ${results.size}/${sorted.size}）")
                break
            }
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

    fun getTestCount(): Int = tests.size

    fun getTestsByPriority(priority: Priority): List<TestDefinition> =
        tests.filter { it.priority == priority }

    fun clear() {
        tests.clear()
    }
}
