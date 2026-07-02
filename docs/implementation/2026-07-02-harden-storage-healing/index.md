# 第四批（末批）：存储信任边界 + 自愈链正确性（B + C）

> **canonical 入口**。当前 Status / final VERDICT / latest summary **只在本文件权威**。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-02-harden-storage-healing |
| Dir Path | docs/implementation/2026-07-02-harden-storage-healing/ |
| Files | index.md（canonical）/ plan.md / implementation.md / review.md |
| Created At | 2026-07-02 |
| Status | done |
| Owner | Claude Code |
| Reviewer | Codex |
| Risk Level | medium（存储格式/key 编码变化 + 自愈写入语义调整） |
| Codex Calls | 4（plan R1/R2 FAIL/R3 PASS + impl R1 PASS）|
| 上游 | audit Accepted Plan v2 第四批（末批） |

## 导航

- ① 计划：[plan.md](./plan.md) ② 实现：[implementation.md](./implementation.md) ③ 过程：[review.md](./review.md)
- 账本：本 session 已读两层 + G4/L-002。

## Final Status / VERDICT（唯一权威）

- Current Status: done
- Final VERDICT: PASS（plan R3 PASS；impl R1 一次 PASS）
- Latest summary: 存储信任边界 + 自愈链正确性加固完成。B1 递归 validator 挡 Gson Unsafe 坏数据；B2 AtomicFileWriter fail-closed 防静默清库；B3 指纹库护出清理池；C1 provisional 禁自动转正+封顶 0.85+TTL；C2 heal 按 package 过滤；C4 key 长度前缀防碰撞；C5 多命中真消歧 fail-closed 不点第一个。LIB_VERSION 1.7.1，JVM 280 全过 + androidTest 回归 OK(7)。

## Final Outcome

Status: done
Summary: 末批通过（plan 3 轮 / impl 1 轮）。四批全部完成——审计发现的 P0/P1 与关键 P2 已逐批修复并各自 Codex 双 gate。
Follow-up: 完整 DeviceGateway（heal 链全 JVM 可测）+ cleanup 按 run 子目录轮换 + debox 1.7.x 接入回归。**四批全完成，下一步：统一优化 docs/ 目录文档（用户已确认）。**

## Completion Checklist

- [x] 任务开始前已读两层账本。
- [x] Original request completed（B1/B2/B3 + C1/C2/C4/C5）。
- [x] Plan review passed（R3 PASS）。
- [x] Accepted Plan recorded（v2）。
- [x] Plan deviations recorded（implementation.md D1/D2）。
- [x] Consult decisions recorded：N/A。
- [x] No unresolved Critical / Important findings（impl R1 PASS）。
- [x] 全量单测（280）+ publishToMavenLocal（1.7.1）+ androidTest 回归 OK(7) 通过。
- [x] Docs updated（四件套）。
- [x] No raw secrets added.
- [x] Retro 已评估：无新增账本条目（存储/自愈坑属项目特化且已固化进代码+任务记录；G4 已录）。
