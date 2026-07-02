# Changelog

所有重要变更记录。格式遵循 [Keep a Changelog](https://keepachangelog.com/)。

## [1.8.0] - 2026-07-02

> 主题：质量 / 可测性收尾（审计 P2 + 延后项）。minor 版号——含 public API 演进。

### Added
- `verifyNoProtobufLite` gradle 护栏（1.7.2 引入，本版沿用）；`requireSafeArg`/`requireSafeTag` 提为可测顶层函数
- 补测：device 包 shell 注入防护（RequireSafeArg）、log 包首个单测（DefaultTestLogger 分级/格式/并发）、
  TestRunner FailureKind 归类矩阵、MonitorMode 失败行 [INFRA]/[ASSERTION] 展示（单测 280 → 295）

### Fixed / Hardened
- **DefaultTestLogger 线程安全**：SimpleDateFormat 改 ThreadLocal（匿名子类 override initialValue，兼容 minSdk24；
  非 `withInitial` 那个 API26+）+ 写文件段 synchronized
- **DeviceActions 加固**：`dumpLogcat(tag)` 过 requireSafeTag（防 `-v`/`*:S`/元字符注入）；
  airplane mode API≥28 用 `cmd connectivity`、24–27 回退，执行后读回校验、未生效告警（不再静默 no-op）
- **LogcatInterceptor `logcat -c` 收敛（D3）**：仅在本步需收集日志时才清全局缓冲，不再抹掉步骤前 crash 痕迹
- **TestRunner 失败类型区分**：FailureKind（INFRA=环境/设备没准备好，ASSERTION=用例真断言失败），进 MonitorMode 报告

### Changed（破坏性 / API 演进，接入方注意）
- `DeviceActions.enableAirplaneMode/disableAirplaneMode`：`Unit → Boolean`（返回是否生效）——JVM 描述符变更，
  源码兼容（返回值可忽略）、ABI 破坏（预编译二进制需重编）
- `TestCaseResult` 加 `failureKind` 字段（带默认值）——源码兼容、data class 主构造器 ABI 变更
- **删除 `DeviceSelector.DevicePreference`**（死代码，零引用）——若外部曾引用需移除
- 说明：唯一接入方 debox 是源码级 androidTestImplementation、重编即可，不受上述 ABI 变更影响

### Stats
- 295 条单元测试 / 19 个模块

## [1.7.2] - 2026-07-02

> 主题：debox 1.7.1 接入回归暴露 A2（protobuf-lite exclude）根治不完整，补全 + 加护栏。

### Fixed
- **A2 protobuf-lite exclude 补全**：1.7.1 只在 espresso-core 上 exclude protobuf-lite，但 protobuf-lite
  另有 `espresso-contrib → accessibility-test-framework:3.1.2 → protobuf-lite:3.0.1` 路径（espresso-contrib
  是 implementation、传到接入方 runtime classpath、无 exclude）→ 接入方删手动 workaround 后 protobuf-lite
  重回 androidTest classpath，复现 G3-① 冲突风险。本版对 espresso-contrib + espresso-intents 也加 exclude。

### Added
- **回归护栏 `verifyNoProtobufLite` gradle 任务**：查 debugAndroidTestRuntimeClasspath 依赖图（resolutionResult，
  不解析 artifact 避 variant 歧义），有 protobuf-lite 即 fail——堵住「只断言单个 dep 的 POM exclusion」的漏
  （1.7.1 的 POM 断言只查 espresso-core，漏了别路径）。

### 验证
- debox 1.7.2 接入回归 Tier A 全绿：androidTest classpath protobuf-lite=0、hamcrest 在、主 App APK 无 com.autotest、
  dex 正对照通过。（任务：debox `docs/implementation/2026-07-02-regress-autotest-1.7.1/`）

## [1.7.1] - 2026-07-02

> 主题：一次全框架架构审计后的四批加固（每批走 agent-dev-loop + Codex 双 gate）。
> 审计与四批任务记录见 `docs/implementation/2026-07-02-*`。

### Added
- **safety 包（新增，铁律#7 代码层）**：DangerousOpsGuard 危险操作点击网关——
  框架全部点击入口（自愈定位/UiAutomator/Espresso/缓存回放/弹窗拦截器）统一比对危险词表，
  命中或不可审计目标默认 fail-closed 拦截 + 截图留证 + 报告独立「危险操作拦截」section；
  显式 allowDangerous/allowUnverifiable 放行并留痕
- **StepContext**：证据/统计按 caseId+runId+attempt 隔离（证据唯一 key 不覆盖 + 性能逻辑 key 聚合）
- **配置**：`safety.*`、`app.waitForIdleTimeout`；EnvironmentManager.applyTo 打通 env 层单一读取入口
- **util**：AtomicFileWriter 原子写（temp+rename，fail-closed）
- **测试**：hermetic instrumented SafetyGateSmokeTest / ProductDirSmokeTest / EnvApplyToSmokeTest

### Fixed / Hardened
- **假绿路径**：assertAppInForeground 从恒过的 Kotlin `assert()` 改 JUnit 断言；
  空用例/空缓存回放不再假 PASS；AI 硬断言无评估器不再静默降级；
  flakySafely 末次尝试补白名单（真 bug 不再被洗成「超时 flaky」）
- **执行引擎并发**：监工超时僵尸线程协作取消 + terminal 拒并发；maxRounds 上限 + Phase6 超时；
  三层重试统一预算硬顶；重试体系走 Throwable 级分类、副作用步骤默认不重放（retriable=false）
- **集成坑框架侧根治**：产物目录改 targetContext（不落公共目录）；hamcrest 显式 api、
  espresso-core exclude protobuf-lite（均进 POM）；发布带 sourcesJar
- **存储信任边界**：Gson 反序列化递归 validator（挡 Unsafe 注入的 null/路径逃逸）；
  原子写防进程中断静默清库；指纹库护出自动清理池
- **自愈链正确性**：自愈/AI 命中写 provisional（禁自动转正 + 置信度封顶 + TTL）；
  heal 按 packageName 过滤系统 UI；SelectorSpec.key 长度前缀防碰撞；多命中真消歧 fail-closed

### Changed（破坏性 / 行为收紧，接入方回归注意）
- **危险操作守卫默认拦截**（safety.enabled 默认 true）：命中危险词表或**不可审计目标**（无 text/res-id/desc）的点击**默认 fail-closed 抛异常**——既有点击/缓存回放/弹窗恢复链路若触达这类目标，运行结果会从「点击」变为「拦截失败」；用例明确需要执行时须显式 `allowDangerous=true`（危险词）/`allowUnverifiable=true`（不可审计目标）放行并留痕
- **DialogDismiss 默认不再自动点确认类按钮**（确定/OK/知道了）——依赖旧「弹窗自动 dismiss-retry」的只读步骤需改 `retriableStep` 或显式 confirmTexts
- Interceptor/BehaviorInterceptor step 回调签名 → `StepContext`
- `Step.retriable` 默认 false（behavior 恢复链默认不重放，副作用保护）
- `SelfHealingLocator.find/tryFind` @Deprecated（裸 UiObject2 点击绕守卫，改用 `element/click`）
- `FlakyClassifier.registerRule/clearRules` @Deprecated（改 DefaultFlakyClassifier 实例注入）
- `SelectorSpec.key()` 编码变化（旧指纹/缓存 key 失效，一次性重积累）
- AI 硬断言无评估器时不再静默通过（改 FAIL）；空用例/空缓存回放不再假 PASS

### Stats
- 280 条单元测试 / 19 个模块（含新增 safety 包）/ 8 条 hermetic instrumented 冒烟

## [1.6.0] - 2026-06-12

> 主题：把「AI 探索」和「确定性回归」焊接起来（机制详见 `docs/09-AI驱动测试机制.md`）。
> 借鉴对象：midscene.js / Healenium / Maestro / Marathon / Kaspresso / AppAgent / Mobile-Agent。

### Added
- **selector 包**：SelectorSpec 复合选择器 DSL（与/或/非 + exact/contains/startsWith/regex）；
  HealingEngine 指纹自愈（加权属性相似度，零 LLM 成本）；FingerprintStore 指纹库落盘；
  SelfHealingLocator 三级降级定位（确定性 → 指纹自愈 → AI 兜底接口），非确定性命中显式记账
- **bridge 包（固化桥）**：CachedCase/CachedStep 用例缓存模型；CaseCacheStore
  （READ_WRITE/READ_ONLY/WRITE_ONLY）；CacheReplay + UiReplayExecutor 确定性回放
- **AI 软断言通道**：AiAsserter（默认 optional，FAIL 不挂测试）+ AiAssertionEvaluator 注入接口；
  RunReport.aiAssertions/healingEvents；HtmlReporter 独立「AI 断言」「定位自愈事件」section
- **stability**：TestHistoryStore 执行历史滚动窗口；AdaptiveRetryPolicy 按历史通过率算重试预算；
  FlakyClassifier 自定义规则链 + 内置正则；RetryRunner 支持自适应模式与历史回写
- **intercept**：BehaviorInterceptor 失败恢复链（与 watcher 链分离）；
  DialogDismissInterceptor 兼任 behavior（步骤中途弹窗失败后再扫+重试）
- **diagnosis 包**：LogcatAnalyzer 失败根因签名（crash/ANR/OOM/native），回填 Failure.rootCause
- **engine**：MonitorMode.runRound(timeoutMs) 轮次硬超时（防 AI 监工闭环卡死）
- **dsl**：ScenarioBuilder.forEach 数据驱动步骤
- **intercept**：PerformanceInterceptor P50/P95 百分位 + PerfSummary；ScreenshotInterceptor 缩放/压缩
- **docs**：`docs/09-AI驱动测试机制.md`；`docs/testing/app-knowledge/` 知识库
  （页面元素模板 + **危险操作清单**）；TEST_GUIDE 新增危险操作 pre-action 比对铁律

### Stats
- 199 条单元测试 / 18 个模块

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
