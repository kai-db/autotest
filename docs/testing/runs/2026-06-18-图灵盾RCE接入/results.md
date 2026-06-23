# 腾讯海外 RCE（图灵盾）接入 — 测试结果

> 用例见同目录 `cases.md`。记录原则：只记是否通过、问题描述、根因分析、修复方案。
> 每轮测试独立一节，按时间倒序排列（最新在最前）。

---

## 当前轮次

> 测试日期：2026-06-18 | 测试环境：构建机（macOS / JBR Android Studio；本轮仅跑构建层，无真机）
> 触发原因：首次测试（接入 `feat/turingshield-rce` 后首轮）— 本轮范围 = **构建层 TC-B 组 + A 层单测 TC-U 组**（TC-I / TC-H / TC-D / TC-PR / TC-R 待真机）
> 配置（`local.properties` 注入，不入库）：channel=`12000025`（⚠️存疑，§0.1 后端口径疑为腾讯云 AppID 而非 SDK channel，待真机 init 复核）/ hostUrl=`https://www.turingfraud.net`（候选，待腾讯定稿）/ appid=`1393866884`

### 0. 构建与静态核验

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-B-001 | 接入模块编译 | `:business:BaseBusiness:compileDebugKotlin` | ✅ PASS | BUILD SUCCESSFUL（up-to-date，接入模块编译无误） |
| TC-B-002 | debug 打包 | `:app:assembleAppDebug` | ✅ PASS | BUILD SUCCESSFUL（6s）；产物 `apks/debox-debug.apk`（v2.13.1 / 21300001） |
| TC-B-003 | ABI 核验 | 解包查 so 目录 | ✅ PASS | APK 仅 `arm64-v8a`+`armeabi-v7a`，无纯 `armeabi`；`libturingpri_mini.so` 两 ABI 均打入（防 -10001） |
| TC-B-004 | manifest 权限移除 | `aapt dump permissions` | ✅ PASS | 最终 manifest 无 `DETECT_SCREEN_CAPTURE`/`DETECT_SCREEN_RECORDING`/`freemme.permission.msa`；SDK 必需 `INTERNET`/`ACCESS_NETWORK_STATE` 保留 |
| TC-B-005 | 密钥红线 | grep SecretId/Key/腾讯云 AppID | ✅ PASS | 客户端无腾讯云密钥；命中项均为标准 `javax.crypto.SecretKey` 或既有 `HTTPDNS_SECRET_KEY`（非本改动）；RCE 三值仅在 `local.properties`、代码/构建脚本零硬编码 |
| TC-B-006 | release/R8 混淆 | `:app:assembleAppRelease` | ✅ PASS | BUILD SUCCESSFUL（3m23s）；R8 收缩/打包完成，无 TuringShield/TNative keep 报错；release APK `apks/debox-release.apk`（188MB），so + 权限同 debug 口径。WARNING 来自无关 `jetified-common-9.9.1`（R8 通用 stack-map 警告，非失败） |
| TC-B-007 | BuildConfig 注入 | 校验 RCE_* 值 | ✅ PASS | 生成 `BuildConfig`：`RCE_CHANNEL=12000025`(int)、`RCE_HOST_URL="https://www.turingfraud.net"`、`RCE_APPID="1393866884"`，类型正确、与 `local.properties` 一致 |

**TC-B 组统计**：PASS 7 / FAIL 0 / 跳过 0 ✅（构建层全通过，可进真机层）

> ⚠️ 注意：TC-B-006 仅验证 R8 编译期不裁剪报错；混淆后 `init`/`reqRiskDetectV3` 能否运行正常属 **V1 运行期验证**，需真机（TC-I 组）确认。

### 1. A 层 JVM 单测

> 测试文件：debox-android `business/BaseBusiness/src/test/java/com/app/base/business/riskcontrol/TuringShieldManagerTest.kt`（本轮新增）
> 运行：`:business:BaseBusiness:testDebugUnitTest --tests "*.TuringShieldManagerTest"` → `tests=4 skipped=0 failures=0 errors=0` ✅

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-U-001 | getDeviceToken 初始空串 | `getDeviceToken_returnsEmptyStringByDefault` | ✅ PASS | 默认返回 `""`（非 null），header 注入处降级安全、不 NPE |
| TC-U-002 | 仅非空 token 注入 | `injectionGuard_skipsWhenTokenEmpty` | ✅ PASS（空分支） | 复用真实 `isNotEmpty()` 判定作用在真实 getDeviceToken() 输出：空串→不注入。**「非空→注入」分支需写私有 cachedToken（需 SDK），下沉 B 层 TC-H-001/004 抓包验证** |
| TC-U-003 | 错误码 Long 透传 | `fetchRealtimeToken_degradesWithLongErrorCodeWhenSdkUnavailable` | ✅ PASS | JVM 无 native so → `reqRiskDetectV3` 抛 Throwable → catch 降级回调 `(null, -1L)`；errorCode 为 Long（编译期保证不截断）；耗时 5ms = 快速失败不挂起 |
| TC-U-004 | header key 常量一致 | `headerKey_matchesBackendContract` | ✅ PASS | `HEADER_KEY_DEVICE_TOKEN == "rceDeviceToken"`，与后端契约一致 |

**TC-U 组统计**：PASS 4 / FAIL 0 / 跳过 0 ✅

> 受限说明（与 §8 测试计划一致）：`init()`/`refreshTokenAsync()`（需 native so + Context）、`PrivacyConsent`（需加密 SP）纯 JVM 不可测，已下沉 B 层（TC-I / TC-PR）。本组 `LogUtils.open=false` 屏蔽 `android.util.Log`，未让任何 mock 流入生产路径。

### 2. 初始化与预热（B 层，三星 SM-S9210 / Android 16）

> 取证：`adb logcat -d`（DEBUG 日志全开）；证据存档 `evidence/tc-i-samsung-init.log`、`tc-i-samsung-home.png`。
> ⚠️ 注：macOS 无 `timeout` 命令，改用 `logcat -c` 清缓冲→冷启动→`logcat -d` dump 缓冲取证。

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-I-001 | 冷启动触发 init | force-stop→launch | ✅ PASS | `StartupTaskManager: 注册任务: 腾讯图灵盾风控初始化 [MEDIUM]` → `✓ ...[main=false] 完成，耗时: 12ms` |
| TC-I-002 | init 结果/错误码可见 | 看 init 返回值 | ✅ PASS | `TuringFdJava: TuringFD v2.93.9 (...) [url(https://www.turingfraud.net);c(12000025)]` + `TuringFdNative: ... success`，**init 成功、无错误码**。**关键：channel=12000025 真机验证有效，无 -10018/-2014，§0.1「channel 存疑」疑虑排除** |
| TC-I-003 | 预热取非空 token | 看 token | ✅ PASS | 预热后请求头 `rceDeviceToken=v3:AAA***qw==`（V3 格式非空）；首个带 token 请求 17:59:20.359（init 完成后约 2.2s，新加坡网关往返） |
| TC-I-004 | SDK 版本输出 | getVersionInfo | ✅ PASS | `TuringFD v2.93.9`，`lc=9D9F727DF8E99A13`=入库 v2.93.9（非旧包 v89/709E） |
| TC-I-005 | 启动不阻塞 | 看线程/耗时 | ✅ PASS | 任务 `main=false`（子线程）、12ms；SDK 自身 519ms monitor 竞争在其 worker 线程（Thread-25），不阻塞主线程/启动链路 |
| TC-I-006 | init 幂等 | 看 init 次数 | ✅ PASS | 本进程内注册/完成各 1 次、TuringFd init 1 次；`inited` 标志保证幂等。跨进程冷启动重新 init 属预期 |

**TC-I 组统计**：PASS 6 / FAIL 0 ✅

### 3. header 注入与域名隔离（B 层，三星）

> 同一份冷启动日志取证。`Thread: OkHttp <URL>` 行给出真实请求 host（注：响应体里的 cipxnn/google 等图片 URL 非请求 host）。

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-H-001 | DeBox 域名带 token | 抓 DeBox 请求 | ✅ PASS | t.debox.pro 业务请求头含 `rceDeviceToken=v3:AAA***qw==`，与缓存 token 一致 |
| TC-H-002 | 字段名精确 | 查 key 拼写 | ✅ PASS | 精确为 `rceDeviceToken`（与后端契约一致） |
| TC-H-003 | 第三方域名不带（防外泄） | 查非 DeBox 请求 | ✅ PASS | OSS `debox-oss...aliyuncs.com/download/conf_test.json`（独立 http_download client）无任何 DeBox header / 无 token；全日志 token 出现 10 次**全在 DeBox 业务链路、0 次在第三方/下载链路** |
| TC-H-004 | 空 token header 缺省 | 查预热前请求 | ✅ PASS | 预热完成前（17:59:18.3~20.4）20 个 DeBox 请求**无 rceDeviceToken**（"仅非空注入"生效，header 缺省），且这些请求照常成功、业务不阻断 |
| TC-H-005 | 不破坏既有 header | 对比 header | ✅ PASS | 同请求 `token`/`userId=100009`/`deviceId`/`channel`/`Content-Sign`/`appEnv=dev` 等既有 header 完整，rceDeviceToken 仅为新增字段 |

**TC-H 组统计**：PASS 5 / FAIL 0 ✅（附带印证 测试环境 appEnv=dev + 已登录 userId=100009，install -r 保住登录态）

### 3. header 注入与域名隔离（B 层）

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-H-001 | DeBox 域名带 token | | | |
| TC-H-002 | 字段名精确 | | | |
| TC-H-003 | 第三方域名不带 | | | |
| TC-H-004 | 空 token header 缺省 | | | |
| TC-H-005 | 不破坏既有 header | | | |

### 4. 降级与容错（B 层，三星）

> 取证：可逆断网 `svc wifi/data disable`（测完已恢复 + 清代理）；证据 `evidence/tc-d-samsung.log`。

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-D-001 | 断网冷启动 | svc 断网→冷启动 | ✅ PASS | 断网下 init 本地化 `success`、28ms 完成、**无崩溃**；DeBox 业务请求被 App 层 -100 守卫短路（不进拦截器）、不阻断；网络恢复后业务正常。注：SDK 离线走本地/缓存，未触发错误码路径 |
| TC-D-002 | channel/hostUrl 无效降级 | 负配置 APK（channel=0/空 hostUrl）装小米冷启动 | ✅ PASS | init 返回 `-10018`、`cachedToken` 维持空、**带 token 请求=0（header 缺省）**、业务请求 1420 条照常、无崩溃、首页正常。证据 `evidence/tc-d002-noconfig-xiaomi.log` |
| TC-D-003 | 阻断腾讯域名 | 选择性封堵 | ⏸️ 跳过 | 非 root 真机无法选择性封堵单域名（见 `app-knowledge/network-domain.md` 限制）；需代理/hosts/联调环境 |
| TC-D-004 | getDeviceToken 不阻塞 | 预热期请求 | ✅ PASS | token 预热 2s 内 20+ DeBox 请求持续发出、无停顿（TC-H-004 时序证据）；拦截器读 token O(1)、无 `-10008` |
| TC-D-005 | init 异常兜底 | — | ✅ PASS（等价） | 单测 TC-U-003 证明 SDK 不可用时 `catch(Throwable)`→降级回调 `(null,-1L)`、不逃逸；真机断网未崩溃佐证 |
| TC-D-006 | 错误码全程可见 | 负配置真机 logcat | ✅ PASS | 真机日志明确打出 `E TuringFdJava: please input valid channel!` + `W TuringShield: TuringSDK.init failed ret=-10018`（TuringShieldManager.kt:71）→ **silent-failure 零容忍达标**，无空 catch、错误码可观测 |

**TC-D 组统计**：PASS 5 / 跳过 1（TC-D-003 选择性封单域名需 root/代理，知识库已述限制）

### 5. 隐私 gate 与权限（B 层，两机）

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-PR-001 | 默认同意 init | 默认启动 | ✅ PASS | `PrivacyConsent.isAgreed()` 默认 true，gate 放行，init 正常执行（TC-I 已证） |
| TC-PR-002 | 不同意不 init | setAgreed(false) | ⏸️ 跳过 | 需写 `PRIVACY_AGREED=false` 到加密 SP，无调试入口不可外部触发；gate 逻辑 `if(!isAgreed())return` 经代码审查确认 |
| TC-PR-003 | 同意态恢复 | setAgreed(true) | ⏸️ 跳过 | 同上，需调试入口 |
| TC-PR-004 | APK 权限合规 | dumpsys（两机） | ✅ PASS | 三星 + 小米均无 `DETECT_SCREEN_CAPTURE/RECORDING`、`freemme.permission.msa`；`INTERNET`/`ACCESS_NETWORK_STATE` granted=true |
| TC-PR-005 | 不采集敏感标识 | dumpsys + logcat | ✅ PASS | `READ_PHONE_STATE` 为**预存权限**（granted=false，非图灵盾新增，manifest diff 未加）；TC-I 日志显示 SDK 隐藏 API 采集（seInfo/getOwnerUid）被平台 **denied**；IMEI/IMSI provider 未实现（Q10 关闭） |

**TC-PR 组统计**：PASS 3（含两机）/ 跳过 2（需调试入口）

### 6. 回归（B 层，三星）

| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-R-001 | 登录态保持 | install -r 后启动 | ✅ PASS | `install -r` 后 userId=100009 在线、未被风控踢，登录态保留（**非卸载重装**） |
| TC-R-002 | 既有业务冒烟 | 冷启动→首页 | ✅ PASS | 首页正常、25+ 业务请求 HTTP 200、无新增报错/崩溃 |
| TC-R-003 | RCE 场景业务可用 | — | ⏭️ 覆盖 | 4 个 RCE 场景的 header 注入与普通 DeBox 请求完全一致，已由 TC-H 统一覆盖；未单独触发场景（领宝箱命中危险清单不触发） |
| TC-R-004 | 既有签名不受影响 | 抓 header | ✅ PASS | `Content-Sign`（signV3 MD5）与既有 header 完整共存，rceDeviceToken 仅为新增字段，互不影响 |

**TC-R 组统计**：PASS 3 / 覆盖 1

---

## 本轮总统计（B 层全部完成）

| 组 | PASS | 部分/覆盖 | 跳过/阻塞 | FAIL |
|---|---|---|---|---|
| TC-B 构建 | 7 | 0 | 0 | 0 |
| TC-U 单测 | 4 | 0 | 0 | 0 |
| TC-I 初始化 | 6 | 0 | 0 | 0 |
| TC-H 注入隔离 | 5 | 0 | 0 | 0 |
| TC-D 降级 | 5 | 0 | 1 | 0 |
| TC-PR 隐私权限 | 3 | 0 | 2 | 0 |
| TC-R 回归 | 3 | 1 | 0 | 0 |
| **合计** | **33** | **1** | **3** | **0** |

**关键结论：0 FAIL。channel=12000025/hostUrl=turingfraud.net 真机 init 成功取到 v3 token、注入 DeBox 域名、第三方零外泄；负配置(channel=0)真机验证 init `-10018` 错误码可见、token 降级为空、业务不阻断不崩溃（silent-failure 零容忍达标）；权限合规。** 仅 TC-D-003（选择性封单域名）因非 root 限制跳过、TC-PR-002/003 因无调试入口跳过，均非缺陷。

**跨机型交叉验证**：三星 SM-S9210（One UI / Android 16）与小米 25067PYE3C（MIUI / Android 16）init 行为一致——均 `success`、`c(12000025)` 生效、token v3 注入、无崩溃、权限均无屏幕检测/msa。证据 `evidence/tc-i-xiaomi.log`。（注：MIUI 灭屏 Dozing 时 MEDIUM 启动任务不触发，需亮屏；属正常 App 启动时序，非缺陷。）

> ⚠️ **测试环境事故（详见文末）**：测试中误清空 debox-android `local.properties`，已从构建产物恢复 24/25 项；剩 `storePassword`/`keyPassword` 两个签名口令无法从产物恢复，待用户补回。**不影响已安装 App 的测试结论**（上述结果均在 install -r 的真配置构建上取得）。

---

## Bug 记录

> 每个 Bug 独立一节，按发现顺序编号。

### BUG-xxx — 标题

**关联用例**：TC-xxx

**状态**：🔴 待修复 / 🟡 已修复待验证 / 🟢 已修复已验证

**现象**：

**根因分析**：（基于 logcat / 抓包证据）

**修复方案**：

**检查清单**：

```
- [ ] 根因基于证据验证
- [ ] 评估影响范围，无回归
- [ ] 确认 UI 正常
- [ ] 改动范围最小
```

**验证结果**：

---

## 联调项（V2，需后端配合，单独记录）

> 客户端用例不判定以下项，仅记录联调结论。

| 项 | 验证点 | 结论 |
|---|--------|------|
| 端到端风控 | 后端用 token 调通 `DescribeRiskAssessment` 返回 `RiskLevel` | |
| header 兜底 | 仅 header 带 `rceDeviceToken`（无请求体）后端也能取到 | |
| 腾讯失败降级 | 模拟腾讯超时/5xx/签名失败，`fail_open=true` 不 500 | |

---

## 结果状态说明

| 标记 | 含义 |
|------|------|
| ✅ PASS | 验证符合预期 |
| ❌ FAIL | 不符合预期，需要修复 |
| ⚠️ 部分通过 | 核心正确但有优化空间（不阻断） |
| ⏸️ 跳过 | 被前置 Bug 阻塞 / 需后端联调 / 需调试入口 |

---

## 测试环境事故记录（非被测代码缺陷）

### INC-01 — 误清空 debox-android `local.properties`

**时间**：2026-06-18 测试 TC-D 期间

**现象**：为构建"负配置 APK"（验证错误码路径）时，一条 shell 命令中 `cp` 因 APK 文件已被用户删除而失败 → 备份 `.bak` 未生成 → 后续 `grep ... > local.properties`（`grep` 为 shell 包装函数）将 `local.properties` 截断为 0 字节。

**影响**：丢失 25 项构建配置（含 HTTPDNS/IM/JIM/CodePush/BLOCKCYPHER/WC/Tron 密钥与 RCE 配置、签名口令）。**不影响测试结论**——所有 B 层结果均在事故前 `install -r` 的真配置构建（已在两机运行）上取得。

**恢复**：
- 从各模块构建产物（BuildConfig.java / resValues XML）精确重建 **24/25 项**（含 RCE_CHANNEL=12000025 等，编译 + kapt 验证通过；`AI_KEY` 原本即空）。
- 修复一处往返转义 bug：`JIM_CALL_APP_ID`/`JIM_CALL_APP_ID_TEST`（long 字段，build.gradle 自动追加 `L`）恢复时误带 `L` → `LL` 语法错误，已去尾 `L`。
- ⛔ **未恢复**：`storePassword` / `keyPassword`（签名口令）——不进任何构建产物（BuildConfig/资源/manifest），`gradle.properties`/`~/.gradle` 也无，**只能由用户补回**。keystore `jks/wallet.jks` 在位、keyAlias=`liutian`。

**待用户操作**：在 `debox-android/local.properties` 末尾补两行（值为本地签名口令，勿提交/勿贴聊天）：
```
storePassword=<release keystore 口令>
keyPassword=<key liutian 口令>
```
**状态（2026-06-18 已闭环）**：用户已补回签名口令；`local.properties` 25 项全部恢复并验证（`:app:assembleAppDebug` 增量构建 BUILD SUCCESSFUL，`apks/debox-debug.apk` 已重建 230MB）。

### INC-02 — `resource` 模块全量资源合并失败（pre-existing，非图灵盾、非本次改动）

**现象**：`./gradlew clean :app:assembleAppDebug`（或任何触发全量资源 re-merge 的构建）在 `mergeAppDebugResources` 阶段失败：
```
ERROR: resource/src/main/res/values-ko/strings.xml:2610:2: Resource and asset merger: 元素内容必须由格式正确的字符数据或标记组成。
```
5 个 locale（values / values-ko / values-ja / values-vi / values-zh）的 strings.xml 末尾附近均报同类错。

**定性（确证非源文件畸形）**：
- `xmllint --noout` 5 文件全部 **合法 XML** ✅
- `aapt2 compile` 单文件 **无报错** ✅
- `git status` 工作区干净（即 committed 版本，非本次测试改动）
- 错误文案是 **JDK SAX 解析器**消息（非 aapt2）→ 指向 **AGP 资源合并器 / JDK 解析层**问题（疑似 AGP 版本 × JBR 21 兼容、或合并阶段编码/overlay 处理），非 strings.xml 内容本身。

**触发与影响**：增量构建因复用**缓存的已合并资源**而成功（17:41/17:46/18:03/19:03 均 OK）；本次测试中的 `./gradlew clean` 清掉该缓存后，全量 re-merge 必现失败（real 配置 + `--no-build-cache` + `--stop` 重启 daemon 均复现）。**这是该分支已存在的「clean 构建不过」隐患，CI/全新 checkout 会撞上**。

**✅ 已解决（2026-06-18 21:46+）**：用户重新切换到 `feat/turingshield-rce` 分支后，资源合并恢复正常——`./gradlew clean :app:assembleAppDebug` 全量构建通过资源合并、产出完整 230MB APK（`mergeAppDebugResources` 不再报错）。INC-02 的根因疑为 Android Studio 一侧的缓存/同步状态（切分支触发 re-sync 后修复），与图灵盾、与源 strings.xml 均无关。**遗留排查建议**：若 CI/全新 checkout 仍偶发该资源合并报错，按上节方向查 AGP×JBR21 / 合并缓存。

**对本次测试的影响（已消除）**：INC-02 解决后已补跑 **TC-D-002/006 负配置真机验证（PASS）**，无残留缺口。

> 附打包提示：自定义输出路径 `apks/debox-${buildType}.apk` 下，若残留旧 APK，增量打包器会报 `Zip ... already contains entry ... cannot overwrite` 或产出残缺包；**构建前删除 `apks/debox-debug.apk` 或用 clean 即可**（已记录，非缺陷）。

**建议（超出图灵盾范围，待用户决定是否跟进）**：排查 AGP/JDK 资源合并兼容性（如尝试 `mergeResources` 的 namespaced/非 namespaced 模式、或核对该 5 文件末尾是否有 BOM/编码声明缺失）；与图灵盾接入无关，可独立处理。
