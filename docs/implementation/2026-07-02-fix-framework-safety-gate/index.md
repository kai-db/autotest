# 第一批：危险操作点击网关 + 假绿路径修复（P0-0~5）

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md` / `implementation.md` / `review.md`。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-fix-framework-safety-gate |
| Dir Path | docs/implementation/2026-07-02-fix-framework-safety-gate/ |
| Files | index.md（canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | medium（框架行为收紧，接口小幅变更；唯一接入方 debox 可控） |
| Codex Calls | 5（plan R1 FAIL/R2 FAIL/R3 PASS + impl R1 FAIL/R2 PASS） |
| High-Cost Modes Used | none |
| 上游 | `2026-07-02-audit-framework-architecture`（Accepted Plan v2 第一批） |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md)
- ② 实现：[implementation.md](./implementation.md)
- ③ 过程 & 问题：[review.md](./review.md)
- 账本：本任务开始前已读两层（本 session 内），并新增 G4 / L-002。

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS（plan R1/R2 FAIL → R3 PASS；impl R1 FAIL → R2 PASS）
- Latest summary: P0-0 危险操作点击网关（DangerousOpsGuard，fail-closed，覆盖全部 5 个点击入口 + 权限分支）+ P0-1 DialogDismiss 收敛（纯 behavior、去确认语义、权限双条件豁免）+ P0-2 flakySafely 白名单穿透 + P0-3 空用例/空缓存必 FAIL + P0-4 assertAppInForeground JUnit 化 + P0-5 硬断言不静默降级。LIB_VERSION 1.6.1。JVM 单测 227 全过，SafetyGateSmokeTest 模拟器 OK(4)，publishToMavenLocal 成功。

## Final Outcome

Status: done
Summary: 第一批钱包安全 + 假绿修复完成并通过双 gate（plan 3 轮 / impl 2 轮）。铁律#7 从流程纪律下沉为框架代码守卫（L-002 落地），4 条假绿路径封堵，弹窗拦截器不再自动点确认类按钮。
Follow-up: 改动未 commit（待用户决定提交时机）；行为变化清单已录（见 plan 验证方式），debox 侧下次回归按新语义修 instrumented 用例。全部四批完成后统一做 docs/ 目录文档对齐（用户 2026-07-02 追加确认）。

## Completion Checklist

- [x] 任务开始前已读两层账本。
- [x] Original request completed（P0-0~5 全部落地）。
- [x] Plan review passed（R3 PASS）。
- [x] Accepted Plan recorded（plan.md v3）。
- [x] Plan deviations recorded（implementation.md D1 targetSdk / D2 冒烟断言）。
- [x] Consult decisions recorded：N/A。
- [x] No unresolved Critical / Important findings（impl R2 PASS，0 finding）。
- [x] 全量单测（227）+ publishToMavenLocal（1.6.1）+ 模拟器冒烟（4）通过。
- [x] Docs updated（任务四件套）。
- [x] No raw secrets added.
- [x] Retro 已评估：G4（stdin 封）+ L-002（安全下沉）本 session 已入账本；本批新增经验 = targetSdk 缺省触发弃用弹窗拖垮 instrumented（候选 lesson，第四批集成坑一并归纳，暂不单列）。
