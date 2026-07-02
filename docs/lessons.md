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

## L-002 安全不变量不能只写进流程纪律，必须下沉为框架代码守卫

- **背景**：2026-07-02 框架架构审计（`docs/implementation/2026-07-02-audit-framework-architecture/`），
  Codex plan-review R1 以 Critical 92 指出：铁律#7「点击前比对危险操作清单」只存在于 AI 黑盒流程的
  纪律层（TEST_GUIDE/CLAUDE.md 文字约束），框架代码的**全部点击入口**（SelfHealingLocator.click /
  UiAutomatorExt.clickText / UiReplayExecutor CLICK / EspressoExt.click）无任何守卫——B 模式回放、
  指纹自愈（还可能命中错误元素）点到「确认转账/删除钱包」时无人拦截。我方三路审查都只盯了
  DialogDismissInterceptor 一处，漏掉了系统性缺口。
- **教训**：对不可逆操作的防线，「AI 读文档自觉遵守」只覆盖 AI 在环的路径；**确定性执行路径
  （回放/自愈/封装点击）必须有代码级守卫**（统一点击网关 + fail-stop + 留证 + 显式 opt-in 放行）。
  评审安全类规则时，问法应是「**哪些执行路径绕过了这条纪律**」，而不是「哪个组件违反了这条纪律」。
- **固化位置**：修复 = 第一批任务 P0-0（DangerousOpsGuard 点击网关）。
- **日期**：2026-07-02
