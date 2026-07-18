# Android AutoTest

可复用的 Android 真机自动化测试框架（Espresso + UiAutomator），发布为 AAR 通过 mavenLocal 供项目依赖。

## 项目结构

```
com.autotest
├── base/          BaseUiTest + BaseActivityTest（测试基类，含自愈定位/AI 断言注入点）
├── action/        AppActions（通用操作：Tab 切换、引导页跳过）
├── assertion/     AppAssertions + AiAsserter（AI 软断言，评估器外部注入）
├── bridge/        固化桥：CachedCase 用例缓存 + CaseCacheStore + CacheReplay 回放
├── config/        分层配置（global<app<env<cli）+ Environment（多环境，显式 applyTo 打通 env 层）
├── data/          TestDataManager + TestAccount（测试数据管理）
├── device/        DeviceActions（网络/权限/屏幕/App管理/Logcat）
├── diagnosis/     LogcatAnalyzer（失败根因：crash/ANR/OOM 签名）
├── dsl/           Scenario + Step + BaseScenario（DSL：step/flakyStep/repeat/forEach）
├── engine/        TestRunner + TestSuite + MonitorMode（轮次超时）+ TestCaseParser
├── intercept/     双链拦截器：watcher 观察 + behavior 失败恢复（6 种内置）
├── lifecycle/     TestLifecycleHook + Manager（测试生命周期钩子）
├── log/           TestLogger + DefaultTestLogger（统一日志：分级+文件+Logcat）
├── report/        RunReport + ReportCollector + ReportWriter + HtmlReporter（AI 断言/自愈/守卫/根因独立 section）
├── runner/        RunnerInfo + DeviceSelector（设备信息）
├── safety/        DangerousOpsGuard 危险操作点击网关（铁律#7 代码层：fail-closed 阻断+截图留证+词表配置注入）
├── selector/      复合选择器 DSL + 指纹自愈 + 三级降级定位（SelfHealingLocator）
├── stability/     FlakyClassifierApi + DefaultFlakyClassifier（规则构造注入）+ AdaptiveRetryPolicy + TestHistoryStore
└── util/          EspressoExt + UiAutomatorExt + WaitUtil + MonotonicTime（单调计时源）+ ScreenshotRule + TestArtifacts
```

> AI 驱动相关机制（缓存回放/自愈/软断言/历史重试）的使用方式见 `docs/09-AI驱动测试机制.md`。

## 构建与发布

```bash
./gradlew :autotest:publishToMavenLocal   # 发布到 ~/.m2
./gradlew :autotest:test                  # 运行单元测试
```

## 铁律

1. **AI 驱动** — 全程自主执行，不等人指示每一步
2. **按用例执行** — `docs/testing/TEST_CASES.md` 是唯一用例来源
3. **监工模式** — 使用 `/loop 5m` 定时检查测试执行状态，防止 AI 卡住
4. **最小人工介入** — 人工只允许白名单五项（验证码/真机首次解锁/危险操作确认/外部系统修复/secret 注入，见 `TEST_GUIDE.md` §7.6），其余全部 AI 自主完成
5. **修复走 agent-dev-loop** — 分析问题/修复代码**不直接改**，必须走 `agent-dev-loop` skill（Claude 计划/实现/修复/记录 + Codex 只读独立 review 闭环）：建 `docs/implementation/YYYY-MM-DD-动词-对象/` 任务目录 → plan → Codex plan review → 实现 → Codex 实现 review → 回写 results.md。修复原则见 `TEST_GUIDE.md` 第三节，闭环步骤见第六节
6. **先读知识库** — AI 测试 session 先读 `docs/testing/app-knowledge/`（元素表/弹窗/**危险操作清单**），探索产物回写
7. **危险操作 pre-action 比对** — 点击前比对 `app-knowledge/dangerous-ops.md`，命中先停（钱包 App 误操作不可逆）；禁止清数据/登出/切环境
8. **用例由影响面决定** — 写用例前必须做改动影响面分析（`TEST_GUIDE.md` 第八节）：改动清单 → 反查调用方 → 顺查下游依赖 → 共享资源竞争 → 跨端边界 → 受影响功能清单；**分析判定「行为会变」的才建 case**（`调用可达 ≠ 会受影响`，按可达性铺会爆炸）；命中行为屏障（契约不变/被中间层吸收/依赖正交字段）即停止传播并一行写明依据。用例设计本身也走 `agent-dev-loop`（Codex plan review 重点评「覆盖是否有洞」）
9. **自我进化（单向棘轮）** — 测试中可自主沉淀与优化，但**收紧/增量自主、放松/削减需批准**：新增 case/危险条目/判据变严/知识库/lessons 可自主；删 case、降优先级、判据变松、放宽 flaky、缩覆盖**必须报批**；铁律本身、Phase 结构、§7.6 白名单、危险红线**即使收紧也只能提议不能自改**。所有进化写入 results.md「本轮进化」节留痕（详见 `TEST_GUIDE.md` 第九节）
10. **模拟器优先** — 设备按阶梯路由：L1 普通模拟器（默认）→ L2 root 模拟器（注入）→ L3 真机（复核），取最低可行层级；真机不在线不阻塞（详见 `TEST_GUIDE.md` 第七节 + `app-knowledge/devices.md`）

## 测试目标

| App | 包名 | 项目路径 | 测试方式 |
|---|---|---|---|
| DeBox | `com.tm.security.wallet` | `/Users/xiaochengcheng/StudioProjects/debox-android` | Claude Code + MCP（AI 驱动） |

## 测试流程

> 详细流程见 `docs/testing/TEST_GUIDE.md`（第五节：自动化闭环流程；**第六节：修复走 agent-dev-loop**）。
> **监工模式**：测试期间使用 `/loop 5m` 每 5 分钟检查 AI 执行状态，发现卡住时自动恢复。

```
Phase 0: 未闭合任务扫描（扫 docs/implementation/ 非终态任务，列入 results.md；
         本轮 FAIL 命中已有目录则续跑不新建）
Phase 1: 确认环境（设备在线，无设备则启动模拟器 → App 可启动 → 分配并行设备分片）
Phase 2: 全量测试（**只测不修**，按 P0→P1→P2 遍历用例，每条重置+截图验证）
  └── 遇 FAIL：①抓原始日志线路（必做）②三分类 A产品/B埋点/C判据
      ③agent-dev-loop「分析记录模式」建/续任务目录（≤10min，**不 plan、不改代码**）
      → 立刻下一条。⛔ 严禁测着测着切进修复
Phase 3: 更新文档（结果写入 results.md，先记录再修复；统计行由表格生成不手写）
Phase 4: 全部修复（**走 agent-dev-loop**：续跑任务目录 → plan → Codex review → 实现 → Codex review → 回写）
  └── 修完**全部** FAIL 才进 Phase 5，不修一个跑一次
Phase 5: 分层回归（必跑 P0 + 受影响面 + 历史 FAIL 过的；无关 P2 可标 ⏸️-分层延后）
  └── 仍有 FAIL → 回到 Phase 3
  └── 全部 PASS → Phase 6 最终验收
Phase 6: 全量验收（不改代码，完整跑一遍含延后项；FAIL 先过 flaky 严格判定门）
```

**测/修分离**是防卡死的核心：测试阶段（2/5/6）只测不修，任何 FAIL 都不打断遍历。
单条 >15min 无进展即判 `FAIL-超时` 并推进下一条；完整防卡死铁则见 `TEST_GUIDE.md` 第五节。

**只有 Phase 5 可分层，Phase 2/6 必须全量**——跑不完的正确解法是 AAR 分层（§5.10）+
并行分片（§5.6），不是砍覆盖。**FAIL 先过三分类（§5.3）再进修复**，防假阳性改坏健康代码。

## 文件约定

| 文件 | 用途 |
|---|---|
| `docs/testing/TEST_GUIDE.md` | 测试规范（铁律、流程、修复原则）——稳定不变 |
| `docs/testing/TEST_CASES.md` | 用例模板——稳定不变 |
| `docs/testing/TEST_RESULTS.md` | 结果模板——稳定不变 |
| `docs/testing/runs/日期-功能/` | 每次测试的用例和结果（从模板拷贝） |
