# debox dev 07ae7655..HEAD 深度分析 + 深度回归测试（保证无问题）

> **canonical 入口**（T2 标准任务）。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。
> 反杂硬规则（0.1.3）：不复述、定稿无空壳（用不上的节直接删）、不 commit 二进制。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-10-01-test-debox-anr-realm-regression |
| Tier | T2 |
| Created / Last Active | 2026-07-10 / 2026-07-10 |
| Status | **done · 全流程自主完成**（核码 + 自主 CLI 打包 + 安装 + 组 B/S/I/R 执行；零人工打包） |
| Risk Level | high（钱包/密钥/Realm 注入域） |
| Codex Calls · High-Cost Modes | plan-review R1 FAIL / R2 假PASS作废 / R2-retry FAIL / R3 FAIL→v4 全采纳到 cap · read-only |
| History | None（首轮） |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md) · ② 实现：[implementation.md](./implementation.md) · ③ 过程 & 问题：[review.md](./review.md)
- 开工前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`），相关条目：全局 L4/L12/L13/L14/L16/L17/L18；项目 L-001/L-002/L-003。

## Final Status / VERDICT / Outcome（唯一权威）

- Current Status: **全部完成（2026-07-11）**。深度核码 + **自主 CLI 构建/安装/执行** 全链跑通（纠正了此前「只能 AS 打包」误判——见 TEST_GUIDE §7.7 新规范）。组 B/S/I/R 执行完毕，零 FAIL。
- Final VERDICT: **PASS_WITH_ACCEPTED_RISK**（G1 核码三提交与档案一致+四高危点正确；G2 Realm 恢复三场景 **on-device 实证正确**（模拟器层，不闭合真机门禁 AR-1）；G3 IM ANR L1 冒烟/清未读/搜索 0 ANR（深路径 L1 内容稀疏靠 36/36 单测兜底）；G4 PicSel 埋点在 APK+相册功能正常；G5 全程零新 FATAL/ANR、零本批引入缺陷。残留=真机门禁/线上归零，见 AR-1/AR-4）
- Summary（latest / final）: **深度核码结论 = `07ae7655`/`fc77707a`/`75d44b39` 三提交实现与 debox 档案声称一致、四个高危实现点（epoch 主线程校验 / 缓存双门禁 / 切号单锁原子 / CONNECTED 先 bump 后 resume）均正确落地**。独立新发现 2 处非本批引入的同源缺陷（F-1 onWarnClick 主线程查库残留、F-2 suspend 无兜底恢复，均 Important，各立 debox 修复任务）+ 关闭 1 处存疑（F-3 stale onError 上层 callback=null 无用户可见报错）。测试计划经 3 轮 plan-review 收敛，诚实定位：模拟器层证据不闭合 debox 真机放量硬门禁（AR-1，真机全锁屏+真资产+旧包硬约束）。
- Follow-up（含未完成验证）: **全部完成**——自主构建/安装/组 B/S/I/R 执行、Realm 恢复三场景模拟器实证 + **真机 AR-1 实质闭合**（三星硬件 TC-R-002/003 通过）；F-1 修复完成(impl PASS)、F-2 wontfix(用户确认)。**遗留**：① 真机 AR-1 事故(Knox 禁 run-as 写致真账号 key 丢失,已 seed 重导恢复,教训入 devices.md+全局 L31)；② TC-R-004 真机未跑(子集,不再注入)；③ IM ANR 深路径靠单测兜底；④ 用户下批要求「debox 新需求改动深度测试」——**待办**。

## Completion Checklist（核心 5 项；完整清单见主协议「最终完成定义」）

- [x] 原始需求「深度分析」完成（Phase A）；「深度测试」阻塞于用户装包（准备就绪）。
- [x] plan-review 3 轮 findings 全采纳至 v4；harness impl-review R2 PASS，无未处理 Critical/Important。
- [x] 验证：分析=核码+agent 独立核查；测试执行验证待装包（已入 Follow-up + Accepted Risks）。
- [x] 未写入任何 secrets（harness 禁 key 值/长度进日志；备份禁本体入产物）。
- [x] Retro 已分流：`[candidate]` 见 `docs/lessons.md`（L-004 候选）。
