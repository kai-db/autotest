# ① 计划 / 方案（审计提案）

## Plan Proposal

### 任务目标

对 autotest 框架（v1.6.0，59 文件 / 4510 行 main / 199 条单测）做全面架构审计，产出**按优先级分级的优化提案**。本任务交付提案本身；采纳项的实施各自另开 agent-dev-loop 任务。

### 审计方法

- 3 路并行只读源码审查（①selector/bridge/dsl/base/assertion/action ②engine/stability/intercept/lifecycle/runner ③config/data/device/diagnosis/log/report/util + build.gradle），维度对齐 commit-review 8 维，只收 ≥80 置信度。
- 对照既有规划（docs/07 完善方案：M3 未开始、sourcesJar P2 暂缓、代码债务清单已还清部分）与两层 lessons（G3 集成坑），避免与已规划/已修项重复。
- 交叉验证：G3 列的 5 个集成坑逐一核对是否已在框架侧根治。

### 总体评价

架构方向（agentic authoring + deterministic execution、AI 接口只注入不实现、非确定性命中强制记账、缓存三模式、纯逻辑与设备胶水分层）设计意识高于平均水平；单测 199 条且多为行为级。短板集中在四个主题：**①危险默认行为与假绿路径 ②证据链/统计的跨用例污染 ③存储层信任边界与生命周期 ④集成坑未在框架侧根治（还停留在"接入方自救"）**。

---

### P0 提案（正确性/假绿/钱包安全，建议尽快修，每项一个独立任务或合并 2-3 个小任务）

| # | 问题 | 位置 | 置信 | 修法 |
|---|---|---|---|---|
| **P0-0** | **【Codex plan-review R1 增补，Critical 92】框架全部点击入口均无危险操作 pre-action 守卫**——铁律7「每次点击前比对 dangerous-ops」目前只是 AI 黑盒流程的纪律约束，框架代码层 `SelfHealingLocator.click`、`UiAutomatorExt.clickText/tryClickText`、`UiReplayExecutor` 的 CLICK 分支、`EspressoExt.click` 都可直接点击钱包危险按钮：B 模式回放/自愈定位（自愈还可能命中错误元素，见 C1/C2）点到「确认转账/删除钱包」无任何拦截 | SelfHealingLocator.kt / UiAutomatorExt.kt / UiReplayExecutor.kt / EspressoExt.kt 全部点击路径 | 92 | 引入统一 **DangerousOpsGuard 点击网关**：所有框架点击入口过同一守卫；危险词表可注入（项目侧接 `app-knowledge/dangerous-ops.md` 语义清单）；命中默认 **fail-stop**（抛出专用异常 + 截图留证 + 进报告独立 section），用例显式声明 `allowDangerous` 白名单才放行 |
| P0-1 | **DialogDismissInterceptor 默认词表含「确定/好的/OK/允许」+ `contains` 模糊匹配 + 盲点首个可点击元素**——钱包 App 弹「确定删除钱包？」会被自动点确定，直接冲突铁律7；且 watcher 角色主动点击违反自家「watcher 纯旁路」边界 | DialogDismissInterceptor.kt:29-39,88-94 | 85 | 默认词表只留 dismiss 语义（取消/跳过/稍后）+ 精确匹配；confirm 语义显式 opt-in；点击只保留在 behavior 角色 |
| P0-2 | **flakySafely 最后一次尝试不判异常白名单**，任意异常（含 NPE 真 bug）被包成「超时」AssertionError → FlakyClassifier 按消息判 FLAKY → RetryRunner 重试真失败（洗白链） | FlakySafely.kt:47-53 | 88 | 最后一次也先 `isAllowed`，非白名单原样上抛；补单测 |
| P0-3 | **Markdown 解析失败静默产出零步骤用例 → 假 PASS**（Parser 只认两种 bullet，全解析失败仍 buildCase，空步骤 Scenario 直接通过）；TEST_CASES.md 是唯一用例来源，格式漂移会把整批用例洗成假绿 | TestSuite.kt:65-72 + TestCaseParser.kt:68-83 | 86 | 空 steps+verifications 拒绝注册或注册为 FAIL；未匹配 bullet 计数告警；`CachedCase` 空 steps 同理 `require` |
| P0-4 | **`assertAppInForeground` 用 Kotlin `assert()`，ART 默认关断言 = 恒过空操作**；与 AppAssertions.assertInForeground（正确用 JUnit）重复且行为不一致 | BaseUiTest.kt:203 | 92 | 改 `Assert.assertEquals`，两处实现合一 |
| P0-5 | **AI 硬断言（optional=false）无评估器时静默 PASS**——「硬」契约被环境配置无声降级，且与「评估器异常时会抛」不一致；该路径无单测 | AiAssert.kt:44-51 | 80 | 硬断言遇 SKIPPED 抛错或提供 `failHardOnSkip` 配置；补单测 |
| P0-6 | **MonitorMode 轮次超时产生僵尸线程**：shutdownNow 的 interrupt 被 TestRunner `catch(Throwable)` 吞掉，僵尸线程继续驱动真机并与下一轮共享非线程安全 `results` 数据竞争 | MonitorMode.kt:139-152 + TestRunner.kt:83,38 | 90 | 协作式取消（每用例前查 interrupt/volatile flag）+ TestRunner 对 InterruptedException 特判上抛 |
| P0-7 | **`maxRounds` 构造参数从未被读取**——监工「防死循环」语义失效，轮次可无限累积 | MonitorMode.kt:44 | 85 | `runRound` 前 `check(rounds.size < maxRounds)`；Phase 6 `runFinalVerification` 顺带补超时（现在裸奔） |
| P0-8 | **步骤证据/性能统计跨用例污染**：Screenshot/Logcat/Memory 拦截器以裸 stepNumber 为 key（Performance 用 `"$stepNumber:$stepName"`，同样缺用例维度）、实例跨用例复用不清理——A 用例失败截图挂到 B 用例 PASS 步骤上，P50/P95 基于被覆盖的残缺样本（Codex 核实：大体属实，Performance key 表述已按实修正） | ScreenshotInterceptor.kt:42,64 / LogcatInterceptor.kt:31,54 / PerformanceInterceptor.kt:15 / MemoryInterceptor.kt:30 + Scenario.kt:34-35 | 88 | 接口引入 `StepContext(caseId, stepNumber, stepName, attempt)`（一次解决 3 条 finding）或 per-test reset |
| P0-9 | **产物目录用 test 包 context 而非 targetContext**——G3-③ 教训未在框架根治：`TestConfig` 用 `getInstrumentation().context`，接入真实 App（test 包≠target 包）scoped storage 拒写，全框架产物走此入口一处错全链错 | TestConfig.kt:128-129 | 88 | 改 `targetContext`；同时修 TestLogger.kt:20 / Environment.kt:22 的 `/sdcard/Pictures` 硬编码默认值（与自家注释矛盾） |

### P1 提案（健壮性/集成体验/API 设计）

**主题 A：集成坑框架侧根治（G3 复发防线，收益最直接——每个新接入方少踩 3 个坑）**
- A1 `api "org.hamcrest:hamcrest-library"` 显式进 POM（现靠 espresso-core 间接传递，公开 API 暴露 `org.hamcrest.Matcher` 却不自己声明；接入方一 exclude 就 NoClassDefFoundError）→ build.gradle
- A2 espresso-core 依赖上加 `exclude protobuf-lite` 写进 POM exclusions，一处根治所有接入方 → build.gradle:49
- A3 `waitForIdleTimeout` 提为 ConfigKeys 配置项（现硬编码在 BaseUiTest.kt:110，绕过 BaseUiTest 的接入方拿不到）
- A4 `singleVariant("release") { withSourcesJar() }`：接入方 IDE 能看到源码与中文 KDoc（现只有反编译字节码）；顺带解决 07 文档遗留的 sourcesJar duplicate P2

**主题 B：存储层信任边界**
- B1 Gson `fromJson` Unsafe 实例化绕过 `init` 校验：手编 JSON 可绕过 caseId 路径安全校验（`../` 逃逸风险），缺字段时非空类型注入 null、NPE 炸在远端 → CaseCacheStore.kt:31 / FingerprintStore.kt:51 加 load 后显式 validate，失败归入既有「损坏按未命中」分支
- B2 TestHistoryStore 每次 record 全量 pretty-print 重写（O(n²) IO）且非原子——写坏即静默清空全部历史资产 → 临时文件+rename、debounce
- B3 指纹库 `fingerprints.json` 落在会被 `TestArtifacts.cleanup`（保留最近100个文件）清理的产物目录——截图多的一轮后下轮 setUp 可能删掉指纹库 → 移独立目录或 cleanup 排除清单；cleanup 本身改按 run 子目录轮换（现按文件数会删掉本 run 早期证据）

**主题 C：自愈链正确性**
- C1 自愈/AI 命中**立即覆写指纹库**——一次误愈永久锁定错误目标且置信度显示 1.0，人审线索消失（kdoc 说「确认后回填」，存储层先斩后奏）→ provisional 标记/暂存区，确认后转正 → SelfHealingLocator.kt:74,84
- C2 heal 候选不过滤 packageName（快照有该字段没用上）——文本相近的系统弹窗按钮可拿 ≥0.75 分被点击 → package 一致性作硬条件 → SelfHealingLocator.kt:67
- C3 同属性多条件（`byText and byTextContains`）让 BySelector 二次设置抛未受控 IllegalStateException，连 `tryFind`「不抛」契约都破 → fold 前按 attr 去重/合并 → SelfHealingLocator.kt:114
- C4 `SelectorSpec.key()` 不转义分隔符可碰撞（指纹库/缓存唯一索引互相污染）→ 转义或长度前缀 → SelectorSpec.kt:61-66
- C5 `findBySnapshot` 多命中 bounds 失配时静默任取第一个（列表项必踩）→ 上报 LocatorEvent 或判失败 → SelfHealingLocator.kt:136

**主题 D：重试体系一致性**
- D1 三层重试判定规则互相矛盾且叠乘（flakyStep 重试一切 Throwable × behavior 整步重放 × RetryRunner 预算 ≈ 数十次）；behavior 重放对已生效副作用步骤（钱包转账/签名类）静默重复执行 → 统一以白名单/FlakyClassifier 为唯一判定入口；Step 加 `retriable=false` 标记 → Scenario.kt:77-91 等
- D2 `RetryRunner(classifier: FlakyClassifier = FlakyClassifier)` 参数类型是 object 单例=假注入点；registerRule 全局可变状态跨 suite 泄漏 → 抽接口、实例级注入 → RetryRunner.kt:17 / FlakyClassifier.kt:28
- D3 LogcatInterceptor 无条件 `logcat -c` 清设备全局缓冲，抹掉 LogcatAnalyzer 依赖的步骤前 crash 痕迹 → 仅需收集时清或 `-T <timestamp>` 增量 → LogcatInterceptor.kt:33-40；LogcatAnalyzer 陈旧 FATAL 误判同因（建议 setUp clearLogcat 或时间戳下限）

**主题 E：错误处理一致性（silent failure 清扫）**
- E1 `TestLifecycleManager.safely` 完全空 catch（同仓 InterceptorChain.safely 已修成留痕，不一致）→ TestLifecycle.kt:63-68
- E2 `TestCase` after 块异常仅 log 吞掉（脏状态传染后续用例）→ 主流程成功而 after 失败应抛 → TestCase.kt:66-69
- E3 BaseActivityTest `@Before` 半途失败 → tearDown 访问未初始化 lateinit 掩盖原始异常且报告丢失 → isInitialized 守卫 + finally → BaseActivityTest.kt:25-27
- E4 错误信息只存 `e.message`（裸 NPE 报告显示空白/「-」）→ fallback `e.toString()` + errorType 字段 → Scenario.kt:49 / TestRunner.kt:106-112
- E5 `stepWithRecovery` recovery 自身抛错吞掉原始异常 → addSuppressed → Scenario.kt:94-102
- E6 ReportCollector 根因分析 `catch(Throwable)→null` 无留痕；ScreenshotRule 忽略 takeScreenshot 返回值；UiReplayExecutor SLEEP 非法 payload 静默回退 1000ms、LAUNCH_APP 不等待 App 到前台

**主题 F：配置体系收敛**
- F1 ConfigLoader 的 app/env 两层恒为 emptyMap（死层）；EnvironmentManager 是平行的第二套配置、与 TestConfig 互不打通 → env 注入 ConfigLoader，收敛单一读取入口 → TestConfig.kt:42-47 / Environment.kt

### P2 提案（质量/性能/可测性/文档）

- Q1 **SelfHealingLocator 抽 `DeviceGateway` 接口**：降级链编排（框架最核心逻辑）现零 JVM 单测——bridge 包 `ReplayExecutor` 已证明该模式价值，照做后 C1-C5 都能补回归网
- Q2 FingerprintStore 每次定位成功全量重写 JSON → 脏标记 + tearDown 统一 flush
- Q3 计时统一 `SystemClock.elapsedRealtime()`（现 currentTimeMillis，NTP 校时会负耗时/误超时）
- Q4 TestCaseParser 正则提 companion 常量；SelectorSpec REGEX 构造期校验+缓存编译
- Q5 DeviceActions：`dumpLogcat(tag)` 未过 requireSafeArg（同类不一致）；airplane mode 用保护广播在 API26+ 静默 no-op（改 `cmd connectivity`）；shell 返回值统一校验留痕；requireSafeArg 提为可 JVM 测并补单测（device/log 两包现零单测）
- Q6 死代码：DeviceSelector.DevicePreference 无引用
- Q7 TestRunner 区分 INFRA/ASSERTION 失败类型（给 MonitorMode/FlakyClassifier 更准信号）；MonitorMode 状态机显式化（Phase 顺序靠自觉）
- Q8 DialogDismiss 叠层弹窗连关（带上限）；AppActions.waitForSplashDismiss 串行 5×2s 白等改复合查询
- Q9 HTML 报告截图 base64 内嵌/相对链接（pull 到本地证据链不断）；DefaultTestLogger SimpleDateFormat 线程安全
- Q10 文档对齐：CLAUDE.md config 行「分层配置」表述与实际（app/env 死层）不符；report 行补 ReportCollector/ReportWriter

### 建议实施顺序

1. **第一批（P0-0~5，钱包安全/假绿）**——P0-0 危险操作点击网关是本批核心（接口级新增，覆盖全部点击入口），连同 P0-1~5 纯框架侧小改动+单测，一个任务打包
2. **第二批（P0-6~8 + D1/D2，执行引擎与重试体系,涉及接口变更 StepContext）**——一个任务
3. **第三批（P0-9 + 主题 A + F，集成体验与配置收敛,发布 1.7.0 验证 debox 接入）**——一个任务
4. **第四批（主题 B/C + Q1,存储与自愈链,配合 DeviceGateway 补 JVM 测试）**——一个任务
5. P2 其余按顺手原则捎带

### 非目标

- 本任务不改任何代码；不重启 docs/07 的 M3(CI 化)议题；不做 B 模式用例扩充。

### 验证方式

- 提案由 Codex 独立评审：**抽查核实关键 findings 是否属实**（引用的均为现有代码，可直接查证）、优先级分级是否合理、有无重大遗漏。
- 后续每个实施批次各自走 agent-dev-loop（plan→review→impl→review），全量单测 + publishToMavenLocal + debox 正对照为准出条件。

### 风险

- 审查 agent 的 findings 可能有个别误报（行号漂移/理解偏差）→ 靠 Codex 抽查 + 实施批次 plan 阶段逐条再核。
- StepContext 等接口变更有破坏性 → 框架仅 debox 一个接入方，代价可控，但需同批更新 DeBoxBaseTest。

### 需要用户确认

- 实施批次的取舍与节奏（本提案默认 4 批全做，用户可裁剪）。

---

## Accepted Plan

Version: v2（R1 增补 P0-0 后）

Accepted At: 2026-07-02

Accepted By:
- Claude Code: 是
- Codex: plan-review R2 `VERDICT: PASS`（R1 FAIL→修订 P0-0 后复审通过）
- User, if needed: 实施批次取舍待用户裁剪（提案本身无阻断确认项）

Decision:
- PASS

Scope:
- 本任务交付 = 上述分级审计提案（P0-0~P0-9 / P1 主题 A-F / P2 Q1-Q10 / 4 批实施顺序）。
- 提案实施不在本任务内，采纳项各自另开 agent-dev-loop 任务。

Verification:
- Codex R1 对 P0-1~9 逐条 spot-check 全部属实；R2 确认 P0-0 设计充分解决其 Critical。

Accepted Risks:
- （无）

Open Design Question（Codex R2 非阻断提问，留给第一批实施的 plan 阶段回答）:
- DangerousOpsGuard 对无文本/无 contentDescription 的点击目标如何取可审计的 target descriptor？
  初步方向：守卫层统一采集 ElementSnapshot（resource-id/class/bounds/package + 命中时刻截图）作为审计凭据；
  纯坐标点击（回放 CLICK 分支）以缓存内目标快照为准，快照缺失时按「不可审计点击」从严处理（可配置 fail-stop）。

Deviation Policy:
- 实施批次若要偏离本提案的分级/范围（如降级 P0 项、合并批次引入接口破坏），先 consult Codex。
