# Review — v1.8.0 后框架/文档/逻辑优化 + debox-android 接入

> ③ 过程 & 问题。当前状态以 `index.md` 为准；本文件存各轮 impl-review 与修复**过程历史**（per-round VERDICT 是快照，非当前状态）。

## Implementation Review Log（per-round，含 VERDICT 快照）

### Implementation Review Round 1（2026-07-03，codex exec read-only / medium）

评审对象：autotest v1.8.1 全部代码改动（WS-A1：C3/E1/E2/E3/E6/Q4 + logger 接线 + 新增 16 单测），diff 见 scratchpad `autotest-v181-src.diff`。

```text
VERDICT: PASS
```

Critical: None

Important: None

Accepted Risk Candidates: None

Verification gaps（Codex 提示，均落在受真机环境限制的范围内）：
1. `SelfHealingLocator.deterministicFind` 同属性多条件 + Not 后置过滤缺真机/instrumented 验证（当前 = 纯 JVM 谓词/主 Leaf 决策单测覆盖）。
2. `UiReplayExecutor.LAUNCH_APP` 新增 `waitForApp` 缺真机回放验证（依赖 waitForApp 既有行为兜底）。

→ 两条 gap 对应的真机验证正是 WS-B debox 端到端受阻部分（地域弹窗 + 真机锁屏），随端到端补跑一并闭合。重点核查项（C3 不丢条件 / E2 addSuppressed 保真 / Q4 @Transient 对 data class equals/key 的影响 / E6 误失败风险）Codex 均未提出 finding → 无回归。

## Review Fix Log

### Round 1

Codex finding: 无 Critical/Important，仅两条 Verification Gaps（见上）。

Claude assessment: 两条 gap 是真机层验证边界，非代码缺陷；受 debox 端到端的外部环境（地域弹窗/锁屏）阻断，不在本轮可解范围。C3 的 Not 后置过滤逻辑由 `PrimaryLeavesTest.被略过的同属性条件仍由 conjunction 谓词后置兜底` + `deterministicFind` 的 `conjunction.all { it.matches(...) }` 静态保证；LAUNCH_APP 的 waitForApp 与既有 launchApp 同一 util，风险低。

Action: 记录为已知验证边界，随 WS-B 端到端补跑闭合；不改代码。

Files changed: 无（PASS，无需修复）。

Verification: JVM 单测 311 全过 + verifyNoProtobufLite + publishToMavenLocal 成功。

Remaining risk: 仅真机层两条 gap，随端到端补跑闭合。

Accepted risk: 无（无 Important）。

## Accepted Risk Log

None（impl-review 无 Critical、无 Important）。

## Verification

### Commands

```bash
./gradlew :autotest:test               # 311 全过 / 0 失败 / 0 跳过
./gradlew :autotest:publishToMavenLocal :autotest:verifyNoProtobufLite  # 均成功
# debox 两 base：assembleAppDebug + assembleAppDebugAndroidTest 均 BUILD SUCCESSFUL
# resolved appDebugAndroidTestRuntimeClasspath = com.autotest:autotest:1.8.1，无 protobuf-lite
```

### Results

- [x] Build/test/lint/manual checks passed or their absence is explained（311 单测全过；两 base 构建通过；端到端 instrument 受外部环境阻断已解释）。
- [x] Secret scan completed without recording matching secret lines（无 secret 写入；debox local.properties 敏感项未打印/未入库）。

## Final Review

```text
VERDICT: PASS
```

Codex final assessment: v1.8.1 静默缺口清扫实现与 Accepted Plan 一致，零 Critical/Important，两条真机层 Verification Gaps 随 WS-B 端到端补跑闭合。

Unresolved risks: debox 端到端冒烟 PASS 待外部条件（真机解锁 + 网络进 DeBox 服务区）——非代码风险，属白名单「外部系统/真机首解」。
