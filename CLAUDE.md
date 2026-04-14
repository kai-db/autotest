# Android AutoTest

可复用的 Android 真机自动化测试框架（Espresso + UiAutomator），发布为 AAR 通过 mavenLocal 供项目依赖。

## 项目结构

```
com.autotest
├── base/          BaseUiTest + BaseActivityTest（测试基类）
├── config/        TestConfig + ConfigLoader（分层配置）
├── action/        AppActions（通用操作：Tab 切换、引导页跳过）
├── assertion/     AppAssertions（通用断言：前台、文本、控件）
├── dsl/           Scenario + Step（DSL 步骤层）
├── report/        RunReport + ReportWriter + ReportCollector（JSON 报告）
├── runner/        RunnerInfo + DeviceSelector（设备信息）
├── stability/     FlakyClassifier + RetryRunner + RetryPolicy（稳定性治理）
└── util/          EspressoExt + UiAutomatorExt + WaitUtil + ScreenshotRule
```

## 构建与发布

```bash
./gradlew :autotest:publishToMavenLocal   # 发布到 ~/.m2
./gradlew :autotest:test                  # 运行单元测试
```

## 使用 mobile-mcp 交互式测试 DeBox

当用户要求测试 DeBox App 时，使用 mobile-mcp 工具连接真机/模拟器。

### DeBox 基本信息

- 包名：`com.tm.security.wallet`
- 启动 Activity：SplashActivity
- 底部 Tab：首页、社区、发现、我的
- 登录方式：手机号验证码、钱包连接

### 测试流程

1. **确认设备连接**：`mobile_list_available_devices` 检查设备
2. **启动 App**：`mobile_launch_app` 包名 `com.tm.security.wallet`
3. **截图观察**：`mobile_take_screenshot` 查看当前页面
4. **列出元素**：`mobile_list_elements_on_screen` 获取可交互元素
5. **执行操作**：`mobile_click_on_screen_at_coordinates` / `mobile_type_keys` / `mobile_swipe_on_screen`
6. **验证结果**：再次截图确认操作是否成功

### 常见测试场景

**冷启动测试**
1. `mobile_launch_app` 启动 DeBox
2. 等待 2-3 秒
3. `mobile_take_screenshot` 确认到达首页
4. 如遇权限弹窗，`mobile_list_elements_on_screen` 找到"允许"按钮并点击

**Tab 导航测试**
1. 启动 App 后截图确认在首页
2. 依次点击底部 Tab（社区、发现、我的）
3. 每次切换后截图验证页面内容

**登录测试**
1. 进入"我的" Tab
2. 点击登录入口
3. 输入手机号 → 获取验证码 → 输入验证码
4. 截图验证登录成功

### 注意事项

- 每步操作后先 `mobile_take_screenshot` 确认状态再继续
- 遇到弹窗/对话框时先 `mobile_list_elements_on_screen` 识别后再操作
- 滑动操作用 `mobile_swipe_on_screen`，注意方向参数
- 操作失败时重新截图分析原因，不要盲目重试
