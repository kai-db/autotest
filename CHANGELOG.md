# Changelog

所有重要变更记录。格式遵循 [Keep a Changelog](https://keepachangelog.com/)。

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
