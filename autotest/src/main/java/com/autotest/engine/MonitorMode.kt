package com.autotest.engine

import com.autotest.log.TestLogger
import java.io.File

/**
 * 监工模式引擎：铁律的代码化实现。
 *
 * 自动循环执行：全量测试 → 统计结果 → 记录文档 → 直到 0 个 FAIL 或达到最大轮次。
 *
 * 流程：
 * ```
 * Phase 2: 全量测试（TestSuite.runAll）
 * Phase 3: 更新 TEST_RESULTS.md
 * Phase 5: 判定是否全部通过
 *   └── 有 FAIL → 回到 Phase 2（下一轮）
 *   └── 全 PASS → Phase 6 最终验收
 * Phase 6: 不改代码，再跑一遍
 *   └── 全 PASS → 结束
 *   └── 有 FAIL → 回到迭代
 * ```
 *
 * 注意：Phase 1（编译部署）和 Phase 4（修复代码）由外部（Claude Code / 人）完成，
 * 本引擎只负责 Phase 2/3/5/6 的自动化执行和结果记录。
 */
class MonitorMode(
    private val suite: TestSuite,
    private val logger: TestLogger,
    private val resultsDir: String = ".",
    private val maxRounds: Int = 10
) {

    data class RoundResult(
        val round: Int,
        val results: List<TestCaseResult>,
        val passed: Int,
        val failed: Int
    ) {
        val allPassed: Boolean get() = failed == 0
    }

    private val rounds = mutableListOf<RoundResult>()

    /**
     * 执行一轮全量测试（Phase 2）。
     * @return 本轮结果
     */
    fun runRound(): RoundResult {
        val roundNumber = rounds.size + 1
        logger.i("Monitor", "══════ 第 $roundNumber 轮测试开始 ══════")

        val results = suite.runAll()
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }

        val round = RoundResult(roundNumber, results, passed, failed)
        rounds.add(round)

        logger.i("Monitor", "══════ 第 $roundNumber 轮结束: PASS $passed / FAIL $failed ══════")
        return round
    }

    /**
     * 执行迭代修复阶段：循环跑全量测试直到 0 个 FAIL 或达到最大轮次。
     * @return 最终是否全部通过
     */
    fun runUntilAllPass(): Boolean {
        for (i in 1..maxRounds) {
            val round = runRound()
            writeResults()

            if (round.allPassed) {
                logger.i("Monitor", "✅ 第 ${round.round} 轮全部通过，进入最终验收")
                return true
            }

            logger.i("Monitor", "第 ${round.round} 轮有 ${round.failed} 个 FAIL，需要修复后再次运行")
            // 返回，等待外部修复后再次调用 runRound()
            return false
        }

        logger.e("Monitor", "达到最大轮次 $maxRounds，仍有 FAIL")
        return false
    }

    /**
     * 最终验收（Phase 6）：不改代码，再跑一遍全量。
     * @return 是否全部通过
     */
    fun runFinalVerification(): Boolean {
        logger.i("Monitor", "══════ 最终验收（Phase 6）══════")
        val round = runRound()
        writeResults()

        return if (round.allPassed) {
            logger.i("Monitor", "✅ 最终验收通过！共 ${rounds.size} 轮")
            true
        } else {
            logger.e("Monitor", "❌ 最终验收失败，有 ${round.failed} 个 FAIL，需要回到迭代阶段")
            false
        }
    }

    /**
     * 生成 TEST_RESULTS.md（Phase 3）。
     */
    fun writeResults(): File {
        val file = File(resultsDir, "TEST_RESULTS.md")
        file.writeText(toMarkdown())
        logger.i("Monitor", "结果已写入: ${file.absolutePath}")
        return file
    }

    /**
     * 生成完整的 Markdown 报告。
     */
    fun toMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# 测试结果")
        sb.appendLine()

        // 总览
        val lastRound = rounds.lastOrNull()
        val totalPassed = lastRound?.passed ?: 0
        val totalFailed = lastRound?.failed ?: 0
        val total = totalPassed + totalFailed

        sb.appendLine("**轮次**: ${rounds.size}")
        sb.appendLine("**最新结果**: PASS $totalPassed / FAIL $totalFailed / 总计 $total")
        sb.appendLine("**状态**: ${if (lastRound?.allPassed == true) "✅ 全部通过" else "❌ 有失败"}")
        sb.appendLine()

        // 每轮详情
        rounds.forEach { round ->
            sb.appendLine("---")
            sb.appendLine("## 第 ${round.round} 轮")
            sb.appendLine()
            sb.appendLine("| 用例 | 结果 | 耗时 | 失败原因 |")
            sb.appendLine("|---|---|---|---|")

            round.results.forEach { r ->
                val status = if (r.passed) "✅ PASS" else "❌ FAIL"
                val err = r.error?.take(60) ?: "-"
                sb.appendLine("| ${r.name} | $status | ${r.durationMs}ms | $err |")
            }
            sb.appendLine()
            sb.appendLine("**统计**: PASS ${round.passed} / FAIL ${round.failed}")
            sb.appendLine()
        }

        return sb.toString()
    }

    fun getRounds(): List<RoundResult> = rounds.toList()

    fun reset() {
        rounds.clear()
    }
}
