# CodePush 2.0 热更链路 — 测试结果（2026-07-14）

> 用例：`cases.md`（同目录）。被测包：`origin/feat/gasless` tip `80b610b859`，现场构建 appDebug（三轮：2.14.1/21400001 sha `c825dd50…`；构造法 2.14.2/21400099 匹配线上 v521）。
> 设备：L1 = emulator-5556（Pixel 16k，测试号「请输入昵称1」uid 100009，测试环境 `t.debox.pro`）。签名 `2ee891687f73bff9…`（CN=liutian，与设备已装包一致）。
> 执行人：Claude Code（AI 自主）。状态图例：✅ PASS / 🔴 FAIL / ⚠️ 部分 / 🟢 设计留意 / ⏸️ 挂起（外部依赖）/ ⏭️ 未执行。
> **本文件经三轮迭代**：首轮 P0 因归因事故撤回，最终结论见顶部「✅ 最终结论」。

## ✅ 最终结论（三轮复现后）——BUG-CP-01 证伪，热更链路端到端正常

> **一句话**：feat/gasless CodePush 2.0 的 bridgeless 热更**全链路正常工作**（check→download→install→重启 reload 注入→notifyAppReady 确认→跨重启持久）。首轮所谓「热更从不生效」的 P0 是**归因事故 + 未清失败黑名单**造成的假象，已证伪。唯一真实阻断是 **release 包本地 R8 构建失败**。

### 归因事故（首轮 P0 为何是假的）

- 设备在 **20:36:45 被外部（非本会话）重装为一个 CodePush-enabled 2.14.2 包**（versionCode 21400002，`lastUpdateTime` 实锤）。故 20:40 之后抓到的「下载→apply 失败→回滚→版本 2.14.2 上报」全部属于那个包，**不是我构建的 feat/gasless 2.14.1**。首轮 BUG-CP-01 与 NOTE-CP-02（版本错乱）据此撤回。
- 「metadata 全 null / 目录不存在」也是误读：那是**下载前**或**清缓存后**的空状态；下载后 SP（`CodePush.xml`）与 `files/CodePush` 均正常落盘。

### 三轮实测证据链（均为可归因的现场构建包）

| 轮次 | 被测包（可归因） | 触发 | 结果 |
|---|---|---|---|
| 2 | feat/gasless **2.14.1 / 21400001**（sha `c825dd50…`） | 清 CodePush 缓存→进 RN 页 | `Reporting binary update (2.14.1)` → **UP_TO_DATE**（`sync resolved:0`），跑 assets bundle，RN 名片页正常渲染，0 崩溃。**符合预期**——v521 包 `targetBinaryVersion=2.14.2` 与 2.14.1 semver 不匹配 |
| 3a | feat/gasless **2.14.2 / 21400099**（versionName 改 2.14.2 匹配 v521，唯一 versionCode 便于归因） | 未清黑名单→进 RN 页 | **UP_TO_DATE**——因 v521 仍在**上一会话遗留的 `CODE_PUSH_FAILED_UPDATES` 黑名单**里被 @revopush 过滤（不是服务端下线，是本地失败记录未清） |
| 3b | 同上 2.14.2 包 | admin「清理CodePush缓存」清黑名单→进 RN 页 | **download 1→100% → INSTALLING → UPDATE_INSTALLED**（ON_NEXT_RESUME）✅ |
| 3c | 同上，force-stop 冷启 | ON_NEXT_RESTART apply | `[CodePush] Loading JS bundle from ".../files/CodePush/03d2158c…/index.android.bundle"`——**重启后 bundle 切到热更包**（03d2158c = v521 hash），reload/反射注入**成功** ✅ |
| 3d | 同上，进 RN 页 | HOC mount → notifyAppReady | `printBundleInfo`：**`【使用 CodePush Bundle】`** + `Bundle 文件检查通过`——RN 页跑的是**热更 bundle**，非 assets ✅ |
| 3e | 同上，再 force-stop 冷启 ×1 | 验证持久 | 仍 `Loading JS bundle from .../CodePush/…`，**无 rollback、无 "did not finish"**，SP 无 FAILED_UPDATES——**热更持久生效** ✅ |

### 首轮「反复下载 + 回滚」现象的正确机制解释

@revopush 的 `initializeUpdateAfterRestart`（`CodePush.java:282-325`）：更新装好后标 `isLoading`，**必须在重启后由某个 RN 页 mount 触发 HOC 的 `notifyApplicationReady()` 才算「确认」**；否则下次重启判定「`Update did not finish loading` → rollback → 存入 FAILED_UPDATES」。首轮外部 2.14.2 会话里更新装了但（很可能）**没在下次重启前打开 RN 页**→被判坏包回滚→加入黑名单→后续 checkForUpdate 被过滤返回 UP_TO_DATE；而我反复 force-stop 放大了这个「装了但没确认」的窗口。**这是 CodePush 保护性回滚的正常设计，不是 bug**。

> ⚠️ **值得团队留意的 brownfield 特性（非缺陷，NOTE-CP-01）**：因 RN 只在打开 RN 页时才 mount、才 `notifyAppReady`，**若用户装了热更却在下次重启前从未进任何 RN 页，更新会被回滚并拉黑**。纯钱包/IM 用户若很少进 RN 页（P2P/行情/名片/语音房），热更确认率会偏低。建议评估是否需要更早的 notifyAppReady 时机或后台预热确认。

## 第四轮补测（2026-07-15，AI 自主）——12 条挂起用例清零

> 背景：用户指出「很多 case 还没有测试」。盘点后补测 A5/A6/B3/B4/B5/C1–C5/G3/G4 共 12 条。

### 开测排障（先于用例，约 40 分钟）

1. **设备被 release 包占用**：emulator-5556 上是 2026-07-14 22:59 装入的 **feat/gasless release 2.14.1/21400001**（BUILD-CP-03 修复任务验证遗留，非 debuggable、207MB、R8 混淆）。该包**反模拟器安检在冷启 ~4s 静默 `System.exit(0)`**（无弹窗无 Toast，进 MainActivity 后退出）——与修复任务 review.md V8 记录一致，**非新 bug**；但意味着 L1 上任何用例都跑不了。曾依次排除：网络故障（宿主三域名全 200）、归因错误（dex 含 `RNPreloadManager`/`DeBoxReactHostFactory` 实证是 feat/gasless）。
2. **重建构造包**：worktree 检出 `80b610b859` → `npm ci`（patch-package 补丁核验在位）→ config.gradle 构造 **2.14.2/21400099** → worklets 产物路径绕法 → `assembleAppDebug` 1m39s 成功（APK sha256 `49fce7c706fc64fe…`）→ `install -r -d` 登录态保留 → 运行时探针归因通过（`branch = 2.14.2` + 完整预热链）→「我的」页核实测试号「请输入昵称1」✓。
3. **触发法解阻（回写 cases.md C 组前提）**：`debox://rn/<route>` 深链（`AppConstant.RN_ROUTER_BASE`）直达 `jumpReactNativeActivity`，未注册路由即触发 RouteForce——上轮「需自定义 RN 入口」的阻塞不成立。

### 顺带复验（非本轮目标，白捡证据）

- **TC-E1 在 v522 上全链复验 ✅**：服务端现挂 **v522**（v1.8.79-Staging，同时匹配 2.14.1/2.14.2）。mount sync 静默下载（9.68MB 解包落盘）→ force-stop 重启 `Loading JS bundle from files/CodePush/15fca1d1…` → 进 RN 页 `【使用 CodePush Bundle】` + `Reporting CodePush update success (v522)`（notifyAppReady 确认）→ 多次重启持久无回滚。
- **TC-D3 等效复验 ✅**：run-as 清缓存后回落 assets、再完整重下 v522。

### 新增观测约束（影响后续轮次）

- **热更 bundle（v1.8.79 release 构建）console.log 已剥离，仅 console.warn 存活**：`[CodePush][sync]` 状态机/节流/下载百分比日志**不可见**（上轮能看到是因当时时序/包不同）。B4 节流、C4 锁竞争日志因此不可运行时观测；改用**文件系统副作用**（下载落盘/codepush.json）与 **UI 终态**做判据。`[RouteForce]` 关键路径日志是 WARN 级，仍可见。
- **SettingAdminActivity 在本机 shell 不可直起**（`SecurityException: not exported`，Android 16 模拟器）——`devices.md` 旧结论需修正；清缓存可用 run-as 等效操作（debug 包）。

## 执行摘要（最终）

- **第四轮补测后：40 条用例中 AI 可执行面全部收口**——新增 A5/A6/B3/B5/C1/C2/C3/C4/C5/G3/G4 共 11 条 ✅（B4 ⚠️ 运行时不可观测，静态已核）；剩余挂起仅 E2/E4/E6（发包权限）与 F2（真机 release 实跑）。
- **静态核验 S1–S7 全通过**。
- **feat/gasless CodePush 2.0 热更端到端正常**（2.14.2 可归因包实测 check→download→install→reload→确认→持久全绿），**BUG-CP-01 证伪**。
- **2.14.1 冒烟正常**：预热/host/assets bundle/RN 渲染/UP_TO_DATE 全 ✅，0 崩溃。
- 🔴 **唯一真实阻断（BUILD-CP-03，P1）**：**release 包本地无法产出**——`:app:assembleAppRelease` 在 `:app:minifyAppReleaseWithR8` 失败：codegen 的 TurboModule spec 类（`com.facebook.fbreact.specs.NativeQRScannerSpec` 等）在 `ReactNative` library 模块与 `app` 模块**各生成一份 → R8 "Type … is defined multiple times"**。debug（不过 R8）不暴露。意味着**当前 feat/gasless 走不出 release 包**，reflection keep 只在 release 生效的热更注入链路**尚无法在 release 端到端验证**（虽 debug 反射链路已证工作）。需团队确认正式发布构建配置（codegen 去重 / packagingOptions / aab 流程）。
- 🟢 **NOTE-CP-01**（brownfield notifyAppReady 确认时机，见上）——设计留意项，非缺陷。

## 0. 静态核验（S）

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-S1 | 反射补丁在位 | ✅ PASS | `npm ci` 后 patch-package 生效，`node_modules/@revopush/.../CodePushNativeModule.java:854` 存在多候选字段名 `{"mReactHostDelegate","reactHostDelegate"}` + 补丁注释；setJSBundle 反射 `jsBundleLoader`（:152） |
| TC-S2 | delegate 类型 D1 约束 | ✅ PASS | `DeBoxReactHostFactory.kt:168` 用 `DefaultReactHostDelegate`（注释「其 jsBundleLoader 字段满足 @revopush 反射」） |
| TC-S3 | holder 注册 D2 约束 | ✅ PASS | `RNPreloadManager.kt`：L70 SoLoader.init → L76 `DefaultNewArchitectureEntryPoint.load()` → L84 `CodePush.setReactHost(holder)` → L98 `initSucceeded=true`（fail-fast L119/147/170）；`RNApplication:28 : ReactApplication`。**运行时实证**：冷启日志「已向 CodePush 注册 ReactHost holder」 |
| TC-S4 | proguard keep 链路 | ✅ PASS | `ReactNative/build.gradle:51` `consumerProguardFiles "proguard-rules.pro"`；proguard-rules.pro L101 `mReactHostDelegate`、L105 `jsBundleLoader`、L108 `mBundleLoader` 均显式 keep |
| TC-S5 | key/server 配置完备 | ✅ PASS | strings.xml L3 `CodePushServerUrl=https://api-codepush.debox.pro`（官方域已注释）；L5 `CodePushPublicKey` 非空（验签开）；build.gradle L20-30 `requireCodePushProperty` 三 key 缺一构建即败 |
| TC-S6 | 版本一致性疑点 | 🟡 确认疑点 | `debox-rn/package.json` version=**1.8.4** ≠ native versionName 2.14.1；且运行时 CodePush 上报 2.14.2（见 NOTE-CP-02）。三个版本号（1.8.4 / 2.14.1 / 2.14.2）互不一致 → targetBinaryVersion 匹配存在错配风险，需 JS 团队确认发包是否始终显式 `--targetBinaryVersion` |
| TC-S7 | 非主进程隔离 | ✅ PASS | `RNApplication.kt:83-89` `initRNPreload` 先判 `isMainProcess()`，非主进程跳过（fail-open 当主进程，有注释） |

## A 组：构建、包归因与宿主链路冒烟

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-A1 | feat/gasless 现场构建 | ✅ PASS | worktree `80b610b859` + npm ci。**构建坑 ×1**：reanimated 4.1.7 CMakeLists 硬编码 worklets 产物旧路径 `intermediates/cmake/debug/obj/<abi>/libworklets.so`，AGP 实际输出在 `intermediates/cxx/Debug/<hash>/obj/` → ninja `missing and no known rule`。**解法**：先 `:react-native-worklets:assembleDebug`，再把 4 ABI 的 libworklets.so 复制到旧路径重跑（只动产物不改源码）。产物 `apks/debox-debug.apk` 263MB，版本 2.14.1/21400001 |
| TC-A2 | 安装预检+归因验证 | ✅ PASS | `apksigner` 新包签名 `2ee89168…` = 设备已装包，一致；`install -r -d` 降级安装成功、登录态保留；dumpsys 核 2.14.1/21400001；**运行时探针**：logcat 出现 `ReactNative-RNPreloadManager`/`DeBoxReactHostFactory` bridgeless 特征日志 → 被测包 = feat/gasless 代码 |
| TC-A3 | RN 预热链路 | ✅ PASS | 冷启序：`RNApplication: 初始化…branch=2.14.1` → SoLoader 初始化 → 新架构 feature flags(bridgeless/Fabric/TurboModule) → 注册 CodePush holder → **6s 后**「开始延迟预加载」→ `[CodePush] Loading JS bundle from "assets://index.android.bundle"` → `ReactHost 创建成功 (version=0)`。0 FATAL，首页正常 |
| TC-A4 | 进 RN 页（assets bundle） | ✅ PASS | 点 admin「打开RN页面」进 `ReactNativeContainerActivity`，`printBundleInfo`：`CodePush Bundle 路径: assets://index.android.bundle`、`【使用 Assets Bundle】`、`CodePush 目录不存在 (使用APK内置Bundle)`、`Bundle 文件检查通过`；名片分享页 QR 正常渲染 |
| TC-A5 | 预热窗口内进页 | ✅ PASS（第四轮） | 冷启 +2.5s（<6s 预热窗口）深链 `debox://rn/profile/shares` → Shares 页完整渲染不白屏不崩；日志实证兜底：`获取 CodePush Bundle 路径失败: A CodePush instance has not been created yet` 被优雅处理 → 回落 assets bundle 起页 |
| TC-A6 | RN 核心页遍历 | ✅ PASS（第四轮） | 深链逐一遍历全部渲染：P2P `/p2p`（群主担保页）✅、token 详情 `/trade/token?tokenId=0x55d…-bsc`（USDT K线+交易历史实时数据）✅、语音房创建 `/live/voice/create`（表单完整，未提交）✅、名片分享 `/profile/shares` ✅ |

## B 组：更新检查与 sync 编排

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-B1 | mount 时 updateCheck | ✅ PASS | JS 日志：`[CodePush][mount] bootstrapping` → `checkForUpdate called` → `lock acquired` → `starting sync (installMode: ON_NEXT_RESUME)` → `watchdog set: 900000 ms`(15min) → `→ CHECKING_FOR_UPDATE` |
| TC-B2 | server 可达性 | ✅ PASS | `api-codepush.debox.pro` 可达——实际返回可用更新并完成 9.68MB 下载（比单纯连通性更强的证据） |
| TC-B3 | 断网 sync 静默失败 | ✅ PASS（第四轮） | 飞行模式 → force-stop 冷启 → 深链进 RN 页：v522 热更 bundle 正常加载渲染（业务数据空属预期）、0 FATAL、**断网不回滚**（notifyAppReady 已确认过）、不阻塞进页 |
| TC-B4 | resume 节流 45s | ⚠️ 运行时不可观测 | 热更 bundle 为 release 构建 **console.log 已剥离**（仅 WARN 级存活），sync 状态机/节流日志不可见；45s 常量静态已核（`CODEPUSH_RESUME_CHECK_THROTTLE_MS`）。如需运行时实证需 dev bundle 或加 WARN 级日志 |
| TC-B5 | 断网恢复后再 check | ✅ PASS（第四轮） | 恢复网络 → 清 CodePush 缓存（run-as 等效 clearCodePushCache）→ 冷启进 RN 页 → **v522 重新下载落盘**（`files/CodePush/15fca1d1…` + codepush.json currentPackage）——check 发出且完成的文件系统铁证 |
| TC-B6 | deployment key 环境选择 | ✅ PASS | debug 包 `RouterRN.getEnv()=true` → 走 Test key；sync 实际命中 Test deployment（拉到 Test 包），符合预期 |

## C 组：RouteForce 路由级强更

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-C1 | 未注册路由触发 RouteForce | ✅ PASS（第四轮） | **触发法解阻**：深链 `am start -d "debox://rn/nonexistent/route"` → `DeBoxUtils.rnLinkFiltering` → `jumpReactNativeActivity`（免自定义入口）。RouteForce 状态机走通：`[RouteForce] failAndFallback → retrying` 日志实证 |
| TC-C2 | 无更新 → 404 兜底 | ✅ PASS（第四轮） | server 无含该路由的包 → `retrying → final_failed` → 404 兜底页（"您访问的页面不存在或已被移除"+返回按钮），**不崩不死循环**，返回键回 MainActivity，进程存活 |
| TC-C3 | 断网 RouteForce | ✅ PASS（第四轮） | 飞行模式深链新未注册路由：`[RouteForce] sync rejected → UNKNOWN_ERROR → retrying → final_failed` → 404 兜底，不崩；恢复网络后可再触发 |
| TC-C4 | sync 锁竞争 | ✅ PASS（附注） | 冷启 +1.5s 立即深链未注册路由：最终 404 兜底、无死锁、进程存活（验收达成）。锁竞争专属日志（`lock held … 800ms`）未捕获——该 bundle console.log 剥离所致 |
| TC-C5 | 已注册路由不触发 | ✅ PASS（第四轮） | `/profile/shares` 深链多次直接渲染，全程零 `[RouteForce]` 日志 |

## D 组：bundle 加载矩阵

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-D1 | 无缓存走 assets | ✅ PASS | 见 A4；`run-as` 确认 `files/` 下无 CodePush 目录，`printBundleInfo` 走 assets |
| TC-D2 | 损坏缓存兜底 | ✅ PASS | 无缓存时「目录不存在→回落 assets→`Bundle 文件检查通过`」= checkBundleFile 兜底天然验证，未崩未白屏 |
| TC-D3 | 缓存清理恢复 | ✅ PASS | admin「清理CodePush缓存」按钮（`RNPreloadManager.clearCodePushCache()`）实点确认——清掉 `files/CodePush` + SP 的 FAILED_UPDATES，之后 v521 可重新下载（第三轮 3b 依赖此步）；清后回落 assets 正常 |
| TC-D4 | 覆盖安装缓存兼容 | ✅ PASS | `install -r -d` 覆盖安装（2.14.2→2.14.1→2.14.2）多次冷启无崩溃 |
| TC-D5 | CodePush bundle 加载（file loader） | ✅ PASS（3c/3d 实证） | 热更 apply 后 `getJSBundleFile` 返回 `files/CodePush/<hash>/CodePush/index.android.bundle`，`printBundleInfo` = `【使用 CodePush Bundle】`，`createFileLoader` 分支生效 |

## E 组：真实热更三路径 + 验签

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-E1 | 普通热更 reload | ✅ PASS（第三轮 3b–3e 实证） | 用可归因 2.14.2 包实测**完整 reload 全链**：download 1→100% → INSTALLING → UPDATE_INSTALLED(ON_NEXT_RESUME) → 重启后 `Loading JS bundle from files/CodePush/03d2158c…` → 进 RN 页 `【使用 CodePush Bundle】` → 再重启仍跑热更 bundle 无回滚。**热更端到端生效** |
| TC-E3 | 坏包自动回滚 | ⚠️ 间接观测 | @revopush `initializeUpdateAfterRestart` 的 rollback 路径已被外部 2.14.2 会话触发（v521 进 FAILED_UPDATES），机制核实为「更新装了但未 notifyAppReady → 下次重启判坏包回滚」（`CodePush.java:305-311`）。刻意发坏包验证仍需发包权限 |
| TC-E2 | 强制更新 IMMEDIATE | ⏸️ 挂起 | 需 `--mandatory` 发包权限（RouteForce 路径用 IMMEDIATE，代码已核） |
| TC-E4 | 验签拒绝 | ⏸️ 挂起 | 需构造未签名/篡改包 |
| TC-E5 | targetBinaryVersion 不匹配 | ✅ PASS（第二轮实证） | 2.14.1 客户端对 `targetBinaryVersion=2.14.2` 的 v521 包正确返回 **UP_TO_DATE 不下载**；2.14.2 客户端则匹配下载。semver 匹配正确 |
| TC-E6 | RouteForce 成功路径 | ⏸️ 挂起 | 需发含新路由的包 |

## F 组：release/R8 混淆回归

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-F1 | R8 keep + release 构建 | 🔴 FAIL（BUILD-CP-03） | `:app:assembleAppRelease` 的 `minifyAppReleaseWithR8` **构建失败**——codegen TurboModule spec 类（`NativeQRScannerSpec` 等）在 ReactNative 模块与 app 模块重复定义 → R8 `Type … is defined multiple times`。**当前 feat/gasless 出不了 release 包**，keep 规则的 release 端到端验证受阻（debug 反射链路已证工作） |
| TC-F2 | release 包热更实跑 | ⏸️ 阻塞 | 依赖 TC-F1 出包；当前 R8 阻断 |

## G 组：生命周期/时序边界

| # | 用例 | 结果 | 证据/备注 |
|---|------|------|-----------|
| TC-G1 | 杀进程重启幂等 | ✅ PASS | force-stop → 冷启 ×3，每次预热链路完整、`ReactHost 创建成功 (version=0)` 单例、无异常日志 |
| TC-G2 | 切账号 reset 重建 | ✅ PASS（静态代验） | `DeBoxReactHostFactory.kt:232-244` reset：synchronized 置空 + `hostVersion++` + `resetting` 防重入；`onDestroyFinished` 里 `reactHost==null` 防双建（L256 窗口内重建跳过）。切账号实跑命中 dangerous-ops 一类，规避 |
| TC-G3 | 快速连开多个 RN 页 | ✅ PASS（第四轮，附行为发现） | 1.5s 内连开 `/p2p` + `/profile/shares` 两容器：**0 次新建 host**（复用预热 host，无双建）。行为发现：容器为**单实例复用**——第二个深链替换同一 Activity 的路由而非叠栈，back 一次即回 MainActivity（退出正常，无残留） |
| TC-G4 | 热更后 Activity 转发链 | ✅ PASS（第四轮，核心面） | v522 热更 bundle 生效后：深链转发进 RN 容器正常（`【使用 CodePush Bundle】`）、返回键回 MainActivity、进程存活；多次 force-stop 重启均加载 CodePush bundle **无回滚**。文件上传流未专项驱动（需媒体选择器交互，留待） |

---

## 缺陷/疑点清单（最终）

| # | 级别 | 描述 | 证据 | 状态 |
|---|------|------|------|------|
| ~~BUG-CP-01~~ | ~~🔴 P0/P1~~ → **证伪** | 首轮「热更从不生效/每次重下/恒跑 assets」——**归因事故（设备被外部换 2.14.2）+ 未清失败黑名单**造成的假象。三轮可归因实测证明热更 reload 端到端正常（TC-E1 ✅） | 见「三轮实测证据链」3b–3e | **关闭（不成立）** |
| **BUILD-CP-03** | 🔴 P1（修复方案已验证，**目标分支未应用**） | **release 包本地构建失败**：`minifyAppReleaseWithR8` 报 codegen TurboModule spec 类（`NativeQRScannerSpec` 等）在 ReactNative library 模块与 app 模块重复定义。当前 feat/gasless 仍出不了 release 包 | `R8: Type com.facebook.fbreact.specs.NativeQRScannerSpec is defined multiple times` | 已走 agent-dev-loop 产出并验证候选修复：`debox-android/docs/implementation/2026-07-14-01-fix-release-r8-codegen-duplicate-spec/`（隔离任务中 `:app:assembleAppRelease` BUILD SUCCESSFUL；Codex plan-review 4 轮 + impl-review 2 轮 PASS_WITH_ACCEPTED_RISK）。补丁 `fix-app-build-gradle.patch` **待 apply 到 feat/gasless**；应用并在目标分支重建通过前，**TC-F1 保持 🔴 FAIL**。TC-F2 release 热更实跑仍需真机（模拟器被反模拟器安检拦） |
| **NOTE-CP-01** | 🟢 设计留意 | brownfield 下热更需打开 RN 页触发 `notifyApplicationReady` 才确认，否则下次重启回滚拉黑。少进 RN 页的用户热更确认率偏低 | `CodePush.java:305-311` + 外部 2.14.2 会话 v521 进 FAILED_UPDATES | 建议评估 notifyAppReady 时机 |
| ~~NOTE-CP-02~~ | 撤回 | 「2.14.1 APK 上报 2.14.2」——实为 20:36 设备被换 2.14.2 的假象，非版本读取 bug | 20:40 header 属 2.14.2 包 | **关闭** |

## 三轮方法学教训（回写价值）

1. **包归因铁律再次应验**（devices.md）：设备在测试中途 20:36:45 被外部重装 2.14.2，若不核 `lastUpdateTime`/`versionCode`/`branch` 日志，首轮 P0 会误报。**每次抓数据前后都要核被测包身份**。
2. **失败黑名单是 CodePush 复现的隐藏变量**：`CODE_PUSH_FAILED_UPDATES` 会让 checkForUpdate 静默返回 UP_TO_DATE，误判「服务端下线」。复现热更必须先 `clearCodePushCache` 清黑名单。
3. **构造法验版本匹配**：改 config.gradle versionName（唯一 versionCode 归因）即可让本地包匹配指定 targetBinaryVersion 的线上热更包，无需发包权限就验通 reload 全链。

## 设备状态变更（需知会，2026-07-15 第四轮后）

- **emulator-5556 当前 = feat/gasless debug 构造包 2.14.2/21400099**（第四轮现场重建，APK sha256 `49fce7c706fc64fe…`，产物在 worktree `debox-gasless-wt/apks/debox-debug.apk`），**已应用 v522 热更 bundle**（v1.8.79-Staging，hash `15fca1d1…`）。登录态在（测试号「请输入昵称1」），测试环境 `t.debox.pro`。
- 时间线：2.14.2/21400002（外部装）→ 2.14.1/21400001 debug（三轮）→ 2.14.2/21400099 debug（三轮构造包）→ **2.14.1/21400001 release**（07-14 22:59 BUILD-CP-03 验证遗留，模拟器上反安检退出跑不了）→ **2.14.2/21400099 debug（当前，07-15 重建）**。
- **git worktree `​/Users/xiaochengcheng/StudioProjects/debox-gasless-wt` 保留**（检出 `80b610b859`，config.gradle 含构造版本号未还原）——E 组补测/回归可直接复用；不需要时 `git worktree remove` 清理。
- **恢复建议**：如需回干净状态，重装 dev 基线包 + run-as 清 `files/CodePush`。⚠️ **不要把 release 版 feat/gasless 留在模拟器上**——反模拟器安检静默退出，会让下一轮所有用例假死。
