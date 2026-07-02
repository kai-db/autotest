# ① 计划 / 方案

## Plan Proposal

### 任务目标

第五批：**质量 / 可测性收尾**——清掉审计遗留的 P2 + 几个明确延后的项。全是健壮性/可测性加固，无阻断性问题。发 **1.8.0**（含 public API 演进,minor 版号）。

### 建议方案（6 项，均可控高价值）

**Q1 requireSafeArg 提为可测 + device/log 两包补测（安全关键，现零单测）**
- `DeviceActions.requireSafeArg`（shell 注入防护，`^[a-zA-Z0-9._]+$`）现是 private——提为 `internal` 顶层/伴生函数，补 JVM 单测（合法包名过 / 含 `;`&`|`空格反引号 拒）。这是安全关键纯逻辑却无回归。
- `MemoryInterceptor.parseTotalPssKb` 已 internal 可测——补一条边界测试（表头两格式）。
- `DefaultTestLogger`：补 log 包首个单测（分级过滤 / 文件写入路径，用临时目录）。

**Q9 DefaultTestLogger 线程安全【R1 修订：明确单实例/文件假设】**
- 共享 `SimpleDateFormat`（非线程安全）+ `appendText` 无同步——多线程拦截器场景日志交错/`SimpleDateFormat` 抛 `ArrayIndexOutOfBounds`。
- 改：两个 `SimpleDateFormat`（消息时间 + 文件名时间）改 `ThreadLocal<SimpleDateFormat>`（每线程独立，无锁）；`log()` 的写文件段 `synchronized(this)`。
- **单实例/文件假设（回应 Q）**：现用法是「每个测试一个 DefaultTestLogger 实例、文件名带秒级时间戳」，一个文件只有一个 logger 实例写——`synchronized(this)` 足够。**KDoc 写明该假设**；跨实例写同一文件不在设计目标内（真要跨实例共享文件需文件级锁，超出本批范围，注明）。

**Q5 DeviceActions 加固【R1 修订】**
- `dumpLogcat(tag)` 过新 `requireSafeTag`（严格语法）：
  - 允许 `^[A-Za-z0-9_.]+$`（单 tag）或受控 `Tag:Priority`（Priority ∈ V/D/I/W/E/F/S 单字符）；
  - **禁止首字符 `-`**（防 option-like 注入，如 `-v`）；禁止 `*`、空格、`;`&`|`反引号；
  - 测试边界：`-v` 拒 / `*:S` 拒 / `tag:V` 过 / `tag:bad` 拒 / 含空格拒 / 合法单 tag 过。
- `enableAirplaneMode`/`disableAirplaneMode`【R1 修订：失败可见 + 版本回退 + 结果校验】：
  - API ≥ 28（`Build.VERSION.SDK_INT`）：`cmd connectivity airplane-mode enable|disable`；
  - API 24–27：回退 `settings put global airplane_mode_on + 保护广播`（26/27 广播可能被拒，见下校验）。
  - **结果校验（不接受静默 no-op）**：执行后 `settings get global airplane_mode_on` 读回，与期望值不符 → `logger.w` 显式告警 + 返回 `Boolean`（false=未生效），**不静默**。方法签名改为返回 Boolean（原 Unit→Boolean，接入方可忽略返回值，向后兼容）。
  - 测试：命令字符串按 SDK 分支断言 + 结果校验逻辑（读回值比对，纯逻辑可注入 device 桩测）。

**Q6 死代码清理**
- `DeviceSelector.DevicePreference` enum 仅定义、零引用——删（或若有保留意图则标注）。全仓 grep 确认后删。

**D3 LogcatInterceptor `logcat -c` 收敛（第二批显式延后的项）**
- `beforeStep` 无条件 `logcat -c` 清设备全局缓冲——会抹掉 `LogcatAnalyzer` 依赖的步骤前 crash 痕迹。改：仅当 `collectOnFailure || collectOnSuccess`（本步确实要收集）才清；否则不动全局缓冲。

**Q7 TestRunner 失败类型区分（INFRA vs ASSERTION）【R1 修订：完整归类矩阵 + 报告消费】**
- `TestCaseResult` 加 `failureKind: FailureKind`（enum `NONE | INFRA | ASSERTION`）。
- **完整归类矩阵**（TestRunner 所有异常入口，只归类**primary failure**——即设 `error` 的那个）：
  - `envReset` 抛 → **INFRA**（primary）；
  - `before` 抛 → **INFRA**（primary）；
  - Scenario 步骤抛 → **ASSERTION**（primary）；
  - `InterruptedException`（超时取消）→ 不设 primary error（上抛，既有），failureKind 不适用；
  - `after` 抛 / lifecycle hook 抛 / reporter 抛 → **secondary**：既有行为是 catch+log 不设 primary error → **不覆盖已有 primary failureKind**（primary 优先保留）；主流程成功时 after 失败仍 log-only、不改 passed（既有语义不动）。
  - 无任何 primary error → `NONE`。
  - 实现：TestRunner 用局部 `failurePhase` 变量，在 envReset/before/steps 各自 try 段设置来源，catch 时据此定 failureKind。
- **报告消费（回应 Q）**：`failureKind` 进 `MonitorMode.toMarkdown` 失败行（FAIL 后缀标 [INFRA]/[ASSERTION]）——让「设备/环境没准备好」的 INFRA FAIL 与「用例真断言失败」的 ASSERTION FAIL 一眼可分，不只是加字段不展示。

### 非目标（继续延后，列 follow-up）

- 完整 DeviceGateway（heal 链全 JVM 可测）——UiObject2 final 类重度 mock，成本高、边际安全有限（第四批已抽纯逻辑 disambiguate 可测）。
- cleanup 按 run 子目录轮换——结构性改动；第四批已 protectedNames 护住指纹库，截图-report 一致性风险已缓解。
- MonitorMode 完整 sealed 状态机 / HtmlReporter base64 自包含——纯质量，收益低于成本。

### 约束

- 不改被测 debox；全量单测过 + publishToMavenLocal；LIB_VERSION 1.7.2 → **1.8.0**（不是 patch 1.7.3——本批含 public API 演进,按 semver 走 minor）。
- **兼容性【R2 修订：如实标注,不称"向后兼容"】**：
  - `TestCaseResult` 加 `failureKind`(带默认值)：**源码兼容**(重新编译的消费方如 debox 无碍),但 data class 主构造器变更 = **ABI 破坏**(预编译二进制消费方需重编)。
  - `enableAirplaneMode/disableAirplaneMode` `Unit→Boolean`：JVM 方法描述符变更 = **ABI 破坏**;源码兼容(返回值可忽略)。
  - 故走 **minor 版号 1.8.0**(非 patch),CHANGELOG 列 API 变更 + 迁移说明;debox 是源码级 androidTestImplementation、重编即可,不受 ABI 影响。

### 实现步骤

1. Q1 requireSafeArg 提 internal + 补 device/log 测。
2. Q9 DefaultTestLogger 线程安全。
3. Q5 dumpLogcat 过校验 + airplane cmd connectivity。
4. Q6 删 DevicePreference。
5. D3 LogcatInterceptor 条件清。
6. Q7 TestRunner failureKind。
7. LIB_VERSION 1.8.0；全量 test + publishToMavenLocal + verifyNoProtobufLite 护栏。
8. Codex impl review → 修 → 回写。

### 验证方式

- 全量 JVM 单测通过（新增 requireSafeArg 注入拒绝 / DefaultTestLogger 分级+并发 / MemoryInterceptor parse / TestRunner failureKind / LogcatInterceptor 条件清 逻辑）。
- 设备相关（airplane cmd / dumpLogcat）纯 JVM 测不到的部分，靠已有 androidTest 冒烟不回归 + 命令字符串断言。

### 风险

1. airplane mode 改 `cmd connectivity`：低版本（<28）回退旧法，注释说明；minSdk24 需保留 fallback。
2. D3 LogcatInterceptor 条件清改变默认收集行为——默认 collectOnFailure=true 时仍清（行为不变），只在都关时不清；影响面小。
3. Q7 加字段——纯增，不破坏现有 passed/error 消费方。

### 需要用户确认

- 无（质量收尾，范围已在「第五批」议定）。

---

## Accepted Plan
Version: v3（R1 3 Important + R2 兼容性/版号修订后）
Accepted By: Claude Code 是 / Codex plan-review R3 PASS（R1 FAIL→R2 FAIL→R3 PASS）/ User 已议定第五批范围
Decision: PASS
Scope: Q1 requireSafeArg 可测+device/log 补测 / Q9 logger 线程安全 / Q5 requireSafeTag+airplane 结果校验返回 Boolean / Q6 删 DevicePreference / D3 logcat -c 条件清 / Q7 failureKind 完整矩阵+MonitorMode 展示。LIB_VERSION 1.8.0（minor,API 演进,兼容性如实标注）。
Verification: 全量 JVM 单测 + publishToMavenLocal + verifyNoProtobufLite 护栏 + androidTest 冒烟不回归。
Deviation Policy: 触碰 API 兼容边界/airplane 失败可见语义/failureKind 归类矩阵的偏离先 consult。
