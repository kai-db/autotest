# 测试产物治理设计

日期：2026-08-05

## 背景

`2026-08-04-debox-weekly-changes` 单次测试在
`docs/testing/runs/2026-08-04-debox-weekly-changes/evidence` 下留下约 203 GB 数据。根因不是必要的测试证据，而是多个
runner 将独立 `GRADLE_USER_HOME` 放进 evidence，并复制 Gradle wrapper、JDK、依赖缓存、
task cache 和构建中间产物。相同内容又按 target、phase 和重试轮次重复生成。生成目录还被
Git、CodeGraph 和磁盘统计当作项目内容处理。

本设计把“运行所需工作数据”和“需要长期保存的证据”分开，并将默认策略收紧为：默认不
创建持久文件，默认不记录完整过程，只有契约明确允许的最小证据可以进入仓库。

## 目标

- checkout、Gradle Home、JDK、构建目录、APK 和依赖副本不进入仓库或 evidence。
- PASS 只保存证明结果所需的结构化摘要；FAIL 只保存有界的诊断信息。
- 所有 runner 使用同一个产物策略入口，未来新增 runner 不能绕过。
- 单文件、单 case、单 run 都有硬容量限制；未知文件或超限都会让收口失败。
- 正常退出、失败、信号中断都清理临时工作区；异常遗留可在下一次启动时安全回收。
- 不削减现有测试用例、Phase、判据、危险操作门禁或证据真实性要求。

## 非目标

- 本次不改变 AutoTest AAR 的生产 API 或测试业务逻辑。
- 本次不自动删除现有约 203 GB 目录；删除作为独立、可审计且需用户确认的操作。
- 本次不引入容器、远程制品库或新的常驻清理服务。
- 容量限制不能通过静默丢弃、伪造 PASS 或省略必需 oracle 来满足。

## 方案选择

采用“统一产物策略库 + runner 强制接入 + 契约测试”的方案。

仅修改当前脚本无法防止新 runner 复发；容器化能提供更强隔离，但会增加设备、ADB、签名和
本地 SDK 的接入成本。统一策略库可以在不改变测试覆盖的前提下，为当前及未来脚本提供同一
套路径、记录和清理约束。

## 数据分类与路径边界

### 持久文档

允许长期保存在 run 目录根部：

- `cases.md`
- `results.md`
- 说明测试契约的 `README.md`

这些文件描述测试计划和结论，不包含构建缓存、完整 stdout 或二进制产物。

### 有界证据

evidence 只能由策略库写入，并使用明确类别：

- 状态与身份：TSV/JSON manifest、source/APK/device SHA、开始和结束时间。
- 机器判据：JUnit XML、oracle、forbidden-lane 扫描结果和校验和。
- 失败诊断：截断后的 stdout/logcat、少量关键截图或 window dump。
- 产物描述：APK 文件列表、签名摘要和哈希；不保存 APK 本体。

PASS 默认不保存完整 stdout、完整 logcat、重复截图或构建目录。FAIL 也不保存完整工作区，只
保留定位失败所需的有界 head/tail 日志、关键截图和结构化失败分类。

### 临时工作数据

以下内容只能位于仓库外的专属临时根目录：

- Git checkout/worktree 和 source overlay。
- `GRADLE_USER_HOME`、wrapper、JDK、dependency/task/build cache。
- module `build/`、APK、mapping、native/CMake 中间产物。
- node_modules shadow、临时 fixture、local.properties 和凭据投影。

临时根目录通过 `mktemp` 创建，带 owner marker 和 run id。路径必须 canonicalize 并验证位于
允许的临时前缀下，清理逻辑不得接受空路径、仓库根、用户目录或未带 marker 的目录。

## 统一策略接口

新增 shell 策略库，提供四类职责：

1. `init`：创建临时工作区、注册 EXIT/INT/TERM trap，并在任何构建前完成路径校验。
2. `workspace`：为 checkout、Gradle Home、build 和设备步骤分配互不重叠的临时路径。
3. `record`：按证据类别复制或生成文件，执行扩展名、敏感信息、单文件和总量检查。
4. `finalize`：验证白名单、manifest、校验和和预算，随后清理临时工作区。

runner 不得把 evidence path 作为 `GRADLE_USER_HOME`、checkout、build 或依赖缓存的祖先。不得
直接使用目录级 `cp`、`rsync` 或 `ditto` 将工作区归档到 evidence。需要留存的单个文件必须
通过 `record`，并在 manifest 中记录来源类别和大小。

为人工诊断保留显式逃生口 `AUTOTEST_KEEP_TEMP=1`。它只保留仓库外的临时工作区，必须打印
路径和占用警告，不改变 evidence 白名单，也不能在无人值守默认流程中启用。

## 容量和记录预算

默认预算：

- 单个文本日志：20 MiB；超过后保留带截断标记的 head/tail。
- 单个截图或 window dump：10 MiB；超过即拒绝记录，不做静默降质。
- 单个 case 证据：50 MiB。
- 单次 run 的 evidence：512 MiB。

预算在写入前和 finalize 时各检查一次。超限时当前 case/run 进入明确的
`BLOCKED_ARTIFACT_BUDGET`，报告超限类别和实际大小；已有必需证据不被静默删除，测试结果也
不能标为 PASS。预算只覆盖 evidence，不允许通过把数据改写到 run 目录其他位置绕过。

## 生命周期与遗留清理

- trap 在任何 fixture、凭据投影、symlink 或 Gradle 执行前注册。
- 正常、失败和信号退出使用同一个幂等 cleanup。
- 新 run 启动时只扫描策略库自己的临时前缀；仅删除带合法 marker、属于当前用户且超过 24
  小时的遗留目录。
- 启动清理先 dry-run 记录候选，再逐个重新验证路径和 marker 后删除。
- 现有 run/evidence、任意用户工程、全局 `~/.gradle` 和 Android SDK 永不被遗留清理器触碰。

## 防回归约束

新增契约测试，至少覆盖：

- runner 不得将 `GRADLE_USER_HOME`、checkout 或 build path 放在 evidence/run 下。
- runner 不得目录级复制 Gradle Home、JDK、wrapper、cache、APK 或源码树到 evidence。
- 记录白名单之外的文件会失败。
- 文本超限会产生带标记的 head/tail；二进制超限会失败。
- run 总量超限会得到 `BLOCKED_ARTIFACT_BUDGET`，不能得到 PASS。
- EXIT、INT、TERM 以及中途命令失败均清理带 marker 的临时目录。
- 非策略目录、缺 marker 目录和危险宽路径永不删除。
- `AUTOTEST_KEEP_TEMP=1` 只保留仓库外临时目录，不放宽证据规则。

测试使用临时 fixture 文件系统，不运行真实 Android 构建。现有 weekly-change runner 的静态
catalog 测试继续保留，并增加对统一策略接入的检查。

## 迁移范围

第一批迁移本次造成膨胀的 runner：

- `scripts/debox-weekly-changes/run-jvm-gates.sh`
- `scripts/debox-weekly-changes/run-build-matrix.sh`
- `scripts/debox-weekly-changes/run-mmkv-migration.sh`
- 它们共享的 `scripts/debox-weekly-changes/lib/` 生命周期代码

随后对 `scripts/` 做静态扫描：任何会创建 checkout、设置 Gradle Home、运行 Android 构建或
写 evidence 的脚本，都必须接入策略库或在契约测试中列出有理由的只读豁免。豁免必须精确到
脚本和行为，不能使用目录级通配。

`.gitignore` 和 CodeGraph 排除规则补充生成目录模式，防止临时失误进入版本控制或索引；它们
是第二道防线，不能替代运行时路径隔离和 finalize 审计。

## 错误处理

- 路径不安全、marker 缺失、未知证据、预算超限或敏感信息扫描失败均 fail closed。
- cleanup 失败在最终结果中显式报告临时路径，不用 log-only 的方式吞掉。
- 证据记录失败不会覆盖原测试失败分类；结果同时记录测试状态和产物治理状态。
- finalize 未执行或未通过时，聚合器拒绝把 run 标记为完成。

## 验收标准

- 合成 runner 执行后，仓库内没有 Gradle Home、JDK、wrapper、APK、build 或 checkout 副本。
- PASS 只留下结构化摘要和必需校验文件；FAIL 只留下预算内诊断证据。
- 任意 runner 尝试把工作目录指向 evidence 时，契约测试和运行时检查都能阻断。
- 信号中断后的临时工作区被清理；模拟崩溃遗留可在 24 小时后被安全回收。
- 单次 evidence 默认不超过 512 MiB；超限状态不可被聚合为 PASS。
- weekly-change runner 的现有行为判据、用例覆盖和安全门禁保持不变。

## 现有大目录处置

实现与验证完成后，单独生成现有异常目录的清理清单。默认保留 `cases.md`、`results.md` 和必要
的轻量结论文件，约 203 GB 的 Gradle Home/build/checkouts 只在用户明确确认后删除。该清理不
属于自动遗留清理器的权限范围。
