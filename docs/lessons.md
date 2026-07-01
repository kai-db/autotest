# 项目经验 Ledger（autotest）

> `agent-dev-loop` 的项目级 ledger。记录本项目特化、可复用的经验教训。
> 通用（工具 / shell / 工作流）经验 → 全局 ledger；项目特化 → 本文件。

---

## L-001 修复阶段必须走 agent-dev-loop（强制）

- **背景**：2026-07-01 复盘发现，历次测试（`runs/` 2026-06-05 ~ 2026-07-01）的 bug 修复
  **全部直接改代码**，`docs/implementation/` 一直为空，从未走过 agent-dev-loop 协作闭环。
  同时存在两类偏离：①「边测边修」破坏独立验收（06-25 第四轮发现新问题就地修）；
  ②「全量没跑全就停」（07-01 仅第 1 轮，7+ 条 ⏸️ 待执行未跑）。
- **教训**：测试闭环 Phase 4 修复，**必须**走 agent-dev-loop（Claude 计划/实现/修复/记录
  + Codex 只读独立 review）：建 `docs/implementation/YYYY-MM-DD-动词-对象/` 任务目录 →
  plan → Codex plan review（`VERDICT`）→ 实现 → Codex 实现 review → 回写 results.md。
  **不直接改代码。** 全量要跑全（⏸️ 不算跑完），验收阶段独立、发现问题回迭代修复阶段。
- **固化位置**：`CLAUDE.md` 铁律 §5；`docs/testing/TEST_GUIDE.md` 第五节强制声明 + 第六节。
- **日期**：2026-07-01
