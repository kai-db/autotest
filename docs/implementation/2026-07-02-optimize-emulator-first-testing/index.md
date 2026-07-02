# 优化测试规范：模拟器优先 + 最小人工介入

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-optimize-emulator-first-testing |
| Dir Path | docs/implementation/2026-07-02-optimize-emulator-first-testing/ |
| Files | index.md（本文件，canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | low（docs-only，测试规范文档改造，不动框架/被测代码） |
| Codex Calls | 2（plan-review R1 / impl-review R1，均 PASS） |
| High-Cost Modes Used | none |

## 任务背景

用户诉求（原话要点）：
1. 通读 `docs/testing/USAGE-使用教程.md`，看整套测试体系还有什么可优化。
2. **测试规范执行过程中尽量减少人工参与**。
3. 很多用例是否可以**直接用模拟器测**——现有两台模拟器：一台 root（`debox_root`）、一台非 root（`Pixel_10_Pro_XL`）。
4. 关键差异：**模拟器不会锁屏，真机会锁屏**（历史上安全锁屏多次打断自动化、需人工解锁）。

## 导航

- ① 计划 / 方案：[plan.md](./plan.md)
- ② 实现：[implementation.md](./implementation.md)
- ③ 过程 & 问题：[review.md](./review.md)
- 知识账本（两层）：全局 `lessons-global.md`（经 skill 解析，跨项目）+ 项目 `docs/lessons.md`（**本任务开始前已读两层**）

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS
- Latest summary: 测试规范已改造为 emulator-first：TEST_GUIDE.md 新增第七节（设备阶梯 L1/L2/L3 路由、锁屏保活、模拟器结论可信度+噪声预检、外部依赖自动降频轮询、人工介入白名单五项），配套 devices.md 知识库、TEST_CASES 设备列、USAGE 0.2 速查、CLAUDE.md 铁律 #4 细化 + #8 新增。Codex plan-review R1 与 impl-review R1 均 `VERDICT: PASS`（0 finding）。

## Final Outcome

Status: done
Summary: docs-only 规范改造完成并通过双 gate。核心收益：①默认执行环境改为不锁屏/不被抢占的模拟器（消除历史最大人工介入源——真机安全锁屏解锁）；②root 模拟器承接故障注入/双网切换（原「待 VPN-DNS 用户决策」类 ⏸️ 消除）；③登录态 snapshot 把验证码人工降为一次性；④外部依赖等待不再逐次请示；⑤人工介入收敛为白名单五项，白名单外人工 = 流程缺陷回写 lessons。
Follow-up: 下一次真实 run 按新规范执行并统计人工介入次数（预期常规轮次 0 人工）；若图灵盾/SDK 升级开始拦 root 模拟器，按 §7.1 降级回真机并回写 lessons。改动未 commit，待用户决定提交时机。
- Retro：无新增可泛化教训——规则本体已直接固化进 TEST_GUIDE 第七节 / devices.md（重复入账本反成噪音）；全局 G1（plan-review 声明 NO CODE WRITTEN YET）本次复用有效，无需修订。

## Completion Checklist

- [x] 任务开始前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`）。
- [x] Original request completed.
- [x] Plan review passed（见 plan.md，R1 PASS）。
- [x] Accepted Plan recorded（见 plan.md）。
- [x] Plan deviations recorded（见 implementation.md D1：app-knowledge/README.md 目录补行）。
- [x] Consult decisions recorded：N/A（见 implementation.md）。
- [x] No unresolved Critical / Important findings（见 review.md，两轮 0 finding）。
- [x] Relevant checks passed or explained（docs-only；交叉引用自检通过，见 review.md Verification）。
- [x] Docs updated.
- [x] No raw secrets added to code, logs, docs, PR comments, or commit messages.
- [x] **Retro：已评估，无新增账本条目（理由见 Final Outcome）。**
