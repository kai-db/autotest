# 第二批：执行引擎并发安全 + 重试体系（P0-6/7/8 + D1/D2）

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-fix-engine-retry |
| Dir Path | docs/implementation/2026-07-02-fix-engine-retry/ |
| Files | index.md（canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | medium（Interceptor 接口破坏性变更 + 并发/重试语义收紧） |
| Codex Calls | 5（plan R1 FAIL/R2 PASS + impl R1 FAIL/R2 FAIL/R3 PASS）|
| 上游 | `2026-07-02-audit-framework-architecture` Accepted Plan v2 第二批 |

## 导航

- ① 计划：[plan.md](./plan.md) ② 实现：[implementation.md](./implementation.md) ③ 过程：[review.md](./review.md)
- 账本：本 session 已读两层并新增 G4 / L-002。

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS（plan R2 PASS；impl R1/R2 FAIL → R3 PASS）
- Latest summary: 执行引擎并发安全 + 重试体系收紧完成。P0-6 僵尸线程协作取消 + timedOut terminal 拒并发；P0-7 maxRounds 上限 + Phase6 默认超时；P0-8 StepContext 双 key（证据 runId#case#step#attempt 不覆盖 + 性能 case#step 聚合），runId 每 run 生成、attempt 随重放递增、文件名带 fileSafeKey；D1 flakyStep 走 classify(Throwable) 只重试 FLAKY、Step.retriable 默认 false（副作用不重放）、三层共享 RetryBudget 硬顶；D2 FlakyClassifierApi + 不可变 DefaultFlakyClassifier。LIB_VERSION 1.6.2，JVM 单测 255 全过。

## Final Outcome

Status: done
Summary: 第二批通过双 gate（plan 2 轮 / impl 3 轮，Codex 每轮抓真问题——并发取消/预算共享/证据隔离逐步收紧到位）。
Follow-up: 改动未 commit；Interceptor 接口破坏性变更（StepContext）+ retriable 默认 false，debox 回归时按行为变化清单 7 条同步改 DeBoxBaseTest。四批全完成后统一做 docs/ 目录文档对齐。

## Completion Checklist

- [x] 任务开始前已读两层账本。
- [x] Original request completed（P0-6/7/8 + D1/D2）。
- [x] Plan review passed（R2 PASS）。
- [x] Accepted Plan recorded（plan.md v2）。
- [x] Plan deviations recorded：N/A。
- [x] Consult decisions recorded：N/A。
- [x] No unresolved Critical / Important findings（impl R3 PASS）。
- [x] 全量单测（255）+ publishToMavenLocal（1.6.2）通过。
- [x] Docs updated（任务四件套）。
- [x] No raw secrets added.
- [x] Retro 已评估：无新增账本条目（G4 已录）；接口破坏性变更影响面已列清单。
