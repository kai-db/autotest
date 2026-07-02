# ② 实现

## Implementation Log

### 2026-07-02 按 Accepted Plan v2 实施

**新增类**
- `intercept/StepContext`：caseId/stepNumber/stepName/runId/attempt；`evidenceKey`=runId#caseId#step#attempt（证据唯一）、`logicalKey`=caseId#step（性能聚合）。
- `stability/RetryBudget`：MAX_TOTAL_ATTEMPTS=5 硬顶，markFirstAttempt/tryConsume/remaining。
- `stability/FlakyClassifierApi`（接口，classify(Throwable)+classify(message)）、`DefaultFlakyClassifier`（不可变实例、规则构造注入、类型级 HARD_FAIL 白名单：NPE/IllegalArgument/IndexOOB/ClassCast/ConcurrentMod）。

**P0-6 僵尸线程协作取消**
- `TestSuite.runAll(shouldStop = {false})`：每用例前检查点，命中即 break（已跑结果照返）。
- `MonitorMode.runAllWithTimeout`：AtomicBoolean cancelled，超时置 cancelled + timedOut + shutdownNow；suite.runAll{cancelled||interrupted}。
- `TestRunner.runTest`：catch(InterruptedException) 恢复中断位 + 上抛（不当普通 FAIL 吞），finally 仍清理。
- `MonitorMode.timedOut` terminal 态：runRound/runFinalVerification 开头 `check(!timedOut)` 拒绝并发下一轮，reset() 清除。

**P0-7 maxRounds + Phase6 超时**
- runRound 开头 `check(rounds.size < maxRounds)`。
- `MonitorMode(defaultRoundTimeoutMs=0)`；runRound 与 runFinalVerification 都默认取它（Phase6 不再裸 runAll 无超时）。

**P0-8 StepContext 双 key**
- Interceptor.beforeStep/afterStep/onStepFailure、BehaviorInterceptor.tryRecover 签名改 `(ctx: StepContext)`。
- Screenshot/Logcat 用 evidenceKey（不覆盖）；Performance/Memory 用 logicalKey（Performance 改 append 多样本、百分位基于全样本）。
- Scenario 持进程内 AtomicLong runId，run() 构造 StepContext 传入 + 回读。

**D1 重试统一 + 副作用保护**
- flakyStep 走 classifier.classify(Throwable)——HARD_FAIL 立即抛不重试；受 maxRetries 与 RetryBudget 双封顶。
- Step.retriable 默认 false；InterceptorChain.runWithRecovery(ctx, retriable, onRetry, action)：recovery 动作照跑，仅 retriable=true 且预算未耗尽才重放；ScenarioBuilder.step(retriable=false 默认)/retriableStep。
- stepWithRecovery：recovery 抛错 addSuppressed(原始) 再抛。
- RetryRunner：maxRetries coerceAtMost(MAX_TOTAL_ATTEMPTS-1)；classify(e) 类型级判定。

**D2 FlakyClassifier 接口化**
- object FlakyClassifier 委托内置 DefaultFlakyClassifier；registerRule/clearRules @Deprecated（保留兼容）。
- RetryRunner 默认 classifier = DefaultFlakyClassifier()（不再默认依赖全局可变 object）。

**版本**：1.6.1 → 1.6.2。

### 验证结果

- JVM 单测 **250 全过**（第一批后 227 → +23：RetryBudget 4 / FlakyStep 4 / StepContextIsolation 4 / MonitorModeGuards 2 / TestSuiteCancel 2 / BehaviorChain 新增 retriable=false+onRetry+默认不重放 3 / FlakyClassifierRules Throwable 3 等；更新 BehaviorChainTest/InterceptorChainTest/PerformancePercentileTest/FlakyClassifierRulesTest/ScenarioTest 到新签名）。
- `publishToMavenLocal` 1.6.2 成功。
- 纯 JVM 可测，无 instrumented 依赖（引擎/重试/拦截器 key 逻辑不依赖设备）。

## Deviation Log

- 无（严格按 Accepted Plan v2；LogcatInterceptor 的 logcat -c 语义属审计 D3，本批显式不动，仅改 key 签名）。

## Consult Log

- N/A。

### 2026-07-02 修复 impl-review R1（4 Critical + 1 Important）

- **C1 runId 每次 run() 生成**（原在构造时，多轮/Phase6 复用）：移进 `Scenario.run()`。
- **C2 attempt 真正生效**：Scenario.run 用 onRetry 递增 attempt，behavior 重放后用 `ctx.copy(attempt=...)` 触发证据记录，evidenceKey 的 attempt 维度非空转。
- **C3 三层共享预算**：新增 `StepRetryBudgetHolder`（ThreadLocal），Scenario.run 每步建一个 RetryBudget 并 set，flakyStep 从 holder 取同一 budget、behavior 重放的 onRetry 也消费它 → 同一逻辑步骤总尝试 ≤ MAX_TOTAL_ATTEMPTS；RetryRunner 方法级独立封顶（KDoc 说明）。
- **C4 证据文件名唯一**：StepContext 加 `fileSafeKey`（evidenceKey 清洗非法字符），Screenshot/Logcat 文件名带它，防同秒/同 stepNumber/多 case 底层文件覆盖。
- **Important FlakyRule 入参回退非空**：`classify(message: String)`（不破坏现有 `message.contains` 规则），classifier 已对 null/空提前判 HARD_FAIL。
- 新增/更新测试：ScenarioRunIdTest（runId 每 run 变 + attempt 递增）、StepContextIsolation 补 fileSafeKey 唯一/清洗、FlakyStep 补三层在场硬顶、FlakyClassifierRules 改回非空 lambda。
- 复验：JVM 单测 **255 全过**；publishToMavenLocal 1.6.2 成功。
