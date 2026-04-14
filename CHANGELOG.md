# Changelog

所有重要变更记录。格式遵循 [Keep a Changelog](https://keepachangelog.com/)。

## [1.5.1] - 2026-04-14

### Fixed（代码审查修复 5 项）
- **clickText/clickResId 静默失败**（P0）：找不到元素时抛 AssertionError 而非静默跳过。新增 tryClickText 用于可选操作
- **FlakyClassifier 关键词过窄**（P0）：新增 Espresso/UiAutomator 常见超时关键词（NoMatchingView/PerformException/StaleObject/AppNotIdle 等），RetryRunner 不再形同虚设
- **TestRunner.generateReport startTime 错误**（P0）：用真实记录的开始时间替代反推计算
- **RetryPolicy/WaitPolicy 无参数校验**（P1）：负数参数直接 require 拒绝，给出明确错误提示
- **DeviceActions.dumpCrashLog 管道失效**（P1）：executeShellCommand 不支持管道，改用 sh -c 包裹

### Stats
- 85 条单元测试 / 19 个测试文件

## [1.5.0] - 2026-04-14

### Added
- **TestCase** (`base/TestCase.kt`)：声明式测试基类（参考 Kaspresso TestCase）
  - `execute(before, after) { step {} }` 四段式 API
- **flakySafely** (`stability/FlakySafely.kt`)：代码块级别自动重试（参考 Kaspresso flakySafely）
  - 支持超时、间隔、允许异常类型、自定义失败消息
  - 与 RetryRunner 区别：RetryRunner 重试整个方法，flakySafely 只重试指定代码块

### Changed
- **TestConfig.packageName**：为空时抛 IllegalArgumentException 并给出明确提示

### Stats
- 79 条单元测试 / 19 个测试文件

## [1.4.0] - 2026-04-14

### Added
- **BaseScenario** (`dsl/BaseScenario.kt`)：可复用测试场景，跨测试 `include()` 一行调用（参考 Kaspresso BaseScenario）
- **DeviceActions** (`device/DeviceActions.kt`)：设备能力封装（参考 Kaspresso Device API）
  - 网络控制：enableWifi/disableWifi/airplaneMode
  - App 管理：clearAppData/forceStopApp/isAppInstalled
  - 权限管理：grantPermission/revokePermission/grantAllPermissions
  - 屏幕控制：wakeup/sleep/rotation/disableAnimations
  - Logcat：clearLogcat/dumpLogcat/dumpCrashLog
- **LogcatInterceptor** (`intercept/LogcatInterceptor.kt`)：步骤失败时自动收集设备 logcat 日志

### Changed（铁律对齐）
- **铁律2**：TestSuite.loadFromMarkdown() — 从 TEST_CASES.md 直接加载可执行用例
- **铁律3**：MonitorMode Phase 职责明确，新增 recordFix() 和 getLastFailures()
- **铁律6**：TestRunner 新增 envReset 参数，每条用例前强制重置环境
- **铁律7**：删除 TestSuite.runFailed()，只允许全量执行
- **铁律8**：ScreenshotInterceptor 记录截图路径，getAllScreenshots() 可查

## [1.3.0] - 2026-04-14

### Added
- **TestCaseParser** (`engine/TestCaseParser.kt`)：从 TEST_CASES.md 自动解析用例（编号/名称/优先级/步骤/验证标准）
- **HTML 报告生成器** (`report/HtmlReporter.kt`)：可视化 HTML 报告（摘要卡片+步骤表格+失败详情+XSS 防护）
- **DialogDismissInterceptor** (`intercept/DialogDismissInterceptor.kt`)：自动弹窗处理拦截器
  - 系统权限弹窗（resource-id 精确匹配）
  - App 弹窗（"稍后再说"/"跳过"/"知道了"等文本匹配）
  - 参考 Kaspresso SystemDialogSafetyBehaviorInterceptor
- 新增 8 条单元测试（总计 70 条，17 个测试文件）

## [1.2.0] - 2026-04-14

### Added
- **环境配置管理** (`config/Environment.kt`)：TestEnvironment 枚举(LOCAL/CI/STAGING) + EnvironmentConfig + EnvironmentManager
- **测试数据管理** (`data/TestData.kt`)：TestAccount + TestDataManager（代码注册 / JSON 文件加载 / 按环境区分）
- **监工模式引擎** (`engine/MonitorMode.kt`)：铁律代码化，自动循环全量测试→记录→判定，直到 0 个 FAIL
  - runRound()：执行一轮全量测试
  - runUntilAllPass()：迭代修复阶段
  - runFinalVerification()：最终验收（Phase 6）
  - writeResults()：自动生成 TEST_RESULTS.md
- 新增 20 条单元测试（总计 62 条）

## [1.1.0] - 2026-04-14

### Added
- **拦截器机制** (`intercept/`)：Interceptor 接口 + InterceptorChain 链式管理
  - LoggingInterceptor：操作和步骤自动日志
  - ScreenshotInterceptor：步骤失败自动截图
  - PerformanceInterceptor：耗时超阈值警告
- **统一日志** (`log/`)：TestLogger 接口 + DefaultTestLogger（分级 D/I/W/E + Logcat + 文件输出）
- **生命周期钩子** (`lifecycle/`)：TestLifecycleHook 接口 + TestLifecycleManager
  - beforeTest / afterTestSuccess / afterTestFailure / afterTestFinally
- **测试执行引擎** (`engine/`)：
  - TestRunner：完整生命周期编排（before→run→after + 拦截器 + 报告）
  - TestSuite：批量执行器（按 P0→P1→P2 优先级排序）
  - Markdown 报告生成
- **DSL 增强**：步骤自动编号、stepIf（条件）、flakyStep（重试）、stepWithRecovery（异常恢复）、repeat（循环）
- **报告摘要** (`ReportSummary`)：自动统计总步骤/通过/失败/通过率/总耗时
- **StepResult** 新增 stepNumber + screenshotPath 字段
- 新增 17 条单元测试（总计 41 条）

### Changed
- BaseUiTest：集成拦截器链 + 日志系统 + 生命周期管理器
- AppActions：Thread.sleep 改为 device.waitForIdle，等待时间可配置
- AppActions.waitForSplashDismiss：新增"稍后再说"跳过文本
- WaitUtil.waitUntil：添加死锁风险说明文档

### Removed
- 删除 `ai/` 模块（AIPlugin、AIResult、AIPatchApplier、AIHooks）
- 删除 `risk/` 模块（RiskLevel、RiskEvaluator、ApprovalGate）
- 删除 ReportCollectorHolder 全局单例

### Fixed
- ReportCollector 全局单例状态污染 → 每次测试新建实例
- DeviceSelector.getSerial() 在 API 26+ 的 SecurityException 崩溃
- ScreenshotRule 未接入 BaseUiTest 基类（失败截图失效）
- 报告文件名无时间戳导致覆盖
- build.gradle 所有依赖用 api 暴露给消费方 → 拆分 api/implementation
- 移除库模块的 targetSdk 声明

## [1.0.0] - 2026-04-14

### Added
- 项目初始化（Android Library + mavenLocal 发布）
- BaseUiTest / BaseActivityTest 测试基类
- Espresso 扩展（viewById/viewByText/click/typeText/replaceText/isDisplayed/hasText/scrollTo）
- UiAutomator 扩展（权限弹窗、滑动、等待、点击）
- WaitUtil（Espresso 线程等待 + 轮询等待）
- ScreenshotRule（失败自动截图）
- AppActions（Tab 切换、引导页跳过）
- AppAssertions（前台验证、文本/控件可见断言）
- TestConfig 分层配置（properties + 命令行参数 + ConfigLoader + ConfigKeys）
- 结构化 JSON 报告（RunReport + ReportWriter + ReportCollector）
- DSL 步骤层（Scenario + Step + ScenarioBuilder）
- RunnerInfo 设备信息收集 + DeviceSelector
- FlakyClassifier（timeout → FLAKY，其他 → HARD_FAIL）
- RetryRunner JUnit Rule（FLAKY 自动重试）
- RetryPolicy + WaitPolicy 数据模型
- 6 条单元测试
