# ② 实现

## Implementation Log

（待 Accepted Plan 落定后开始。）

## Deviation Log

- （无）

## Consult Log

- （无）

### 2026-07-02 按 Accepted Plan v2 实施

**新增**
- `util/AtomicFileWriter`：temp+rename 原子写；rename 失败保留旧文件 + 临时转 .failed + logger.e + 返回 false（fail-closed，B2）。
- `LocatorLevel.AMBIGUOUS`（C5）；`SelfHealingLocator.disambiguate`（internal 纯函数，C5 消歧）；`PROVISIONAL_CEILING=0.85`/`PROVISIONAL_TTL_MS=24h`（C1）。

**B1 递归 validator**
- `ElementSnapshot.isStructurallyValid()`（6 String 非 null）；`CachedCase.validationError()/CachedStep.validationError()`（caseId 正则+steps 非空+按 action 必需 payload/target）。
- CaseCacheStore.load 过 validator（坏数据按未命中）；FingerprintStore.load/TestHistoryStore.load 逐条过 validator 跳过坏条目。History outcomes 转 `List<Boolean?>` 判 null（避免拆箱 NPE）。

**B2 原子写**：FingerprintStore/TestHistoryStore/CaseCacheStore save 改走 AtomicFileWriter。

**B3**：TestArtifacts.cleanup 增 protectedNames；BaseUiTest cleanup 传 `setOf("fingerprints.json")`。

**C1 provisional**：ElementFingerprint 增 provisional 字段；L1 record 权威、L2/L3 recordProvisional（权威不被降级覆盖、provisional 永不自动转正）；heal 对 provisional 基线 confidence 封顶 PROVISIONAL_CEILING + TTL 过期不作基线。

**C2**：SelfHealingLocator heal 前 filterByPackage（指纹 packageName 非空时只留同包候选）。

**C4**：SelectorSpec.key 长度前缀编码 `attr:mode:len:value`。

**C5**：findBySnapshot 用 disambiguate；无法唯一确定 → 上报 AMBIGUOUS + 返回 null（fail-closed，不点第一个）。

**版本**：1.7.0 → 1.7.1。

### 验证结果

- JVM 单测 **280 全过**（+24：Disambiguate 5 / AtomicFileWriter 3 / CachedCaseValidation 7 / Fingerprint provisional+validator 6 / History null 元素 1 / Artifacts protected 1 / SelectorSpec 含分隔符 key 1 等）。
- publishToMavenLocal 1.7.1 成功。
- instrumented 回归（L1 模拟器）：ProductDir + EnvApplyTo×2 + SafetyGate×4 = **OK(7)** 无回归。

### Deviation Log

- **D1（测试前提修正）**：ElementSnapshot 全默认参数，Gson 用默认构造填 `""` 而非 null——「缺字段」测试改用显式 null JSON 才真正触发 validator。
- **D2（实现修正，测试暴露）**：History outcomes 含 null 校验 `it == null`（it: Boolean）会拆箱 NPE，改 `(outcomes as List<Boolean?>).any{it==null}`——单测先暴露、当轮修掉。

### Consult Log

- N/A。
