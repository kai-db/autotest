# CodePush 2.0（@revopush + RN 新架构 bridgeless 热更）— 本轮测试用例（2026-07-14）

> 被测对象：`debox-android` **`origin/feat/gasless`** 分支接入的 CodePush 2.0 热更链路
> （`@revopush/react-native-code-push@1.5.0` + RN 0.78.3 新架构 Fabric/bridgeless + Hermes + 自建 RevoPush 服务）。
> 本文件按 `TEST_CASES.md` 模板 + `TEST_GUIDE.md` 第七节设备阶梯落地为本环境可执行用例集。
> 优先级：P0 = 必测阻塞项 / P1 = 重要 / P2 = 边界。设备：L1 = 普通模拟器 / L3 = 真机 / S = 静态（代码/构建核验，不跑 App）。
> 执行方式：AI 自主 / ⏸️ 外部依赖（发热更包需 CLI 账号+签名私钥，属 §7.6 secret 注入白名单）/ 静态代验。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | CodePush 2.0 热更链路：bridgeless 反射注入 / bundle 加载矩阵 / sync 编排（静默+强更+RouteForce）/ 回滚兜底 / proguard keep / 版本匹配 |
| App 包名 | `com.tm.security.wallet`（所有 variant 同包名；`USE_METRO=false` 时 debug 不加 `.test` 后缀） |
| 被测包 | `origin/feat/gasless`（tip `80b610b859`，versionName **2.14.1** / versionCode **21400001**）现场构建 appDebug；⚠️ 低于已装 2.14.2(21400002) → 装包用 `install -r -d` 保数据降级 |
| 仓库路径 | native：`/Users/xiaochengcheng/StudioProjects/debox-android`（勿切分支，用 git worktree）；JS：`/Users/xiaochengcheng/StudioProjects/debox-rn`（branch `feat/rn-new-arch`） |
| 测试方式 | Claude Code + mobile-mcp / adb logcat（`ReactNativeJS`（JS logger）+ `CodePush`/`ReactNative` native tag + `printBundleInfo` 输出）+ 静态代码核验 |
| 设备 | L1 = emulator-5556（Pixel，测试号 `10b92305`，测试环境 `t.debox.pro`，GeeGuard 已加白——本包无 GeeGuard 亦无碍）；L3 真机本轮不需要（无厂商 ROM 相关点） |
| 前置条件 | 模拟器登录态在；开测核实「我的」页账号 = 测试号；`local.properties` 三把 CODE_PUSH key 已配、`USE_METRO=false`；`debox-rn` 与 worktree `ReactNative/` 均 `npm ci` 就绪 |

---

## 代码事实基线（探索结论，用例判定依据）

来自 `origin/feat/gasless`（native）+ `debox-rn` `feat/rn-new-arch`（JS）代码探索（2026-07-14）：

1. **host 架构**：bridgeless `ReactHost`（非 ReactNativeHost/RIM）。`DeBoxReactHostFactory` 单例建 host：`CodePush.getInstance(key, ctx, RouterRN.getEnv())` → `CodePush.getJSBundleFile("index.android.bundle")` → 按路径前缀选 `JSBundleLoader`（`assets://` → assetLoader / 文件路径 → fileLoader / 兜底 assetLoader）→ `DefaultReactHostDelegate` → `ReactHostImpl`。
2. **两处反射 = 热更命脉**：
   - (a) `CodePushNativeModule.getReactHostDelegate` 反射 `ReactHostImpl.mReactHostDelegate`（RN 0.78.3 字段名）——补丁 `@revopush+react-native-code-push+1.5.0.patch` 多候选字段名修复（上游只查 `reactHostDelegate` → 必 miss → 热更**静默失效**）；
   - (b) `setJSBundle` 反射 `DefaultReactHostDelegate.jsBundleLoader` 且 miss 时**静默忽略**（上游原样）⇒ D1 硬约束：delegate 必须是 `DefaultReactHostDelegate`。
3. **holder 注册**：`RNPreloadManager.init()` 里 `CodePush.setReactHost(holder)`；`RNApplication` 实现 `ReactApplication` 为第二兜底（D2：两者皆无 → ClassCastException 崩）。
4. **预热**：`RNApplication.onCreate` → 非主进程跳过 → 选 key → `RNPreloadManager.init`（SoLoader → `DefaultNewArchitectureEntryPoint.load()` → setReactHost → `initSucceeded=true` fail-fast 门禁）→ **延迟 6s** `preload()`。
5. **key 选择**：`RouterRN.getEnv()`（=debug）→ `CodePushTestKey`；release 且 `DISPLAY_VERSION` 含 beta → `CodePushBetaKey`；否则 Production。Server=`https://api-codepush.debox.pro`（strings.xml `CodePushServerUrl`）；`CodePushPublicKey` 已配 → bundle 验签开启。
6. **USE_METRO 开关**：`BuildConfig.USE_METRO`（local.properties，默认 false）。false → 走 CodePush bundle + `useDevSupport=false`；true → assets bundle + Metro。
7. **bundle 路径**：内置 `assets://index.android.bundle`（分支内预置 12.7MB）；热更下载至 `filesDir/CodePush/`（元数据 `codepush.json`、包内 `app.json`）。`ReactNativeContainerActivity.checkBundleFile()` 起页前校验，缺失/无效 → **Toast + finish 不崩**；`printBundleInfo()` 全量打 bundle 路径/大小/CodePush 目录树。
8. **JS 侧 sync 编排**（`debox-rn/src/index.tsx`，HOC MANUAL + mount 时 `checkForUpdate`，`notifyAppReady` 由 HOC 自动）：
   - 普通更新：静默下载，`installMode=ON_NEXT_RESUME`；强更 `mandatoryInstallMode=IMMEDIATE`；
   - resume 检查节流 45s（`CODEPUSH_RESUME_CHECK_THROTTLE_MS`）；普通 sync watchdog 15min；下载进度按 1/25/50/75/100% 打日志；
   - **RouteForce**：`initialRoute` 未在当前 bundle 注册 → 路由级强制 sync（IMMEDIATE）+ 进度页 → 失败重试（500ms/次，有限次）→ `final_failed` → 404 兜底页；sync 锁竞争 800ms 重试。
9. **proguard keep**（`ReactNative/proguard-rules.pro`，经 `consumerProguardFiles` 传入 app R8）：keep `ReactHostImpl.{mReactHostDelegate,reactHostDelegate}`、`DefaultReactHostDelegate.jsBundleLoader`、RIM 旧架构字段。**仅 release/R8 生效，debug 测不出回归**。
10. **版本匹配**：targetBinaryVersion ↔ native versionName(2.14.1)；⚠️ 静态疑点：`debox-rn/package.json` version=**1.8.4** ≠ 2.14.1（CODEPUSH_SETUP.md 要求一致，发包若靠缺省 targetBinaryVersion 会错配）。
11. **reset 链路**：切账号/登出 → `RNPreloadManager.reset()` → 旧 host 异步 destroy 后串行重建。⚠️ 切账号/登出命中 `dangerous-ops.md` 一类 → 本轮**不实跑**，静态代验。
12. **构建**：分支已 `apply plugin: "com.facebook.react"`（§7.7 shim 过时勿加）；构建需 `ReactNative/` 目录 `npm ci`；debug 变体 codepush.gradle 跳过 bundling（brownfield patch）。

---

## 0. 静态核验（S）——不改 autotest 框架代码，框架前置检查以静态核验替代

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-S1 | 反射补丁在位 | 核对 worktree `patches/@revopush+react-native-code-push+1.5.0.patch` 已被 patch-package 应用到 `node_modules` | `CodePushNativeModule.getReactHostDelegate` 含 `mReactHostDelegate` 候选 + 继承链查找 + miss 留痕日志 | P0 | S | AI 自主 |
| TC-S2 | delegate 类型 D1 约束 | 核对 `DeBoxReactHostFactory` 使用的 delegate 类型 | 为 `DefaultReactHostDelegate`（非自定义子类/匿名类） | P0 | S | AI 自主 |
| TC-S3 | holder 注册 D2 约束 | 核对 `RNPreloadManager.init` 调用序 | `CodePush.setReactHost` 在 host 首次 start 前注册；`RNApplication : ReactApplication` 成立 | P0 | S | AI 自主 |
| TC-S4 | proguard keep 链路 | 核对 `ReactNative/build.gradle` `consumerProguardFiles` 指向 `proguard-rules.pro`（非不存在的 consumer-rules.pro）；规则含 2 处反射字段 keep | 单一真相源成立；`mReactHostDelegate`/`jsBundleLoader` 均显式 keep | P0 | S | AI 自主 |
| TC-S5 | key/server 配置完备 | 核对 strings.xml `CodePushServerUrl`/`CodePushPublicKey`、build.gradle `requireCodePushProperty` 三 key 强校验 | server=自建域；公钥非空（验签开）；缺 key 构建即败（fail-closed） | P0 | S | AI 自主 |
| TC-S6 | 版本一致性疑点 | 比对 `debox-rn/package.json` version 与 native versionName | **已知不一致（1.8.4 vs 2.14.1）**——记录为疑点上报：确认发包流程是否始终显式 `--targetBinaryVersion`；若靠缺省则热更永不命中 | P1 | S | AI 自主（上报项） |
| TC-S7 | 非主进程隔离 | 核对 `RNApplication.initRNPreload` 进程判断 | `:pushservice` 等子进程不初始化 RN/CodePush | P1 | S | AI 自主 |

## A 组：构建、包归因与宿主链路冒烟（P0 基座）★

> 包归因铁律（devices.md）：现场构建 + versionName + 运行时探针 + sha256 三对齐。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-A1 | feat/gasless 现场构建 | `git worktree add` 检出 `origin/feat/gasless` → `ReactNative/ npm ci` → `NODE_PATH=… ./gradlew :app:assembleAppDebug` | BUILD SUCCESSFUL；产物 versionName=2.14.1/versionCode=21400001；记录 APK sha256 | P0 | S | AI 自主 |
| TC-A2 | 安装预检+归因验证 | `apksigner verify` 比对签名与已装包一致 → `adb install -r -d` → `dumpsys package` 核版本 → 冷启 | 签名一致；降级安装成功且**登录态保留**；冷启进 MainActivity 无 FATAL；运行时探针（logcat 出现 bridgeless/RNPreload 特征日志）证明是新包 | P0 | L1 | AI 自主 |
| TC-A3 | RN 预热链路 | 冷启 → logcat 观察 RNPreloadManager/SoLoader 序列 | 初始化序：SoLoader → NewArchEntryPoint → setReactHost → ~6s 后 preload；无 crash/ANR | P0 | L1 | AI 自主 |
| TC-A4 | 进 RN 页（首装 assets bundle） | 进入 RN 页（如「我的」→ 名片分享 `/profile/shares` 或行情 token 详情）→ 看 `printBundleInfo` | 页面正常渲染；bundle 路径 = `assets://index.android.bundle`（无 CodePush 缓存时）；`filesDir/CodePush` 目录树打印 | P0 | L1 | AI 自主 |
| TC-A5 | 预热窗口内进页 | 冷启后 **6s 内**立刻深链/路由进 RN 页 | 未预热完成时兜底同步建 host，页面正常起，不白屏不崩 | P1 | L1 | AI 自主 |
| TC-A6 | RN 核心页遍历 | 依次进 P2P、token 详情、语音房创建、名片分享 | 全部正常渲染（热更影响面基线，供 E 组对照） | P1 | L1 | AI 自主 |

## B 组：更新检查与 sync 编排（无需发包即可观测）★

> 观测：`adb logcat` 过滤 `ReactNativeJS`——JS logger 打 `[CodePush][sync]` 状态机、下载百分比、watchdog、resume 节流。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-B1 | mount 时 updateCheck | 冷启 → 进 RN 页 → 看 JS 日志 | `[CodePush][mount] bootstrapping` → `CHECKING_FOR_UPDATE` → `UP_TO_DATE`（Test deployment 无 pending 包时）；无异常抛出 | P0 | L1 | AI 自主 |
| TC-B2 | server 可达性 | 模拟器 shell 内探测 `api-codepush.debox.pro` 443 + 观察 updateCheck 结果 | 域名可解析可达；updateCheck 有响应（非超时）；若不可达记录并转 TC-B3 口径 | P0 | L1 | AI 自主 |
| TC-B3 | 断网 sync 静默失败 | 飞行模式 → 冷启 → 进 RN 页 | RN 页正常渲染（用当前 bundle）；sync 报错被吞（日志有 failed 记录），**不阻塞进页、不 crash** | P0 | L1 | AI 自主 |
| TC-B4 | resume 节流 45s | 前后台快速切 3 次（<45s 内）→ 再等 >45s 切一次 | 45s 内不重复 checkForUpdate（日志节流命中）；超 45s 后触发新 check | P1 | L1 | AI 自主 |
| TC-B5 | 断网恢复后再 check | 接 B3：恢复网络 → 后台/前台切换（>45s） | 恢复后 checkForUpdate 正常发出并完成 | P1 | L1 | AI 自主 |
| TC-B6 | deployment key 环境选择 | debug 包冷启日志/请求核对所用 key | debug → `CodePushTestKey`（Test deployment）；不误用 Production key | P1 | L1 | AI 自主 |

## C 组：RouteForce 路由级强更（不发包可触发失败/兜底路径）★

> 触发法（07-15 已验证可行）：深链 `adb shell am start -a android.intent.action.VIEW -d "debox://rn/<未注册路由>"`（`AppConstant.RN_ROUTER_BASE` → `jumpReactNativeActivity`）；直接 `am start` 组件不可行（Activity 非 exported 且 ARouter 嵌套 Bundle shell 传不了）。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-C1 | 未注册路由触发 RouteForce | 深链/am start 传 `initialRoute=/nonexistent/route` | 进入 RouteUpdateProgressScreen（checking 进度页）→ IMMEDIATE sync 发起（日志 `[RouteForce] starting sync`） | P0 | L1 | AI 自主 |
| TC-C2 | 无更新 → 404 兜底 | 接 C1：server 无含该路由的新包 | 重试有限次（retrying 状态、500ms 间隔）→ `final_failed` → 404 页展示，**不崩不死循环**；返回键可退出 | P0 | L1 | AI 自主 |
| TC-C3 | 断网 RouteForce | 飞行模式下触发 C1 | 快速失败/超时（`ROUTE_FORCE_UPDATE_TIMEOUT_MS`）→ 404 兜底；恢复网络后重进可再触发 | P1 | L1 | AI 自主 |
| TC-C4 | sync 锁竞争 | 冷启立即（mount sync 进行中）触发 RouteForce | 锁被 mount sync 持有 → 日志 `lock held by … retrying in 800ms` → 不死锁、最终完成或兜底 | P2 | L1 | AI 自主 |
| TC-C5 | 已注册路由不触发 | 正常进 `/profile/shares` | 不进 RouteForce（无 `[RouteForce]` 日志），直接渲染 | P1 | L1 | AI 自主 |

## D 组：bundle 加载矩阵与损坏兜底

> `filesDir/CodePush` 操作用 `run-as com.tm.security.wallet`（debug 包模拟器可写，真机 Knox 禁写——本轮 L1 无此问题）。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-D1 | 无缓存走 assets | `run-as` 确认无 `filesDir/CodePush` → 进 RN 页 | fileLoader 不启用，`printBundleInfo` 显示 `assets://`；渲染正常 | P0 | L1 | AI 自主（与 A4 合并执行） |
| TC-D2 | 伪造损坏缓存元数据 | `run-as` 写入伪造 `filesDir/CodePush/codepush.json`（指向不存在包）→ force-stop → 冷启进 RN 页 | `getJSBundleFile` 回退 assets 或 `checkBundleFile` 拦截 Toast+finish；**不 crash、不白屏死等**；清缓存后恢复 | P0 | L1 | AI 自主 |
| TC-D3 | 缓存目录残留清理 | 接 D2：删除 `filesDir/CodePush` + 清 `CodePush` SP → 冷启 | 回到首装态（assets bundle）；RN 页正常 | P1 | L1 | AI 自主 |
| TC-D4 | 覆盖安装后缓存兼容 | 已有伪造/残留缓存时 `install -r -d` 重装 → 冷启 | 不因缓存与新 APK 不匹配崩溃（`autoCleanCodePushCacheInDebug` 未启用——观察实际行为并记录） | P2 | L1 | AI 自主 |

## E 组：真实热更三路径（reload / mandatory / rollback）+ 验签 —— ⏸️ 外部依赖为主

> 发真实热更包需：RevoPush CLI 登录态（账号 token）+ bundle 签名私钥（`CodePushPublicKey` 验签开启）→ 属 §7.6「secret 注入」白名单，**AI 不能自主完成发包**。本轮先跑 A–D 组把「本地可验面」做满；E 组等人工提供 CLI 权限后补测（步骤已写好可直接执行）。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-E1 | 普通热更 reload | `debox-rn` 构建 bundle（`npm run build:codepush:android`）→ CLI 发到 **Test** deployment（targetBinaryVersion=2.14.1，签名）→ App 冷启进 RN 页 → 静默下载 → 退后台再回 | 下载进度日志 1→100%；`ON_NEXT_RESUME` 生效：回前台后新 bundle 生效；`printBundleInfo` 路径变 `filesDir/CodePush/…`；**验证跑的是新内容非静默跑旧包** | P0 | L1 | ⏸️ 外部依赖（发包） |
| TC-E2 | 强制更新 IMMEDIATE | CLI `--mandatory` 发包 → App 触发 check | mandatory 走 IMMEDIATE：下载完立即重启生效，不可跳过 | P0 | L1 | ⏸️ 外部依赖 |
| TC-E3 | 坏包自动回滚 | 发一个启动即抛错的 bundle（notifyAppReady 达不到）→ 冷启 | 崩溃/白屏后**下次启动自动回滚**旧 bundle；不进崩溃死循环；server 标记 failed 不再下发 | P0 | L1 | ⏸️ 外部依赖 |
| TC-E4 | 验签拒绝未签名包 | 发未签名/篡改 bundle（或构造法：本地 mock server 下发无签名包） | 安装被拒（signature verification failed），继续用旧 bundle，不崩 | P1 | L1 | ⏸️ 外部依赖 / P2 构造法 |
| TC-E5 | targetBinaryVersion 不匹配不下发 | 发 targetBinaryVersion=9.9.9 的包 → App check | `UP_TO_DATE`，不下载 | P1 | L1 | ⏸️ 外部依赖 |
| TC-E6 | RouteForce 成功路径 | server 有含新路由的包时深链进该新路由 | RouteForce sync 下载 → IMMEDIATE 重载 → 直接进入新页（非 404） | P1 | L1 | ⏸️ 外部依赖 |

## F 组：release/R8 混淆回归（keep 生效性）

> debug 不混淆测不出反射回归；release 签名走正式流程（§7.7 CLI 只覆盖 debug）。本地先做 R8 静态验证，release 实跑标 ⏸️。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-F1 | R8 keep 静态验证 | worktree 跑 `:app:minifyAppReleaseWithR8`（或等价任务，不需安装）→ 查 `seeds.txt`/`mapping.txt`/`configuration.txt` | `mReactHostDelegate`/`jsBundleLoader` 在 seeds 中且 mapping 未改名；consumer 规则进入 app R8 合并配置 | P0 | S | AI 自主（可构建 R8 时） |
| TC-F2 | release 包热更实跑 | 正式签名 release 包 + Production/Beta key 实测 E1 全流程 | 同 TC-E1 | P1 | L3 | ⏸️ 外部依赖（正式签名+发包） |

## G 组：生命周期/时序边界

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-G1 | 杀进程重启幂等 | force-stop → 冷启 ×3 | 每次预热链路完整走通；host 单例不重复建；无内存/句柄异常日志 | P1 | L1 | AI 自主 |
| TC-G2 | 切账号 reset 重建 | 切账号 → `RNPreloadManager.reset()` → 旧 host destroy → 再进 RN 页 | ⚠️ 切账号命中 dangerous-ops 一类 → **不实跑**；静态核验 reset 串行化实现（异步 destroy 完成后重建、hostVersion 递增） | P1 | S | 静态代验 |
| TC-G3 | 快速连开多个 RN 页 | 连续快速打开 2–3 个 RN 容器（整页+透明弹窗） | 共享同一 host；无双建；返回键逐层退出正常 | P2 | L1 | AI 自主 |
| TC-G4 | 热更后 Activity 转发链 | E1 完成后验文件上传/深链/返回键 | bridgeless host 上转发正常（依赖 E1，随 E 组补测） | P2 | L1 | ⏸️ 随 E 组 |

---

## 统计

| 分类 | 用例数 | P0 | P1 | P2 | AI 自主可执行 |
|------|--------|----|----|----|----|
| 0 静态核验 | 7 | 5 | 2 | 0 | 7 |
| A 构建/归因/冒烟 | 6 | 4 | 2 | 0 | 6 |
| B sync 编排 | 6 | 3 | 3 | 0 | 6 |
| C RouteForce | 5 | 2 | 2 | 1 | 5 |
| D bundle 矩阵 | 4 | 2 | 1 | 1 | 4 |
| E 真实热更 | 6 | 3 | 3 | 0 | 0（⏸️ 发包权限） |
| F R8/release | 2 | 1 | 1 | 0 | 1 |
| G 生命周期 | 4 | 0 | 2 | 2 | 2（+1 静态代验） |
| **合计** | **40** | **20** | **16** | **4** | **31** |

> 执行顺序：0 → A（A1/A2 是全部实跑用例的前置）→ B → C → D → G → F1；E 组/F2 挂起待人工提供发包权限（白名单 secret 注入），届时按已写步骤补测。
