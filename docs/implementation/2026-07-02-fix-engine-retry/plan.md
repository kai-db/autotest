# ① 计划 / 方案

## Plan Proposal

### 任务目标

实施审计提案第二批：**执行引擎并发安全 + 重试体系可预测性**——P0-6（监工僵尸线程）、P0-7（maxRounds/Phase6 超时）、P0-8（跨用例证据/统计污染）、D1（重试层叠 + 副作用重放）、D2（FlakyClassifier 假注入 + 全局可变状态）。框架代码改动 + 单测。

### 当前项目上下文（已精读源码）

- `MonitorMode.runAllWithTimeout`：超时只 `executor.shutdownNow()`（发 interrupt），但 `TestRunner.runTest` 的 `catch(Throwable)` 把 InterruptedException 当普通失败吞掉、不恢复中断位 → 僵尸线程继续跑完剩余用例、并与主线程下一轮共享非线程安全 `results`（`TestRunner.results = mutableListOf`）。
- `MonitorMode.maxRounds` 声明但从不读取；`runFinalVerification` 直接 `suite.runAll()` 无超时。
- 证据/统计 key：ScreenshotInterceptor `screenshotMap[stepNumber]`、LogcatInterceptor 同、PerformanceInterceptor `stepDurations["$stepNumber:$stepName"]`、MemoryInterceptor `stepPssKb[stepNumber]`——拦截器实例在 suite/多轮间复用，stepNumber 每用例从 1 重排 → 跨用例覆盖/串味。`Scenario.run` 用裸 stepNumber 回读。
- `RetryRunner(classifier: FlakyClassifier = FlakyClassifier)`：参数类型是 `object` 单例类型，无法注入第二实现（假注入）；`FlakyClassifier.registerRule` 是全局 `mutableListOf`（跨 suite 泄漏，测试靠 @After clearRules 自救）。
- 三层重试：`flakyStep` 重试一切 Throwable（与 flakySafely 白名单哲学冲突）；`stepWithRecovery`/behavior 链整步重放（副作用步骤会被重复执行）；`RetryRunner` 预算叠乘。

### 约束

- 不改被测 debox；不动第一批已交付内容；不做第三/四批范围（集成坑 POM、存储、自愈链）。
- 全量单测须过、`publishToMavenLocal` 须成功；LIB_VERSION 1.6.1 → **1.6.2**。
- 兼容性：唯一接入方 debox DeBoxBaseTest；Interceptor 接口变更需列行为/签名变化清单。

### 非目标

- 不重构 MonitorMode 整体状态机（Q7 的 sealed state 留后续）；不改计时时钟（Q3 留第四批顺带）。

### 建议方案（5 个改动点）

**P0-6 监工僵尸线程 → 协作式取消【R1 修订：超时后拒绝下一轮直到 worker 退出】**
- `TestSuite.runAll(shouldStop: () -> Boolean = { false })`：每条用例执行前 `if (shouldStop()) { logger.w(...); break }`，已跑结果照常返回。
- `MonitorMode.runAllWithTimeout`：持 `@Volatile var cancelled`；超时置 `cancelled=true` 再 `shutdownNow()`；`suite.runAll { cancelled || Thread.currentThread().isInterrupted }`。
- `TestRunner.runTest` catch 分支：`if (e is InterruptedException) { Thread.currentThread().interrupt(); throw e }`——中断不当普通 FAIL 吞，向上传播让线程尽快停。
- **超时后进入 terminal 状态、阻止并发下一轮**：`MonitorMode` 持 `@Volatile var timedOut`；一旦某轮超时置 true，`runRound`/`runFinalVerification` 开头 `check(!timedOut) { "上一轮超时、worker 可能仍存活，拒绝启动新一轮（防并发操作 App/共享 results 竞争）；请人工确认设备状态后 reset" }`——把「僵尸线程仍在跑」与「主线程开新一轮」在时间上强制互斥（worker 忽略 interrupt 时的兜底）。`reset()` 清 timedOut。

**P0-7 maxRounds + Phase6 超时【R1 修订】**
- `runRound(timeoutMs)` 开头：`check(rounds.size < maxRounds) { "监工轮次已达上限 $maxRounds，疑似修复死循环，停下交用户" }`。
- **Phase6 默认即有超时**：`MonitorMode` 构造加 `defaultRoundTimeoutMs: Long = 0`（AI 驱动场景装配方设为非零，如 300_000）；`runRound(timeoutMs: Long = defaultRoundTimeoutMs)` 与 `runFinalVerification(timeoutMs: Long = defaultRoundTimeoutMs)` **都默认取它**——现有无参 Phase6 调用自动获得与 runRound 一致的超时保护（不再默认 0 裸奔）。

**P0-8 跨用例证据/统计隔离 → StepContext【R1 修订：双 key + 唯一 runId】**
- 新增 `data class StepContext(val caseId: String, val stepNumber: String, val stepName: String, val runId: String, val attempt: Int = 0)`。
- **区分两种 key**（消除同名 case / 多轮 / Phase6 / 重试 attempt 覆盖）：
  - **证据唯一 key**（截图/logcat）：`runId#caseId#stepNumber#attempt`——每次执行唯一，绝不覆盖历史证据。
  - **性能聚合逻辑 key**（Performance/Memory 百分位）：`caseId#stepNumber`——**按 case+step 聚合多样本**（改为 append 到 list 而非覆盖），百分位基于全样本；不再互相覆盖也不丢样本。
- `runId`：`Scenario` 创建时取一个进程内单调计数器（`AtomicLong`，避免 Date.now 依赖）生成，保证同名 scenario 多次执行（多轮/Phase6/RetryRunner 重跑）各自唯一。`attempt` 本批由 Scenario 内部重试语义传入（flakyStep/behavior 重放时 +1）。
- `Interceptor`/`BehaviorInterceptor` step 回调签名 `(stepNumber, stepName)` → `(ctx: StepContext)`；action 回调不变。破坏性接口变更（下方清单）。
- `InterceptorChain.stepScreenshotPath/stepLogcatPath(ctx)` 用证据唯一 key 回读。

**D1 重试体系统一 + 副作用保护【R1 修订：重放 fail-closed + 统一预算】**
- `flakyStep`：重试判定走 classifier 的 **Throwable 级** `classify(t)`（见 D2）——HARD_FAIL（NPE/AssertionError 等真 bug）直接抛，不再重试一切 Throwable。
- **behavior 重放 fail-closed**：`Step` 增 `retriable: Boolean = false`（**默认不重放**，钱包安全优先）。`runWithRecovery`：behavior 节点的 recovery **动作照常执行**（如关掉挡路弹窗），但仅当 `retriable=true` 才 `action()` 重放；`retriable=false` 时恢复后**不重放、原始失败照抛**（避免转账/签名类已生效副作用被重复执行）。只读/幂等步骤显式 `step(name, retriable = true)` 或用 `retriableStep(name)` 换回 dismiss-then-retry 行为。
- **统一重试预算**：新增 `RetryBudget`（进程内 or 每 Scenario 传入的最大总尝试数上限，默认如 5），`flakyStep` / behavior 重放 / RetryRunner 共同 `consume()`，超预算即停——防三层叠乘成数十次。至少落一个全局 `MAX_TOTAL_ATTEMPTS` 硬顶 + 单测。
- `stepWithRecovery`：recovery 自身抛错时 `newEx.addSuppressed(originalError)` 再抛（不丢原始失败）。

**D2 FlakyClassifier 接口化【R1 修订：默认不可变实例 + Throwable 级分类】**
- 抽 `interface FlakyClassifierApi { fun classify(t: Throwable): FlakyType; fun classify(message: String?): FlakyType }`（Throwable overload 按**类型**先判：NPE/IllegalArgument 等归 HARD_FAIL 不受 message 措辞影响；再回落 message 规则；保留 message overload 兼容旧调用）。
- `class DefaultFlakyClassifier(rules: List<FlakyRule> = emptyList()) : FlakyClassifierApi`——**不可变实例、规则构造注入**。
- `RetryRunner(classifier: FlakyClassifierApi = DefaultFlakyClassifier())`——**默认用不可变实例**，不再默认依赖可被 registerRule/clearRules 改写的全局对象。
- 向后兼容：保留 `object FlakyClassifier`（内置 classify 逻辑迁到 DefaultFlakyClassifier，object 委托它）；`registerRule/clearRules` 标 `@Deprecated`（全局可变状态泄漏，改用 DefaultFlakyClassifier 实例注入），暂不删。

### 接口/行为变化清单（供 debox 回归）

1. `Interceptor.beforeStep/afterStep/onStepFailure` 签名 `(stepNumber, stepName)` → `(ctx: StepContext)`；`BehaviorInterceptor.tryRecover` 同（自定义拦截器需改签名）。
2. `flakyStep` 不再重试 HARD_FAIL 类异常（NPE 等真 bug 立即抛，不再重试到耗尽）。
3. **`Step.retriable` 默认 false = behavior 链默认不重放**：原「弹窗自动 dismiss 后重试」现只对 `step(retriable=true)`/`retriableStep` 生效；debox 依赖自动 dismiss-retry 的只读步骤需显式标 retriable。
4. `FlakyClassifier.registerRule/clearRules` @Deprecated；`RetryRunner` 默认 classifier 改为 `DefaultFlakyClassifier()` 实例（不再受全局 registerRule 影响）。
5. `MonitorMode` 超过 maxRounds、或上一轮超时未 reset 时抛 IllegalStateException（原静默无限累积/并发）。
6. `MonitorMode` 构造新增 `defaultRoundTimeoutMs`；Phase6 默认带超时。
7. 三层重试共享 `MAX_TOTAL_ATTEMPTS` 硬顶，超顶即停。

### 可选方案

- P0-8 用「per-scenario reset」而非 StepContext——否决：Performance/Memory 需要跨用例聚合做百分位趋势，reset 会丢；StepContext 保聚合又消歧。
- D2 直接删全局 registerRule——否决：debox 可能在用，先 @Deprecated 过渡。

### 实现步骤

1. StepContext + Interceptor/BehaviorInterceptor 接口改造 + 各拦截器 key 改造 + InterceptorChain/Scenario 接线。
2. P0-6 协作取消（TestSuite/MonitorMode/TestRunner）+ 单测（注入 shouldStop 验证提前停 + InterruptedException 传播）。
3. P0-7 maxRounds check + Phase6 超时 + 单测。
4. D1 flakyStep 走 classifier + Step.retriable + runWithRecovery 不重放 + stepWithRecovery addSuppressed + 单测。
5. D2 FlakyClassifierApi 接口 + DefaultFlakyClassifier + RetryRunner 注入 + 单测。
6. 更新受影响既有测试（InterceptorChainTest / BehaviorChainTest / PerformancePercentileTest / RetryRunnerTest / MonitorModeTest 等）到新签名。
7. LIB_VERSION 1.6.2；全量 test + publishToMavenLocal。
8. Codex impl review → 修 → 回写。

### 验证方式

- 全量 JVM 单测通过（新增：僵尸线程协作取消、maxRounds 上限、StepContext 跨用例不串味、flakyStep HARD_FAIL 不重试、retriable=false 不重放、FlakyClassifier 实例注入）。
- 这些都是纯 JVM 可测（引擎/重试/拦截器 key 逻辑不依赖设备），无需 instrumented。

### 风险

1. Interceptor 接口破坏性变更波及 debox 自定义拦截器（若有）——列清单，回归时同步改；框架内置拦截器本批一并改。
2. 协作取消依赖 suite 循环检查点——只能在「用例之间」停，单条用例内部卡死仍靠 timeout 兜底（本就如此，不退化）。
3. StepContext.attempt 本批先占位（默认 0），RetryRunner 的 attempt 感知留后续，不在本批过度设计。

### 需要用户确认

- 无（批次范围已确认）。

---

## Accepted Plan

Version: v2（R1 三 Critical + 四 Important 全部修订后）

Accepted At: 2026-07-02

Accepted By:
- Claude Code: 是
- Codex: plan-review R2 `VERDICT: PASS`（R1 FAIL → R2 PASS）
- User: 批次范围已确认（四批全做）

Decision: PASS

Scope: 上文 P0-6/7/8 + D1/D2 全部改动点（含【R1 修订】）+ 单测 + 接口/行为变化清单 7 条。LIB_VERSION 1.6.2。

Verification: 全量 :autotest:test + publishToMavenLocal（纯 JVM 可测，无 instrumented 依赖）。

Accepted Risks: 无。

Deviation Policy: 触碰重试语义（retriable 默认/预算硬顶）、并发取消模型、StepContext key 语义的偏离先 consult Codex。
