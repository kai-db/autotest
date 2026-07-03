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
└── util/          EspressoExt + UiAutomatorExt + WaitUtil + ScreenshotRule + TestArtifacts
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
5. **修复走 agent-dev-loop** — 分析问题/修复代码**不直接改**，必须走 `agent-dev-loop` skill（Claude 计划/实现/修复/记录 + Codex 只读独立 review 闭环）：建 `docs/implementation/YYYY-MM-DD-动词-对象/` 任务目录 → plan → Codex plan review → 实现 → Codex 实现 review → 回写 results.md。修复原则见 `TEST_GUIDE.md` 第四节，闭环步骤见第六节
6. **先读知识库** — AI 测试 session 先读 `docs/testing/app-knowledge/`（元素表/弹窗/**危险操作清单**），探索产物回写
7. **危险操作 pre-action 比对** — 点击前比对 `app-knowledge/dangerous-ops.md`，命中先停（钱包 App 误操作不可逆）；禁止清数据/登出/切环境
8. **模拟器优先** — 设备按阶梯路由：L1 普通模拟器（默认）→ L2 root 模拟器（注入）→ L3 真机（复核），取最低可行层级；真机不在线不阻塞（详见 `TEST_GUIDE.md` 第七节 + `app-knowledge/devices.md`）

## 测试目标

| App | 包名 | 项目路径 | 测试方式 |
|---|---|---|---|
| DeBox | `com.tm.security.wallet` | `/Users/xiaochengcheng/StudioProjects/debox-android` | Claude Code + MCP（AI 驱动） |

## 测试流程

> 详细流程见 `docs/testing/TEST_GUIDE.md`（第五节：自动化闭环流程；**第六节：修复走 agent-dev-loop**）。
> **监工模式**：测试期间使用 `/loop 5m` 每 5 分钟检查 AI 执行状态，发现卡住时自动恢复。

```
Phase 1: 确认环境（设备在线，无设备则启动模拟器 → App 可启动）
Phase 2: 全量测试（按 P0→P1→P2 遍历用例，每条重置+截图验证）
Phase 3: 更新文档（结果写入 TEST_RESULTS.md，先记录再修复）
Phase 4: 全部修复（**走 agent-dev-loop**：建任务目录 → plan → Codex review → 实现 → Codex review → 回写）
Phase 5: 回归测试（全量重跑）
  └── 仍有 FAIL → 回到 Phase 3
  └── 全部 PASS → Phase 6 最终验收
Phase 6: 全量验收（不改代码，完整跑一遍，全 PASS → 结束）
```

## 文件约定

| 文件 | 用途 |
|---|---|
| `docs/testing/TEST_GUIDE.md` | 测试规范（铁律、流程、修复原则）——稳定不变 |
| `docs/testing/TEST_CASES.md` | 用例模板——稳定不变 |
| `docs/testing/TEST_RESULTS.md` | 结果模板——稳定不变 |
| `docs/testing/runs/日期-功能/` | 每次测试的用例和结果（从模板拷贝） |
