# ② 实现

## Implementation Log

（待 Accepted Plan 落定后开始。）

## Deviation Log

- （无）

## Consult Log

- （无）

### 2026-07-02 按 Accepted Plan v3 实施

- **Q1**：requireSafeArg 提为顶层 internal（可 JVM 测）；新增 requireSafeTag（严格语法）;dumpLogcat 过 requireSafeTag。补 RequireSafeArgTest（合法过/shell 元字符拒/tag 边界 -v、*:S、tag:bad 拒）+ DefaultTestLoggerTest（分级/格式/并发 400 行不丢）。MemoryInterceptor.parseTotalPssKb 已有测试,跳过。
- **Q9**：DefaultTestLogger 两 SimpleDateFormat 改 ThreadLocal;写文件段 synchronized(this);KDoc 写明单实例/单文件假设。
- **Q5**：airplane API≥28 用 cmd connectivity / 24-27 回退,执行后 settings get 读回校验、未生效 logger.w + 返回 Boolean(不静默 no-op);dumpLogcat tag 过校验。
- **Q6**：删 DeviceSelector.DevicePreference 死代码枚举(零引用)。
- **D3**：LogcatInterceptor.beforeStep 仅当 collectOnFailure||collectOnSuccess 才 logcat -c(否则不抹步骤前 crash 痕迹)。
- **Q7**：TestRunner 加 FailureKind(NONE/INFRA/ASSERTION)+ failurePhase 局部变量归类 primary(envReset/before=INFRA、步骤=ASSERTION、after/secondary 不覆盖);TestCaseResult 加 failureKind 字段;MonitorMode.toMarkdown 失败行标 [INFRA]/[ASSERTION]。补 FailureKindTest(5 例覆盖矩阵)。
- **版本**：1.7.2 → 1.8.0(minor,API 演进)。

### 验证结果
- JVM 单测 **292 全过**(+12:RequireSafeArg 4 / DefaultTestLogger 3 / FailureKind 5)。
- verifyNoProtobufLite 护栏 PASS;publishToMavenLocal 1.8.0 成功。
- 纯 JVM 可测(device 命令字符串/失败归类/logger 逻辑不依赖真机);airplane cmd/dumpLogcat 的设备语义靠已有 androidTest 冒烟不回归。

### Deviation Log
- 无(严格按 Accepted Plan v3)。

### Consult Log
- N/A。
