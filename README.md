# Android AutoTest Framework

可复用的 Android 真机自动化测试框架，基于 **Espresso + UiAutomator**，支持 `mavenLocal` 发布，任意项目一行依赖接入。

---

## 接入方式

### 1. 发布到本地 Maven

```bash
git clone git@github.com:kai-db/autotest.git
cd autotest
./gradlew :autotest:publishToMavenLocal
```

发布后 aar 位于 `~/.m2/repository/com/autotest/autotest/1.0.0/`

### 2. 项目中引用

**根 build.gradle** 的 `allprojects.repositories` 加：

```groovy
mavenLocal()
```

**app/build.gradle** 加依赖（一行搞定，所有测试库自动传递）：

```groovy
androidTestImplementation 'com.autotest:autotest:1.0.0'
```

**app/build.gradle** 确认 testInstrumentationRunner：

```groovy
defaultConfig {
    testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
}
```

### 3. 创建配置文件

新建 `app/src/androidTest/assets/test-config.properties`：

```properties
# 被测 App 包名（必填）
app.packageName=com.your.app

# App 启动超时（毫秒）
app.launchTimeout=15000

# 最大可接受启动时间（性能测试用）
app.maxLaunchTime=10000

# 底部 Tab 文本（逗号分隔）
app.bottomTabs=首页,消息,我的

# 截图保存目录
app.screenshotDir=/sdcard/Pictures/autotest

# 测试失败时自动截图
app.screenshotOnFailure=true
```

也支持自定义 key，在测试里用 `TestConfig.getString("your.key")` 读取。

---

## 运行测试

```bash
# 全部测试
./gradlew :app:connectedAndroidTest

# 指定测试类
./gradlew :app:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.xxx.test.cases.AppLaunchTest

# 命令行临时覆盖配置（优先级最高）
./gradlew :app:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.app.packageName=com.other.app
```

---

## 框架结构

```
com.autotest
├── config/TestConfig          配置中心（properties + 命令行参数）
├── base/BaseUiTest            测试基类（设备操作 + App 启动）
├── base/BaseActivityTest      ActivityScenario 基类
├── action/AppActions          通用操作（Tab 切换、引导页跳过）
├── assertion/AppAssertions    通用断言（前台验证、文本/控件可见）
└── util/
    ├── EspressoExt            Espresso 扩展（viewById().click() 等）
    ├── UiAutomatorExt         UiAutomator 扩展（权限弹窗、滑动、等待）
    ├── WaitUtil               等待工具（轮询、延迟）
    └── ScreenshotRule         失败自动截图 JUnit Rule
```

---

## 写测试用例

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class XxxTest : BaseUiTest() {

    @get:Rule
    val screenshotRule = ScreenshotRule()

    @Before
    override fun setUp() {
        super.setUp()
        launchAppAndDismissDialogs()
    }

    @Test
    fun testSomething() {
        // UiAutomator：跨 App / 系统操作
        device.clickText("某个按钮")
        device.waitForText("预期文本", 5000)

        // Espresso：App 内 View 操作
        viewById(R.id.edit_text).typeText("hello")
        viewById(R.id.button).click()
        viewByText("预期结果").isDisplayed()

        // 断言
        AppAssertions.assertTextVisible(device, "成功")
        takeScreenshot("test_done")
    }
}
```

---

## API 速查

### BaseUiTest（基类）

| 方法 | 说明 |
|---|---|
| `launchApp()` | 通过包名启动 App |
| `launchAppAndDismissDialogs()` | 启动 + 处理权限弹窗 |
| `launchActivity(cls)` | 启动指定 Activity |
| `pressBack()` / `pressHome()` | 按键 |
| `sleep(millis)` | 等待 |
| `takeScreenshot(name)` | 截图 |
| `assertAppInForeground()` | 断言 App 在前台 |

### UiAutomator 扩展（device.xxx）

| 方法 | 说明 |
|---|---|
| `device.clickText("文本")` | 点击含指定文本的元素 |
| `device.clickResId("id")` | 点击指定 resource-id |
| `device.waitForText("文本", 5000)` | 等待文本出现 |
| `device.waitForResId("id", 5000)` | 等待控件出现 |
| `device.waitForApp("包名", 10000)` | 等待 App 前台 |
| `device.allowPermission()` | 权限弹窗 - 允许 |
| `device.denyPermission()` | 权限弹窗 - 拒绝 |
| `device.scrollUp()` / `scrollDown()` | 滑动 |

### Espresso 扩展

| 方法 | 说明 |
|---|---|
| `viewById(R.id.xxx)` | 通过 id 查找 |
| `viewByText("文本")` | 通过文本查找 |
| `.click()` | 点击 |
| `.typeText("内容")` | 输入文本 |
| `.replaceText("内容")` | 替换文本 |
| `.isDisplayed()` | 断言可见 |
| `.hasText("内容")` | 断言文本 |
| `.scrollTo()` | 滚动到可见 |

### AppActions

| 方法 | 说明 |
|---|---|
| `AppActions.switchBottomTabs(device)` | 遍历底部 Tab |
| `AppActions.waitForSplashDismiss(device)` | 跳过引导页 |

### AppAssertions

| 方法 | 说明 |
|---|---|
| `assertInForeground(device)` | App 在前台 |
| `assertTextVisible(device, "文本")` | 文本可见 |
| `assertResIdVisible(device, "id")` | 控件可见 |

### TestConfig

| 方法 | 说明 |
|---|---|
| `TestConfig.packageName` | 被测包名 |
| `TestConfig.bottomTabs` | Tab 列表 |
| `TestConfig.getString("key", "default")` | 读字符串 |
| `TestConfig.getLong / getInt / getBoolean / getList` | 读其他类型 |

---

## 配置优先级

```
命令行参数（-e key value） > test-config.properties > 代码默认值
```

---

## Espresso vs UiAutomator

| 场景 | 选择 |
|---|---|
| App 内 View 交互 | Espresso |
| 系统权限弹窗 | UiAutomator |
| 通知栏 / 跳转外部 App | UiAutomator |
| 启动性能测试 | UiAutomator |

两者可在同一个测试方法中混合使用。

---

## 测试报告

```bash
# HTML 报告
app/build/reports/androidTests/connected/index.html

# 拉取失败截图
adb pull /sdcard/Pictures/autotest/ ./test-screenshots/
```

---

## 版本升级

修改 `autotest/build.gradle` 中的 `LIB_VERSION`，重新 `publishToMavenLocal`，使用方改版本号即可。
