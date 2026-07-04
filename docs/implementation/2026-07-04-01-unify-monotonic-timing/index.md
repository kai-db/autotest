# 计时统一单调钟（审计延后项 Q3）

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-04-01-unify-monotonic-timing |
| Dir Path | docs/implementation/2026-07-04-01-unify-monotonic-timing/ |
| Files | index.md（本文件，canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-04 10:20 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | low |
| Codex Calls | 5 |
| High-Cost Modes Used | none |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md)
- ② 实现：[implementation.md](./implementation.md)
- ③ 过程 & 问题：[review.md](./review.md)
- 知识账本（两层）：全局 `lessons-global.md`（经 skill 解析，跨项目）+ 项目 `docs/lessons.md`（**本任务开始前已读两层**）

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS（plan R1/R2 FAIL → R3 PASS；impl R1 FAIL 3 Important → 修复 → R2 PASS 零 finding）
- Latest summary: 审计延后项 Q3 兑现：src/main 25 处 System.currentTimeMillis 宽 grep 分类——11 处控制流时长/超时改 `MonotonicTime.nowMs()`（默认源运行时探测：Android=SystemClock.elapsedRealtime 全入口生效 / JVM=nanoTime 回退；对外仅 nowMs()，注入口 internal+@JvmSynthetic）；14 处持久化/展示/命名显式保留墙钟+逐处语义注释（RunReport schema、持久化格式不变）。新增假钟单测 12 条（冻结不假超时/跳变即超时/durationMs 精确等于假钟步进）。全量 646 执行 0 失败；v1.8.2 已发布 mavenLocal；docs 全对齐；原延后记录已回销标注。

## Final Outcome

Status: done
Summary: 墙钟跳变（NTP 同步/手动调时）不再影响框架任何超时判定与耗时统计；持久化 TTL/报告展示语义保持墙钟且有注释护栏。v1.8.2 发布 mavenLocal，debox 侧升级由下一次测试 run 承接。
Follow-up:
1. ~~改动未 commit~~ → 用户确认后已提交（2026-07-04，见 git log）。
2. ~~候选 lesson 待确认~~ → 用户已确认，入账全局账本 **L24**（skill 仓 commit 95ac581）。
3. debox 侧升 1.8.2 回归：下一次测试 run 顺带（与 1.8.1 流程一致），非本任务范围。

## Completion Checklist

- [x] 任务开始前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`）。
- [x] Original request completed.
- [x] Plan review passed（见 plan.md）。
- [x] Accepted Plan recorded（见 plan.md）。
- [x] Plan deviations recorded, or N/A（见 implementation.md）。
- [x] Consult decisions recorded, or N/A（见 implementation.md）。
- [x] No unresolved Critical / Important findings（见 review.md）。
- [x] Relevant checks passed or explained（见 review.md）。
- [x] Docs updated.
- [x] No raw secrets added to code, logs, docs, PR comments, or commit messages.
- [x] **Retro：1 条可泛化教训经用户确认已入全局账本 L24（@JvmSynthetic 挡 AAR 测试钩子）。**
