# Android AutoTest

可复用的 Android 真机自动化测试框架（Espresso + UiAutomator），发布为 AAR 通过 mavenLocal 供项目依赖。

## 项目结构

```
com.autotest
├── base/          BaseUiTest + BaseActivityTest（测试基类）
├── config/        TestConfig + ConfigLoader（分层配置）
├── action/        AppActions（通用操作：Tab 切换、引导页跳过）
├── assertion/     AppAssertions（通用断言：前台、文本、控件）
├── dsl/           Scenario + Step（DSL：编号/条件/重试/恢复/循环）
├── intercept/     Interceptor + InterceptorChain（拦截器：日志/截图/性能）
├── lifecycle/     TestLifecycleHook + Manager（测试生命周期钩子）
├── log/           TestLogger + DefaultTestLogger（统一日志：分级+文件+Logcat）
├── report/        RunReport + ReportWriter + ReportSummary（报告+摘要统计）
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
2. **按用例执行** — `TEST_CASES.md` 是唯一用例来源
3. **监工模式** — 迭代修复循环，直到 0 个 FAIL
4. **最小人工介入** — 尽量减少人为操控，除验证码等必须环节外全部 AI 自主完成
5. **修复必须合规** — 符合项目规范、不引入新 Bug、先理解根因再修复

详细流程见 `docs/05-AI测试手册.md`（第 5 节：监工模式）。

## 使用 mobile-mcp 测试

当用户要求测试 App 时，使用 mobile-mcp 工具连接真机/模拟器。

### App 信息

| App | 包名 | 底部 Tab |
|---|---|---|
| DeBox | `com.tm.security.wallet` | 消息、朋友、发现、我的 |
| 门禁终端 | `io.xcc.access` | - |

### 测试前检查

```
1. mobile_list_available_devices  → 确认设备在线
2. mobile_take_screenshot         → 查看当前状态
3. mobile_launch_app              → 启动目标 App
```

### 操作铁律

- **关键节点截图**（用例开始、页面跳转后、验证点、失败时）
- **操作前先 list_elements**找坐标，不要盲猜
- **遇到弹窗先处理弹窗**
- **每条用例重置环境**（terminate → launch）
- **回归必须全量跑**，不能只跑失败的
- **验证必须有截图证据**

### 监工模式流程

```
Phase 1: 编译部署（gradlew assembleDebug → install → 启动确认）
Phase 2: 全量测试（按 P0→P1→P2 遍历 TEST_CASES.md，每条重置+验证）
Phase 3: 更新文档（结果写入 TEST_RESULTS.md）
Phase 4: 全部修复（分析根因 → 修复代码 → 记录修复）
Phase 5: 回归测试（重新编译 → 全量重跑）
  └── 仍有 FAIL → 回到 Phase 3
  └── 全部 PASS → Phase 6 最终验收
Phase 6: 全量验收（不改代码，完整跑一遍，全 PASS → 结束）
```

### 文件约定

| 文件 | 用途 |
|---|---|
| `TEST_CASES.md` | 用例定义（步骤+验证标准+优先级） |
| `TEST_RESULTS.md` | 每轮结果（PASS/FAIL+截图+修复记录） |
| `screenshots/` | 测试截图 |
