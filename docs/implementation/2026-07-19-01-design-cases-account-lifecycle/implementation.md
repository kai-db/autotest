# Implementation — <Task Title>

> ② 实现。当前状态以 `index.md` 为准。严格按 `plan.md` 的 Accepted Plan 实现。
> **不复述 plan**：本文件只记 ①与基线的差异 / 补充发现 ②consult ③做了什么的最小可追溯日志。

## Implementation Log

| Time | Action | Files / Areas | Notes（差异/发现，照计划无事写 per plan） |
| --- | --- | --- | --- |
|  |  |  |  |

## Plan Deviation Log

（无偏离写 None）

| Time | Deviation | Reason | Consulted Codex | User Confirmed |
| --- | --- | --- | --- | --- |

## Consult Log

（无 consult 写 None）

> **两种 Mode 记录格式不同，不要混用**（`RC-consult-preference-contract-conflict`）：
> - `peer-consensus`（承诺—揭示）→ 用下面的「格式 A」。**`adl-consult.sh` 两种模式都承载**（2026-07-19-01 落地）。
> - `lead-advisor`（顾问式，Claude 保留决定权）→ 用「格式 B」，**没有**互盲承诺 / `ALIGNMENT` / 复议 / 死锁裁决，
>   且请求中**必须**写明 Claude 倾向（这正是与 peer-consensus 相反的要求：后者严禁倾向进入请求）。
>
> **审计字段归属 = 随实际执行的 attempt 落位**（consult `RD-01` 定案）：走 `lead-advisor` 落格式 B；
> 升级为 `peer-consensus` **必须生成新 `ATTEMPT_ID`** 并在新请求件中重新声明，落格式 A。
> 旧格式 B 只保留**已实际发生**的 advisory attempt，不迁移、不覆盖，也不作为升级后 peer attempt 的权威来源。
> **未真正执行过 `lead-advisor` 就直接选 peer-consensus → 只产生格式 A，不得为留痕制造虚构的格式 B 记录。**
>
> `Recommendation`（结论）与 `Reasoning`（理由）**必须分立**——合并为单一「建议」字段时机械层只能验非空，
> 「有结论无理由」的空壳响应会被判成功。字段语法以主协议 **canonical Advisory grammar v1** 为唯一权威，
> 本模板是其派生物；两者不一致时按协议第 0 节次序**先改协议**。

**格式 A — peer-consensus**

### Consult 1 — YYYY-MM-DD HH:MM

Mode: peer-consensus · ATTEMPT_ID: / request sha256:

**模式选择人**（必填）: / **模式选择理由**（必填、非空，写明**本 attempt** 为何用该模式）:

议题: / 选项集（OPT-A / OPT-B …）: / BALLOT_COMPLETENESS（已知未列入的候选项及排除理由）:

Claude 承诺: CHOICE / RISK_DOMAIN / RISK_CLAUSE_IDS / sha256

Codex 承诺: CHOICE / RISK_DOMAIN / RISK_CLAUSE_IDS / BALLOT

ALIGNMENT（**编排器派生，双方不得自报**）: AGREE | DISAGREE | META_DISAGREE | UNDECIDABLE

复议（如有）: 双方 POSITION / BASIS / REVISED_CHOICE

裁决档位（如进入死锁裁决）: ① 验证优先 / ② HIGH 域 / ③ NORMAL 域 / ④ 元分歧

**格式 B — lead-advisor**

### Consult 1 — YYYY-MM-DD HH:MM

Mode: lead-advisor · ATTEMPT_ID:

**模式选择人**（必填）: / **模式选择理由**（必填、非空，写明**本 attempt** 为何用该模式）:

议题: / 背景:

Claude 倾向（**本模式必填**，随请求发出）:

BALLOT_COMPLETENESS（**本模式同样必填**，留空即拒）:

Codex `Recommendation`（结论）:

Codex `Reasoning`（理由，编号列表 ≥1 条）:

Claude 采纳与否 + 理由（不采纳也要写明）:

Decision: / 是否问过用户: / Follow-up:
