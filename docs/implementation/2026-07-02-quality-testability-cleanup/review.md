# ③ 过程 & 问题

## Plan Review Rounds

### Plan Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（Critical None；3 Important + 2 Question）
  - I1[Security/Regression 85]：Q5 requireSafeTag 放宽 -/: 未定义完整边界（首位 - option-like、: filter-spec 语义）。
  - I2[Regression/Test 82]：Q5 airplane cmd connectivity 仅断言字符串,不证失败可见/fallback/不静默 no-op。
  - I3[Design/Regression 84]：Q7 failureKind 只覆盖 envReset/before/Scenario,未定 after/cleanup/lifecycle/reporter 归类矩阵。
  - Q1：failureKind 是否被报告消费。Q2：DefaultTestLogger 多实例写同文件。
- 处理（Plan Revision v2）：
  1. requireSafeTag 严格语法（ 或受控 Tag:Priority、禁首位 -/*/空格）+ 边界测试（-v/*:S/tag:V/tag:bad）。
  2. airplane：API≥28 cmd connectivity / 24-27 回退,执行后 settings get 读回校验、不符 logger.w + 返回 Boolean（不静默 no-op）。
  3. failureKind 完整矩阵（只归类 primary：envReset/before=INFRA、Scenario=ASSERTION；after/lifecycle/reporter=secondary 不覆盖 primary；InterruptedException 不适用）+ 进 MonitorMode.toMarkdown 失败行展示（回应 Q1）。
  4. logger 两 SimpleDateFormat 改 ThreadLocal + synchronized(this) 写文件段；KDoc 写明单实例/文件假设（回应 Q2）。

### Plan Review R2 — 2026-07-02

- 结果：**VERDICT: FAIL**（Critical None；1 Important 88）：`Unit→Boolean`/data class 加字段是 ABI 破坏,不该称"向后兼容"、patch 版号低估风险。
- 处理：版号 1.7.3 → **1.8.0**(minor,signal API 演进);兼容性如实标注(源码兼容/ABI 破坏,debox 源码级重编无碍),CHANGELOG 列 API 变更。

### Plan Review R3 — 2026-07-02

- 结果：**VERDICT: PASS**（全 None）→ 落 Accepted Plan v3,进入实现。

## Implementation Review Rounds

### Impl Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（1 Critical + 2 Verification Gap + 1 Accepted Risk）
  - Critical[92]：`ThreadLocal.withInitial` 是 Android API26+,minSdk24 会 NoSuchMethodError。
  - Gap：airplane 读回失败路径无测（设备依赖）；MonitorMode [INFRA]/[ASSERTION] 展示无断言。
  - Accepted Risk：DevicePreference public enum 删除需 CHANGELOG 记。
- 处理：ThreadLocal 改匿名子类 override initialValue（minSdk24 兼容）；补 MonitorModeFailureKindMarkdownTest（3 例）；CHANGELOG 1.8.0 显式列 API 变更（airplane Unit→Boolean / TestCaseResult 加字段 / DevicePreference 删）；airplane 设备侧 gap 记录（androidTest 覆盖）。

### Impl Review R2 — 2026-07-02

- 结果：**VERDICT: PASS**（Critical/Important/Accepted Risk/Verification Gaps 全 None）→ 归档。

## Review Fix Log

（待记录。）

## Accepted Risk

- 无（DevicePreference 删除等已在 CHANGELOG 1.8.0 显式记录）。

## Verification

- JVM 单测 **295 全过**（+15 第五批）；verifyNoProtobufLite 护栏 PASS；publishToMavenLocal 1.8.0 成功。
- Follow-up：airplane API24-27/28+ 真机分支 + 读回失败路径设备侧覆盖（纯 JVM 测不到，androidTest 兜底）；完整 DeviceGateway / cleanup run 子目录轮换 / MonitorMode sealed 状态机继续 defer。
