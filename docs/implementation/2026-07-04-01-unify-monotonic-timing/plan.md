# Plan — 计时统一单调钟（审计延后项 Q3）

> ① 计划 / 方案。当前状态以 `index.md` 为准；本文件存方案与各轮 plan-review **过程历史**（per-round VERDICT 是快照，非当前状态）。

## User Request

```text
用户指令「按照你的来开始做」，承接 2026-07-04 项目遗留审视的推荐顺序，
启动审计延后项 Q3：框架内时长/超时计时统一到单调钟（原提案措辞 elapsedRealtime），
消除墙钟（System.currentTimeMillis）被 NTP 同步/手动调时回拨·前跳时，
超时判定瞬间失效（提前超时或永不超时）的风险。
延后记录出处：docs/implementation/2026-07-02-optimize-framework-integrate-debox/implementation.md
「Q3 计时统一 elapsedRealtime（21 处）：机械但触全部计时语义，宜独立任务整体做+回归——延后。」
```

## Naming Check

- Dir path: `docs/implementation/2026-07-04-01-unify-monotonic-timing/` ✓（2026-07-04 当日首个任务，NN=01）
- `verb-object`：unify-monotonic-timing ✓

## Background

- Project context：autotest 框架 v1.8.1（`autotest/build.gradle` LIB_VERSION=1.8.1），311 条 JVM 单测全绿。
- Relevant docs / files：`src/main` 全量 25 处 `System.currentTimeMillis()`（宽 grep 实测，原提案记 21 处系 v1.8.x 后新增了 GuardEvent 等）。
- Project-specific rules：铁律#5 修复走 agent-dev-loop；发布走 `publishToMavenLocal`。
- **已读两层账本**：是。全局 L1（改约定先宽 grep 全仓——本任务 25 处清单即产物）、L2（一个 finding 查所有同类文件——同类=所有计时点逐一分类）、L13/L16/L23（codex exec 只读评审姿势/后台跑法/stdin 封死）；项目 L-001（修复必须走本闭环）、L-003（提案项要么实施要么显式记延后——本任务就是在兑现 Q3 延后记录的触发）。

## Goals / Non-goals / Constraints

- Goals:
  - [ ] 时长/超时类**控制流**计时（11 处）改走单调时间源，墙钟跳变不再影响超时判定与耗时统计。
  - [ ] 单调时间源 JVM 可测（现有 311 条 JVM 单测不破坏）、可注入假钟，Android 端用 `SystemClock.elapsedRealtime()`。
  - [ ] 持久化/展示/命名类墙钟（14 处）**显式保留**并逐处注释语义，杜绝后人误改。
  - [ ] 新增假钟单测覆盖超时判定路径；全量单测回归绿；版本 1.8.2 发布 mavenLocal。
- Non-goals:
  - 不改 RunReport JSON schema（startTime/endTime 仍是 epoch 墙钟，消费方 HtmlReporter/外部解析不受影响）。
  - 不动持久化格式（指纹库 lastSeenMs、TestHistoryStore 仍存墙钟 epoch）。
  - 不做 Q2（指纹库增量写）/Q8（叠层弹窗上限），维持各自延后记录。
- Constraints:
  - 不记录任何 secret；Codex 只读评审；不需要用户中途确认（低风险纯框架内改动）。

## Plan Proposal

### 目标

框架内「测量一段时间有多长」的代码不再读墙钟。墙钟只保留给三类合法语义：持久化时间戳（跨进程/重启比对）、报告展示（人读的日期时间）、文件命名（唯一性）。

### 25 处全量分类（宽 grep 对账，L1/L2）

**A 组 · 改单调钟（11 处，控制流时长/超时）：**

| 文件:行 | 语义 |
|---|---|
| `util/WaitUtil.kt:34,36` | waitUntil 轮询超时判定（墙钟回拨 → 永不超时；前跳 → 瞬间假超时） |
| `stability/FlakySafely.kt:34,37` | flakySafely 重试窗口判定（同上） |
| `intercept/InterceptorChain.kt:135,138` | afterAction 动作耗时 |
| `dsl/Scenario.kt:32,48,64` | step 耗时（写 StepResult.durationMs，报告展示的是**时长**不是时刻） |
| `engine/TestRunner.kt:67,117` | 用例 durationMs |

**B 组 · 保留墙钟（14 处，逐处加语义注释）：**

| 文件:行 | 保留理由 |
|---|---|
| `selector/FingerprintStore.kt:45,62` | nowMs 写入**持久化** lastSeenMs（跨进程/重启比对，单调钟重启归零不可用） |
| `selector/SelfHealingLocator.kt:134` | nowMs() 与持久化 lastSeenMs 比对 TTL，必须同源墙钟 |
| `stability/TestHistoryStore.kt:41` | 持久化测试历史时间戳 |
| `selector/LocatorEvent.kt:30`、`safety/GuardEvent.kt:33` | 报告展示时间戳（人读） |
| `assertion/AiAssert.kt:42` | AiAssertion 展示时间戳 |
| `report/ReportCollector.kt:44,79`、`engine/TestRunner.kt:62,148`、`base/BaseUiTest.kt:209` | RunReport.startTime/endTime epoch 语义（HtmlReporter 按 Date 格式化展示），schema 不动 |
| `base/BaseUiTest.kt:80,128` | 截图文件名唯一性 |

> 注：HtmlReporter 用 `endTime - startTime` 展示总时长属**展示层近似**（跨墙钟跳变时偏差），接受——它不驱动任何控制流；改 schema 收益低于成本（Non-goal）。

### 实现步骤

1. **新增 `com.autotest.util.MonotonicTime`**（object，默认源=**运行时探测**，plan-review R1 修订）：
   - 类初始化时 probe 一次 `SystemClock.elapsedRealtime()`：Android 运行时成功 → 默认源 = `{ SystemClock.elapsedRealtime() }`（计 deep sleep，完整兑现 Q3 语义，**对所有入口生效**，不依赖 BaseUiTest）；JVM 单测中 android.jar stub 抛 not mocked → 回退默认源 = `{ System.nanoTime() / 1_000_000 }`（JVM 也是 CLOCK_MONOTONIC，**311 条现有单测零改动直接过**）。
   - **可见性收敛（plan-review R2 修订）**：`private @Volatile var source: () -> Long = DEFAULT`，对外公开只有 `fun nowMs(): Long`；假钟注入口为 `internal fun overrideSourceForTest(source: () -> Long)` + `internal fun resetForTest()`（Kotlin 同模块 test source set 对 internal 可见，AAR 外部消费者不可及——全局计时源不成为对外可变 API）。
   - 不再需要 BaseUiTest `@Before` 切换（R1 finding 的根除式修复：非 BaseUiTest 入口同样正确）。
   - 假钟注入 = 测试调 `overrideSourceForTest`，finally 里 `resetForTest()`（KDoc 注明非并发安全、测试须复位）。
2. **A 组 11 处机械替换**为 `MonotonicTime.nowMs()`；B 组 14 处逐处补一行语义注释（`// 墙钟：持久化/展示/文件名语义，勿改单调钟`，按处措辞）。
3. **新增单测**（JVM，假钟）：
   - `MonotonicTimeTest`：JVM 上探测回退 nanoTime 且非降序；overrideSourceForTest 注入生效；resetForTest 复原到探测所得默认。
   - `FlakySafelyClockTest`：假钟不前进 → 不会假超时（按次数重试）；假钟一步跳过 timeoutMs → 走最后一次尝试后抛 AssertionError。
   - `WaitUtilClockTest`：条件恒 false + 假钟跳变 → 抛「等待超时」；条件立即 true → 不抛。
   - `InterceptorChain`/`Scenario`/`TestRunner` 现测回归 + 各补一条「假钟步进 → durationMs 精确等于步进值」。
4. **护栏**：宽 grep 复核 `src/main` 内 `System.currentTimeMillis` 仅剩 B 组 14 处白名单（对账清单进 implementation.md）。
5. **版本与文档**：LIB_VERSION → 1.8.2；CHANGELOG 记条目；grep 全仓版本号引用同步（L1）；`docs/09-AI驱动测试机制.md`/`01-架构设计.md` 若有涉计时表述则对齐（预计仅版本号）。
6. `./gradlew :autotest:test` 全绿 → `publishToMavenLocal`。

### 验证方式

- 全量单测（311 + 新增 ≈ 8-10 条）0 失败。
- 宽 grep 对账：A 组 0 残留、B 组恰 14 处且各带语义注释。
- `publishToMavenLocal` 产出 1.8.2。
- debox 侧不在本任务内升级（消费方回归由下一次测试 run 承接，与 v1.8.1 流程一致）。

### 风险

- **运行时探测默认源**：探测只区分「SystemClock 可用/不可用」。Android 上恒为 elapsedRealtime（无残余风险）；Robolectric 类环境会探测到假 SystemClock——本项目无 Robolectric（Non-goal 范围外），KDoc 注明可经 `source` 覆盖。
- **可变全局时间源**：注入口已收敛为 internal 测试辅助（R2 修订），AAR 外部无法改写；模块内并发测试注入互踩的残余风险，缓解 = KDoc 约定测试串行注入 + finally resetForTest；Gradle JVM 单测默认串行。
- **行为变化**：墙钟跳变场景从「超时判定错乱」变为「不受影响」——只修 bug 无正常路径变化；durationMs 数值语义（ms）不变。

### 需要用户确认

- 无（低风险、纯框架内、不改对外 schema/持久化格式）。

## Plan Review Log（per-round，含 VERDICT 快照）

### Plan Review Round 1（2026-07-04，codex exec read-only, effort=medium）

```text
VERDICT: FAIL
```

Critical: None

Important:
- [Plan alignment / Regression risk] [85] 「Android 端用 elapsedRealtime」只经 BaseUiTest.@Before 落地；WaitUtil/flakySafely/TestRunner 等公开 API 被非 BaseUiTest 入口用于 instrumentation 时仍走默认 nanoTime，不计 deep sleep，未完全兑现 Q3 语义 -> 让 Android 运行时默认源就是 elapsedRealtime 且 JVM 可替换，或把非 BaseUiTest 入口记为显式 accepted risk。

Questions: None

Required Changes:
1. 修订 MonotonicTime 初始化策略或入口覆盖策略，确保 Android 端所有公开计时 API 默认符合 elapsedRealtime 语义；若只覆盖 BaseUiTest 路径则按 accepted risk 格式记录。

### Plan Review Round 2（2026-07-04，codex exec read-only, effort=medium）

```text
VERDICT: FAIL
```

Critical: None

Important:
- [Design / Regression risk] [82] 计划把 `@Volatile var source` 放在 src/main 的 public object 上，全局计时源成为 AAR 公开可变 API；外部误改/未 reset 会影响 WaitUtil/flakySafely/TestRunner 全部超时控制流 -> setter 收敛为测试辅助（internal / @VisibleForTesting），不公开裸 var。

Questions: None

Required Changes:
1. 收敛 MonotonicTime.source 可见性与写入口；保留 JVM 假钟注入但限定测试辅助。
2. （确认）R1 finding 已由运行时探测方案解决，无需 BaseUiTest 切换。

### Plan Review Round 3（2026-07-04，codex exec read-only, effort=medium）

```text
VERDICT: PASS
```

Critical: None / Important: None / Questions: None / Required Changes: None

## Plan Revision Log

> 每次按 review 修订方案时追加：vN · 针对哪条 finding · 改了什么。

- **v3（2026-07-04，针对 R2 Important-82）**：`source` 收为 private @Volatile；对外仅 `nowMs()`；注入口改 `internal overrideSourceForTest/resetForTest`（同模块单测可见、AAR 外不可及）；风险节同步改写。
- **v2（2026-07-04，针对 R1 Important-85）**：默认源从「nanoTime + BaseUiTest 手动切」改为「类初始化运行时探测：Android → SystemClock.elapsedRealtime（全入口生效）；JVM → nanoTime 回退」；删除 BaseUiTest.@Before 切换步骤；风险节同步改写（残余风险仅 Robolectric 类环境，项目未用）；MonotonicTimeTest 补探测行为断言。

## Accepted Plan Summary

- Plan: v3（本文件 Plan Proposal 全文即基线）——25 处墙钟分类：A 组 11 处控制流时长/超时 → `MonotonicTime.nowMs()`（默认源运行时探测：Android=SystemClock.elapsedRealtime 全入口生效 / JVM=nanoTime 回退；private @Volatile source，对外仅 nowMs()，注入口 internal overrideSourceForTest/resetForTest）；B 组 14 处持久化/展示/命名 → 保留墙钟+逐处语义注释；假钟单测（MonotonicTime/FlakySafely/WaitUtil/InterceptorChain/Scenario/TestRunner）；宽 grep 白名单对账；LIB_VERSION 1.8.2 + CHANGELOG + 版本引用同步；:autotest:test 全绿 → publishToMavenLocal。
- Accepted at: 2026-07-04（plan review R1 FAIL → v2 → R2 FAIL → v3 → R3 PASS）
- Decision: PASS（R3 零 finding）
- Accepted risks: 无（Robolectric 类环境探测到假 SystemClock 属 Non-goal 范围外，KDoc 注明可覆盖）。
