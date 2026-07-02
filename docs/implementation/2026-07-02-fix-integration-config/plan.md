# ① 计划 / 方案

## Plan Proposal

### 任务目标

实施审计提案第三批：**集成坑框架侧根治 + 产物目录正确性 + 配置体系收敛**——
P0-9（产物目录用 targetContext + 消除 `/sdcard/Pictures` 硬编码默认）、
主题 A（A1 hamcrest 显式 api、A2 protobuf-lite exclude 进 POM、A3 waitForIdleTimeout 配置化、A4 sourcesJar 发布）、
主题 F（ConfigLoader app/env 死层 + EnvironmentManager 平行配置收敛）。发 1.7.0。

### 当前项目上下文（已精读源码）

- `TestConfig.screenshotDir`（TestConfig.kt:128-129）：默认用 `getInstrumentation().context`（**test 包 context**）的 `getExternalFilesDir`——接入真实 App 时 test 包 ≠ target 包，scoped storage 拒写；全框架产物走此一个入口（G3-③ 教训未在框架根治）。
- `DefaultTestLogger`（log/TestLogger.kt:20）默认 `logDir="/sdcard/Pictures/autotest"`、`EnvironmentConfig.screenshotDir`（config/Environment.kt:22）默认同——scoped storage 必死，且与 TestConfig.kt:122 自己的注释矛盾。
- `build.gradle`：hamcrest 无声明（靠 espresso-core 间接传递，公开 API EspressoExt/WaitUtil 暴露 `org.hamcrest.Matcher` 却不自己 api 声明，接入方 exclude espresso 即 NoClassDefFound）；无任何 protobuf-lite exclude（每个接入方自己排）；无 sourcesJar（接入方 IDE 只有反编译字节码，中文 KDoc 全丢）。
- `waitForIdleTimeout`（base/BaseUiTest.kt:110）硬编码 `= 0L`，非配置项、绕过 BaseUiTest 的接入方拿不到、全局单例从不恢复。
- `ConfigLoader(global, app, env, cli)`：`defaultLoader` 里 app/env 恒 `emptyMap()`（死层）；`EnvironmentManager` 是与 TestConfig 平行的第二套配置，`current()` 读 TestConfig 但其 EnvironmentConfig.launchTimeout/screenshotDir 不反哺 TestConfig 读取路径（改了环境配置，ScreenshotRule 等仍读 TestConfig）。

### 约束

- 不改被测 debox；不动第一/二批已交付；不做第四批（存储/自愈链）。
- 全量单测须过、`publishToMavenLocal` 须成功；LIB_VERSION 1.6.2 → **1.7.0**（次版本号：含集成契约变更 + 破坏面小）。
- POM 依赖/exclusions 变更需 **dex 级 + 正对照** 验证不进主包（G3 铁证方法）——但 debox 侧接入验证作为 follow-up（不阻塞本批出条件，本批以框架 self 验证为准）。

### 非目标

- 不在本批做 debox 的 1.7.0 接入回归（另约时间，需 debox 工作区 + 真机）；本批交付框架侧根治 + 框架自测。

### 建议方案（P0-9 + A + F）

**P0-9 产物目录根治**
- `TestConfig.screenshotDir`：`getInstrumentation().context` → **`.targetContext`**（产物落目标 App 自己的外部目录 `/sdcard/Android/data/<目标包>/files/autotest`，目标进程有权写）。
- `DefaultTestLogger` 默认 `logDir` 与 `EnvironmentConfig.screenshotDir` 的 `/sdcard/Pictures/autotest` 硬编码默认**删除**，改为「不传就走 `TestConfig.screenshotDir` 单一来源」：
  - `DefaultTestLogger(logDir: String? = null)`，null 时 lazy 取 `TestConfig.screenshotDir`（延迟到用时，避免类加载即触发 TestConfig）。
  - `EnvironmentConfig.screenshotDir: String? = null`，`defaultConfig` 已传 TestConfig.screenshotDir；null 语义 = 未显式指定，消费方回退 TestConfig.screenshotDir。
- `dir.mkdirs()` 返回值检查：`DefaultTestLogger.logFile` 初始化时若 mkdirs 失败告警（Android 10+ 公共目录曾静默失败刷屏）。

**A1 hamcrest 显式 api**
- `api "org.hamcrest:hamcrest:2.2"`（框架公开 API 暴露 `org.hamcrest.Matcher`，按 Gradle 语义必须自己 api 声明；G3 用 2.2 实证）。进 POM，接入方即便 exclude espresso 也不缺类。

**A2 protobuf-lite exclude 进 POM**
- `api("androidx.test.espresso:espresso-core:3.5.1") { exclude group: 'com.google.protobuf', module: 'protobuf-lite' }`——写进 POM exclusions，一处根治所有接入方（宿主用 protobuf-javalite 的项目不再自己排，G3-①）。

**A3 waitForIdleTimeout 配置化 + 原值恢复【R1 修订】**
- `ConfigKeys.APP_WAIT_FOR_IDLE_TIMEOUT="app.waitForIdleTimeout"`；`TestConfig.waitForIdleTimeout: Long`（默认 **0**，兼容当前硬编码）。
- `BaseUiTest`：setUp 前**记录原始值** `savedIdleTimeout = Configurator.getInstance().waitForIdleTimeout`，setUp 设 `= TestConfig.waitForIdleTimeout`，**tearDown 恢复** `= savedIdleTimeout`——解决「全局单例从不恢复、跨测试类泄漏」既有风险（Configurator 是进程级单例）。

**A4 sourcesJar 发布**
- `android { publishing { singleVariant("release") { withSourcesJar() } } }`——接入方 IDE 能看到源码 + 中文 KDoc；POM 补 license/scm（可选，至少 name/description 已有）。

**F 配置体系收敛**
- 消除「app/env 死层」误导 + 打通 EnvironmentManager → TestConfig 单一读取入口：
  - `TestConfig.init` 增可选 `envOverrides: Map<String,String>` 参数，注入 ConfigLoader 的 `env` 层（不再恒空）。
  - `EnvironmentManager` 增 `fun applyTo()`：把 `current()` 的 EnvironmentConfig 关键项（launchTimeout/maxLaunchTime/screenshotDir/extraConfig）转成 map，调用 `TestConfig.init(envOverrides=...)`——**显式调用**，避免 defaultLoader 里自动调用 EnvironmentManager 造成 init 循环递归。
  - **applyTo 幂等 + 覆盖顺序【R1 修订，回应 Question】**：applyTo 允许重复调用（每次以最新 EnvironmentConfig 重建 env 层 override，幂等——同输入同结果，不累积）；覆盖顺序沿用 ConfigLoader 既有优先级 `global < app < env < cli`——即 env 层（applyTo 注入）**低于 cli**（命令行 `-e` 仍最高），高于 global（properties 文件）。KDoc 写明该顺序与幂等语义。
  - 文档/注释更新：CLAUDE.md config 行表述「分层配置 + Environment（**显式 applyTo 打通**）」，不再暗示 env 层自动可用。

### 可选方案

- F 用「defaultLoader 里自动读 EnvironmentManager」——**否决**：EnvironmentManager.current()→resolveFromTestConfig()→TestConfig.getString()→ensureInit()→defaultLoader 循环递归。改用**显式 applyTo** 打破环。
- A2 用 configurations.all exclude——否决：不写进 POM，接入方仍需自己排；per-dependency exclude 才进 POM exclusions。

### 实现步骤

1. TestConfig.screenshotDir → targetContext；TestConfig.waitForIdleTimeout + ConfigKeys；TestConfig.init(envOverrides) 打通 env 层。
2. DefaultTestLogger/EnvironmentConfig 去 `/sdcard/Pictures` 硬编码，null → TestConfig.screenshotDir；mkdirs 失败告警。
3. BaseUiTest.setUp 用 TestConfig.waitForIdleTimeout。
4. EnvironmentManager.applyTo()。
5. build.gradle：hamcrest api、espresso-core exclude protobuf-lite、withSourcesJar、LIB_VERSION 1.7.0。
6. 单测（新增：targetContext 语义不可纯 JVM 测，但 waitForIdleTimeout 配置读取、env 层打通、EnvironmentConfig null 回退可测）+ 更新受影响测试（ConfigLoaderTest/EnvironmentManagerTest/TestConfigTest）。
7. `publishToMavenLocal` + **dex 级正对照**验证：`unzip -p <发布 aar 里 classes.jar 无 dex>`；改用发布 POM 检查 exclusions/hamcrest（`grep` POM）+ 一个消费 smoke（可选）。
8. Codex impl review → 修 → 回写。

### 验证方式

- 全量 JVM 单测通过。
- POM 断言（自动化）：发布后 `~/.m2/.../autotest-1.7.0.pom` 含 `org.hamcrest:hamcrest:2.2` 且 espresso-core 带 `<exclusions>` protobuf-lite；`autotest-1.7.0-sources.jar` 存在。
- **【R1 修订：本批必做 instrumentation smoke】** 新增 `ProductDirSmokeTest`（androidTest，L1 模拟器）：断言 `TestConfig.screenshotDir` 路径**包含 targetContext 的 packageName**（`getInstrumentation().targetContext.packageName`）、**不含 `/sdcard/Pictures`**、且**可写**（在该目录 create + write + delete 一个探针文件成功）。这直接坐实 P0-9 框架侧根治（不再把 targetContext 语义全推到 follow-up）。框架自测场景 target=框架自身包，路径应为 `/sdcard/Android/data/com.autotest.test/files/autotest` 一类（targetContext=框架 test 包自身，可写）。
- debox 真实接入（target=debox 包）的 scoped storage 端到端仍作 follow-up（需 debox 工作区），但本批 smoke 已证明「用的是 targetContext 且可写、不落公共目录」。

### 风险

1. hamcrest 2.2 与 espresso 传递的 hamcrest 1.x 版本冲突——Gradle 解析取高（2.2），G3 debox 实证可用；若接入方锁 1.x 需 resolutionStrategy（文档提示）。
2. targetContext 改动改变产物落盘位置——框架自测（androidTest 在 test 进程但 targetContext=框架自身包）与真实接入（target=debox）行为一致性靠 follow-up 坐实；本批不改变「一个入口」结构。
3. TestConfig.init 新增可选参数——保持默认无参兼容；env 层默认仍空（只有 applyTo 才填），不破坏现有行为。

### 需要用户确认

- 无（批次范围已确认）。debox 侧 1.7.0 接入回归需另约（涉及 debox 工作区 + 设备），归档时列为 follow-up。

---

## Accepted Plan

Version: v2（R1 两 Important + Question 修订后）
Accepted At: 2026-07-02
Accepted By:
- Claude Code: 是
- Codex: plan-review R2 `VERDICT: PASS`（R1 FAIL → R2 PASS）
- User: 批次范围已确认
Decision: PASS
Scope: P0-9（targetContext + 去 /sdcard/Pictures + mkdirs 告警）+ A1 hamcrest api + A2 protobuf-lite exclude 进 POM + A3 waitForIdleTimeout 配置化+恢复 + A4 sourcesJar + F 配置收敛（init(envOverrides)+applyTo 幂等/覆盖顺序）。LIB_VERSION 1.7.0。
Verification: 全量 JVM 单测 + POM 断言（hamcrest/exclusions/sources.jar）+ ProductDirSmokeTest（L1 模拟器坐实 targetContext 可写不落公共目录）。
Accepted Risks: 无。
Deviation Policy: 触碰 POM 依赖契约、context 语义、init/applyTo 覆盖顺序的偏离先 consult Codex。
