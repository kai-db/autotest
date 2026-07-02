# 框架与架构全面审计：优化提案

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-audit-framework-architecture |
| Dir Path | docs/implementation/2026-07-02-audit-framework-architecture/ |
| Files | index.md（本文件，canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | low（审计/提案任务，本任务本身不改框架代码；后续采纳项各自另开任务） |
| Codex Calls | 2（plan-review R1 FAIL→R2 PASS） |
| High-Cost Modes Used | none |

## 任务背景

用户诉求：「整个框架和架构还有什么可以优化的吗」——继 2026-07-02-optimize-emulator-first-testing
（测试规范 emulator-first 改造）之后，对 **autotest 框架代码本身**（`com.autotest:autotest` v1.6.0，
约 4510 行 main / 199 条单测）与整体架构做全面审计，产出按优先级排序的优化提案。
提案的采纳与实施不在本任务范围（每个采纳项后续各自走 agent-dev-loop）。

## 导航

- ① 计划 / 方案：[plan.md](./plan.md)
- ② 实现：[implementation.md](./implementation.md)
- ③ 过程 & 问题：[review.md](./review.md)
- 知识账本（两层）：全局 `lessons-global.md` + 项目 `docs/lessons.md`（**本任务开始前已读两层**，同 session 内已读）

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS（plan-review R1 FAIL：漏「全点击入口危险操作守卫」Critical → 增补 P0-0 后 R2 PASS）
- Latest summary: 框架审计完成：3 路并行源码审查 + Codex 逐条 spot-check（P0-1~9 全部属实）。
  交付分级优化提案：P0 ×10（危险操作点击网关 / 假绿路径 ×4 / 监工引擎 ×3 / 证据污染 / 产物目录）、
  P1 六大主题（集成坑 POM 根治 / 存储信任边界 / 自愈链正确性 / 重试体系 / silent-failure 清扫 / 配置收敛）、
  P2 ×10（可测性/性能/文档），建议分 4 批实施（见 plan.md Accepted Plan）。

## Final Outcome

Status: done
Summary: 审计发现框架架构方向健康（authoring/execution 分层、注入式 AI 接口、行为级单测 199 条），
但存在 1 个安全级缺口（铁律7 未下沉到框架点击入口——Codex 抓出）、4 条假绿路径（ART assert 恒过/
零步骤假 PASS/硬断言静默降级/flaky 洗白真失败）、跨用例证据污染、以及 G3 集成坑未在框架侧根治等问题。
全部固化为可实施的 4 批任务清单。
Follow-up: ①用户裁剪实施批次后，每批各开 agent-dev-loop 任务（第一批=P0-0~5 钱包安全/假绿，优先）；
②两条候选 lesson 待用户确认后入账本：G-候选「后台跑交互式 CLI 必须 `< /dev/null` 封 stdin，
否则静默挂起无产出」（全局）、项目候选「安全不变量不能只写进流程纪律，要下沉为框架代码守卫」。

## Completion Checklist

- [x] 任务开始前已读两层账本。
- [x] Original request completed（交付分级优化提案）。
- [x] Plan review passed（R2 PASS，见 review.md）。
- [x] Accepted Plan recorded（见 plan.md，v2）。
- [x] Plan deviations recorded：N/A（审计任务无实现偏离）。
- [x] Consult decisions recorded：N/A。
- [x] No unresolved Critical / Important findings（R1 Critical 已由 P0-0 增补解决）。
- [x] Relevant checks passed or explained（docs-only；findings 经 Codex spot-check 独立核实）。
- [x] Docs updated（本任务四件套）。
- [x] No raw secrets added.
- [x] Retro 已评估（2 条候选 lesson 待用户确认，见 Final Outcome Follow-up）。
