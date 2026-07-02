# ① 计划 / 方案

## Plan Proposal

### 任务目标

实施审计提案（`2026-07-02-audit-framework-architecture`，Accepted Plan v2）**第一批 P0-0~P0-5**：
钱包安全（危险操作点击网关）+ 4 条假绿路径修复 + DialogDismiss 危险默认行为收敛。全部为 autotest 框架代码改动 + 配套单测。

### 当前项目上下文

- 框架 v1.6.0；涉改文件均已精读（源码行号以当前工作区为准）。
- 审计 findings 已经 Codex spot-check 逐条核实属实（见审计任务 review.md R1）。
- 项目 lessons L-002：安全不变量必须下沉为代码守卫，评审问法=「哪些执行路径绕过纪律」。

### 约束

- 不改被测项目（debox）；本批不动 P0-6 以后的项（批次隔离）。
- 兼容性：框架唯一接入方是 debox 的 `DeBoxBaseTest`（androidTestAutotest），接口破坏可控但需在 plan 里显式列出行为变化。
- 全量单测须过（现 199 条），`publishToMavenLocal` 须成功；版本号 bump 1.6.0 → **1.6.1**。

### 非目标

- 不做 StepContext 接口改造（第二批）、不动 POM 依赖（第三批）、不做 Gson 校验加固与 DeviceGateway（第四批）。
- 不在本批更新 docs/01~10 系列文档（全部批次完成后统一做文档对齐——用户已确认该收尾步骤）。

### 建议方案（6 个改动点）

**P0-0 DangerousOpsGuard 点击网关（新包 `com.autotest.safety`）**

```kotlin
// 目标描述：可审计的点击目标快照（复用 selector.ElementSnapshot 字段语义）
data class ClickTarget(text, resourceId, contentDesc, className, packageName, bounds)

class DangerousOperationException(message) : AssertionError(message)  // fail-stop：测试直接 FAIL

class DangerousOpsGuard(
    dangerousTexts: List<String> = DEFAULT_DANGEROUS_TEXTS,  // 词表可注入/可配置
    private val logger: TestLogger? = null,
    private val evidenceCapture: ((String) -> String?)? = null  // 命中时截图，返回路径
) {
    val events: List<GuardEvent>  // 全部守卫事件（阻断/放行/禁用），报告可取
    fun checkClick(target: ClickTarget, allowDangerous: Boolean = false, allowUnverifiable: Boolean = false)
    // 词表命中且未放行：截图留证 + logger.e + event(DANGEROUS_BLOCKED) + 抛 DangerousOperationException
    // 词表命中且 allowDangerous=true：event(DANGEROUS_ALLOWED) + logger.w，放行
    // 全空字段且未放行：截图 + event(UNVERIFIABLE_BLOCKED) + 抛 DangerousOperationException
    // 全空字段且 allowUnverifiable=true：event(UNVERIFIABLE_ALLOWED) + logger.w，放行
}
object GuardRegistry { @Volatile var current: DangerousOpsGuard? }  // 扩展函数类入口的接入点
```

**【R2 修订】`allowUnverifiable: Boolean = false` 与 `allowDangerous` 一样贯穿全部点击入口 API**：
`SelfHealingLocator.click(spec, timeoutMs, allowDangerous, allowUnverifiable)`、
`GuardedElement.click(allowDangerous, allowUnverifiable)/longClick(...)`、
`UiAutomatorExt.clickText/clickResId/tryClickText(..., allowDangerous, allowUnverifiable)`、
`EspressoExt.click(allowDangerous, allowUnverifiable)`、
`UiReplayExecutor`（CLICK/LONG_CLICK 读 CachedStep 可选新字段 `allowDangerous/allowUnverifiable`，缺省 false）。
两个开关语义、事件类型、日志文案严格区分（危险命中 vs 不可审计），互不复用。

**【R2 修订】`safety.enabled=false` 留痕策略**：定位=本地调试/非钱包 App 接入的逃生口；关闭时
BaseUiTest.setUp `logger.w` 显著告警 + 记一条 `GuardEvent(GUARD_DISABLED)` 进报告（审计可见），
不允许静默关闭。
```kotlin
// （补充结束，下面回到原方案正文）
```

- **词表【R1 修订：默认合并】**：内置 `DEFAULT_DANGEROUS_TEXTS`（转账/付款/支付/提现/删除钱包/删除账户/清除数据/登出/退出登录/切换环境/确认交易/签名/授权/Send/Transfer/Sign/Approve/Delete/Logout…，中英），匹配 = text/contentDesc/resourceId 任一 `equals` 或 `contains`（词长≥2）；项目词表经 `safety.dangerousTexts`（逗号分隔）注入后**默认与内置词表合并**；确需完全替换必须显式 `safety.dangerousTextsReplace=true`（opt-in）；`safety.enabled=false` 可整体关闭（默认 true）。
- **可审计 descriptor【R1 修订：fail-closed】**：guard 统一采集 ClickTarget 全字段 + 命中时刻截图；无文本/无 desc 的目标以 resourceId/className/bounds 兜底描述；**全字段（text/desc/resourceId）皆空的目标默认阻断**（记 UNVERIFIABLE event + 截图 + 抛 DangerousOperationException——无语义可比对的点击在钱包场景不可默认放行）；调用方确知安全时以显式 `allowUnverifiable=true` 逐次放行（记 ALLOWED_UNVERIFIABLE event 留痕）。回放 CLICK 分支的 target 由缓存快照提供，正常必有字段，不受影响。
- **接入点（全部点击入口）**：
  1. `SelfHealingLocator.click(spec, timeoutMs, allowDangerous=false)`：构造器新增 `guard: DangerousOpsGuard? = null`；click 时 find→toSnapshot→guard.checkClick→click。**【R1 修订：封死裸 click 绕过】**新增受控替代 API `element(spec): GuardedElement`——包装 UiObject2，`click()/longClick()` 过守卫，`text/setText/滚动`等只读/输入操作直接委托；`find(): UiObject2` 标 `@Deprecated("裸 UiObject2 点击绕过危险守卫，点击用 click()/element()", ReplaceWith("element(spec)"))`，框架内部全部调用点（UiReplayExecutor.findTarget 等）迁移到 GuardedElement，不再有内部裸 click 路径；外部裸用 find() 会得到编译期弃用告警（检测机制）。
  2. `UiAutomatorExt.clickText/clickResId/tryClickText`：函数签名加 `allowDangerous: Boolean = false`，内部查 `GuardRegistry.current`（未注册=无守卫，兼容独立使用）。
  3. `UiReplayExecutor` CLICK/LONG_CLICK 分支：构造器新增 `guard` 参数（默认取 GuardRegistry），以缓存 target 快照构造 ClickTarget 比对。
  4. `EspressoExt.click()`：改为自定义 `GuardedClickAction`（perform 内读 view 的 text/contentDescription/id 构造 ClickTarget → guard → 委托 `ViewActions.click()`），签名加 `allowDangerous`。
  5. `DialogDismissInterceptor.dismissDialogs()`：每次 click 前过 guard（词表命中的「弹窗」正是最危险场景）；系统权限白名单豁免**【R1 修订：双条件】**= res-id 精确匹配 **且** 元素 `applicationPackage ∈ {com.android.permissioncontroller, com.google.android.permissioncontroller, com.android.packageinstaller}`（回应 Codex Question：防第三方页面复用相同 res-id 绕过）。
  6. `BaseUiTest.setUp`：构造 guard（词表来自 TestConfig 新键，evidenceCapture 复用截图逻辑）→ 注入 locator + 注册 GuardRegistry；tearDown 反注册。
- **报告**：`RunReport` 增 `guardEvents` 字段 + `ReportCollector.guardEventsProvider` + HtmlReporter 独立「危险操作拦截」section（与自愈事件同构）。

**P0-1 DialogDismissInterceptor 收敛**
- `DEFAULT_DISMISS_TEXTS` 移除确认语义（知道了/我知道了/OK/确定/好的），保留 dismiss 语义 + 权限「允许」组；新增可选构造参数 `confirmTexts: List<String> = emptyList()` 显式 opt-in。
- 文本匹配从 `text == it || text.contains(it)` 收紧为 **精确相等**（防「取消订单」类误命中）。
- **watcher 角色去点击化**：`beforeAction/beforeStep` 不再主动点击（违反「watcher 纯旁路」契约）；弹窗处理全部收敛到 behavior 链 `tryRecover`（弹窗挡路→步骤失败→恢复→重试一次，能力等价、契约干净）。`BaseUiTest` 装配同步：dialogDismiss 只 `addBehavior`，不进 watcher `addAll`。

**P0-2 flakySafely 最后一次尝试补白名单判定**
- `catch (e) { if (!isAllowed(e, allowedExceptions)) throw e; 否则才包装「超时」AssertionError }`——与循环体行为一致，真 bug 原样上抛不再被 FlakyClassifier 按「超时」误判 FLAKY。

**P0-3 零步骤用例假 PASS 封堵**
- `TestSuite.loadFromMarkdown`：`steps+verifications` 均空的 parsed case → 注册为**必 FAIL 用例**（step 内抛 AssertionError「用例解析为空，检查 TEST_CASES.md 格式」），不静默跳过（可见性优先）。
- `TestCaseParser`：用例体内以 `-`/`*` 开头但未匹配任何模式的行计数，`ParsedTestCase` 增 `unmatchedBullets: Int`，TestSuite 加载时 >0 则 logger.w 告警。
- `CacheReplay.toScenario`：`require(case.steps.isNotEmpty())`（空缓存回放=假阳性）。

**P0-4 assertAppInForeground 修复**
- `assert(...)` → `org.junit.Assert.assertEquals("App 不在前台", packageName, device.currentPackageName)`；实现与 `AppAssertions.assertInForeground` 合一（BaseUiTest 委托或直接内联 JUnit 断言，消除双实现）。

**P0-5 AI 硬断言禁止静默降级**
- `evaluator == null && !optional`：记 SKIPPED 后**抛 AssertionError**（对齐 KDoc「硬断言不允许静默降级」与「评估器异常」路径行为）；软断言路径不变。

### 可选方案

- P0-0 接入方式 A（推荐，如上）：构造注入为主 + GuardRegistry 兜底扩展函数。B：全部走全局单例——否决：可测性差、与框架现有注入风格（logger/evaluator 均构造注入）不一致。C：改 `find()` 返回包装类型彻底封死裸 click——否决：破坏所有调用方，收益边际（留作后续批次评估）。

### 实现步骤

1. 新建 `safety/` 包：ClickTarget/GuardEvent/DangerousOperationException/DangerousOpsGuard/GuardRegistry + JVM 单测（词表命中/放行/UNVERIFIABLE/事件记录）。
2. 接入 6 个点击入口（见上）；ConfigKeys/TestConfig 加 `safety.*` 键。
3. P0-1~P0-5 逐项修改 + 单测（flakySafely 非白名单穿透 / 空用例必 FAIL + 告警 / 空缓存 require / 硬断言 SKIPPED 抛错）。
4. 报告链：RunReport/ReportCollector/HtmlReporter 增 guard section + 单测。
5. `LIB_VERSION=1.6.1`；全量 `./gradlew :autotest:test` + `publishToMavenLocal`。
6. Codex impl review → 修复 → 回写。

### 验证方式

- 全量 JVM 单测通过（199 + 新增）；publishToMavenLocal 成功。
- **【R1 修订：真实点击链路 instrumented 冒烟进本批】**：`src/androidTest` 新增 hermetic `SafetyGateSmokeTest` + androidTest-only `GuardTestActivity`（androidTest manifest 声明，不进 AAR main）：Activity 放「转账」（危险）与「普通按钮」（安全）两个按钮，在模拟器实测——①Espresso GuardedClickAction 点「转账」抛 DangerousOperationException、点「普通按钮」正常；②UiAutomator clickText 同断言；③`allowDangerous=true` 放行且记 ALLOWED event；④DialogDismiss 收敛后不自动点「确定」。跑一遍 `connectedAndroidTest`（L1 模拟器）作为本批准出条件之一。
- 行为变化清单（供 debox 侧回归时知悉）：①硬 AI 断言在无评估器环境将 FAIL（原静默过）②弹窗预扫从 watcher 移到失败恢复链（弹窗处理时机后移）③「确定/知道了」不再被自动点击（如需保留由 DeBoxBaseTest 传 confirmTexts）④命中危险词表的点击默认抛 DangerousOperationException。
- 真机/模拟器 instrumented 冒烟（DeBoxSmokeTest 回放）作为 follow-up（Phase B 回归时验证）。

### 风险

1. 行为变化 ①~④ 可能让 debox 现有 2 条 instrumented 用例挂——属预期收紧，回归时按新语义修用例（不回滚框架）。
2. GuardRegistry 全局可变状态：仅作为扩展函数兜底入口，测试间 tearDown 反注册；风险有限。
3. **UNVERIFIABLE fail-closed 的误杀面**（R1 修订后）：RN 自绘/无语义控件的合法点击会被阻断，需调用方显式 `allowUnverifiable=true`——钱包场景宁误杀不误放（fail-closed 是 Codex R1 Critical 的要求）；误杀出现时有 event+截图便于快速定位改用例。

### 需要用户确认

- 无（批次范围用户已确认；行为变化清单在归档时呈现）。

---

## Accepted Plan

Version: v3（R1 两 Critical 修订 fail-closed/GuardedElement + R2 一 Important 修订 allowUnverifiable 贯穿）

Accepted At: 2026-07-02

Accepted By:
- Claude Code: 是
- Codex: plan-review R3 `VERDICT: PASS`（R1 FAIL → R2 FAIL → R3 PASS）
- User: 批次范围已确认（四批全做，第一批先行）

Decision: PASS

Scope: 上文 P0-0~P0-5 六改动点（含全部【R1/R2 修订】标注内容）+ 配套 JVM 单测 + hermetic instrumented 冒烟 + 报告链 + 1.6.1。

Verification: 全量 :autotest:test + publishToMavenLocal + L1 模拟器 connectedAndroidTest（SafetyGateSmokeTest）。

Accepted Risks: 无。

Deviation Policy: 触碰守卫语义（fail-closed/词表合并/事件类型）、扩大豁免面、或改动本批范围外文件的偏离，先 consult Codex。
