# Implementation — 测试执行 debox feat/share-refactor

> ② 实现。当前状态以 `index.md` 为准。严格按 `plan.md` 的 Accepted Plan（Proposal v5）实现。
> **不复述 plan**：本文件只记 ①与基线的差异 / 补充发现 ②consult ③做了什么的最小可追溯日志。
> 用例与结果落 `docs/testing/runs/2026-07-16-分享弹窗V2重构/{cases.md,results.md}`（本文件不复述表格）。

## Implementation Log

| Time | Action | Files / Areas | Notes（差异/发现） |
| --- | --- | --- | --- |
| 07-16 18:5x | 建 worktree 检出分支 + §7.7 shim + NODE_PATH 构建 debug 包 | scratchpad worktree | 不动用户 `feat/gasless-beta` 工作区；APK 2.15.0/21500000 sha256 `f5e768abd404d9ad` |
| 07-16 19:0x | 分支 4 模块 share 单测（offline） | im:imKit/ReactNative/BaseModule/moduleMain | **197 tests / 0 fail**；首跑非 offline 遇 sonatype 504 与 `testAppDebugUnitTest` 任务名错，改 `testDebugUnitTest` + `--offline` 解决 |
| 07-16 19:2x | 安装预检 + 冷启归因 | emulator-5554 | 签名一致、无降级、版本对齐、登录态在、0 FATAL |
| 07-16 19:2x-19:4x | L1 UI 逐类型执行 | 见 results.md | 5 类型 PASS + 发送 + 旋转 + 断网；6 类型 BLOCKED |
| 07-16 19:4x | 网络恢复 + 旋转设置还原 | emulator-5554 | `svc enable`+`http_proxy :0`；`accelerometer_rotation 1` |

## 补充发现（超出计划的实证）

- **V2/V1 判据实操调整**：cases.md 设计的 view-id 组合判据（`scrollPreview`+`rvChannelList`+`rvSessionList`）在本机 uiautomator dump 频繁返回陈旧树（App「永不 idle」，MessageHomeScreen.md 已记）→ 改用 **`dumpsys activity top` 的 Fragment 类名探针 `ShareDialogFragmentV2`**（计划 Δ2 已列为兜底探针）作为主判据，全程可靠命中。此为计划内兜底，非偏离。
- **剪贴板判据调整（降级 UI 证据）**：`cmd clipboard set-primary/get-primary` 在本 AVD 报 `No shell command implementation` → 无法执行 Δ8 的「基线写入 + 读取比对」。改用**分享弹窗自身 toast「复制成功」+ 屏幕剪贴板悬浮气泡（含深链原文）**作为触发证据。**证据强度限定**：此手段证明「点击触发了复制动作 + 目标链接文本正确」，**不能证明剪贴板值相对预设基线发生变化**（无读取通道）→ 复制类结论按**降级 UI 证据**记（见下方偏离日志 D-1）。
- **MediaStore 判据按 Δ17 实测语法执行成功**（冒号 projection，before 空 → after `_id=39`）。

## Plan Deviation Log

| Time | Deviation | Reason | Consulted Codex | User Confirmed |
| --- | --- | --- | --- | --- |
| 07-16 19:2x | V2/V1 判据主手段由 view-id 组合切为 Fragment 类名探针 | uiautomator dump 陈旧（App 永不 idle）；Δ2 已列 Fragment 探针为兜底 | 计划内授权，无需 | — |
| 07-16 19:3x | **D-1** 复制链接判据由 Δ8 剪贴板基线读取降为 toast+气泡触发证据 | 本 AVD 无 `cmd clipboard` 实现，无读取通道 | 环境限制，非设计偏离 | 复制类结论已在 results.md 标注「触发+链接正确」而非「剪贴板值变更」 |

## Consult Log

None（测试执行期无需 mid-impl consult；安全类判定均按 plan-review 已定策略执行）。
