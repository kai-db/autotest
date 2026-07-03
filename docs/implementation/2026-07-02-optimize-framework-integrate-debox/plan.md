# Plan — v1.8.0 后框架/文档/逻辑优化 + debox-android 接入

> ① 计划 / 方案。当前状态以 `index.md` 为准；本文件存方案与各轮 plan-review **过程历史**（per-round VERDICT 是快照，非当前状态）。

## User Request

```text
本项目看看架构/文档/逻辑/代码还需要怎么优化，以及debox-android项目接入
```

## Naming Check

- Dir path: `docs/implementation/2026-07-02-optimize-framework-integrate-debox/`（默认四文件）。
- `NN` 当天序号约定从 2026-07-02 **之后**新建任务生效（主协议决策9 向后兼容条款）；本仓今日既有 7 个任务目录均无 NN，本任务保持一致，grandfather。
- `verb-object`：optimize-framework-integrate-debox（优化框架 + 接入 debox），非模糊命名。

## Background

- Project context: autotest v1.8.0（5681 行 main / 19 包 / 295 JVM 单测），07-02 审计（P0×10 / P1 六主题 / P2×10）后经 5 任务四批实施到 v1.8.0。
- Relevant docs / files:
  - 前序审计：`docs/implementation/2026-07-02-audit-framework-architecture/plan.md`（Accepted Plan 提案全集）
  - 5 个实施任务 index.md（认领分工与 Follow-up）
  - debox 侧：`debox-android/docs/testing/autotest-authoring.md`、`debox-android/docs/implementation/2026-07-02-regress-autotest-1.7.1/`（升 1.7.2 + 删 4 处 workaround，端到端冒烟列为 follow-up）
- Project-specific rules: CLAUDE.md 铁律 #5（修复走 agent-dev-loop）、#7（危险操作 pre-action 比对，钱包 App 不可逆）、#8（模拟器优先阶梯）。
- **已读两层账本**：是。相关条目——全局 L13/L14/L16（codex exec 只读评审姿势/输出解析/后台超时）、L18（debox RN 子目录 CLI 构建需 `NODE_PATH=ReactNative/node_modules`）、L4（标完成前先验证）、L2（一个 finding 查所有同类）；项目 L-001（修复必走本协议）、L-002（安全不变量下沉代码守卫）。

## Goals / Non-goals / Constraints

- Goals:
  - [ ] G1 处置审计**被漏接的静默缺口**（既未实施也无延后记录）：修复正确性/静默失败类，其余补上正式延后记录，消除"无人认领"状态。
  - [ ] G2 落地前序已确认欠账①：docs/ 目录文档统一对齐 v1.8.x（CLAUDE.md / docs/01/03/04/09/10 / README 通用接入缺口）。
  - [ ] G3 落地前序已确认欠账②：debox-android 升级 1.7.2 → 1.8.x + 行为变化清单同步 + `.test` 包名对齐 + 端到端冒烟跑通 + 悬挂引用修复。
- Non-goals:
  - 不做新功能设计；不重启审计（以 07-02 审计结论 + 本次两路摸底为准）。
  - 不处理 `docs/02/05/06/07/08`（历史/规划文档，属历史陈述，仅当引用错误才动）。
  - 明确延后项（完整 DeviceGateway / cleanup run 子目录轮换 / MonitorMode sealed / airplane 真机分支 / HtmlReporter base64）维持延后，不重新拉起。
  - debox 侧不扩写新用例（只保证既有冒烟在 1.8.x 下跑通）。
- Constraints:
  - 不记录 secrets；debox 为钱包 App——**只读冒烟**，不清数据/不登出/不切环境（铁律#7）；`autotest.enabled` 用完复位默认 off。
  - Codex 只读；plan review 上限 3 轮，impl review/fix 上限 5 轮。
  - debox CLI 构建按 L18 前置 `NODE_PATH`。

## Plan Proposal

### 总体结构：两个工作流，先代码后文档再接入

依赖链：WS-A1 修代码 → bump v1.8.1 + publishToMavenLocal → WS-A2 文档一次性对齐到 1.8.1 → WS-B debox 升级接入（避免文档对齐两次 / debox 升到中间版本）。

### WS-A1 · 静默缺口处置（autotest 代码，产出 v1.8.1）

摸底证实以下项**既未实施也未在任何任务记录延后**。逐条处置决策：

**修复（6 项，正确性/静默失败，对齐原审计 P1-E 主题与 C3）**：

| # | 项 | 位置 | 修复方向 |
|---|---|---|---|
| 1 | C3 同属性多条件 conjunction 二次 set BySelector 抛 IllegalStateException，破坏 tryFind「不抛」契约 | SelfHealingLocator.kt:163,246-267 | **不丢任何条件**：每属性只取一个可驱动 UiAutomator 的**主 Leaf** 建 BySelector，**完整 conjunction 保留为后置过滤**（对候选元素逐条 matchLeaf 复核）；条件不可表达/不满足时受控返回 null（deterministicFind 失败走既有降级链），绝不未捕获抛出（R1-Important-1 收敛） |
| 2 | E1 TestLifecycleManager.safely 空 catch 且类无 logger | TestLifecycle.kt:63-69 | 注入 TestLogger（可选参数默认 DefaultTestLogger），safely 内 logger.w 留痕；与 InterceptorChain.safely 打法对齐 |
| 3 | E2 TestCase.after 失败仅 log 吞掉，脏状态传染后续用例 | TestCase.kt:64-70 | after 失败上抛（对齐 JUnit @After 语义），**带异常保真**：主流程已失败时 after 异常以 `addSuppressed` 附加到主失败上抛（根因不漂移）；仅主流程成功时 after 异常作为主失败抛出。**行为变更**进清单（R1-Important-2 收敛） |
| 4 | E3 BaseActivityTest.tearDown 无 isInitialized 守卫，掩盖 setUp 原始异常 | BaseActivityTest.kt:24-28 | `if (::scenario.isInitialized)` 守卫 |
| 5 | E6 四处静默 | UiReplayExecutor.kt:40（SLEEP 非法 payload 静默回退 1000ms）、:55-59（LAUNCH_APP 不等前台）、ScreenshotRule.kt:28（takeScreenshot 返回值忽略）、ReportCollector.kt:49-51（根因 catch→null 无留痕） | 非法 payload 记 warn + 显式失败该步骤；LAUNCH_APP 后 wait 前台（复用 WaitUtil）；截图失败 logger.w；根因 catch 加 logger.w |
| 6 | Q4 正则热路径 | SelectorSpec.kt:56（matchLeaf 每次 `Regex(value)`，且非法 pattern 运行期才炸）、SelfHealingLocator.kt:251,261,271-274、TestCaseParser.kt:47-82 | REGEX 类 Leaf 构造期校验并缓存 compiled；parser 正则提 companion 常量。兼具正确性（构造期 fail-fast）与性能 |

**补正式延后记录（3 项，不修，写入本任务 index.md Follow-up + 理由）**：

- Q2 指纹库全量重写（写放大）：B2 已保原子性，增量化属结构改动，收益低。
- Q3 计时统一 elapsedRealtime（21 处）：机械但触全部计时语义，宜独立任务整体做 + 回归。
- Q8 叠层弹窗连关带上限：DialogDismiss 刚经 P0-1 收敛为保守语义，连关循环与保守方向相逆，待真实用例出现叠层弹窗证据再做。

**验证**：`./gradlew :autotest:test` 全绿（≥295，新行为补单测：C3 去重、E2 上抛、E6 各分支）+ `verifyNoProtobufLite` + `publishToMavenLocal`（LIB_VERSION=1.8.1）。

### WS-A2 · docs 统一对齐（前序欠账①，1.8.1 定稿后一次性做）

| 文件 | 对齐内容 |
|---|---|
| CLAUDE.md | 项目结构补 `safety/`（P0-0 网关）；report 行补 ReportCollector/ReportWriter；版本相关表述核对 |
| docs/01-架构设计.md | v1.8.1 / 单测数 / 模块树补 safety / DialogDismiss「双角色」→纯 behavior / FlakyClassifier 接口化 |
| docs/03-可行性评估.md、04-开发计划.md | 版本号 / 单测数（280/54 → 现值） |
| docs/09-AI驱动测试机制.md | **整篇修订（漂移最重）**：`locator.find()` 已 @Deprecated（绕守卫）→ 改受守卫 API 示例；「无评估器自动 SKIPPED」与 P0-5 硬断言抛错矛盾 → 改现语义；`FlakyClassifier.registerRule` 已弃用 → FlakyClassifierApi；补 safety / StepContext 双 key / provisional 指纹 |
| docs/10-项目技术分享.md | 版本 / 行数 / 单测数 / DialogDismiss 描述 |
| README.md | 补**通用接入方** runner/instrumentation 配置说明（现仅 debox 特化示例，generic consumer 缺口） |

**验证**：对照摸底漂移清单逐条 grep 复核清零（L4：标完成前先验证）；历史过程文件（各任务 implementation.md 旧数字）**不回写**（防漂移铁律：过程历史不修改）。

### WS-B · debox-android 接入收尾（前序欠账②）

debox 侧改动在 **debox 仓另开任务目录**（`debox-android/docs/implementation/2026-07-02-upgrade-autotest-1.8-e2e-smoke/`，与该仓当日惯例一致），本任务 index.md 链接之；两仓任务记录各自权威。

| # | 步骤 | 内容 |
|---|---|---|
| **B0** | **危险操作前置（铁律#6/#7 执行步骤，非结论）** | ① 冒烟前**先读** **autotest 仓** `docs/testing/app-knowledge/`（元素表/弹窗/**dangerous-ops.md**——已验证该目录与文件真实存在于本仓，debox 仓无此目录；执行前再次校验可读，读不到即停）；② 点击点清单覆盖 **B4 完整执行路径的所有显式/隐式点击入口**——DeBoxSmokeTest 方法体 + `DeBoxBaseTest.@Before`（launchAppAndDismissDialogs/登录 preflight）+ 框架自动弹窗/权限处理（DialogDismissInterceptor 词表点击）+ interceptor 失败恢复路径，逐项对照 dangerous-ops.md 做 **pre-action 比对**并标注**是否被 `DangerousOpsGuard` fail-closed 覆盖**，结果留痕到 debox 任务目录；③ 把 `dangerous-ops.md` 项目危险词表接入 `DangerousOpsGuard`（构造参数 `dangerousTexts: List<String>` 通道已验证存在，DangerousOpsGuard.kt:23；优先经 `test-config.properties` 配置项注入，若无配置通道则 DeBoxBaseTest 显式传参并记录与 `DEFAULT_DANGEROUS_TEXTS` 的覆盖关系）；④ 核对 guard fail-closed 生效（命中即停）后才允许 B4 执行（R2-Important-2 收敛） |
| B1 | 升级依赖 | `app/build.gradle:335` `com.autotest:autotest:1.7.2` → `1.8.1`（gated 结构不动） |
| B2 | `.test` 包名对齐 | debug applicationId 实为 `com.tm.security.wallet.test`，而 `androidTestAutotest/assets/test-config.properties` 写无后缀包名。方案优先级：① 若框架支持从 targetContext 动态取包名则用之（免配置漂移）；② 否则 config 改 `.test` 后缀值。实施时查框架 TestConfig 现能力后二选一并记录 |
| B3 | 行为变化同步 | 按 engine-retry / safety-gate 任务的行为变化清单（StepContext 双 key、Interceptor 接口破坏性变更、retriable 默认 false、DialogDismiss 语义收敛、硬断言抛错等）核对 DeBoxBaseTest.kt / DeBoxSmokeTest.kt，编译不过或语义变化处同步修改；本次新增 E2（after 上抛）一并核对 |
| B4 | 端到端冒烟（**仅定向 DeBoxSmokeTest**） | 前置 = B0 完成。铁律#8 L1 模拟器优先（图灵盾不拦 instrumentation 已实证，debox lessons P2）。`autotest.enabled=true` + `NODE_PATH=ReactNative/node_modules`(L18)。**执行方式（保持只跑 DeBoxSmokeTest）**：首选 Gradle 定向 `:app:connectedAppDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tm.security.wallet.autotest.DeBoxSmokeTest`（Gradle 负责 assemble+install 两个 APK，runner args 限定执行面）；备选 `assembleAppDebug`+`assembleAppDebugAndroidTest` 手动 install 后 `am instrument -w -e class ...`，instrumentation 组件名**不硬编码**，装包后用 `pm list instrumentation` 实测取值（debug `.test` 后缀下 test APK 包名预期为 `com.tm.security.wallet.test.test`，以实测为准）→ 全 PASS + resolved classpath 无 protobuf-lite（R1-Critical + R2-Important-1 收敛） |
| B5 | 悬挂引用修复 | `autotest-authoring.md` / debox `lessons.md` 指向已不存在的 `2026-07-02-integrate-autotest-instrumented/` → 改指 `2026-07-02-regress-autotest-1.7.1/` 与新任务目录 |
| B6 | 复原 | `autotest.enabled` 复位默认 off；设备/工作区无残留 |

**验证**：B4 冒烟 PASS 日志/报告留证到 debox 任务目录；B6 复原核验。

### 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| E2 after 上抛让既有"假绿"用例显性失败 | 中 | 目的即防脏状态传染；进行为变化清单，debox B3 核对；框架单测覆盖新语义 |
| C3 去重改变多条件 selector 语义 | 中 | 仅影响「同属性多条件」这一原本必抛异常的路径（现状是 bug，无正确行为可破坏）；补单测钉住 |
| debox 冒烟依赖登录态/设备状态 | 中 | 模拟器已有测试钱包（07-01/02 run 建过）；不可用则按阶梯换设备，不清数据重建 |
| 1.8.1 与 1.7.2 混装缓存 | 低 | mavenLocal 覆盖发布 + debox 侧 `--refresh-dependencies` 核对 resolved 版本 |
| docs 对齐引入新漂移 | 低 | 只改摸底清单列出的漂移点，逐条 grep 复核 |

### 需要用户确认

- 无 prod 部署/破坏性操作/付费服务。debox 侧为 debug 变体 + gated 开关 + 只读冒烟，属常规测试路径，按自主执行推进。
- 本任务不 commit（沿用前序惯例：改动留工作区，由用户决定提交时机）。

## Plan Review Log（per-round，含 VERDICT 快照）

### Plan Review Round 1（2026-07-02，codex exec read-only / medium）

```text
VERDICT: FAIL
```

Critical:
- [Security/Standards/Plan alignment] [92] WS-B 冒烟未把 `app-knowledge/dangerous-ops.md` 先读 + 点击前比对 + 危险词表接入 `DangerousOpsGuard` 写成执行步骤；「无危险操作路径」是结论不是约束 → 增加 B0；B4 只跑定向 DeBoxSmokeTest。

Important:
- [Bugs/Design] [88] C3「按属性去重」可能丢条件扩大命中面，或抛配置错误继续破坏不抛契约 → 主 Leaf 建 BySelector + 完整 conjunction 后置过滤，不满足受控返回 null，不丢条件。
- [Bugs/Regression] [86] E2 直接上抛会在「主流程已失败 + after 也失败」时覆盖原始失败 → 主失败在时 after 异常 addSuppressed，仅无主失败时 after 作为主失败抛出。

Questions: None

Required Changes:
1. B0 危险操作前置步骤（读 app-knowledge / pre-action 比对留痕 / 词表接入 guard）。
2. B4 改定向 DeBoxSmokeTest 命令。
3. C3 语义收敛，禁止去重丢条件。
4. E2 异常保真策略。

### Plan Review Round 2（2026-07-02，codex exec read-only / medium）

```text
VERDICT: FAIL
```

Critical: None

Important:
- [Bugs/Plan alignment] [88] B4 `am instrument` 硬编码 `com.tm.security.wallet.test` 当 instrumentation 包名——debug `applicationIdSuffix ".test"` 下 test APK 实际包名很可能不是该值，且未覆盖 assemble/install → 明确变体/test APK 包名（`pm list instrumentation` 核验）或改 Gradle runner args 定向。
- [Security/Standards/Plan alignment] [84] B0 点击点清单只列 DeBoxSmokeTest 方法体，漏 `DeBoxBaseTest.@Before`/launchAppAndDismissDialogs/权限弹窗/DialogDismissInterceptor 恢复等隐式点击入口 → 扩展到完整执行路径并逐项标注 guard 覆盖。

Questions: None

Required Changes:
1. 修正 B4 执行命令（变体/包名/装包或 Gradle 定向），保留只跑 DeBoxSmokeTest 约束。
2. B0 比对范围扩到完整执行路径。

### Plan Review Round 3（2026-07-02，codex exec read-only / medium，达 3 轮上限）

```text
VERDICT: FAIL
```

Critical:
- [Security/Standards/Plan alignment] [92] B0 指向 `debox-android/docs/testing/app-knowledge/`，但 debox 仓无该目录——危险操作前置无真实输入源，安全闭环断 → 改为实际权威来源（autotest 仓 `docs/testing/app-knowledge/`）并在 B4 前校验 `dangerous-ops.md` 可读，词表注入 `dangerousTexts` 或等价注册路径。

Important: None / Questions: None

Required Changes:
1. 修正 B0 的 app-knowledge 权威路径 + 前置校验 + 词表注入通道。

### Plan Review Round 4（2026-07-02，用户批准的超上限确认轮）

```text
VERDICT: PASS
```

Critical: None / Important: None / Questions: None / Required Changes: None

## Plan Revision Log

> 每次按 review 修订方案时追加：vN · 针对哪条 finding · 改了什么。

- v2（2026-07-02，针对 R1 全部 4 条 Required Changes）：① WS-B 新增 **B0 危险操作前置**（读 app-knowledge、逐点击点 pre-action 比对留痕、危险词表经 test-config 注入 DangerousOpsGuard / 无通道则 DeBoxBaseTest 显式注册、guard fail-closed 核对后才放行 B4）；② B4 收敛为**仅定向** `am instrument -e class ...DeBoxSmokeTest`，删「无危险操作路径」结论式表述；③ C3 修复方向改为「主 Leaf 建 BySelector + 完整 conjunction 后置过滤 + 不满足受控 null」，明确不丢条件；④ E2 补异常保真（addSuppressed 规则）。
- v3（2026-07-02，针对 R2 两条 Important）：① B4 执行方式改为首选 Gradle 定向（runner args 限定 class，Gradle 管 assemble+install）、备选手动装包后 `pm list instrumentation` 实测组件名（不再硬编码 `com.tm.security.wallet.test/...`，预期实为 `...test.test`）；② B0 点击点清单扩展到 **B4 完整执行路径**（@Before/launchAppAndDismissDialogs/权限与弹窗自动处理/interceptor 恢复），逐项标注 DangerousOpsGuard fail-closed 覆盖情况。
- v4（2026-07-02，针对 R3 Critical）：B0 知识库路径修正为 **autotest 仓** `docs/testing/app-knowledge/`（已用 `ls` 验证目录与 `dangerous-ops.md` 存在，debox 仓确无此目录；执行前再校验可读，读不到即停）；词表注入通道落实为 `DangerousOpsGuard` 构造参数 `dangerousTexts`（DangerousOpsGuard.kt:23 已验证）。

## Accepted Plan Summary

- Plan: **v4**（本文件 Plan Proposal 章节现文本 = Accepted Plan 基线）——WS-A1 修 6 项静默缺口（C3 主 Leaf+后置过滤 / E1 logger 注入 / E2 上抛+addSuppressed 保真 / E3 isInitialized 守卫 / E6 四处留痕与显式失败 / Q4 正则构造期校验+缓存）出 v1.8.1；Q2/Q3/Q8 补正式延后记录 → WS-A2 docs 一次性对齐 1.8.1（CLAUDE.md/01/03/04/09/10/README）→ WS-B debox 收尾（B0 危险操作前置[autotest 仓 app-knowledge，词表注入 dangerousTexts] → B1 升 1.8.1 → B2 `.test` 包名对齐 → B3 行为变化同步 → B4 仅定向 DeBoxSmokeTest 冒烟 → B5 悬挂引用 → B6 复原）。
- Accepted at: 2026-07-02（R4 PASS；R1-R3 FAIL 的全部 findings 已在 v2/v3/v4 收敛）
- Decision: PASS（第 4 轮为用户批准的超上限确认轮）
- Accepted risks: 无 PASS_WITH_ACCEPTED_RISK 条目；行为变更（E2 上抛、C3 受控 null）进行为变化清单由 WS-B B3 消化。
