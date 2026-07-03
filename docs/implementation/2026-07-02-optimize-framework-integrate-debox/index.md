# v1.8.0 后框架/文档/逻辑优化 + debox-android 接入

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-optimize-framework-integrate-debox |
| Dir Path | docs/implementation/2026-07-02-optimize-framework-integrate-debox/ |
| Files | index.md（本文件，canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 11:30 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | medium（本仓改动 low；debox-android 接入涉及被测钱包 App 工程配置，medium） |
| Codex Calls | 5（plan R1/R2/R3 FAIL → R4 PASS[用户批准的超上限确认轮] + impl-review R1 PASS） |
| High-Cost Modes Used | none |

## 任务背景

用户诉求：「本项目看看架构/文档/逻辑/代码还需要怎么优化，以及 debox-android 项目接入」。
承接 2026-07-02-audit-framework-architecture（P0×10/P1 六主题/P2×10）及其后 5 个实施任务
（v1.6.1→v1.8.0）。前序任务遗留两条已确认欠账：① 四批完成后「统一做 docs/ 目录文档对齐」
（用户 07-02 已确认，未做）；② debox 侧 1.7.x/1.8.0 接入回归（Interceptor/StepContext 破坏性
变更 + retriable 默认 false 等行为变化清单需 debox 同步）。本任务 = 新一轮 gap 审视 + 落地这两条。

## 导航

- ① 计划 / 方案：[plan.md](./plan.md)
- ② 实现：[implementation.md](./implementation.md)
- ③ 过程 & 问题：[review.md](./review.md)
- 知识账本（两层）：全局 `lessons-global.md`（经 skill 解析，跨项目）+ 项目 `docs/lessons.md`（**本任务开始前已读两层**）

## Final Status / VERDICT（唯一权威）

- Current Status: done（代码/文档收口；debox 端到端冒烟待外部环境补跑）
- Final VERDICT: PASS（plan R1-R3 FAIL→R4 PASS；impl R1 PASS 零 finding）
- Latest summary: 三工作流全部落地。**WS-A1** autotest v1.8.1：修 6 项审计遗漏静默缺口（C3 选择器不抛契约 / E1 生命周期 hook 留痕 / E2 after 异常保真 addSuppressed / E3 isInitialized 守卫 / E6 四处静默显式化 / Q4 REGEX 构造期 fail-fast+缓存），Q2/Q3/Q8 补正式延后记录消除"无人认领"；311 单测全过 + 护栏 + publishToMavenLocal；Codex impl-review PASS 零 Critical/Important。**WS-A2** docs 全对齐 1.8.1（CLAUDE.md/01/03/04/09/10/README/CHANGELOG，漂移逐条 grep 清零，docs/09 整篇修订）。**WS-B** debox 接入升 1.8.1：develop base + release base（用户指令新开 `kai/autotest-1.8.1-regression` 做发版门禁）接入均健康；**release base 真机端到端冒烟已跑绿**（S25 `DeBoxSmokeTest` OK (1 test)、5/5 步骤全过）——用户确认「IP 不在服务范围」弹窗无害后，`DeBoxBaseTest` 加关无害弹窗前置，preflight 命中登录，端到端闭环。

## Final Outcome

Status: done（含 debox 真机端到端跑绿）
Summary: 用户诉求「框架/文档/逻辑/代码优化 + debox 接入」全部交付。框架侧 v1.8.1 清扫审计遗漏缺口并双 gate 通过；文档层消除全部 v1.8 漂移；debox 接入在 develop/release 两 base 验证健康，**release 发版线真机端到端冒烟 `DeBoxSmokeTest` OK (1 test) 5/5 全过**（加关无害地域弹窗前置后 preflight 命中登录）。全链闭环，代码/接入侧无缺陷。
Follow-up:
1. **端到端已跑绿**（release base，S25，2026-07-03 10:57）：`DeBoxSmokeTest` 5/5 全过。Codex 两条 Verification Gaps（C3 后置过滤 + LAUNCH_APP waitForApp）中，chip 交互步骤（自愈定位点『群组』→ 复位）已在真机走通；LAUNCH_APP waitForApp 走 launchAppAndDismissDialogs 亦真机验证。develop base 端到端可按需另跑（机制同 release，已闭环）。
2. **两仓改动提交时机**（用户决定）：autotest 仓 v1.8.1 代码+docs（`debox` 分支，未提交）；debox 仓 `kai/autotest-1.8.1-regression`（release base，未提交）。develop base 原改动存 `git stash@{0}` + scratchpad 备份。
3. **候选 lesson（待用户/Codex 确认后入账本，核心原则 0.2 不擅自写入）**：
   - 项目（autotest）候选：「审计提案项必须要么实施要么显式记延后——既未实施也无延后记录 = 无人认领的静默缺口，会在后续审视才被发现」（本次 C3/E/Q 即此类）。
   - 项目（debox）候选：「登录 preflight 前应先关业务级 modal（如『IP 不在服务范围』地域弹窗）——modal 遮挡时背后 chip visibleToUser=false，waitForText 找不到会把已登录误判为未登录」。
   - 全局候选：「复用固定输出路径的 APK（`apks/debox-debug.apk`）跨 base 构建时，陈旧同名产物会让 packageDebug 抛 `Zip already contains entry ... cannot overwrite`——换分支/换 base 构建前先清该产物」。

## Completion Checklist

- [x] 任务开始前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`）。
- [x] Original request completed（框架/文档/逻辑优化 + debox 接入，三工作流交付）。
- [x] Plan review passed（plan R4 PASS，见 plan.md）。
- [x] Accepted Plan recorded（plan.md v4 Accepted Plan Summary）。
- [x] Plan deviations recorded（implementation.md：debox 任务目录 NN 命名 + B4 设备 L1→L3，均在 plan 授权内）。
- [x] Consult decisions recorded：N/A（无 mid-impl consult）。
- [x] No unresolved Critical / Important findings（impl-review R1 PASS 零 finding）。
- [x] Relevant checks passed or explained（311 单测 + 护栏 + 两 base 构建；端到端受外部环境阻断已解释）。
- [x] Docs updated（WS-A2 全对齐 + 三仓任务记录）。
- [x] No raw secrets added（debox local.properties 敏感项未打印/未入库）。
- [x] **Retro 已评估：3 条候选 lesson 待用户/Codex 确认后分流入账本（见 Follow-up 3；核心原则 0.2 不擅自写入）。**
