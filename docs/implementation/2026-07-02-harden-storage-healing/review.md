# ③ 过程 & 问题

## Plan Review Rounds

### Plan Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**（3 Critical + 1 Important）
  - C1[Design 95]：provisional 仍可被自身快照命中自动转正/满分复用，未真正打破「一次误愈永久锁定」。
  - C5[Plan alignment 92]：「上报+取第一个」不是消歧，钱包仍可能点错列表项。
  - B2[Bugs 91]：rename 失败回退直写会半截 JSON 覆盖旧库 → load 空库路径。
  - I-B1[Bugs 86]：校验粒度不足，未覆盖嵌套非空字段 + action 必需 payload。
- 处理（Plan Revision v2，全部采纳）：
  1. C1：provisional 永不因自身快照命中转正（只 L1 原始 selector 确定性命中转正）；heal 对 provisional 基线 confidence 封顶 PROVISIONAL_CEILING(0.85) 不给满分 + provisional TTL 过期；权威不被 provisional 降级覆盖。
  2. C5：真消歧（bounds 精确→唯一→全属性收窄），无法唯一确定则 fail-closed 返回 null + 上报 AMBIGUOUS，绝不取第一个。
  3. B2：rename 失败保留旧文件不动 + 临时文件转 .failed + logger.e + 返回 false，绝不直写覆盖。
  4. B1：递归 validator 覆盖 ElementSnapshot/CachedStep(含 action 必需 payload)/CachedCase/ElementFingerprint/CaseHistory，只放结构完整数据进内存。

### Plan Review R2 — 2026-07-02

- 结果：**VERDICT: FAIL**（Critical None；1 Important）：CaseHistory.outcomes 元素在 Gson 下是 boxed Boolean，可注入 [true,null] → NPE。
- 处理：CaseHistory validator 改为 outcomes 非 null 且不含 null 元素，含 null 跳过 + 补 JVM 测。

### Plan Review R3 — 2026-07-02

- 结果：**VERDICT: PASS**（全 None）→ 落 Accepted Plan v2，进入实现。

## Implementation Review Rounds

### Impl Review R1 — 2026-07-02

- 结果：**VERDICT: PASS**（Critical/Important/Accepted Risk/Verification Gaps 全 None）——一次通过。
- 说明：实现前经 3 轮 plan review 把 provisional/fail-closed/原子写/validator 语义收紧到位；实现中 2 处边界（ElementSnapshot 全默认 Gson 填默认、History outcomes 拆箱 NPE）由单测先暴露、当轮修掉，未遗留到 review。

## Review Fix Log

（待记录。）

## Accepted Risk

- 无。

## Verification

- JVM 单测 280 全过（+24 末批回归）；publishToMavenLocal 1.7.1 成功。
- instrumented（L1 模拟器）回归 OK(7)：ProductDir + EnvApplyTo×2 + SafetyGate×4，无回归。
- Follow-up：完整 DeviceGateway（heal 链全 JVM 可测）+ cleanup 按 run 子目录轮换（截图-report 一致性）+ debox 1.7.x 接入回归。
