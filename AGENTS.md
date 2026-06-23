# Android AutoTest

可复用的 Android 真机自动化测试框架（Espresso + UiAutomator），发布为 AAR 通过 mavenLocal 供项目依赖。

## 项目结构

```
com.autotest
├── base/          BaseUiTest + BaseActivityTest（测试基类，含自愈定位/AI 断言注入点）
├── action/        AppActions（通用操作：Tab 切换、引导页跳过）
├── assertion/     AppAssertions + AiAsserter（AI 软断言，评估器外部注入）
├── bridge/        固化桥：CachedCase 用例缓存 + CaseCacheStore + CacheReplay 回放
├── config/        分层配置 + Environment（多环境管理）
├── data/          TestDataManager + TestAccount（测试数据管理）
├── device/        DeviceActions（网络/权限/屏幕/App管理/Logcat）
├── diagnosis/     LogcatAnalyzer（失败根因：crash/ANR/OOM 签名）
├── dsl/           Scenario + Step + BaseScenario（DSL：step/flakyStep/repeat/forEach）
├── engine/        TestRunner + TestSuite + MonitorMode（轮次超时）+ TestCaseParser
├── intercept/     双链拦截器：watcher 观察 + behavior 失败恢复（6 种内置）
├── lifecycle/     TestLifecycleHook + Manager（测试生命周期钩子）
├── log/           TestLogger + DefaultTestLogger（统一日志：分级+文件+Logcat）
├── report/        RunReport + HtmlReporter（AI 断言/自愈事件/根因独立 section）
├── runner/        RunnerInfo + DeviceSelector（设备信息）
├── selector/      复合选择器 DSL + 指纹自愈 + 三级降级定位（SelfHealingLocator）
├── stability/     FlakyClassifier 规则链 + AdaptiveRetryPolicy + TestHistoryStore
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
4. **最小人工介入** — 尽量减少人为操控，除验证码等必须环节外全部 AI 自主完成
5. **修复必须合规** — 符合修复原则（见 `docs/testing/TEST_GUIDE.md` 第四节）
6. **先读知识库** — AI 测试 session 先读 `docs/testing/app-knowledge/`（元素表/弹窗/**危险操作清单**），探索产物回写
7. **危险操作 pre-action 比对** — 点击前比对 `app-knowledge/dangerous-ops.md`，命中先停（钱包 App 误操作不可逆）；禁止清数据/登出/切环境

## 测试目标

| App | 包名 | 项目路径 | 测试方式 |
|---|---|---|---|
| DeBox | `com.tm.security.wallet` | `/Users/xiaochengcheng/StudioProjects/debox-android` | Codex + MCP（AI 驱动） |

## 测试流程

> 详细流程见 `docs/testing/TEST_GUIDE.md`（第五节：自动化闭环流程）。
> **监工模式**：测试期间使用 `/loop 5m` 每 5 分钟检查 AI 执行状态，发现卡住时自动恢复。

```
Phase 1: 确认环境（设备在线 → App 可启动）
Phase 2: 全量测试（按 P0→P1→P2 遍历用例，每条重置+截图验证）
Phase 3: 更新文档（结果写入 TEST_RESULTS.md，先记录再修复）
Phase 4: 全部修复（分析根因 → 修复代码 → 记录方案）
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
