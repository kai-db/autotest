# 修 cache caseId 全局冲突 + 给 CachedCase 增加日志断言能力

> **canonical 入口**（T2 标准任务）。当前 Status / final VERDICT / latest summary **只在本文件权威**；
> 过程细节见 `plan.md`（①方案）/ `implementation.md`（②实现）/ `review.md`（③过程&问题）。
> 反杂硬规则（0.1.3）：不复述、定稿无空壳（用不上的节直接删）、不 commit 二进制。

## Metadata

| Field | Value |
| --- | --- |
| Task ID | 2026-07-18-01-fix-case-cache-key-and-log-assert |
| Tier | T2 |
| Created / Last Active | 2026-07-18 / 2026-07-18 |
| Status | **PAUSED（用户主动暂缓，未进入 plan）** |
| Risk Level | medium（已发布 AAR 的公开契约变更 + 缓存存储格式变更） |
| Codex Calls · High-Cost Modes | 0 · none |
| History | 首轮，None |

## 导航

- ① 计划 / 方案：[plan.md](./plan.md) · ② 实现：[implementation.md](./implementation.md) · ③ 过程 & 问题：[review.md](./review.md)
- 开工前已读两层账本（全局 `lessons-global.md` + 项目 `docs/lessons.md`），相关条目：
  **L1**（改约定/路径/术语前先宽 grep 全仓——caseId 命名方案变更必须全仓核查）、
  **L2**（一个 finding 先查所有同类文件）、
  **L12**（纯 mock/单测过 ≠ 能跑，需真实回放冒烟）、
  **L24**（对外发布的 Kotlin 库，internal 测试钩子要加 `@JvmSynthetic`）、
  **L33**（macOS 无 `timeout`——本 session 已实际踩到）

## Final Status / VERDICT / Outcome（唯一权威）

- **Current Status: PAUSED（暂缓，非阻塞、非卡住）**
- Final VERDICT: —（未开始评审）
- **暂缓原因**：用户决定等 debox 侧改动（分支 `feat/btc-derive-sign-native`：BTC 签名链路
  由 WebView/JS 换 bitcoin-kmp 原生 + 登录链三处修复 + `AppCacheManager`/`LoginAttemptCoordinator`
  未提交改动）落定后再动框架。理由充分：本任务的目的是让确定性用例能沉淀为 cache 回放，
  而**用例本身正因 debox 改动要重写**，先改框架会对着移动靶设计接口。
- **恢复触发条件（满足任一即可重启）**：
  1. debox `feat/btc-derive-sign-native` 改动落定（无未提交改动 / 合入 dev）；
  2. 新一轮 cases.md 定稿，明确了哪些用例需要日志断言、断言形态是什么；
  3. 用户主动要求继续。
- Follow-up：恢复时**从 plan 阶段重新开始**，本目录当前无任何设计结论可复用。

## 待解决问题（已实证，供恢复时直接用）

### 问题 1：cache caseId 全局冲突（回放会跑错用例）

- `CaseCacheStore.fileOf()`（`autotest/src/main/java/com/autotest/bridge/CaseCacheStore.kt:63`）
  按 `dir.resolve("$caseId.cache.json")` **全局平铺命名**，而 caseId 由各 run 的 `cases.md` 自行编号。
- **实证冲突**：`docs/testing/cache/TC-S-001.cache.json` = 「冷启动停在消息页」（旧 run 的 UI 形态用例），
  而 `runs/2026-07-18-登录单飞与实例释放回归/cases.md` 的 `TC-S-001` = 「助记词钱包正常登录」。
  同名不同案 → 回放 `TC-S-001` 会执行另一个 run 的用例。
- 候选方向（未评审，恢复时重新设计）：cache key 加 run 命名空间 / 用 `runId+caseId` 复合键 /
  在 `CachedCase` 增加 `runId` 字段并在 load 时校验。**需一并考虑既有 3 个 cache 文件的迁移与兼容。**

### 问题 2：`CachedCase` 无日志断言能力

- `CachedActionType`（`autotest/src/main/java/com/autotest/bridge/CachedCase.kt:9-41`）共 11 个动作，
  断言类只有 `ASSERT_VISIBLE` / `WAIT_TEXT`，**纯 UI 断言**。
- 后果：判据形如「恰一次 `app_login ok`」「`WS onOpen` 恰 1 次且 `onClose` 0 次」「无 FATAL/ANR」
  的用例**无法沉淀为 cache**，只能留在 MCP 慢路径 → 这是 §5.10「AAR 分层回归」落不了地的直接原因，
  也是 L-008（轮数与产能不匹配）的技术根因。
- 候选方向（未评审）：新增 `ASSERT_LOG` / `ASSERT_LOG_COUNT`（含比较符与期望值）动作类型，
  复用 `diagnosis/LogcatAnalyzer`。**注意这是已发布 AAR 的公开 enum 变更**，需评估向后兼容
  （旧 cache JSON 反序列化、消费方 when 穷尽性）。

> ⚠️ 以上仅为**现状取证**，不构成设计结论。恢复时须走完整 plan → Codex plan review 流程。
