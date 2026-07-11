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

## L-003 审计提案项必须「要么实施、要么显式记延后」，杜绝无人认领的静默缺口

- **背景**：2026-07-02 框架审计产出 P0×10 / P1 六主题 / P2×10 提案，随后 5 任务实施到 v1.8.0。
  2026-07-03 新一轮审视（`docs/implementation/2026-07-02-optimize-framework-integrate-debox/`）发现
  C3、E1/E2/E3/E6、Q2/Q3/Q4/Q8 一组提案项**既未实施、也无任一任务记录延后原因**——不是评估后
  决定不做，而是任务拆分时漏接、无人认领，直到再审才暴露。
- **教训**：多任务承接一份审计清单时，每个提案项**终态必须二选一**：已实施（有 commit/测试）或
  显式延后（写明理由+触发条件，进 Follow-up）。二者皆无 = 静默缺口。收尾核对要拿**原始提案全集
  逐项对账**（本次用 Explore agent 对照 Accepted Plan 逐项判「实施/延后/未提及」），不能凭"大概都做了"。
- **固化位置**：v1.8.1 修 C3/E/Q4，Q2/Q3/Q8 补正式延后记录。
- **日期**：2026-07-03

## L-004 [candidate] 测「代码已修的证据」≠测「门禁已闭合」——模拟器/线上观测不替代真机硬门禁，如实标注闭合态

- **背景**：2026-07-10 回归 debox dev 07ae7655..HEAD 三提交（`2026-07-10-01`）。debox 侧挂账一条「Realm 恢复真机注入验证=2.14.3 放量硬门禁」。初版计划想用「autotest 在 L2 模拟器测试钱包做注入验证」去**闭合**它，被 Codex plan-review 连续三轮盯出：① 模拟器≠真机，模拟器证据是「恢复逻辑正确」的**必要非充分**证据，不能闭合真机门禁；② 一度把「Crashlytics 线上归零」写成真机门禁的替代完成条件，与自己定的边界自相矛盾。
- **教训**：测试产出要**分层标注证据强度与门禁闭合态**，绝不虚假宣称闭合（L4 家族）。① 模拟器/单测/代码核验 = 证「路径正确」；② 真机放量门禁只由真机清单闭合；③ 线上归零只由出包后 Crashlytics 观测闭合——三者不互相替代。真机全锁屏+真资产+装旧包时，autotest 能贡献的就是模拟器层增量证据，**如实写「模拟器已验证恢复逻辑，真机硬门禁仍未闭合」**，把未闭合项完整登记 Accepted Risk（owner/reason/follow-up/expiry 四要素齐）。
- **配套**：安全域测试注入设施走同 UID instrumented harness（`am instrument`），**禁 adb 广播钩子**（无认证、外部可触发篡改钱包 key，plan-review C-95）；模拟器上的破坏性注入用 **AVD 整盘快照**做事务式兜底（磁盘级原子，胜过逐文件 rename 的混合快照风险）。
- **固化位置**：`2026-07-10-01` Accepted Plan v4（AR-1~AR-6）；注入 harness = debox `2026-07-10-06`。
- **日期**：2026-07-10
