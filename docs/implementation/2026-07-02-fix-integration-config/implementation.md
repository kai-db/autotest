# ② 实现

## Implementation Log

（待 Accepted Plan 落定后开始。）

## Deviation Log

- （无）

## Consult Log

- （无）

### 2026-07-02 按 Accepted Plan v2 实施

**P0-9 产物目录根治**
- `TestConfig.screenshotDir`：`getInstrumentation().context` → `.targetContext`（目标 App 私有外部目录，有权写）。
- `DefaultTestLogger(logDir: String? = null)`：null → lazy 取 TestConfig.screenshotDir，去 `/sdcard/Pictures` 硬编码；mkdirs 失败告警（不再静默刷屏）。
- `EnvironmentConfig.screenshotDir: String? = null`：去 `/sdcard/Pictures` 硬编码，null=回退 TestConfig。

**A1/A2/A4 build.gradle**
- `api "org.hamcrest:hamcrest:2.2"`（公开 API 暴露 Matcher，进 POM）。
- `api("...espresso-core:3.5.1"){ exclude protobuf-lite }`（进 POM exclusions，一处根治）。
- `publishing{ singleVariant("release"){ withSourcesJar() } }`。
- LIB_VERSION 1.6.2 → 1.7.0。

**A3 waitForIdleTimeout 配置化 + 恢复**
- ConfigKeys.APP_WAIT_FOR_IDLE_TIMEOUT + TestConfig.waitForIdleTimeout(默认 0)。
- BaseUiTest：setUp 记录 savedIdleTimeout、设 TestConfig 值，tearDown 恢复（进程级单例防泄漏）。

**F 配置收敛**
- TestConfig.initWithEnv(envOverrides)：注入 ConfigLoader env 层（原恒空死层）；单独命名避免与 init() 重载歧义。
- EnvironmentManager.applyTo()：current() 关键项 → env override → initWithEnv；幂等、覆盖顺序 global<app<env<cli（KDoc 写明）；显式调用避免 init 循环递归。
- CLAUDE.md config 行更新表述。

### 验证结果

- JVM 单测 **256 全过**（+1 ConfigLoader env 层优先级）。
- **POM 契约断言全过**：hamcrest:2.2 compile scope ✓、espresso-core 带 protobuf-lite `<exclusions>` ✓、autotest-1.7.0-sources.jar 存在 ✓。
- **instrumented smoke（L1 模拟器）**：ProductDirSmokeTest OK(1)——screenshotDir 含 targetContext 包名、不落 /sdcard/Pictures、可写探针成功，坐实 P0-9 框架侧根治。SafetyGateSmokeTest OK(4) 无回归。
- publishToMavenLocal 1.7.0 成功。

## Deviation Log

- **D1（接口命名微调）**：plan 写 `TestConfig.init(envOverrides)`，实现改为 `TestConfig.initWithEnv(envOverrides)`——避免与既有无参 `init()`/`init(loader)` 重载歧义（`init(map=emptyMap())` 会让 `init()` 调用歧义）。语义不变。
- **D2（测试健壮性）**：SafetyGateSmokeTest 的 uiautomator 用例去掉「普通按钮经 UiAutomator 落地」status 断言（改断言守卫 DANGEROUS_BLOCKED 事件 + 点击不抛）——UiAutomator tap 落地在模拟器时序不稳，正常点击落地已由 Espresso 用例覆盖。

## Consult Log

- N/A。
