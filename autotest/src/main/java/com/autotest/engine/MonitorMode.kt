package com.autotest.engine

import com.autotest.log.TestLogger
import java.io.File

/**
 * 监工模式引擎：铁律的代码化实现。
 *
 * Phase 职责划分：
 * ┌──────────────────────────────────────────────┐
 * │ Phase 1: 编译部署    → 外部执行（AI/人）       │
 * │ Phase 2: 全量测试    → 引擎执行（runRound）    │
 * │ Phase 3: 更新文档    → 引擎执行（writeResults）│
 * │ Phase 4: 修复代码    → 外部执行（AI/人）       │
 * │ Phase 5: 回归判定    → 引擎执行（自动）        │
 * │ Phase 6: 最终验收    → 引擎执行（finalVerify） │
 * └──────────────────────────────────────────────┘
 *
 * AI 调用流程：
 * ```
 * // Phase 1: AI 自行编译部署
 * bash("./gradlew assembleDebug")
 * mobile_install_app(...)
 *
 * // Phase 2+3: 引擎执行
 * val round = monitor.runRound()  // Phase 2
 * monitor.writeResults()           // Phase 3
 *
 * if (!round.allPassed) {
 *     // Phase 4: AI 自行修复代码
 *     // ... 修改代码 ...
 *
 *     // Phase 5: 回到 Phase 1 重新编译，全量重跑
 * }
 *
 * // Phase 6: 全部通过后，不改代码再跑一遍
 * monitor.runFinalVerification()
 * ```
 */
class MonitorMode(
    private val suite: TestSuite,
    private val logger: TestLogger,
    private val resultsDir: String = ".",
    private val maxRounds: Int = 10,
    /** 每轮默认硬超时（毫秒），0=不限。runRound 与 Phase6 都默认取它——AI 驱动场景应设非零（如 300_000） */
    private val defaultRoundTimeoutMs: Long = 0
) {

    /** 某轮超时后置 true：worker 可能仍存活，拒绝启动新一轮（防并发操作 App / 共享状态竞争），reset 清除 */
    @Volatile
    private var timedOut: Boolean = false

    data class RoundResult(
        val round: Int,
        val phase: String,
        val results: List<TestCaseResult>,
        val passed: Int,
        val failed: Int
    ) {
        val allPassed: Boolean get() = failed == 0
        val failedCases: List<TestCaseResult> get() = results.filter { !it.passed }
    }

    private val rounds = mutableListOf<RoundResult>()
    private val fixRecords = mutableListOf<FixRecord>()

    data class FixRecord(
        val round: Int,
        val testCaseId: String,
        val rootCause: String,
        val fixDescription: String
    )

    data class PreflightResult(
        val passed: Boolean,
        val failedChecks: List<String>
    )

    /**
     * Phase 1 前置检查：开跑前校验环境就绪（设备在线 / App 已安装 / 屏幕解锁 / 已登录等）。
     *
     * 检查项由调用方注入，使引擎不依赖具体设备能力、便于单测：
     * ```
     * val d = DeviceActions.create(logger)
     * val pre = monitor.preflight(
     *     "App 已安装" to { d.isAppInstalled(pkg) },
     *     "屏幕已解锁" to { d.isScreenUnlocked() }
     * )
     * if (!pre.passed) return  // 中止本轮，避免一连串假 FAIL
     * ```
     * 任一检查返回 false 或抛异常即视为不通过。
     */
    fun preflight(vararg checks: Pair<String, () -> Boolean>): PreflightResult {
        logger.i("Monitor", "══════ Phase 1: 前置检查（${checks.size} 项）══════")
        val failed = mutableListOf<String>()
        checks.forEach { (name, check) ->
            val ok = try {
                check()
            } catch (e: Throwable) {
                logger.e("Monitor", "前置检查「$name」执行异常: ${e.message}", e)
                false
            }
            if (ok) {
                logger.i("Monitor", "  ✅ $name")
            } else {
                logger.e("Monitor", "  ❌ $name")
                failed.add(name)
            }
        }
        val passed = failed.isEmpty()
        logger.i("Monitor", "══════ Phase 1 ${if (passed) "通过" else "不通过（${failed.size} 项失败）"} ══════")
        return PreflightResult(passed, failed)
    }

    /**
     * Phase 2: 执行一轮全量测试。
     * 铁律7：全量跑，不跳过任何用例。
     *
     * @param timeoutMs 本轮硬超时（毫秒），0 表示不限。AI 驱动场景必须设：
     *   被测 App 卡死/弹窗死循环时，超时让本轮以 FAIL 收尾而非永久挂起监工闭环。
     */
    fun runRound(timeoutMs: Long = defaultRoundTimeoutMs): RoundResult {
        check(!timedOut) {
            "上一轮超时、worker 可能仍存活，拒绝启动新一轮（防并发操作 App / 共享 results 竞争）；请人工确认设备状态后调用 reset()"
        }
        check(rounds.size < maxRounds) {
            "监工轮次已达上限 $maxRounds，疑似修复死循环，停下交用户裁决（不再无限累积）"
        }
        val roundNumber = rounds.size + 1
        logger.i("Monitor", "══════ Phase 2: 第 $roundNumber 轮全量测试（${suite.getTestCount()} 条用例）══════")

        val results = if (timeoutMs <= 0) suite.runAll() else runAllWithTimeout(timeoutMs)
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }

        val round = RoundResult(roundNumber, "迭代测试", results, passed, failed)
        rounds.add(round)

        if (failed > 0) {
            logger.i("Monitor", "第 $roundNumber 轮有 ${failed} 个 FAIL：")
            round.failedCases.forEach { tc ->
                logger.i("Monitor", "  ❌ ${tc.name}: ${tc.error}")
            }
        }

        logger.i("Monitor", "══════ Phase 2 结束: PASS $passed / FAIL $failed ══════")
        return round
    }

    /**
     * 在独立线程跑全量并施加硬超时；超时记一条合成 FAIL（监工可据此进入修复阶段而非卡死）。
     * 协作式取消（P0-6）：超时时置 cancelled + shutdownNow(interrupt)，suite 在用例间检查点停下；
     * 并置 timedOut——worker 可能忽略 interrupt 仍在跑，后续 runRound/Phase6 拒绝并发启动直到 reset。
     */
    private fun runAllWithTimeout(timeoutMs: Long): List<TestCaseResult> {
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        return try {
            executor.submit(java.util.concurrent.Callable {
                suite.runAll { cancelled.get() || Thread.currentThread().isInterrupted }
            }).get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            cancelled.set(true)
            timedOut = true
            logger.e("Monitor", "本轮执行超时（${timeoutMs}ms），疑似卡死，发取消信号并强制收尾；worker 未退出前拒绝启动新一轮")
            listOf(TestCaseResult("ROUND_TIMEOUT", false, timeoutMs, "本轮执行超时（${timeoutMs}ms），疑似卡死"))
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e // 还原 suite 内抛出的原始异常
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Phase 3: 更新 TEST_RESULTS.md。
     * 每轮测试后必须调用。
     */
    fun writeResults(): File {
        val file = File(resultsDir, "TEST_RESULTS.md")
        file.writeText(toMarkdown())
        logger.i("Monitor", "Phase 3: 结果已写入 ${file.absolutePath}")
        return file
    }

    /**
     * Phase 4 记录：记录修复内容（由外部调用）。
     * 铁律5：修复必须合规，修复记录写入 TEST_RESULTS.md。
     */
    fun recordFix(testCaseId: String, rootCause: String, fixDescription: String) {
        val currentRound = rounds.size
        fixRecords.add(FixRecord(currentRound, testCaseId, rootCause, fixDescription))
        logger.i("Monitor", "Phase 4 记录: $testCaseId — $fixDescription")
    }

    /**
     * Phase 6: 最终验收。不改代码，再跑一遍全量。
     * 前置条件：上一轮已全部通过。
     */
    fun runFinalVerification(timeoutMs: Long = defaultRoundTimeoutMs): Boolean {
        check(!timedOut) {
            "上一轮超时、worker 可能仍存活，拒绝启动最终验收；请人工确认设备状态后调用 reset()"
        }
        logger.i("Monitor", "══════ Phase 6: 最终验收 ══════")
        // Phase 6 默认复用 defaultRoundTimeoutMs——不再裸 runAll() 无超时，避免 App 卡死时永久挂起
        val results = if (timeoutMs <= 0) suite.runAll() else runAllWithTimeout(timeoutMs)
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }

        val round = RoundResult(rounds.size + 1, "最终验收", results, passed, failed)
        rounds.add(round)
        writeResults()

        return if (round.allPassed) {
            logger.i("Monitor", "✅ 最终验收通过！共 ${rounds.size} 轮，修复 ${fixRecords.size} 个问题")
            true
        } else {
            logger.e("Monitor", "❌ 最终验收失败，${round.failed} 个 FAIL，需回到迭代阶段")
            false
        }
    }

    /**
     * 获取上一轮的失败用例信息（供 Phase 4 修复时参考）。
     */
    fun getLastFailures(): List<TestCaseResult> {
        return rounds.lastOrNull()?.failedCases ?: emptyList()
    }

    /**
     * 生成完整的 Markdown 报告（含修复记录）。
     */
    fun toMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# 测试结果")
        sb.appendLine()

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
            sb.appendLine("## 第 ${round.round} 轮（${round.phase}）")
            sb.appendLine()
            sb.appendLine("| 用例 | 结果 | 耗时 | 失败原因 |")
            sb.appendLine("|---|---|---|---|")

            round.results.forEach { r ->
                // 失败标注 [INFRA]/[ASSERTION]（Q7）：一眼分「环境没准备好」与「用例真失败」
                val kind = when (r.failureKind) {
                    FailureKind.INFRA -> " [INFRA]"
                    FailureKind.ASSERTION -> " [ASSERTION]"
                    FailureKind.NONE -> ""
                }
                val status = if (r.passed) "✅ PASS" else "❌ FAIL$kind"
                val err = r.error?.take(60) ?: "-"
                sb.appendLine("| ${r.name} | $status | ${r.durationMs}ms | $err |")
            }
            sb.appendLine()
            sb.appendLine("**统计**: PASS ${round.passed} / FAIL ${round.failed}")
            sb.appendLine()
        }

        // 修复记录
        if (fixRecords.isNotEmpty()) {
            sb.appendLine("---")
            sb.appendLine("## 修复记录")
            sb.appendLine()
            sb.appendLine("| 轮次 | 用例 | 根因 | 修复内容 |")
            sb.appendLine("|---|---|---|---|")
            fixRecords.forEach { fix ->
                sb.appendLine("| 第${fix.round}轮 | ${fix.testCaseId} | ${fix.rootCause} | ${fix.fixDescription} |")
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    fun getRounds(): List<RoundResult> = rounds.toList()

    fun reset() {
        rounds.clear()
        fixRecords.clear()
        timedOut = false
    }
}
