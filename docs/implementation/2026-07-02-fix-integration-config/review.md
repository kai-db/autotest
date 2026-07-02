# ③ 过程 & 问题

## Plan Review Rounds

### Plan Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（Critical: None；2 Important + 1 Question）
  - I1[Regression 86]：A3 只配置化 waitForIdleTimeout 未恢复原值，全局单例跨测试类泄漏。
  - I2[Test 82]：P0-9 targetContext 根治把真机语义全推 follow-up，本批出条件无法证明最关键集成坑已根治。
  - Q：EnvironmentManager.applyTo 重复调用/覆盖顺序/幂等语义未定义。
- 处理（Plan Revision v2，全部采纳）：①BaseUiTest 记录原始 waitForIdleTimeout、tearDown 恢复；②本批新增 ProductDirSmokeTest（androidTest）断言 screenshotDir 含 targetContext.packageName、不含 /sdcard/Pictures、可写；③applyTo 幂等 + 覆盖顺序 global<app<env<cli（env 低于 cli 高于 global），KDoc 写明。

### Plan Review R2 — 2026-07-02

- 结果：**VERDICT: PASS**（全 None）→ 落 Accepted Plan v2，进入实现。

## Implementation Review Rounds

### Impl Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（1 Critical 误报 + 1 Important 真实 + 1 Gap）
  - Critical[95]：担心删了无参 init() 破坏调用——实为误报（init(loader=default) 默认参数使 init() 仍可调，256 单测+编译已证）；仍按建议显式恢复 fun init()。
  - Important[84]：BaseUiTest guard evidenceCapture 静默丢危险操作审计截图 → 检查 mkdirs/takeScreenshot 返回值 + logger.w 留痕。
  - Gap：缺 applyTo + TestConfig 集成测试。
- 处理：恢复 fun init()；evidenceCapture 失败留痕；新增 androidTest EnvApplyToSmokeTest（注入 env 层可读 + 幂等，applyTo 依赖 instrumentation 故 androidTest）。

### Impl Review R2 — 2026-07-02

- 结果：**VERDICT: PASS**（Critical/Important/Gaps 全 None）→ 归档。

## Review Fix Log

（待记录。）

## Accepted Risk

- 无。

## Verification

- JVM 单测 256 全过；publishToMavenLocal 1.7.0 成功。
- POM 契约断言：hamcrest:2.2 compile ✓ / espresso-core protobuf-lite `<exclusions>` ✓ / sources.jar ✓。
- instrumented（L1 模拟器）：ProductDirSmokeTest OK(1)（targetContext 可写不落公共目录）、EnvApplyToSmokeTest OK(2)（applyTo 注入+幂等）、SafetyGateSmokeTest OK(4) 无回归。
- Follow-up：debox 侧 1.7.0 真实接入回归（target=debox 的 scoped storage 端到端 + dex 级正对照）需另约 debox 工作区。
