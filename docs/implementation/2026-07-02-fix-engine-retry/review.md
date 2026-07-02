# ③ 过程 & 问题

## Plan Review Rounds

### Plan Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（3 Critical + 4 Important）
  - C1 [Security 92]：retriable 默认 true，副作用步骤仍可能被 behavior 重放。
  - C2 [Bugs 90]：runFinalVerification 默认 timeout=0，Phase6 无参调用无超时保护。
  - C3 [Design 90]：RetryRunner 默认仍依赖可被 registerRule 改写的全局 object。
  - I1 [Design 88]：StepContext key 只 scenario#step，同名/多轮/Phase6/attempt 仍覆盖。
  - I2 [Design 86]：三层重试无统一预算，仍可叠乘。
  - I3 [Bugs 84]：classify 只看 message，无法按 Throwable 类型可靠判 HARD_FAIL。
  - I4 [Regression 83]：超时后未阻止存活 worker 与下一轮并发。
- 处理（Plan Revision v2，全部采纳）：
  1. `Step.retriable` 默认 **false**（fail-closed），behavior recovery 动作照跑但仅 retriable=true 才重放；retriableStep 换回 dismiss-retry。
  2. `MonitorMode.defaultRoundTimeoutMs` 构造参数，runRound + runFinalVerification 都默认取它（Phase6 不再裸 0）。
  3. `RetryRunner` 默认 `DefaultFlakyClassifier()` 不可变实例；全局 object 仅兼容入口 @Deprecated。
  4. StepContext 双 key：证据唯一 key `runId#caseId#step#attempt`（不覆盖）+ 性能聚合逻辑 key `caseId#step`（append 多样本）；runId 用 AtomicLong 单调计数。
  5. `RetryBudget` + `MAX_TOTAL_ATTEMPTS` 硬顶，三层重试共享消费。
  6. `FlakyClassifierApi.classify(t: Throwable)`（类型先判 HARD_FAIL）+ 保留 message overload。
  7. 超时后 `timedOut` terminal 态，runRound/runFinalVerification 开头 check 拒绝并发下一轮，reset 清除。

### Plan Review R2 — 2026-07-02

- 结果：**VERDICT: PASS**（Critical/Important/Questions/Required Changes 全 None）→ 落 Accepted Plan v2，进入实现。

## Implementation Review Rounds

### Impl Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（4 Critical + 1 Important）
  - C1[95] runId 在构造时生成，多轮/Phase6 复用 → 证据隔离失效。
  - C2[93] attempt 恒 0、onRetry 未接入 Scenario.run → attempt 维度未生效。
  - C3[94] RetryBudget 各层局部封顶未共享 → 三层仍可叠乘。
  - C4[90] logcat/screenshot 文件名未含 runId/attempt → 同秒同 step 多 case 底层文件覆盖。
  - I[85] FlakyRule 入参改 String? 破坏现有 `message.contains` 规则。
- 处理（全部采纳）：runId 移进 run()；onRetry 递增 attempt + ctx.copy 触发证据；StepRetryBudgetHolder ThreadLocal 三层共享 per-step budget；StepContext.fileSafeKey 进文件名；FlakyRule 回退非空。补 5 条回归测试，单测 250→255。

### Impl Review R2 — 2026-07-02

- 结果：**VERDICT: FAIL**（1 Critical，实为上下文不足误报 + 1 真实小瑕疵）
  - Critical[92]：担心 onRetry 忽略 tryConsume() 返回值。**核实**：runWithRecovery 有 `if (!onRetry()) break`、onRetry lambda 返回 tryConsume() 的 Boolean，硬顶实际已 enforce；Codex 因未拿到 runWithRecovery diff（Verification Gap 自述）从保守角度判 Critical。真实瑕疵 = `attempt++` 在 tryConsume() 前，预算耗尽时 attempt 多加一次（off-by-one）。
- 处理：onRetry 改为「仅 tryConsume() 成功才 attempt++ 并返回 true，否则 false」；R3 补齐 runWithRecovery/RetryBudget/flakyStep/测试完整源码给 Codex 复核。

### Impl Review R3 — 2026-07-02

- 结果：**VERDICT: PASS**（补齐 enforcement 源码后，Critical/Important/Gaps 全 None）→ 三层预算硬顶经代码核实成立，进入归档。

## Review Fix Log

（待记录。）

## Accepted Risk

- 无。

## Verification

- JVM 单测 **255 全过**（第一批 227 → +28）；publishToMavenLocal 1.6.2 成功。
- 纯 JVM 可测，无 instrumented 依赖。
- 行为变化清单 7 条已录（供 debox 回归）。
