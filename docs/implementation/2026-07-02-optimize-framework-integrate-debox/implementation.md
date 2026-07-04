# Implementation — v1.8.0 后框架/文档/逻辑优化 + debox-android 接入

> ② 实现。当前状态以 `index.md` 为准；本文件存实现过程记录。严格按 `plan.md` 的 Accepted Plan 实现，偏离必记。

## Implementation Log

### WS-A1 · 静默缺口处置（v1.8.1）— done（2026-07-02/03）

| # | 项 | 实际改动 |
|---|---|---|
| 1 | C3 | `SelfHealingLocator.deterministicFind`：新增 `primaryLeaves()`（companion 纯函数，`distinctBy { attr }`）驱动 BySelector，同属性其余 Leaf 由既有完整-conjunction 后置过滤兜底（不丢条件）；同属性二次 set 的 IllegalStateException 路径消除 |
| 2 | E1 | `TestLifecycleManager` 增 `var logger: TestLogger?`（与 InterceptorChain 同模式）；`safely(phase, hook)` 异常留痕（phase+hook 类名+异常摘要）。接线：BaseUiTest.setUp + TestRunner.init |
| 3 | E2 | `TestCase.execute` 记录 primary，`resolveAfterFailure`（companion 纯函数）：主失败在 → addSuppressed 不另抛；主流程成功 → after 异常作为主失败抛出 |
| 4 | E3 | `BaseActivityTest.tearDown` 加 `::scenario.isInitialized` 守卫 |
| 5 | E6 | UiReplayExecutor：SLEEP 非法 payload 显式失败（`parseSleepMs` 纯函数，不再默默回退 1000ms）+ LAUNCH_APP 后 `waitForApp` 10s 到前台才算成功；ScreenshotRule：mkdirs/takeScreenshot 失败留痕（`var logger` 注入，未注入降级 logcat）；ReportCollector：根因分析降级留痕（`var logger` 注入） |
| 6 | Q4 | SelectorSpec.Leaf REGEX 构造期编译校验 + `pattern` 字段缓存复用（matchLeaf/applyLeaf/toPattern 三处消费）；TestCaseParser 4 个正则提 object 级常量 |

- 版本：LIB_VERSION 1.8.0 → **1.8.1**；publishToMavenLocal ✅；verifyNoProtobufLite ✅。
- 单测：**311 全过 / 0 失败 / 0 跳过**（原 295 + 新增 16：SelectorRegexFailFastTest ×4、PrimaryLeavesTest ×4、AfterFailurePolicyTest ×3、SleepPayloadTest ×4、TestLifecycleManagerTest +1）。
- 统计现值：main 5790 行 / 70 文件 / 19 包；test 62 文件 / 311 条 @Test。

#### 行为变化清单（供 WS-B B3 debox 同步核对）

1. E2：`TestCase.execute` 的 after 失败不再被吞——主流程成功时 after 异常 = 测试失败；主流程失败时附加 suppressed（根因不漂移）。
2. E6-SLEEP：缓存回放 SLEEP 步骤 payload 缺失/非数字/负数 → 步骤显式失败（原静默回退 1000ms）。
3. E6-LAUNCH_APP：回放 LAUNCH_APP 后 10s 内 App 未到前台 → 步骤失败（原发完 monkey 即过）。
4. Q4：REGEX 选择器非法 pattern 在**构造处**抛 IllegalArgumentException（原定位执行时才抛 PatternSyntaxException）。
5. C3：`byText(...) and byTextContains(...)` 同属性组合原来**必抛** IllegalStateException，现在正常工作（主 Leaf 查找 + 全条件后置过滤）——修 bug，无既有正确行为可破坏。

#### 延后记录（Accepted Plan 决定，消除「静默缺口」无人认领状态）

- Q2 指纹库全量重写（写放大）：B2 已保原子性，增量化属结构改动，收益低——延后。
- Q3 计时统一 elapsedRealtime（21 处）：机械但触全部计时语义，宜独立任务整体做+回归——延后。**（已兑现：2026-07-04 独立任务 `2026-07-04-01-unify-monotonic-timing/` 实施，v1.8.2）**
- Q8 叠层弹窗连关带上限：与 P0-1 保守化方向相逆，待真实叠层弹窗证据——延后。

### WS-A2 · docs 统一对齐（1.8.1）— done（2026-07-03）

| 文件 | 改动 |
|---|---|
| docs/09-AI驱动测试机制.md | **整篇修订 v1.2**：示例 `locator.find().click()` → `locator.click()/element()`（守卫路径）+ find 弃用说明；补 safety 网关总述、provisional 指纹（封顶 0.85/TTL 24h/不转正）、StepContext 双 key、缓存回放 fail-fast（空用例/SLEEP/LAUNCH_APP）；硬断言无评估器语义改为「SKIPPED 留痕后抛 AssertionError」（对齐 P0-5）；`registerRule` → `DefaultFlakyClassifier(rules)` 构造注入（API 经源码核实：RetryRunner.classifier 参数/FlakyRule fun interface/`safety.dangerousTexts` 键名均验证存在）；DialogDismiss 纯 behavior |
| CLAUDE.md | 结构块补 `safety/` 行；report 行补 ReportCollector/ReportWriter；stability 行改 FlakyClassifierApi 构造注入 |
| docs/01-架构设计.md | v1.8.1 / 5790 行 / 311 条·62 文件；模块树补「安全层 safety/」；DialogDismiss 纯 behavior；stability 行接口化 |
| docs/03-可行性评估.md | v1.8.1 / 311 条；里程碑行补 v1.8.x 静默缺口清扫 |
| docs/04-开发计划.md | 311 条 / 62 文件 |
| docs/10-项目技术分享.md | v1.8.1 / 5790 行 / 311 条；DialogDismiss 描述更新 |
| README.md | v1.8.1 ×3 处；**新增「接入方 runner 配置（通用）」section**（testInstrumentationRunner/test-config/包名后缀注意/queries/定向执行/pm list instrumentation 核验/POM 已根治说明）+ gated 接入建议 |
| CHANGELOG.md | 新增 [1.8.1] 条目（Fixed/Changed 行为收紧/Deferred 正式记录/Stats） |

- L4 复核：`grep v1.7.1|280 条|54 个测试|5600 行|双角色|watcher 预扫|1.8.0` 在 README/CLAUDE/docs 01/03/04/09/10 全部清零（02/05-08 历史文档按 Non-goals 不动）。

### WS-B · debox-android 接入收尾 — in progress（2026-07-03）

- debox 侧任务目录：`debox-android/docs/implementation/2026-07-03-01-upgrade-autotest-1.8-e2e-smoke/`（B0 清单 + 步骤记录在彼处，两仓各自权威）。
- 进度：B0 ✅（点击点清单全过守卫 + `safety.dangerousTexts` 项目词表注入）/ B1 ✅（1.7.2→1.8.1）/ B2 ✅（方案②：`app.packageName=com.tm.security.wallet.test`，框架无 targetContext 回退已核实）/ B3 ✅（核对零触及，无需改码）/ B5 ✅（authoring.md + lessons.md 补注）/ B4 🔄（设备阶梯偏离：L1 模拟器无 `.test` 安装与登录态 → 按铁律#8 升 L3 真机 S25，Accepted Plan 风险行预授权）/ B6 ⏳。

## Plan Deviation Log

| Time | Deviation | Reason | Consulted Codex | User Confirmed |
| --- | --- | --- | --- | --- |
| 07-03 | debox 任务目录名 `2026-07-03-01-...`（plan 原文写 `2026-07-02-upgrade-autotest-1.8-e2e-smoke`） | 实际创建日 07-03 + NN 序号约定（07-02 之后新建任务生效） | 否（命名规范机械应用） | — |
| 07-03 | B4 设备 L1→L3（S25） | 模拟器只装无后缀包、无 `.test` 登录态；铁律#8 取最低可行层级 + plan 风险行预授权「按阶梯换设备，不清数据重建」 | 否（plan 内已授权路径） | — |

## Consult Log

（暂无）
