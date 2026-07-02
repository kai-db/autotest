# ① 计划 / 方案

## Plan Proposal

### 任务目标

实施审计提案第四批（末批）：**存储层信任边界 + 自愈链正确性**——
主题 B（B1 Gson 反序列化校验、B2 原子写防静默清零、B3 指纹库移出自动清理路径）、
主题 C（C1 自愈/AI 命中 provisional 不直接锁定、C2 heal 过滤 packageName、C4 SelectorSpec.key 转义防碰撞、C5 findBySnapshot 多命中消歧）。
配套纯逻辑 JVM 测试。发 1.7.1。

### 当前项目上下文（已精读源码）

- **B1**：`CaseCacheStore.load` / `FingerprintStore.load` / `TestHistoryStore.load` 用 `gson.fromJson`——无全默认参数的类走 Unsafe 实例化、`init{}` 校验不执行；`CachedCase.init` 的 caseId 正则（防文件名 `../` 逃逸）可被手编 JSON 绕过；缺字段时非空类型注入 null，NPE 炸在远端（CacheReplay 的 `case.steps.forEach` / `when(action)`、HealingEngine.score 读 `fingerprint.snapshot`）。
- **B2**：`FingerprintStore.save` / `TestHistoryStore.save` 直接 `file.writeText`（非原子）——进程被杀/超时 worker 写一半即损坏，load 端 catch 后按空库处理 = **静默清空全部历史/指纹资产**。
- **B3**：`fingerprints.json` 落在 `TestConfig.screenshotDir`（BaseUiTest.kt:69），而 setUp（BaseUiTest.kt:114）对同目录 `TestArtifacts.cleanup`（按 mtime 保留最近 100 文件）——截图多的一轮后下轮 setUp 可能把指纹库连同 report 一起清掉。
- **C1**：`SelfHealingLocator.tryFind` L2 自愈以 0.75 命中即 `store.record(key, result.candidate)`、L3 AI 命中同样 `store.record`——一次误愈把错误候选写成新指纹，下轮 heal 对错误元素打分 1.0，永久锁定且置信度满分，人审线索消失（LocatorEvent kdoc 说「确认后回填」，存储层先斩后奏）。
- **C2**：`HierarchyParser.parse` 返回整棵 window hierarchy（含系统 UI/输入法/悬浮窗），`HealingEngine.score` 有 `packageName` 字段却不比对——文本相近的系统弹窗按钮可能拿 ≥0.75 被点击。
- **C4**：`SelectorSpec.key()`（SelectorSpec.kt:61）value 含 `,`/`(`/`)` 时键碰撞——`Or(Leaf("a,text:exact:b"),...)` 与其它写法产生同键，指纹库/缓存唯一索引互相污染。
- **C5**：`findBySnapshot`（SelfHealingLocator.kt:136）多命中且 bounds 失配时 `matches.firstOrNull()` 静默任取第一个（列表项必踩），无 event/日志。

### 约束

- 不改被测 debox；不动前三批已交付；全量单测过 + publishToMavenLocal 成功；LIB_VERSION 1.7.0 → **1.7.1**。
- 兼容性：唯一接入方 debox；这些多为内部健壮性加固，公开 API 破坏面小（列清单）。

### 非目标

- **Q1 完整 DeviceGateway 抽象**（让整条 heal 链 JVM 可测）——UiObject2 是 Android final 类，全链 JVM 测需重度 mock，成本高、边际安全有限；本批改为**抽取可 JVM 测的纯决策逻辑**（package 过滤 / 多命中消歧 / key 转义 / 反序列化校验），完整 DeviceGateway 列为 follow-up。
- 不改 LogcatInterceptor 的 `logcat -c`（审计 D3）、不改计时时钟（Q3）——非本批范围。

### 建议方案

**B1 Gson 反序列化校验【R1 修订：递归 validator，只放结构完整数据进内存】**
- 新增显式递归 validator，覆盖 Gson Unsafe 可注入 null 的**全部嵌套非空字段 + action 相关必需字段**：
  - `ElementSnapshot`：6 个 String 字段非 null。
  - `CachedStep`：`name`/`action` 非 null；`target`（若非空）过 ElementSnapshot validator；按 action 校验必需 payload——`INPUT_TEXT`/`WAIT_TEXT` 必须有 `payload`，`CLICK`/`LONG_CLICK`/`ASSERT_VISIBLE` 必须有可定位 `target`。
  - `CachedCase`：caseId 正则；`steps` 非 null 非空；每个 step 过 CachedStep validator。
  - `ElementFingerprint`：`selectorKey` 非空白；`snapshot` 非 null 且过 ElementSnapshot validator。
  - `CaseHistory`：`outcomes` 非 null **且不含 null 元素**（`List<Boolean>` 在 Gson 下元素是 boxed `Boolean`，手写 JSON 可注入 `[true,null]` → 后续 `count{o->o}` NPE）；含 null 元素的条目视为损坏跳过。
- `CaseCacheStore.load` 反序列化后过 CachedCase validator，失败 → logger.w + 返回 null（既有损坏路径）；`FingerprintStore.load`/`TestHistoryStore.load` 逐条过 validator，坏条目 logger.w 跳过、只把结构完整条目放进内存索引。

**B2 原子写（temp + rename，rename 失败绝不直写覆盖旧库）【R1 修订：fail-closed】**
- 新增 `util/AtomicFileWriter.writeText(file, text): Boolean`：写临时文件 `<file>.tmp.<nanoOrSeq>` → `renameTo(file)`（同目录 rename，多数文件系统原子）。**rename 失败：保留旧目标文件不动 + 保留/改名临时文件为 `<file>.failed` 便于排查 + `logger.e` 显式报错 + 返回 false**——绝不回退直写覆盖目标（避免半截 JSON 覆盖 → load 端静默清零）。
- `FingerprintStore.save` / `TestHistoryStore.save` / `CaseCacheStore.save` 改走它，save 返回值上抛/留痕（写失败可见）。

**B3 指纹库移出自动清理路径**
- `TestArtifacts.cleanup(dir, keepRecent, protectedNames: Set<String> = emptySet())`：`protectedNames`（文件名）永不删除。
- `BaseUiTest.setUp`：cleanup 传 `protectedNames = setOf("fingerprints.json")`（指纹库是跨 run 持久资产，不能与易腐截图同池竞争）。report_*.json 引用的截图被按数量清掉是更大结构问题——本批先护住指纹库，截图-report 一致性列 follow-up（cleanup 按 run 子目录轮换）。

**C1 自愈/AI 命中 provisional，禁止自动转正 + provisional 基线置信度封顶【R1 修订】**
- `ElementFingerprint` 增 `provisional: Boolean = false`。
- **写入规则（禁止自动转正）**：
  - L1 确定性 BySelector 命中 → `store.record(...)` 写**权威**指纹（provisional=false）——这是「原始 selector 真的匹配到元素」的唯一可信转正来源。
  - L2 自愈 / L3 AI 命中 → `store.recordProvisional(...)` 写 provisional=true；**若已有权威指纹则不降级覆盖**（权威 > provisional）；provisional 只覆盖 provisional。
  - **provisional 永不因「自身快照又被命中」而自动转正**——只有 L1 原始 selector 确定性命中才转正。
- **打分封顶 + 过期（打破「一次误愈永久满分锁定」）**：
  - `HealingEngine.heal` 对 provisional 基线的最终 confidence **封顶 `PROVISIONAL_CEILING`（如 0.85）**——即便当前屏幕候选与 provisional 快照 exact match（原始 score=1.0），也不给满分、始终留在「需人审」区间，且**每次仍上报 LocatorEvent**。
  - provisional 指纹带 **TTL**（如 lastSeenMs + 24h 过期）：过期 provisional 不作 heal 基线（避免陈旧误愈长期驻留）。
- 效果：误愈写的是**封顶且会过期的 provisional**，永不满分、永不自动转正、每次命中都上报人审；只有原始 selector 恢复可用（L1 命中）才转正为权威。

**C2 heal 过滤 packageName**
- `SelfHealingLocator.tryFind`：heal 前把候选按「指纹 packageName（非空时）」过滤——只在被测 App 自己的元素里自愈，排除系统 UI/输入法（`HierarchyParser` 已带 packageName 字段）。指纹 packageName 为空时不过滤（向后兼容旧指纹）。

**C4 SelectorSpec.key 转义**
- `Leaf.key()`：对 `value` 做转义（`\` → `\\`、`,` → `\,`、`(` → `\(`、`)` → `\)`）或改用长度前缀编码 `text:exact:<len>:<value>`——保证不同写法产生不同键，消除指纹/缓存索引碰撞。选**长度前缀**（简单无歧义）：`"${attr}:${mode}:${value.length}:${value}"`。

**C5 findBySnapshot 多命中真消歧 + fail-closed【R1 修订】**
- 消歧顺序（钱包场景宁失败不点错列表项）：
  1. bounds 精确匹配 → 用它（唯一确定）。
  2. bounds 失配但**候选唯一** → 用它。
  3. bounds 失配且多命中 → **用 snapshot 全属性（res-id ∧ text ∧ desc ∧ bounds 近邻）收窄**；收窄到唯一 → 用它。
  4. 仍无法唯一确定 → **fail-closed：上报 `LocatorEvent`（AMBIGUOUS，注明多命中 N、无法消歧）+ 返回 null**（定位失败，交给 find() 抛/降级）——**绝不默认点第一个**。
- 新增 `LocatorLevel.AMBIGUOUS`（或 event 上带 ambiguous 标记）；上报即留痕，不静默。

### 接口/行为变化清单（供 debox 回归）

1. `ElementFingerprint` 增 `provisional` 字段（Gson 兼容默认 false，旧指纹文件可读）。
2. `SelectorSpec.key()` 编码变化——**旧指纹/缓存 key 失效**（key 变了查不到旧条目）：属一次性重探索代价（指纹库会重新积累），CI READ_ONLY 缓存的 caseId 文件名不受影响（caseId 不走 key），但缓存内 target 的 selectorSpec 回放走 toSelectorSpec 现算、不依赖持久 key，无碍。
3. `TestArtifacts.cleanup` 增 `protectedNames` 可选参数（默认空，向后兼容）。
4. 自愈/AI 命中写 provisional 指纹（行为更保守，不影响可用性）。

### 可选方案

- C4 用「转义」vs「长度前缀」——选长度前缀（无需转义规则、解析无歧义）。
- C5【R1 修订】改为「真消歧 + 无法确定则 fail-closed」（不再取第一个）——钱包场景宁失败不点错。
- Q1 完整 DeviceGateway——本批降级为纯逻辑抽取 + 测试（见非目标）。

### 实现步骤

1. B1：CachedCase.validate + 三个 store.load 加校验/过滤。
2. B2：AtomicFileWriter + 三个 store.save 改用。
3. B3：TestArtifacts.cleanup protectedNames + BaseUiTest 传参。
4. C1：ElementFingerprint.provisional + FingerprintStore.recordProvisional + SelfHealingLocator L2/L3 改 provisional。
5. C2：SelfHealingLocator heal 前 package 过滤。
6. C4：SelectorSpec.key 长度前缀编码。
7. C5：findBySnapshot 多命中上报 event。
8. 纯逻辑 JVM 测（key 唯一性/转义、反序列化坏数据过滤、原子写、cleanup 保护、heal package 过滤纯函数、provisional 语义）+ 更新受影响测试（SelectorSpecTest/FingerprintStoreTest/ToSelectorSpecTest 等）。
9. LIB_VERSION 1.7.1；全量 test + publishToMavenLocal。
10. Codex impl review → 修 → 回写。

### 验证方式

- 全量 JVM 单测通过（B/C 的存储与决策逻辑多为纯 JVM，可测）。
- SelfHealingLocator 触设备的部分（tryFind 编排 + findBySnapshot 的 UiObject2 交互）：把 package 过滤/多命中判定的**纯决策函数**抽出单测，编排整体靠既有 androidTest 冒烟间接覆盖（DeBoxCacheReplayTest）。

### 风险

1. C4 key 编码变化使旧指纹库 key 失效——一次性重积累，无功能损失（自愈降级链有 L1 兜底）；列清单。
2. B1 校验可能把「宽松但可用」的旧缓存判为损坏——校验只查结构性必需字段（caseId 格式 / 非 null），不收紧语义，误杀面小。
3. C2 package 过滤对「指纹无 packageName」的旧数据不过滤，保持兼容；新指纹带 package。

### 需要用户确认

- 无（末批，范围已确认）。debox 侧 1.7.1 接入回归随第三批 follow-up 一并另约。

---

## Accepted Plan

Version: v2（R1 三 Critical + R2 一 Important 修订后）
Accepted At: 2026-07-02
Accepted By:
- Claude Code: 是
- Codex: plan-review R3 `VERDICT: PASS`（R1 FAIL → R2 FAIL → R3 PASS）
- User: 末批范围已确认
Decision: PASS
Scope: B1 递归 validator / B2 原子写 fail-closed / B3 指纹库护住 / C1 provisional 禁自动转正+封顶+TTL / C2 package 过滤 / C4 key 长度前缀 / C5 真消歧 fail-closed。LIB_VERSION 1.7.1。
Verification: 全量 JVM 单测（含坏数据反序列化拒绝/原子写 fail-closed/key 唯一/provisional 状态转移/多命中 fail-closed/package 过滤纯函数）+ publishToMavenLocal + 既有 androidTest 冒烟无回归。
Accepted Risks: 无。
Deviation Policy: 触碰 provisional 转正规则、C5 fail-closed 语义、原子写 fail 行为、validator 收紧面的偏离先 consult Codex。
Follow-up: 完整 DeviceGateway（heal 链全 JVM 可测）+ cleanup 按 run 子目录轮换 + debox 1.7.x 接入回归。
