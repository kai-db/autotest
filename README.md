# Android AutoTest Framework

可复用的 Android 自动化测试框架，基于 **Espresso + UiAutomator**，配合 **Claude Code + mobile-mcp** 实现 AI 驱动的测试。

**v1.8.2** | 19 个模块 | 323 条单元测试 | AAR 发布至 mavenLocal

> v1.7 新增：危险操作点击网关（safety 包，铁律#7 代码层）、StepContext 证据隔离、
> 集成坑 POM 根治（hamcrest/protobuf-lite/sourcesJar）、存储信任边界与自愈链加固。
> 四批架构加固见 `docs/implementation/2026-07-02-*`；机制说明见 `docs/09-AI驱动测试机制.md`。

---

## 快速开始

### 发布到本地 Maven

```bash
git clone git@github.com:kai-db/autotest.git
cd autotest
./gradlew :autotest:publishToMavenLocal
```

### 项目中引用

```groovy
// 根 build.gradle
allprojects { repositories { mavenLocal() } }

// app/build.gradle
androidTestImplementation 'com.autotest:autotest:1.8.2'
```

> 只用 `androidTestImplementation`：框架只进测试 APK，绝不进生产包。
> 建议像 debox 一样用 gradle.properties 开关（如 `autotest.enabled=true` 才加依赖）gate 住，
> 并用 `mavenLocal { content { includeGroup 'com.autotest' } } }` 限定仓库只服务本坐标。

### 接入方 runner 配置（通用）

```groovy
// app/build.gradle → android.defaultConfig
testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
```

- 测试类继承 `com.autotest.base.BaseUiTest`（或 `TestCase`），配置经 androidTest 的
  `assets/test-config.properties` 注入（至少 `app.packageName=<被测包名>`）。
- **包名注意**：`app.packageName` 填**运行时实际 applicationId**——debug 变体带
  `applicationIdSuffix`（如 `.test`）时要写含后缀的值。
- 跨进程测外部 App（黑盒模式）需在 androidTest 的 manifest 声明 `<queries>` 对应包名。
- 定向执行单个测试类：
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`，
  或 `adb shell am instrument -w -e class <FQCN> <testApkAppId>/androidx.test.runner.AndroidJUnitRunner`
  （test APK 的实际组件名用 `adb shell pm list instrumentation` 核验，勿硬编码猜测）。
- espresso/hamcrest/protobuf-lite 冲突已在框架 POM 侧根治（v1.7.x），接入方无需手动 exclude。

---

## 框架结构

```
com.autotest
├── base/          测试基类（集成拦截器+日志+生命周期+危险操作守卫）
├── safety/        危险操作点击网关（DangerousOpsGuard，铁律#7 代码层，fail-closed）
├── config/        分层配置（global<app<env<cli）+ 多环境（applyTo 打通 env 层）
├── data/          测试数据管理（TestAccount + JSON + 按环境区分）
├── device/        设备能力封装（网络/权限/屏幕/App/Logcat）
├── action/        通用操作（Tab 切换、引导页跳过）
├── assertion/     通用断言 + AI 软断言（AiAsserter）
├── selector/      复合选择器 DSL + 三级自愈定位 + 指纹库（provisional/TTL）
├── bridge/        固化桥：用例缓存 + 确定性回放
├── dsl/           DSL（step/retriableStep/flakyStep/恢复/循环 + StepContext）
├── engine/        执行引擎（TestRunner + TestSuite + MonitorMode + TestCaseParser）
├── intercept/     双链拦截器：watcher（日志/截图/性能/内存/Logcat）+ behavior（弹窗恢复）
├── lifecycle/     测试生命周期钩子
├── log/           统一日志（分级 D/I/W/E + Logcat + 文件）
├── diagnosis/     LogcatAnalyzer 失败根因签名（crash/ANR/OOM/native）
├── report/        报告（JSON + HTML + AI断言/自愈/危险操作 独立 section）
├── runner/        设备信息收集
├── stability/     Flaky 分类（可注入）+ 自适应重试 + 统一预算硬顶
└── util/          Espresso/UiAutomator 扩展 + 原子写 + 等待 + 截图 Rule
```

---

## 配置

### test-config.properties

```properties
app.packageName=com.your.app
app.launchTimeout=15000
app.bottomTabs=消息,朋友,发现,我的
# app.screenshotDir 可选：默认落目标 App 私有外部目录（targetContext，可写）；一般不用手动配
app.screenshotOnFailure=true
test.env=LOCAL
```

### 多环境

```kotlin
EnvironmentManager.register(
    EnvironmentConfig(LOCAL, "com.example.debug", launchTimeout = 10000),
    EnvironmentConfig(CI, "com.example", launchTimeout = 20000)
)
val config = EnvironmentManager.current()
```

### 测试数据

```kotlin
// 代码注册
TestDataManager.registerAccount(LOCAL, TestAccount(phone = "138...", password = "123"))

// 或从 JSON 加载
TestDataManager.loadFromJson(assets.open("test-data.json"))

// 使用
val account = TestDataManager.getAccount()
```

---

## 写测试

### 方式一：TestCase 声明式 API（推荐）

```kotlin
class LoginTest : TestCase() {

    @Test
    fun testLogin() = execute(
        before = { launchAppAndDismissDialogs() },
        after = { pressHome() }
    ) {
        step("进入登录页") { device.clickText("登录") }
        step("输入账号") { viewById(R.id.et_phone).typeText("138...") }
        flakyStep("点击登录", maxRetries = 2) {
            viewById(R.id.btn_login).click()
        }
        step("验证成功") {
            flakySafely(timeoutMs = 5000) {
                AppAssertions.assertTextVisible(device, "首页")
            }
        }
    }
}
```

### 方式二：继承 BaseUiTest + scenario DSL

```kotlin
class LoginTest : BaseUiTest() {

    @Test
    fun testLogin() {
        val s = scenario("登录流程", reportCollector, interceptors) {
            step("启动App") { launchAppAndDismissDialogs() }
            step("进入登录页") { device.clickText("登录") }
            step("验证成功") { AppAssertions.assertTextVisible(device, "首页") }
        }
        s.run()
    }
}
```

### DSL 能力

| 方法 | 说明 |
|---|---|
| `step("名称") {}` | 普通步骤（自动编号） |
| `stepIf(condition, "名称") {}` | 条件步骤 |
| `flakyStep("名称", maxRetries) {}` | 可重试步骤 |
| `stepWithRecovery("名称", action, recovery)` | 带异常恢复 |
| `repeat(times, "名称") { i -> }` | 循环步骤 |
| `include(BaseScenario)` | 嵌入可复用场景 |

### 可复用场景（BaseScenario）

```kotlin
class LoginScenario(private val phone: String) : BaseScenario("登录") {
    override fun ScenarioBuilder.steps() {
        step("点击登录") { device.clickText("登录") }
        step("输入手机号") { viewById(R.id.phone).typeText(phone) }
    }
}

// 在任何测试中一行调用
scenario("完整流程") {
    step("启动") { launchApp() }
    include(LoginScenario("138..."))
    step("验证首页") { ... }
}
```

---

## 设备能力（DeviceActions）

```kotlin
val deviceActions = DeviceActions.create()
deviceActions.disableAnimations()        // CI 加速
deviceActions.grantAllPermissions(pkg)   // 免弹窗
deviceActions.disableWifi()              // 网络异常测试
deviceActions.enableWifi()               // 恢复
deviceActions.clearAppData(pkg)          // 重置 App
deviceActions.dumpCrashLog(pkg)          // 崩溃日志
```

---

## 执行引擎

### TestRunner — 单用例

```kotlin
val runner = TestRunner(appPackage = "com.test", logger = logger)
runner.runTest("冷启动", before = { launchApp() }) {
    step("验证首页") { ... }
}
```

### TestSuite — 批量执行

```kotlin
val suite = TestSuite("冒烟测试", runner, logger)
suite.addTest("TC-001", "冷启动", Priority.P0) { step("...") {} }
suite.addTest("TC-002", "Tab导航", Priority.P0) { step("...") {} }
suite.addTest("TC-003", "登录", Priority.P1) { step("...") {} }

val results = suite.runAll()  // 按 P0→P1→P2 排序执行
```

### MonitorMode — 监工模式

```kotlin
val monitor = MonitorMode(suite, logger)
monitor.runUntilAllPass()      // 迭代直到 0 个 FAIL
monitor.runFinalVerification() // 最终验收
monitor.writeResults()         // 生成 TEST_RESULTS.md
```

---

## 拦截器

操作和步骤前后自动执行通用逻辑：

```kotlin
// BaseUiTest 默认注册了 Logging/Screenshot/Logcat/Performance/Memory + DialogDismiss（behavior）
// 自定义拦截器（step 回调收 StepContext，含 caseId/runId/attempt，证据按此隔离）：
interceptors.add(object : Interceptor {
    override fun beforeStep(ctx: StepContext) {
        logger.i("Custom", "开始步骤: ${ctx.stepName}")
    }
    override fun onStepFailure(ctx: StepContext, error: Throwable) {
        // 失败时自动收集日志
    }
})
```

内置拦截器：

| 拦截器 | 链 | 功能 |
|---|---|---|
| `DialogDismissInterceptor` | behavior | 步骤失败后关弹窗恢复（默认不点确认类按钮；权限/App 弹窗） |
| `LoggingInterceptor` | watcher | 自动记录操作和步骤日志 |
| `ScreenshotInterceptor` | watcher | 步骤失败自动截图（证据 key 唯一，跨用例不覆盖） |
| `PerformanceInterceptor` | watcher | 耗时 P50/P95 + 超阈值警告 |
| `LogcatInterceptor` | watcher | 步骤失败自动收集设备 logcat |
| `MemoryInterceptor` | watcher | 每步 PSS + 相对基线泄漏告警 |

---

## 稳定性

### Flaky 分类

```kotlin
FlakyClassifier.classify("Timeout...") // → FLAKY（可重试）
FlakyClassifier.classify("Not found")  // → HARD_FAIL（直接失败）
```

### RetryRunner — 测试方法级重试

```kotlin
@get:Rule
val retryRule = RetryRunner(RetryPolicy(maxRetries = 2, intervalMs = 1000))
```

### flakySafely — 代码块级重试（推荐）

```kotlin
// 对不稳定的操作包一层自动重试
step("验证元素") {
    flakySafely(timeoutMs = 5000) {
        viewByText("首页").isDisplayed()
    }
}

// 支持自定义允许重试的异常类型
flakySafely(
    timeoutMs = 10000,
    allowedExceptions = setOf(AssertionError::class.java)
) {
    device.findObject(By.text("加载完成")) ?: throw AssertionError("未找到")
}
```

---

## 报告

自动生成 JSON 报告，包含摘要统计：

```json
{
  "summary": {
    "totalSteps": 12,
    "passedSteps": 11,
    "failedSteps": 1,
    "passRate": 91.7,
    "totalDurationMs": 15000
  },
  "steps": [...],
  "failures": [...],
  "runnerInfo": { "deviceModel": "SM-S9210", ... }
}
```

---

## AI 驱动测试（Claude Code + mobile-mcp）

框架配合 mobile-mcp 实现 AI 驱动的交互式测试，详见 `docs/testing/TEST_GUIDE.md` 及 `docs/05-AI测试手册.md`。

**测/修分离**：测试阶段只测不修，遇 FAIL 用 agent-dev-loop「分析记录模式」登记后立刻下一条，
跑完全量才统一进修复阶段——这是防止「测着测着切进修复、剩余用例永远没跑」的核心规则。

**监工模式**：测试期间使用 `/loop 5m` 每 5 分钟检查 AI 执行状态，防止卡住自动恢复。

---

## 文档

| 文件 | 内容 |
|---|---|
| `docs/01-架构设计.md` | 系统全景 + 模块职责 |
| `docs/02-技术方案.md` | Claude Code + mobile-mcp 方案 + 铁律 |
| `docs/03-可行性评估.md` | 风险分析 + 能力边界 |
| `docs/04-开发计划.md` | 4 阶段任务计划 |
| `docs/05-AI测试手册.md` | 给 AI 的操作手册 |
| `CHANGELOG.md` | 版本变更记录 |
| `TEST_CASES.md` | 测试用例定义 |

---

## 版本

当前版本 `1.8.2`。修改 `autotest/build.gradle` 中的 `LIB_VERSION`，重新 `publishToMavenLocal` 发布。
