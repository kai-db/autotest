# Android AutoTest

可复用的 Android 真机自动化测试框架（Espresso + UiAutomator），发布为 AAR 通过 mavenLocal 供项目依赖。

## 项目结构

```
com.autotest
├── base/          BaseUiTest + BaseActivityTest（测试基类）
├── action/        AppActions（通用操作：Tab 切换、引导页跳过）
├── assertion/     AppAssertions（通用断言：前台、文本、控件）
├── config/        分层配置 + Environment（多环境管理）
├── data/          TestDataManager + TestAccount（测试数据管理）
├── device/        DeviceActions（网络/权限/屏幕/App管理/Logcat）
├── dsl/           Scenario + Step + BaseScenario（DSL + 可复用场景）
├── engine/        TestRunner + TestSuite + MonitorMode + TestCaseParser
├── intercept/     InterceptorChain + 5种拦截器（弹窗/日志/截图/性能/Logcat）
├── lifecycle/     TestLifecycleHook + Manager（测试生命周期钩子）
├── log/           TestLogger + DefaultTestLogger（统一日志：分级+文件+Logcat）
├── report/        RunReport + ReportWriter + HtmlReporter + ReportSummary
├── runner/        RunnerInfo + DeviceSelector（设备信息）
├── stability/     FlakyClassifier + RetryRunner + RetryPolicy（稳定性治理）
└── util/          EspressoExt + UiAutomatorExt + WaitUtil + ScreenshotRule
```

## 构建与发布

```bash
./gradlew :autotest:publishToMavenLocal   # 发布到 ~/.m2
./gradlew :autotest:test                  # 运行单元测试
```

## 铁律

1. **AI 驱动** — 全程自主执行，不等人指示每一步
2. **按用例执行** — `docs/testing/TEST_CASES.md` 是唯一用例来源
3. **监工模式** — 迭代修复循环，直到 0 个 FAIL
4. **最小人工介入** — 尽量减少人为操控，除验证码等必须环节外全部 AI 自主完成
5. **修复必须合规** — 符合修复原则（见 `docs/testing/TEST_GUIDE.md` 第四节）

## 测试目标

| App | 包名 | 项目路径 | 测试方式 |
|---|---|---|---|
| DeBox | `com.tm.security.wallet` | `/Users/xiaochengcheng/StudioProjects/debox-android` | Claude Code + MCP（AI 驱动） |

## 测试流程

详细流程见 `docs/testing/TEST_GUIDE.md`（第六节：自动化闭环流程）。

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
