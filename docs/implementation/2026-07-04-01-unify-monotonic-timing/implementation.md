# Implementation — 计时统一单调钟（审计延后项 Q3）

> ② 实现记录。当前状态以 `index.md` 为准。

## 按 Accepted Plan（v3）落地

### 1. MonotonicTime（新增 `util/MonotonicTime.kt`）

- 默认源类初始化运行时探测：probe `SystemClock.elapsedRealtime()` → Android 成功用 elapsedRealtime（全入口生效）；JVM 单测 stub 抛异常回退 `System.nanoTime()/1_000_000`。
- `private @Volatile var source`，对外仅 `fun nowMs()`；注入口 `internal overrideSourceForTest` + `internal resetForTest`（同模块单测可见，AAR 外不可及）。KDoc 注明：返回值只可相减求时长、Robolectric 场景可覆盖、测试须 finally 复位。

### 2. A 组 11 处替换（控制流时长/超时 → MonotonicTime.nowMs()）

| 文件 | 处 |
|---|---|
| `util/WaitUtil.kt` | waitUntil 起点+超时判定（2） |
| `stability/FlakySafely.kt` | 重试窗口起点+判定（2） |
| `intercept/InterceptorChain.kt` | intercept 动作耗时（2） |
| `dsl/Scenario.kt` | step 起点+成功/失败双路径 duration（3） |
| `engine/TestRunner.kt` | 用例 startTime+duration（2） |

### 3. B 组 14 处墙钟显式保留 + 逐处语义注释

FingerprintStore.record/recordProvisional（持久化 lastSeenMs TTL）、SelfHealingLocator.nowMs（与持久化 TTL 同源比对）、TestHistoryStore.record（持久化历史）、LocatorEvent/GuardEvent.timestampMs（报告展示）、AiAssert now（展示）、ReportCollector.starting/buildReport + TestRunner.suiteStartTime/generateReport endTime + BaseUiTest tearDown endTime（RunReport epoch 展示区间，schema 不变）、BaseUiTest 截图文件名 ×2（唯一性）。

### 4. 新增单测 12 条（全假钟，JVM）

- `util/MonotonicTimeTest`（3）：JVM 探测回退 nanoTime 且非降序；注入生效；reset 复原。
- `stability/FlakySafelyClockTest`（2）：假钟冻结 → 不假超时、按次数重试到成功；假钟跳过窗口 → 只走最后一次尝试后抛。
- `util/WaitUtilClockTest`（3）：条件立即真不读超时；假钟跳变 → 抛「等待超时」；假钟冻结 → 轮询到真不假超时。
- `intercept/InterceptorChainClockTest`（1）/`dsl/ScenarioClockTest`（2，成功+失败路径）/`engine/TestRunnerClockTest`（1）：假钟步进 → durationMs 精确等于步进值。

### 5. 版本/文档

- `LIB_VERSION` 1.8.1 → **1.8.2**；CHANGELOG 新条目（Added/Changed）。
- README（版本×3、单测数 323）、docs/01（版本、源码 71 文件/5846 行、单测 323/68 文件）、docs/03、docs/10、docs/04（1.11 条目数）同步。

## 验证结果

- `./gradlew :autotest:test`：**646 执行（323×debug/release 双变体）/ 0 失败 / 0 跳过**。
- 宽 grep 对账：`src/main` 内 `System.currentTimeMillis` 调用恰 **14 处**（+1 处为 MonotonicTime KDoc 文字提及，非调用），全部带「墙钟…勿改单调钟」语义注释；`MonotonicTime.nowMs` 调用恰 **11 处**。
- `publishToMavenLocal`：`~/.m2/repository/com/autotest/autotest/1.8.2/` aar/pom/sources 产出 ✓。

## Plan 偏离

- 无实质偏离。文档统计数字（71 文件/5846 行/68 测试文件）按实测更新，属 plan 步骤 5「版本引用同步」范围内。

## Consult 记录

- N/A（无 mid-impl consult）。
