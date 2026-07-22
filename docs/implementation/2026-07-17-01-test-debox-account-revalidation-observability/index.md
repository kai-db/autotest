# 测试 debox dev 账号重验门禁 + 网络/IM 可观测性改动

> **canonical 入口**（T2 标准任务）。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。
> 反杂硬规则（0.1.3）：不复述、定稿无空壳（用不上的节直接删）、不 commit 二进制。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-17-01-test-debox-account-revalidation-observability |
| Tier | T2 |
| Created / Last Active | 2026-07-17 / 2026-07-17 |
| Status | planning |
| Risk Level | medium（含登出类注入用例，快照兜底） |
| Codex Calls · High-Cost Modes | 0 · none |
| History | 首轮，None |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md) · ② 实现：[implementation.md](./implementation.md) · ③ 过程 & 问题：[review.md](./review.md)
- 开工前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`），相关条目：L1/L2/L4/L12/L13/L14/L16/L17/L18（全局）；L-002/L-003/L-004/L-005（项目，其中 L-004/L-005 为 [candidate]，本任务实际应用后处置）

## Final Status / VERDICT / Outcome（唯一权威）

- Current Status:
- Final VERDICT: PASS | FAIL | PASS_WITH_ACCEPTED_RISK
- Summary（latest / final 合一，只写一处）:
- Follow-up（含未完成验证）:

## Completion Checklist（核心 5 项；完整清单见主协议「最终完成定义」）

- [ ] 原始需求完成。
- [ ] 无未处理 Critical / Important finding（见 review.md）。
- [ ] 验证通过或缺失原因已说明（见 review.md；未完成的验证已移入 Follow-up）。
- [ ] 未写入任何 secrets。
- [ ] Retro 已分流（`[candidate]` 进账本）或确认无教训。
