# Android AutoTest Framework

可复用的 Android 自动化测试框架，基于 **Espresso + UiAutomator**，配合 **Claude Code + mobile-mcp** 实现 AI 驱动的测试。

**v1.5.0** | 15 个模块 | 74 条单元测试 | 145.9K AAR

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
androidTestImplementation 'com.autotest:autotest:1.5.0'
```

---

## 框架结构

```
com.autotest
├── base/          测试基类（集成拦截器+日志+生命周期）
├── config/        分层配置 + 多环境管理（LOCAL/CI/STAGING）
├── data/          测试数据管理（TestAccount + JSON + 按环境区分）
├── device/        设备能力封装（网络/权限/屏幕/App/Logcat）
├── action/        通用操作（Tab 切换、引导页跳过）
├── assertion/     通用断言（前台、文本、控件可见）
├── dsl/           DSL（编号/条件/重试/恢复/循环 + BaseScenario 复用）
├── engine/        执行引擎（TestRunner + TestSuite + MonitorMode + TestCaseParser）
├── intercept/     拦截器链（日志/截图/性能/弹窗/Logcat 5种内置）
├── lifecycle/     测试生命周期钩子
├── log/           统一日志（分级 D/I/W/E + Logcat + 文件）
├── report/        报告（JSON + HTML + 摘要统计）
├── runner/        设备信息收集
├── stability/     Flaky 分类 + 自动重试
└── util/          Espresso/UiAutomator 扩展 + 等待工具 + 截图 Rule
```

---

## 配置

### test-config.properties

```properties
app.packageName=com.your.app
app.launchTimeout=15000
app.bottomTabs=消息,朋友,发现,我的
app.screenshotDir=/sdcard/Pictures/autotest
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
// BaseUiTest 默认注册了 LoggingInterceptor + ScreenshotInterceptor
// 自定义拦截器：
interceptors.add(object : Interceptor {
    override fun beforeStep(stepNumber: String, stepName: String) {
        logger.i("Custom", "开始步骤: $stepName")
    }
    override fun onStepFailure(stepNumber: String, stepName: String, error: Throwable) {
        // 失败时自动收集日志
    }
})
```

内置拦截器：

| 拦截器 | 功能 |
|---|---|
| `DialogDismissInterceptor` | 操作前自动关闭权限弹窗/App 弹窗 |
| `LoggingInterceptor` | 自动记录操作和步骤日志 |
| `ScreenshotInterceptor` | 步骤失败自动截图（路径可追溯） |
| `PerformanceInterceptor` | 耗时超阈值警告 |
| `LogcatInterceptor` | 步骤失败自动收集设备 logcat 日志 |

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

当前版本 `1.5.0`。修改 `autotest/build.gradle` 中的 `LIB_VERSION`，重新 `publishToMavenLocal` 发布。
