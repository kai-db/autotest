# ③ 过程 & 问题

## Plan Review Rounds

### Plan Review R1 — 2026-07-02

- 结果：**VERDICT: FAIL**
  - Critical [Security 92]：UNVERIFIABLE 目标「留痕不阻断」= fail-open → 改默认阻断 + 显式 `allowUnverifiable` 放行。
  - Critical [Standards 90]：`find()` 裸 UiObject2.click() 绕过只靠 KDoc → 需受控替代 API + 弃用检测。
  - Important [Regression 84]：`safety.dangerousTexts` 覆盖式配置易漏内置高危词 → 默认合并、替换需显式 opt-in。
  - Important [Test coverage 83]：P0-0 真实点击链路验证不能推迟到 Phase B → 本批加 hermetic instrumented 冒烟。
  - Question：权限 res-id 豁免需限定系统包名，防第三方复用 res-id 绕过。
- 处理（Plan Revision v2，全部采纳）：①UNVERIFIABLE fail-closed；②新增 `element(spec): GuardedElement` 受控 API + `find()` @Deprecated + 框架内部全迁移；③词表默认合并 + `safety.dangerousTextsReplace` opt-in；④新增 androidTest-only `GuardTestActivity` + `SafetyGateSmokeTest`（connectedAndroidTest 进本批准出条件）；⑤权限豁免 res-id+系统包名双条件。

### Plan Review R2 — 2026-07-02

- 结果：**VERDICT: FAIL**（Critical: None；Important ×1 [Design 88]：`allowUnverifiable` 未贯穿点击链路 API，目标与接口不闭环；Question：`safety.enabled=false` 需可审计留痕）
- 处理（Plan Revision v3）：①`allowUnverifiable=false` 参数贯穿 checkClick / SelfHealingLocator.click / GuardedElement / UiAutomatorExt / EspressoExt / UiReplayExecutor（CachedStep 可选字段），事件类型四分（DANGEROUS_BLOCKED/ALLOWED、UNVERIFIABLE_BLOCKED/ALLOWED），与 allowDangerous 语义严格区分；②`safety.enabled=false` 定位为调试/非钱包逃生口，关闭时 logger.w 显著告警 + GuardEvent(GUARD_DISABLED) 进报告，不允许静默关闭。

### Plan Review R3 — 2026-07-02

- 结果：**VERDICT: PASS**（Critical/Important/Questions/Required Changes 全 None）→ 落 Accepted Plan v3，进入实现。

## Implementation Review Rounds

### Impl Review R1 — 2026-07-02

- 评审对象：staged git diff（主源码 1160 行 + 测试 581 行）
- 结果：**VERDICT: FAIL**
  - Critical [Security/Plan alignment 92]：DialogDismissInterceptor 系统权限分支 `obj.click()` 绕过守卫、无审计事件——违反 P0-0「覆盖全部点击入口」。
  - Important [Plan alignment 84]：UiReplayExecutor.findTarget 对 `toSelectorSpec()==null` 的 target 先抛错，allowUnverifiable 未贯穿该分支、不产生 UNVERIFIABLE 事件。
  - Verification Gaps：缺 DialogDismiss 权限豁免路径测试、缺 UiReplayExecutor allowUnverifiable 回归。

### Impl Review R2 — 2026-07-02

- 结果：**VERDICT: PASS**（Codex 复审，见下方 Review Fix Log R1 的修复）

## Review Fix Log

### Round 1（修 Impl Review R1）

- **Critical 修复**：DialogDismiss 权限分支与 App 内弹窗分支统一走新 `guardedClick(obj)`（点击前 `GuardRegistry.current?.checkClick`）——权限按钮有 res-id、非危险词会干净通过并可审计；万一命中危险词则拦截。恢复「全部点击入口有守卫」不变量。
- **Important 修复**：确认 `toSelectorSpec()==null` 的条件（三属性全空）**等价于** `ClickTarget.isUnverifiable`——不可审计的缓存 target 根本产生不了选择器、定位不到，`require` 快速失败即 fail-closed（比守卫更早一道闸）；`allowUnverifiable` 作用于「已定位到、实时快照不可审计」的元素（GuardedElement.click 点击时判定，已接线）。加代码注释澄清 + 新增 `UnverifiableInvariantTest` 锁住该不变量。
- **Verification Gaps 补测**：新增 `DialogDismissWordlistTest`（默认词表不含确认语义 / 含 dismiss 语义 / 权限包名白名单仅系统控制器不含被测 App）；`UnverifiableInvariantTest`（全空快照 isUnverifiable ⇔ toSelectorSpec null）。
- 复验：JVM 单测 **227 全过**（+5）；`SafetyGateSmokeTest` 模拟器 **OK (4)** 无回归。

## Accepted Risk

（原候选「find() 裸 click 不经守卫」已被 R1 Critical 否决，v2 以 GuardedElement + @Deprecated 封死，不再是 accepted risk。当前无。）

## Verification

（待记录。）
