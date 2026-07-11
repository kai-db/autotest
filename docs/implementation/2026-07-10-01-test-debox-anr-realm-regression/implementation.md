# Implementation — debox dev 07ae7655..HEAD 深度分析 + 深度回归测试

> ② 实现。当前状态以 `index.md` 为准。严格按 `plan.md` 的 Accepted Plan 实现。
> **不复述 plan**：本文件只记 ①与基线的差异 / 补充发现 ②consult ③做了什么的最小可追溯日志。

## Phase A · 独立核码报告（2026-07-10，任务核心产物之一）

### A-1 提交范围与构成

`07ae7655~1..dev(8960bb29)` 共 5 提交：3 代码（07ae7655 IM ANR / fc77707a PicSel 埋点 / 75d44b39 Realm 恢复）+ 2 文档（debox 任务档案）。

### A-2 逐提交「声称 vs 实码」对照结论

| 提交 | 核查方式 | 结论 |
|---|---|---|
| `75d44b39` Realm 恢复 | 本席亲核 4 文件全量 diff | ✅ 与 debox Accepted Plan v5 一致：obtainConfiguration 原子快照、候选 probe 直发布（无二次打开）、①发布→②回填→③备份清理且②③非阻断、非 ACCESS_ERROR/构建异常携真实 cause 走地板、全败 fail-closed 不 mint。备份 key 常量一致性已验证（put fallback 写 `${key}_backup`，恢复读 `DB_NAME+"_backup"`，同为 `leek_box_name_lt_backup`） |
| `fc77707a` PicSel 埋点 | 本席亲核 2 文件 diff | ✅ 纯观测零行为改动；面包屑仅枚举/时长/布尔，无媒体路径/PII；lifecycle 回调主线程串行，计时字段无并发问题 |
| `07ae7655` IM ANR | 只读 agent 独立核查（diff + 现源 + 直接调用方） | ✅ P1'–P5' + ImSessionEpoch 全部与描述一致；四个高危实现点（epoch 校验在主线程执行时、putIfGeneration「同代且未挂起」双门禁、clearAndSuspend/Resume 单锁原子、CONNECTED 先 bump 再 resume）均正确落地 |

### A-3 独立发现的疑点（非本任务修复，供上报/立案）

| # | 等级建议 | 发现 | 处置建议 |
|---|---|---|---|
| F-1 | Important | **残留主线程同步查库**：`MessageViewModel.java:1167 onWarnClick`（点失败消息警告图标重发）仍调 `DBXJMessage.getLocalAttribute`→getSync，cache miss 时主线程查库——与被修四入口同源，本次漏网 | 报告用户；建议 debox 后续切 cached-only（低频入口，非阻塞本轮） |
| F-2 | Important | **suspend 无兜底恢复**：connect 发起/logout 均 clearAndSuspend，仅 CONNECTED 才 resume。connect 长挂 connecting（正是 ANR 的 navi 全挂场景）时缓存长期挂起：bind 恒降级 extra、getSync 读穿不缓存——功能可用但缓存在最需要的弱网场景失效 | 报告用户；建议 debox 评估 connect 失败/超时也 resume 或 suspend 加超时。组 I 增弱网观察点 |
| F-3 | ✅ 已关闭 | **stale→onError(-1) 上层语义**：代码级核毕——唯一业务调用方 `MessageViewModel.java:2400` 传 `callback=null`，`IMCenter.java:1092-1096` onError 仅在 callback 非空时透传 → 换号 stale 零用户可见报错，仅跳过 onClearedUnreadStatus 监听（正是防串号预期） | 无需测试动作 |
| F-4 | 观察项 | `searchMessages`（DBXJConversation:527，本次未动）调用方 SearchViewModel.java:78 未见线程切换，疑似同步查库残留 | 测搜索聊天记录场景顺带观察掉帧 |
| F-5 | 已知/解释 | localAttr 缓存仅 setMessageExtra 写穿（SDK 绕过则陈旧）= debox 档案已登记 Accepted Risk；warmUp 超 512 容量 LRU 淘汰属预期降级 | 组 I 观察头像/昵称顽固旧值即可 |

### A-4 行为回归观察点（agent 报告 R1–R10 → cases.md 用例判据）

R1 清未读徽标最终一致性 / R2 换号竞态不误清 / R3 cached-only miss 首帧降级值最终刷新 / R4 localAttr 顽固旧值 / R5 置顶免打扰开关初值闪跳 / R6 换号读状态不串 / R7 空路径上传显式失败态（不崩、可重发、队列不卡）/ R8 搜索最近联系人异步返回正确 / R9 onWarnClick 连点无卡顿 / R10 长挂 connecting 时 bind 降级可用。

## Implementation Log

| Time | Action | Files / Areas | Notes（差异/发现，照计划无事写 per plan） |
| --- | --- | --- | --- |
| 07-10 | Phase A 独立核码完成 | 本文件 A-1~A-4 | 三提交与档案声称一致；新发现 F-1/F-2（Important 级，非阻塞） |

## Plan Deviation Log

（无偏离写 None）

| Time | Deviation | Reason | Consulted Codex | User Confirmed |
| --- | --- | --- | --- | --- |

## Consult Log

（无 consult 写 None）
