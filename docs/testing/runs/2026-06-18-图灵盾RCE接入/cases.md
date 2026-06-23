# 腾讯海外 RCE（图灵盾 TuringShield）接入 — 测试用例

> 被测分支：debox-android `feat/turingshield-rce`（基线 `release`，版本 2.13.1 / 21300001）
> 改动核心：接入腾讯图灵盾设备安全 SDK（AAR v2.93.9），取 deviceToken 后**双通道注入**——① 通用 **header `rceDeviceToken`（驼峰）** 注入所有 DeBox 自有域名业务请求；② C1 在 **4 个风控场景**（登录/注册、抢红包、进语音房）的请求 **body 额外带 `rce_device_token`（蛇形）**，后端口径 **body 优先、header 兜底**。客户端不做加解密/签名/风控决策（全在后端）。
> 增量提交：`3104223c4f`（C1 四场景实时 token 接入 + C4 hostUrl 多地址容灾）—— 本次优化针对该提交，上轮 06-18 仅覆盖 header 通道，**body 通道为本轮新增待测面**。
> 依据文档：debox-android `docs/rce/腾讯海外RCE图灵盾-Android接入方案.md`（§6 落地设计 / §7 错误码 / §8 测试计划）、`docs/rce/腾讯海外RCE图灵盾-待确认事项总清单.md`（§6 契约 / §6.9 联调清单）
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 腾讯图灵盾 RCE 接入（SDK 初始化 / token 预热缓存 / header 注入 / 降级 / 隐私 gate / ABI·权限·混淆） |
| App 包名 | `com.tm.security.wallet` |
| 仓库路径 | `/Users/xiaochengcheng/StudioProjects/debox-android`（分支 `feat/turingshield-rce`） |
| 测试方式 | 构建层 gradle 命令（自动）+ A 层 JVM 单测（受限）+ B 层真机（mobile-mcp / `adb logcat` / 抓包 mitmproxy/charles） |
| 设备 | 真机 DEBUG 构建（测试时填型号/序列号；当前无连接设备） |
| 前置条件 | 设备已解锁、**已登录、测试环境、网络在线**；`local.properties` 已注入 `RCE_CHANNEL`/`RCE_HOST_URL`/`RCE_APPID` |

---

## 改动逻辑分析（测试依据）

### 客户端数据流（仅 Android 侧职责）

```
Application.onCreate
  └─ StartupTaskManager(MEDIUM 子线程) → TuringShieldInitTask.execute
       └─ PrivacyConsent.isAgreed()==false → return（gate 拦截，SDK 不 init）
       └─ ==true → TuringShieldManager.init(app)
            └─ TuringSDK.createConf(app, ITuringPrivacyPolicy{ userAgreement()=isAgreed() })
                 .channel(BuildConfig.RCE_CHANNEL)            // int，缺失=-10018 / 错误=-2014
                 .hostUrl(*parseHostUrls(BuildConfig.RCE_HOST_URL))  // C4：逗号分隔多地址容灾，按序探测首个可用；空/单值行为不变
                 [.appid(RCE_APPID) 仅非空时]
                 .build().init():Int
                 └─ ret==0 → inited=true → refreshTokenAsync(retriesLeft=2)（子线程预热）
                      └─ reqRiskDetectV3(RiskDetectReq.Builder().build())  // 默认 cache=true
                           └─ errorCode==0L → cachedToken = deviceToken
                           └─ errorCode!=0L / Throwable → LogUtils.w/e + retryPrewarm（仅 token 仍空且有次数时退避 2s 重试，最多 1+2 次）
                 └─ ret!=0 → LogUtils.w("TuringSDK.init failed ret=$ret")（不吞错）
                 └─ catch(Throwable) → LogUtils.e（兜住 SO 加载 UnsatisfiedLinkError，不逃逸启动链路）

业务请求（Retrofit/OkHttp）
  ├─ ① 通用 header 注入（所有 DeBox 域名）：HeadInterceptor（仅 isDeBoxHost）→ HeaderUtils.getHashMap()
  │     └─ val t = TuringShieldManager.getDeviceToken()   // O(1) 读 @Volatile 缓存，非阻塞
  │        if (t.isNotEmpty()) hash["rceDeviceToken"] = t  // header key=驼峰；仅非空注入，空串=降级不注入 key
  └─ ② C1 四风控场景 body 注入（后端 body 优先、header 兜底）：
        app_login/register → AppCacheManager.login | onchain_box_receive → LuckyBoxDialogFragment（抢红包，live+普通两路）| live_room_join → JoinSpaceDialogFragment.join
        └─ val rceToken = getDeviceToken()                 // 当次用「缓存」token（O(1) 非阻塞，秒抢不拖慢）
           TuringShieldManager.fetchRealtimeToken{ _,_-> } // fire-and-forget：仅 cache=false 刷新缓存供「后续」，当次请求不等它
           if (rceToken.isNotEmpty()) body.put("rce_device_token", rceToken)  // body key=蛇形，4 处裸字符串硬编码（无常量）
```

### 关键不变量（用例验证目标）

1. **getDeviceToken() 永不阻塞网络线程**：只读 `@Volatile cachedToken`，绝不在拦截器同步调 SDK（主线程调 SDK 返回 `-10008`）。
2. **silent failure 零容忍**：init 失败 / 取 token 失败的错误码必须落 logcat（`TuringShield` tag，对照 §7 错误码），禁止空 catch / 静默吞错。
3. **降级不崩溃不阻断**：任何失败 → `cachedToken` 维持空串 → header 缺省 → 业务请求照常 200，App 不崩溃、首页正常展示。
4. **域名隔离防外泄**：`isDeBoxHost` 保证 token 只注入 DeBox 自有域名，第三方域名**不带** `rceDeviceToken`（body 的 `rce_device_token` 仅在 4 场景的 DeBox API 出现，同样不外泄）。
5. **隐私 gate**：`PrivacyConsent.isAgreed()==false` → SDK 不初始化（不应反复触发 `-10019`）。
6. **双字段名精确**：① header key 精确为 `rceDeviceToken`（驼峰）；② C1 body key 精确为 `rce_device_token`（蛇形，4 处硬编码无常量，**任一处拼错 = 该场景风控空跑、请求仍 200 的隐性故障**）。两者拼写均不可偏差。
7. **C1 非阻塞 + 同源**：4 场景取的是缓存 token（不阻塞请求发出），`fetchRealtimeToken` 仅刷新缓存供后续；同一请求若同时带 header 与 body，**两值应同源一致**（均来自 `cachedToken`）。⚠️ 首次进场景时缓存若仍空 → 当次 body/header 均缺省，属已知 C5 非阻塞决策，**非 FAIL**。
8. **密钥红线**：`SecretId`/`SecretKey`/ 腾讯云 `AppId` 不得出现在客户端代码/配置/构建脚本/日志/提交。
9. **启动不拖慢**：init 挂 MEDIUM 子线程，冷启动无明显劣化、风控非首帧必需。

### ⚠️ 本轮测试的前置约束（影响判定口径）

- **★ APK 时效自检（真机层第一道硬 gate，本轮血泪教训）**：真机抓包前**必须确认设备上的 APK 构建/安装时间 ≥ 被测提交时间**，否则是「拿旧包测新逻辑」的假阴性。核验：`adb shell dumpsys package com.tm.security.wallet | grep lastUpdateTime` 对比 `git show -s --format=%ci <被测提交>`；不满足则先 `:app:assembleAppDebug` + `adb install -r`（保登录态）再测。**典型坑**：本轮 body 通道首测，设备包(06-21 22:12)早于 C1 提交(06-22 10:00)，导致 header 有、body 无的假象——根因是旧包而非代码 bug。
- **配置真值未最终定稿**：当前 `channel=12000025`（存疑，§0.1 后端口径认为它是后端腾讯云 AppID 而非 SDK channel）、`hostUrl=https://www.turingfraud.net`（候选，待腾讯最终确认）、`appid=1393866884`。**真机 init 可能返回 `-10018`/`-2014`**——若如此，按「错误码可见 + 降级不崩」判 PASS，并标注需向腾讯复核 channel（用例 TC-I-002 专门覆盖）。
- **功能无可见 UI**：验证全靠 `adb logcat` + 抓包 + APK 静态核验 + gradle 构建命令，不靠界面截图判 PASS/FAIL。
- **钱包 App 铁律**：禁登出 / 禁清数据 / 禁切环境。`app_login`/`register` 场景**不通过登出重登验证**（命中危险清单）——其 body 注入逻辑与其余 3 场景代码模式完全一致，由可安全触发的场景代验。
- **C1 实时 token 已接入 4 场景（本轮新增待测面）**：`AppCacheManager.login`(登录/注册)、`LuckyBoxDialogFragment`(抢红包)、`JoinSpaceDialogFragment.join`(进语音房) 均在请求 body 带 `rce_device_token`。**4 场景代码模式一致**（取缓存 token + 非空才 put + fire-and-forget 刷缓存），抓包验证**任选一个可安全触发的场景**即可推广全部 → 本轮选 **抢红包(`onchain_box_receive`)** 作为 body 通道验证点（见 §3.B / 危险操作标注）。
- **C1 为非阻塞版（C5 已知）**：当次请求用缓存 token，`fetchRealtimeToken` 只刷新缓存供后续；首进场景缓存空则当次缺省，按「非阻塞」口径判定，不 FAIL。「本次请求强保证带实时 token」需阻塞等待，属后续产品决策（C5），不在本轮。
- **端到端（V2 后端侧）需后端配合**：客户端侧只能验到「header 已正确发出/缺省」这一侧；腾讯 `DescribeRiskAssessment` 调通与 RiskLevel 返回属后端联调，不在本客户端用例判定范围。

---

## 0. 构建与静态核验（P0，无需运行 App）

> 落点：debox-android 仓库 gradle 命令 + APK/manifest 静态核验。任一 P0 失败阻断后续真机测试。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-B-001 | 接入模块编译 | `./gradlew :business:BaseBusiness:compileDebugKotlin` | BUILD SUCCESSFUL，`TuringShieldManager`/`PrivacyConsent`/`HeaderUtils` 注入无编译错误，`BuildConfig.RCE_*` 生成 | P0 |
| TC-B-002 | debug 打包 | `./gradlew :app:assembleAppDebug` | BUILD SUCCESSFUL，产出 APK | P0 |
| TC-B-003 | ABI 核验（防 -10001） | 解包 debug APK，列 `lib/` 下 so 目录 | 仅 `arm64-v8a` + `armeabi-v7a`，**无 `armeabi`**（AAR 自带 armeabi 被 app `abiFilters` 排除） | P0 |
| TC-B-004 | manifest 权限移除核验（C2/Q9） | 反编译最终打包 manifest（`aapt dump permissions` 或 `processAppDebugManifestForPackage` 产物） | 不含 `DETECT_SCREEN_CAPTURE` / `DETECT_SCREEN_RECORDING` / `freemme.permission.msa`（三者已 `tools:node="remove"`） | P0 |
| TC-B-005 | 密钥红线核验 | 全仓库 grep `SecretId`/`SecretKey`/`secret_key`/`12000025`(腾讯云 AppID 语境) 在 Android 代码/配置/构建脚本/提交中 | 客户端无任何腾讯云密钥；`RCE_*` 仅经 `BuildConfig` 从 `local.properties`/CI env 注入、无硬编码、`local.properties` 不入库 | P0 |
| TC-B-006 | release / R8 混淆（V1，发版前必做） | `./gradlew :app:assembleAppRelease` | BUILD SUCCESSFUL；AAR 自带 `proguard.txt`（`TNative`/`TuringWebInterface` keep）足够，无 R8 裁剪报错 | P1 |
| TC-B-007 | BuildConfig 注入正确性 | 校验生成的 `BaseBusiness/BuildConfig` 的 `RCE_CHANNEL`/`RCE_HOST_URL`/`RCE_APPID` 值 | 与 `local.properties` 一致（channel=int、hostUrl=String、appid=String），未配置时为默认 `0`/`""`/`""`；`RCE_HOST_URL` 支持逗号分隔多地址（C4） | P1 |
| TC-B-008 | hostUrl 多地址解析（C4 单测/构建核验） | 配 `RCE_HOST_URL="https://a.net , https://b.net"`（含空白）重新生成 BuildConfig，核 `parseHostUrls` 拆分逻辑 | 拆分为 `["https://a.net","https://b.net"]`（去空白、滤空串）；单值/空值回退原始值，降级行为与单地址一致 | P1 |

---

## 1. A 层 JVM 单元测试（受限，P1）

> 约束：`PrivacyConsent`（依赖加密 SP `PreferencesUtils` 需 Android Context）、`init()`（需 Application + native so）**无法在纯 JVM 单测**，下沉 B 层 logcat 取证。
> A 层仅覆盖不依赖 Context 的纯逻辑；SDK 调用以接口桩 mock，**不让 mock 流入生产路径**。

| # | 用例 | 验证标准 | 覆盖点 | 优先级 |
|---|------|----------|--------|--------|
| TC-U-001 | `getDeviceToken()` 初始返回空串 | 未 init / 未预热时返回 `""`（非 null） | 降级默认值、非阻塞读 | P1 |
| TC-U-002 | header 仅非空 token 注入 | token 非空 → map 含 `rceDeviceToken`；token 为空 → map **不含** key（不注入空串） | §6.5 注入逻辑、防隐性故障 | P0 |
| TC-U-003 | 错误码 Long 透传不截断 | `fetchRealtimeToken` 回调 errorCode 为 `Long`，大错误码（如 -30000~-39999 区间）不被 `toInt()` 收窄 | 错误码类型契约（§6.2） | P1 |
| TC-U-004 | header key 常量一致 | `TuringShieldManager.HEADER_KEY_DEVICE_TOKEN == "rceDeviceToken"` | 与后端契约字段名一致 | P0 |

> ⚠️ **body key 无单测保护（review finding，confidence 85）**：C1 的 `rce_device_token` 在 `AppCacheManager.login`、`LuckyBoxDialogFragment`(2 处)、`JoinSpaceDialogFragment.join` 共 **4 处裸字符串硬编码**，无 `HEADER_KEY` 那样的常量约束，纯 JVM 也无法覆盖（需 Fragment/Context）。建议源码侧提取常量 `BODY_KEY_DEVICE_TOKEN`；在此之前，body key 拼写一致性**强制下沉 B 层抓包**（TC-H-006/007）逐字校验，不可省。

---

## 2. B 层真机 — SDK 初始化与 token 预热（P0）

> 取证：冷启动后 `adb logcat | grep -iE "TuringShield|turingfd"`；版本用 `TuringSDK.getVersionInfo()`（AAR 已确认存在）。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-I-001 | 冷启动触发初始化 | terminate → launch → 等首页展示 → 看 logcat | 出现 `TuringShield` init 日志；进程不崩溃，首页正常 | P0 |
| TC-I-002 | init 结果与错误码可见（silent-failure 零容忍） | 观察 init 返回值日志 | **二选一均算 PASS**：① `ret==0`（init 成功）；② `ret!=0` 但 logcat 明确打出 `TuringSDK.init failed ret=<错误码>`（对照 §7）。若为 `-10018`/`-2014` → 标注「需向腾讯复核 channel 真值」 | P0 |
| TC-I-003 | 预热取到非空 deviceToken（V3） | init 成功后看 `reqRiskDetectV3` 日志 / 验证后续 header 带 token | `errorCode==0L` 且 `cachedToken` 非空；若 `errorCode!=0` 则日志可见 `reqRiskDetectV3 err=<码>`，走降级 | P0 |
| TC-I-004 | SDK 版本输出 | logcat / 调试入口打印 `TuringSDK.getVersionInfo()` | 输出 v2.93.9 版本串（确认入库 AAR 版本，非旧包 v89） | P2 |
| TC-I-005 | 启动不阻塞主线程 | 对比接入前后冷启动到首页时长；确认 init 在子线程 | 冷启动无明显劣化（MEDIUM 子线程）；无主线程 `-10008` 日志 | P1 |
| TC-I-006 | init 幂等 | 连续 terminate→launch 多次 | 不重复完整初始化（`inited` 置位后 return）；无重复预热风暴 | P2 |
| TC-I-007 | 预热失败退避重试 | 制造首次预热失败（弱网/冷启动网络未就绪），观察 logcat | 预热失败打 `reqRiskDetectV3 err=<码>` 后 **2s 退避重试，最多 1+2 次**；一旦取到非空 token 即停止重试；token 已非空时不重复刷新（不覆盖、无线程泄漏） | P2 |

---

## 3. B 层真机 — header + body 注入与域名隔离（P0，抓包）

> 取证：**优先 `adb logcat -s http_log_interceptor`**——`BaseLogInterceptor.logRequest` 已把每个请求的 header 与 body（FormBody 进 map / JSON body 进 `bodyToString`）打入该 tag，body 通道无需配代理证书即可逐字核 `rce_device_token`（与上轮 header 取证同源）；需更完整可叠加 mitmproxy/Charles。前提 init 成功且 `cachedToken` 非空（TC-I-003 PASS）。若本轮 init 失败，本组转为「TC-D 降级组」验证 header/body 缺省。
>
> **A. header 通道（所有 DeBox 域名，上轮已 PASS，回归即可）**

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-H-001 | DeBox 域名请求带 token | 已登录态触发任意 DeBox 自有域名业务请求（首页刷新等），抓包 | 请求 header 含 `rceDeviceToken: <非空值>`，值与缓存 token 一致 | P0 |
| TC-H-002 | header 字段名精确一致 | 检查 header key 拼写 | 精确为 `rceDeviceToken`（大小写/拼写无偏差，与后端契约一致） | P0 |
| TC-H-003 | 第三方域名不带 token（防外泄） | 触发走第三方域名的请求（如三方节点/统计/图片 CDN），抓包 | 第三方域名请求 **不含** `rceDeviceToken`（`isDeBoxHost` 过滤生效） | P0 |
| TC-H-004 | token 为空时 header 缺省 | 制造 token 空场景（见 TC-D 组），抓包 DeBox 请求 | header **缺省**（不出现 `rceDeviceToken` key，也不是空串），业务请求仍 200 | P0 |
| TC-H-005 | 不破坏既有 header | 对比接入前后同一请求 header | `token`/`userId`/`deviceId`/`channel`/`Content-Sign` 等既有 header 完整、未被影响 | P1 |

> **B. body 通道（C1 新增，本轮重点 ★）** —— 唯一实跑接口：**抢红包**（`onchain_box_receive`，命中危险清单 → 测试环境 + 测试账号自发小额红包闭环，见文末标注）。一次成功抢红包请求同时经 header 拦截器(驼峰)+ 场景代码(蛇形 body)，可一并验证双通道与同源一致。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-H-006 | 抢红包请求 body 带 token | 测试群/测试房内抓包 `API_LIVE_ROB_LUCKY_BOX`/`API_ROB_LUCKY_BOX`(普通) 请求 | 请求 **body 含 `rce_device_token: <非空值>`**；该接口同时带 header `rceDeviceToken`，**两值同源一致**（均= `cachedToken`） | P0 |
| TC-H-007 | body 字段名精确（防硬编码拼错） | 逐字核对 body key 拼写 | 精确为 `rce_device_token`（蛇形，与后端 `ParamLuckyBox` 字段一致；非驼峰、非 `rceDeviceToken`）—— 对应 review finding，无常量保护必须人工核 | P0 |
| TC-H-008 | 缓存空时 body 缺省（C1 非阻塞） | 预热完成前/缓存空时触发抢红包，抓包 | body **不含** `rce_device_token` key（仅非空才 put），抢红包业务仍正常 200、不崩；属已知非阻塞行为，非 FAIL | P1 |
| TC-H-009 | 既有红包参数不受影响 | 对比抢红包 body | `id`/`room_id`/`gid` 等既有参数完整，`rce_device_token` 仅为新增字段，互不影响；抢红包结果正确 | P1 |

---

## 4. B 层真机 — 降级与容错（P1，重点 / fail-open）

> 这是本接入的核心质量目标：任何失败都不能崩溃、不能阻断业务、不能 silent failure。所有失败路径错误码必须落 logcat。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-D-001 | 断网/飞行模式启动 | 开飞行模式 → 冷启动 → 看 logcat + 进首页 | init/取 token 失败，logcat 有错误码（`-10012` 无网/`-10004` 超时等）；App 不崩，首页正常；恢复网络后业务可用 | P1 |
| TC-D-002 | hostUrl 错误/为空降级 | 临时把 `RCE_HOST_URL` 置空/错误地址重新构建 → 冷启动 | init 返回非 0 且日志可见；`cachedToken` 维持空串；DeBox 请求 header/body 缺省；业务照常发出、不 500 | P1 |
| TC-D-007 | hostUrl 多地址容灾（C4） | 配 `RCE_HOST_URL="<不可用地址>,<有效地址>"` 重新构建 → 冷启动 | SDK 按序探测、跳过首个不可用地址改用次个 → init 成功、`cachedToken` 非空；全程无崩溃、降级链路保留 | P2 |
| TC-D-003 | 阻断腾讯域名、DeBox API 可达 | 用代理阻断 `turingfraud.net`（腾讯 SDK 网关）但放行 DeBox 域名 → 操作 App | 取 token 失败有日志；header 缺省；DeBox 业务请求正常（验证客户端降级与后端 fail-open 在同一口径） | P1 |
| TC-D-004 | getDeviceToken 不阻塞网络线程 | 高频连续业务请求（列表滚动/刷新）下观察 | 无 ANR、无主线程卡顿；拦截器读 token 为 O(1)；无 `-10008` 日志 | P1 |
| TC-D-005 | init 异常兜底不逃逸 | （探索）模拟 SO 加载失败 / 构造 Throwable | 异常被 `catch(Throwable)` 兜住（UnsatisfiedLinkError 一并），不逃逸到启动链路、App 不崩；日志 `TuringShield.init crashed` | P2 |
| TC-D-006 | 错误码全程可见（无空 catch） | 通览各失败用例 logcat | 所有失败分支都有 `LogUtils.w/e` 错误码输出（对照 §7），无静默吞错 | P1 |

---

## 5. B 层真机 — 隐私 gate 与权限合规（P1/P2）

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-PR-001 | 隐私默认同意 init | 全新启动（默认 `PRIVACY_AGREED=true`） | SDK 正常进入 init 流程（不被 gate 拦截） | P1 |
| TC-PR-002 | 隐私不同意则不 init | 通过调试入口 `PrivacyConsent.setAgreed(false)` → 重启 | `TuringShieldInitTask` 直接 return，SDK 不初始化；**不反复触发 `-10019`**；header 缺省、业务正常 | P1 |
| TC-PR-003 | 同意态切回可恢复 init | `setAgreed(true)` → 重启 | SDK 重新走 init；取到 token 后 header 恢复注入 | P2 |
| TC-PR-004 | 已装 APK 权限列表合规 | `adb shell dumpsys package com.tm.security.wallet \| grep -iE "DETECT_SCREEN\|msa"` | 已安装应用不含屏幕录制/截屏检测、`freemme.permission.msa` 权限（与 TC-B-004 呼应） | P1 |
| TC-PR-005 | 不主动采集敏感标识 | 通览运行期权限请求 / logcat | 不触发 `READ_PHONE_STATE`，不实现 IMEI/IMSI provider（Q10 维持关闭）；不新增敏感权限弹窗 | P2 |

---

## 6. B 层真机 — 回归（P1，确保接入不破坏现有）

> 接入新增一个 header + 一个 MEDIUM 启动任务，回归面集中在「网络请求」与「启动链路」。**全程不登出、不清数据、不切环境**。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-R-001 | 登录态保持 | 冷启动 → 进首页 | 不因接入风控被踢登录；用户态正常（不主动登出验证） | P1 |
| TC-R-002 | 既有业务冒烟 | 首页 / 钱包 / 语音房列表等核心页基本操作 | 各页正常加载、网络请求 200，无新增报错/崩溃 | P1 |
| TC-R-003 | RCE 场景业务可用（不破坏） | 加入语音房（`live_room_join`，测试房）/ 抢红包（`onchain_box_receive`，测试数据闭环） | 正常进房 / 正常抢到红包；请求按 TC-H 规则带/缺省 header+body token；功能不受影响 ⚠️见危险操作标注 | P1 |
| TC-R-004 | 既有签名机制不受影响 | 抓包确认 `Content-Sign`（signV3 MD5）仍正确 | 既有签名 header 不变，与图灵盾 token 互不影响 | P1 |

---

## ⚠️ 危险操作专项标注（钱包 App 铁律，执行前比对 `app-knowledge/dangerous-ops.md`）

- **`app_login` / `register` 场景**：不通过「登出 → 重新登录 / 注册新账号」验证（命中危险清单：登出丢登录态、注册产生脏数据）。header 注入对所有 DeBox 域名请求统一生效，用 **TC-H-001（已登录态普通请求）** 验证即可，无需触发登录/注册场景。
- **`onchain_box_receive`（抢红包）场景 —— 本轮唯一实跑的 body 通道验证接口（用户指令明确要求）**：命中危险清单「资金/资产类」+「领取」，按 dangerous-ops §二「**仅用例明确要求 + 测试环境 + 测试数据时允许**」，三条件本轮均满足后允许触发：
  1. **用例明确要求**：用户指令「调试抢红包接口」→ TC-H-006/007/008/009 明确要求；
  2. **测试环境**：已确认 `appEnv=dev`（上轮 TC-H-005 佐证），非正式环境；
  3. **测试数据闭环**：**用测试账号在测试群/测试房自发小额红包，由被测设备账号抢取**（资产在测试账号间闭环，不抢真实用户红包、不产生外部脏数据）；
  - **安全边界**：只为抓包验证 body 带 `rce_device_token`，触发**最少必要次数**（成功抢到一次即可）；抢红包弹窗内**不点**「提现」「转账」等清单内资金出口。
- **`live_room_join`（加入语音房）**：进房本身不可逆性低，可在**测试房**执行（TC-R-003）；但进房后**不点**发布动态 / 举报 / 移除房间等清单内入口。
- **修改 `PrivacyConsent` / `RCE_HOST_URL`**（TC-PR-002 / TC-D-002）：仅在**测试构建 + 测试环境**通过调试入口或重新构建进行，验证后**复原**，不污染常规测试前提。

---

## 统计

| 分类 | 用例数 | P0 | P1 | P2 |
|------|--------|----|----|----|
| 0. 构建与静态核验 | 8 | 5 | 3 | 0 |
| 1. A 层 JVM 单测 | 4 | 2 | 2 | 0 |
| 2. 初始化与预热 | 7 | 3 | 1 | 3 |
| 3. header + body 注入与域名隔离 | 9 | 6 | 3 | 0 |
| 4. 降级与容错 | 7 | 0 | 5 | 2 |
| 5. 隐私 gate 与权限 | 5 | 0 | 3 | 2 |
| 6. 回归 | 4 | 0 | 4 | 0 |
| **合计** | **44** | **16** | **21** | **7** |

> 本轮相对上轮 06-18 新增 7 条：TC-B-008（多地址解析）、TC-I-007（预热重试）、TC-H-006~009（body 通道，★ 核心）、TC-D-007（多地址容灾）。其中 **body 通道 4 条为代码变更直接对应的待测面**。

---

## 测试执行建议顺序

1. **构建层（TC-B）先跑**：编译/打包/ABI/manifest/密钥红线 全 PASS 才进真机（任一 P0 失败阻断）。
2. **A 层单测（TC-U）**：纯逻辑快验，受 Context 限制的项明确标注下沉 B 层。
3. **B 层初始化（TC-I）**：决定后续分支——
   - init 成功取到 token → 走 **TC-H 抓包注入组**；
   - init 失败（候选配置无效）→ 走 **TC-D 降级组**，并标注「需腾讯复核 channel/hostUrl」，本轮注入组转为验证「header/body 缺省 + 业务不阻断」。
4. **TC-H 抓包组**：先 A 段 header 通道（已登录普通请求，回归上轮结论）→ 再 **B 段 body 通道 ★**（抢红包测试数据闭环，本轮核心新增面，逐字核 `rce_device_token`）。
5. **降级组（TC-D）必跑**：无论 init 成败，降级与不崩溃是硬性验收。
6. **隐私/权限（TC-PR）+ 回归（TC-R）** 收尾。
7. 端到端（后端取到 token 调通腾讯、RiskLevel 返回）属**联调项（V2）**，需后端配合，不在本客户端用例判定范围，结果另记。
