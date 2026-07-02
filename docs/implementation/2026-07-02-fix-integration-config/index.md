# 第三批：集成坑 POM 根治 + 产物目录 + 配置收敛（P0-9 + A + F）

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-fix-integration-config |
| Dir Path | docs/implementation/2026-07-02-fix-integration-config/ |
| Files | index.md（canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | medium（POM 依赖契约变更 + 产物目录 context 切换） |
| Codex Calls | 4（plan R1 FAIL/R2 PASS + impl R1 FAIL/R2 PASS）|
| 上游 | audit Accepted Plan v2 第三批 |

## 导航

- ① 计划：[plan.md](./plan.md) ② 实现：[implementation.md](./implementation.md) ③ 过程：[review.md](./review.md)
- 账本：本 session 已读两层 + G4/L-002；G3 集成坑教训本批正是框架侧根治对象。

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS（plan R2 PASS；impl R2 PASS）
- Latest summary: 集成坑框架侧根治完成。P0-9 产物目录改 targetContext + 去 /sdcard/Pictures 硬编码 + mkdirs 告警；A1 hamcrest 显式 api、A2 espresso-core exclude protobuf-lite（均进 POM）、A3 waitForIdleTimeout 配置化 + tearDown 恢复、A4 withSourcesJar；F TestConfig.initWithEnv 打通 env 死层 + EnvironmentManager.applyTo（幂等/覆盖顺序）。LIB_VERSION 1.7.0。JVM 256 全过 + POM 断言全过 + 3 config smoke + SafetyGate 无回归。

## Final Outcome

Status: done
Summary: 第三批通过双 gate（plan 2 轮 / impl 2 轮）。G3 记录的集成坑（protobuf-lite/hamcrest/产物目录 UID）在框架侧根治，以后任何项目接入 1.7.0 少踩 3 个坑。
Follow-up: debox 侧 1.7.0 接入回归（target=debox scoped storage 端到端 + dex 级正对照）需另约 debox 工作区；四批全完成后统一 docs 对齐。

## Completion Checklist

- [x] 任务开始前已读两层账本。
- [x] Original request completed（P0-9 + A + F）。
- [x] Plan review passed（R2 PASS）。
- [x] Accepted Plan recorded（v2）。
- [x] Plan deviations recorded（implementation.md D1 initWithEnv / D2 冒烟断言）。
- [x] Consult decisions recorded：N/A。
- [x] No unresolved Critical / Important findings（impl R2 PASS）。
- [x] 全量单测（256）+ publishToMavenLocal（1.7.0）+ POM 断言通过。
- [x] Docs updated（四件套 + CLAUDE.md config 行）。
- [x] No raw secrets added.
- [x] Retro 已评估：无新增账本条目（G3 集成坑本批已在框架侧根治，教训已在账本）。
