# 设计 debox account-lifecycle 分支全量回归用例（b8fd28d..bd165ef 净生效）

> **canonical 入口**（T2 标准任务）。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。
> 反杂硬规则（0.1.3）：不复述、定稿无空壳（用不上的节直接删）、不 commit 二进制。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-19-01-design-cases-account-lifecycle |
| Tier | T2（高风险域：钱包/账号/资金路径的用例设计；跨模块；用户要求完整记录） |
| Created / Last Active | 2026-07-19 / 2026-07-19 |
| Status | planning（Plan Proposal 已产出，等待主会话 Codex plan-review） |
| Risk Level | high（被测域含钱包生命周期/支付密码/转账/被踢下线；设计错误会驱动危险操作或假阳性修复） |
| Codex Calls · High-Cost Modes | 0 · none（本会话按用户指令不自行调用 Codex；plan-review 由主会话 Codex 独立执行） |
| History | None（新建。与 `2026-07-17-01-test-debox-account-revalidation-observability` 同域但**不续写**：该目录是旧一轮「子集测试执行」任务且停在 planning，本任务是对 b8fd28d..bd165ef 全量净生效的**用例重设计**，用户明确指令旧 cases/结论一律不继承、另建新任务；旧目录仅作证据源，其挂账处置见 plan.md Phase 0 节） |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md) · ② 实现：[implementation.md](./implementation.md) · ③ 过程 & 问题：[review.md](./review.md)
- 开工前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`），相关条目：
  - 全局：L1（宽 grep 全仓）/ L2（同类文件全查）/ L4（声明前先验证——本轮已实证：首版「触码提交」检测脚本因 `grep -q` 早退 SIGPIPE 竞态漏报 8 个代码提交，经逐提交 `git show --stat` 复核纠正）/ L5（引原文不错安编号）/ L12（mock 单测过 ≠ 能跑，真实依赖要 e2e）/ L17（版本归因，行号探针）/ L18（debox CLI 构建需 NODE_PATH）
  - 项目：L-001（修复必走 agent-dev-loop）/ L-004（证据强度分层，模拟器不闭合真机门禁）/ L-005（BLOCKED 须有探索证据，否则 NOT_RUN）/ L-006（FAIL 三分类，判据须数独立信号）/ L-007（留原始日志线路非摘要）/ L-008（轮数与产能匹配：并行分片+AAR 分层+Phase 5 分层）

## Final Status / VERDICT / Outcome（唯一权威）

- Current Status: planning——Plan Review R1 = **FAIL**（3 Critical + 3 Important，原文见 review.md），已只读补证并修订为 **Proposal v2**，待主会话 Codex Round 2
- Final VERDICT: （未终审；R1 快照 FAIL 已按 6 条 finding 全部处置）
- Summary（latest / final 合一，只写一处）: 对 debox refactor/account-lifecycle 46 提交区间净生效甄别（22 代码提交 / 2 对 revert 净零 / merge 带入无产品行为）；v2 按 R1 findings 完成调用方逐项定性（saveConnectWallet/setWallet 全入口）、D11 被踢契约补全（manual=true / 1500ms 去抖 / AR-A3/A5 注入、AR-A4 显式 gap）、D8 BTC 转账降级 UI case、新增 D13 第三方连接/导入域、worktree 构建接线修正——**13 功能域 + 前置域，共 78 条（P0 42 / P1 29 / P2 7）**，无覆盖类 TODO 残留。
- Follow-up（含未完成验证）: Round 2 plan-review → Accepted Plan → cases.md 生成 → 测试执行；唯一显式 verification gap = AR-A4（见 plan.md §10）。

## Completion Checklist（核心 5 项；完整清单见主协议「最终完成定义」）

- [ ] 原始需求完成。
- [ ] 无未处理 Critical / Important finding（见 review.md）。
- [ ] 验证通过或缺失原因已说明（见 review.md；未完成的验证已移入 Follow-up）。
- [ ] 未写入任何 secrets。
- [ ] Retro 已分流（`[candidate]` 进账本）或确认无教训。
