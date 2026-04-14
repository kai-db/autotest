# Android AutoTest Framework

可复用的 Android 自动化测试框架，基于 **Espresso + UiAutomator**，配合 **Claude Code + mobile-mcp** 实现 AI 驱动的测试。

**v1.3.0** | 15 个模块 | 62 条单元测试

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
androidTestImplementation 'com.autotest:autotest:1.3.0'
```

---

## 框架结构

```
com.autotest
├── base/          测试基类（Espresso + UiAutomator + 拦截器 + 日志）
├── config/        分层配置 + 多环境管理（LOCAL/CI/STAGING）
├── data/          测试数据管理（TestAccount + JSON + 按环境区分）
├── action/        通用操作（Tab 切换、引导页跳过）
├── assertion/     通用断言（前台、文本、控件可见）
├── dsl/           增强 DSL（自动编号/条件/重试/恢复/循环）
├── engine/        执行引擎（TestRunner + TestSuite + MonitorMode）
├── intercept/     拦截器链（日志/截图/性能）
├── lifecycle/     测试生命周期钩子
├── log/           统一日志（分级 D/I/W/E + Logcat + 文件）
├── report/        结构化报告 + 摘要统计
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

## DSL 写测试

```kotlin
class LoginTest : BaseUiTest() {

    @Test
    fun testLogin() {
        val s = scenario("登录流程", reportCollector, interceptors) {
            step("启动App") {
                launchAppAndDismissDialogs()
            }
            step("进入登录页") {
                device.clickText("登录")
            }
            stepIf(needsInput, "输入账号") {
                viewById(R.id.et_phone).typeText("138...")
            }
            flakyStep("点击登录", maxRetries = 2) {
                viewById(R.id.btn_login).click()
            }
            step("验证成功") {
                AppAssertions.assertTextVisible(device, "首页")
            }
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
| `LoggingInterceptor` | 自动记录操作和步骤日志 |
| `ScreenshotInterceptor` | 步骤失败自动截图 |
| `PerformanceInterceptor` | 耗时超阈值警告 |

---

## 稳定性

```kotlin
// Flaky 分类
FlakyClassifier.classify("Timeout...") // → FLAKY（可重试）
FlakyClassifier.classify("Not found")  // → HARD_FAIL（直接失败）

// 自动重试 Rule
@get:Rule
val retryRule = RetryRunner(RetryPolicy(maxRetries = 2, intervalMs = 1000))
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

框架配合 mobile-mcp 实现 AI 驱动的交互式测试，详见 `docs/05-AI测试手册.md`。

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

当前版本 `1.3.0`。修改 `autotest/build.gradle` 中的 `LIB_VERSION`，重新 `publishToMavenLocal` 发布。
