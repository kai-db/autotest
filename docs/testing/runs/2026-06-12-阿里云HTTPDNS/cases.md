# 阿里云 HTTPDNS 异常兜底接入 — 测试用例

> 被测分支：**debox** 仓库 `feat/ali-dns`（实现提交 `926b14ed55`，基于方案文档
> `docs/aliyun-httpdns-integration-plan.md` §14 验证方案 + §12 降级表 + §4.3/§4.4 状态机）
> 注意区分仓库：实现位于 `/Users/xiaochengcheng/StudioProjects/debox`（非 debox-android）
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 阿里云 HTTPDNS 异常兜底（per-host 三态状态机 + OkHttp Dns 适配 + 配置兜底链） |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | 静态核对（A 层）+ JVM 单测（B 层）+ MockWebServer 集成（C 层）+ 真机 AI 驱动（D 层） |
| 设备 | 小米 25067PYE3C（`402714f0`）+ 三星 SM-S9210（`RFCYA0F9SSZ`） |
| 前置条件 | B 层无需设备；D 层需设备解锁 + 分支构建安装；**危险红线**：不登出/不清数据/不切环境 |
| 关键现状 | `local.properties` 未注入 HTTPDNS 密钥 → 构建产物中 HTTPDNS 整体禁用（设计内安全态）；激活依赖 P0 外部项（控制台开通 / OSS `httpdns` 段 / 密钥注入） |

---

## 0. 框架前置检查

| # | 检查项 | 步骤 | 验证标准 | 优先级 |
|---|--------|------|----------|--------|
| TC-P-001 | 框架编译 | `./gradlew :autotest:compileReleaseKotlin` | BUILD SUCCESSFUL | P0 |
| TC-P-002 | 单元测试 | `./gradlew :autotest:test` | 0 failures | P0 |
| TC-P-003 | 发布到 mavenLocal | `./gradlew :autotest:publishToMavenLocal` | aar 生成成功 | P0 |

---

## A 层：方案前提与接入点静态核对（debox 仓库）

> 验证方案断言的环境前提仍成立 + §13-P1 全部接入点真实落地（漏挂接 = 整条链路静默失效）。

| # | 用例 | 验证标准 | 方案依据 | 优先级 |
|---|------|----------|----------|--------|
| PRE-01 | Maven 镜像可解析 SDK 且含声明版本 | `com.aliyun.ams:alicloud-android-httpdns` metadata 含 `2.6.9`（config.gradle 声明一致） | §3.1 | P0 |
| PRE-02 | minSdk/targetSdk 与方案一致 | minSdk=25、targetSdk=35 | §3.1 | P0 |
| PRE-03 | OkHttp 4.12.0（ThreadLocal 单线程建连前提） | config.gradle = 4.12.0；升级需复核 §4.4 | §4.4 | P0 |
| PRE-04 | `RetrofitFactory` 挂接 `.dns(AliHttpDnsDns())` | 挂接存在；`AliHttpDnsDns` 无状态可被重建 client 共享 | §13-P1.7 | P0 |
| PRE-05 | 混淆 keep 规则 | `-keep class com.alibaba.sdk.android.httpdns.**` 存在（BaseBusiness consumer rule） | §13-P1.5 | P1 |
| PRE-06 | `DomainManager` 三处挂接 | bootstrap（SP 恢复后）/ `HttpDnsConfig.updateFromOssContent`（拉取回调）/ `updateDomainPool`（池热更新）均存在 | §13-P1.8/13/14 | P0 |
| PRE-07 | `DomainSwitchInterceptor` 同口径双驱动 | 成功/失败回调驱动 `HttpDnsFallbackPolicy`，受管判断走 `AliHttpDnsManager`（不依赖 accessor）；`chain.proceed` 前清 ResolutionContext | §13-P1.9 / §4.4 | P0 |
| PRE-08 | 密钥缺省禁用（安全合入前提） | 密钥未注入时 `hasCredentials()=false` → 全路径系统 DNS，行为与现状一致 | §10/§12 | P0 |
| PRE-09 | 真机基线：正常网络系统 DNS 路径可用 | 启动 DeBox 进首页，网络请求正常（实现后对照组） | §14 | P0 |

---

## B 层：JVM 单测（debox 仓库，全自动）

> 落点：`business/BaseBusiness/src/test/.../network/httpdns/`，共 **50 用例 / 5 个测试类**。
> 其中 37 个随实现提交（`926b14ed55`），**13 个为本轮补缺新增**（`AliHttpDnsManagerTest` 7 +
> `HttpDnsConfigTest` 6——首版对 Manager/Config 两个组件无专属覆盖）。
> 运行命令（必须关 configure-on-demand，否则 react-native-screens 配置期失败）：
> ```bash
> ./gradlew :business:BaseBusiness:testDebugUnitTest \
>   --tests "com.app.base.business.network.httpdns.*" -Dorg.gradle.configureondemand=false
> ```

### B1 组：Dns 适配层（`AliHttpDnsDnsTest`，8 例）

| # | 用例（§14 条目） | 落点测试 | 优先级 |
|---|------|----------|--------|
| UT-01 | 非受管 host 零开销直通且不触碰 policy | `non-managed host passes through without touching policy` | P0 |
| UT-02 | NORMAL/PROBE 决策走系统 DNS，不调 SDK | `system and probe decisions resolve via system dns only` | P0 |
| UT-03 | FALLBACK 返回完整候选列表（多 IP 不取首个） | `httpdns decision returns full candidate list without append` | P0 |
| UT-04 | HTTPDNS 空结果降级系统 DNS + **来源改写（修正⑬）** | `empty httpdns result degrades to system dns and rewrites source` | P0 |
| UT-05 | SDK 异常吞掉降级，不冒泡 | `sdk exception is swallowed and degrades to system dns` | P0 |
| UT-06 | append 模式追加系统结果并去重 | `append mode appends deduplicated system addresses after httpdns` | P1 |
| UT-07 | append + 系统 DNS 抛 UHE：保留 HTTPDNS 结果 | `append mode survives system dns failure` | P0 |
| UT-08 | HTTPDNS 空 + 系统 DNS 失败：异常正常冒泡 | `empty httpdns plus failing system dns propagates exception` | P1 |

### B2 组：三态状态机（`HttpDnsFallbackPolicyTest`，19 例）

| # | 用例（§14 条目） | 落点测试 | 优先级 |
|---|------|----------|--------|
| UT-09 | 默认态系统 DNS | `default state resolves via system dns` | P0 |
| UT-10 | 开关关闭：异常不进 FALLBACK | `switch off - abnormal mark does not enter fallback` | P0 |
| UT-11 | 异常 + 开关开：进 FALLBACK + 触发预解析（每次进入仅一次） | `abnormal with switch on enters fallback and triggers preresolve` + `repeated abnormal in normal state only preresolves once per fallback entry` | P0 |
| UT-12 | FALLBACK 成功只续期不清除（§4.4 核心） | `fallback success only renews ttl - never clears` | P0 |
| UT-13 | TTL 到期进 PROBE 且单飞 | `ttl expiry enters probe with single flight` | P0 |
| UT-14 | PROBE 成功清标记回 NORMAL | `probe success clears mark back to normal` | P0 |
| UT-15 | PROBE 失败重回 FALLBACK 重置 TTL | `probe failure re-enters fallback with reset ttl` | P0 |
| UT-16 | PROBE 租约超时可重新租用 | `expired probe lease can be re-acquired` | P1 |
| UT-17 | 连接复用（来源未知）成功/失败均不动状态 | `unknown source success does not change state` + `unknown source failure in fallback keeps state` | P0 |
| UT-18 | 竞态①：PROBE 恢复后陈旧 HTTPDNS 成功被 generation 拦截 | `stale httpdns success after probe recovery is ignored by generation check` | P0 |
| UT-19 | 竞态②：系统 DNS 在途请求成功不误续期 | `in-flight system request is not treated as fallback renewal` | P0 |
| UT-20 | FALLBACK 失败触发强刷缓存且不续期 | `fallback request failure triggers force re-resolve without renewing ttl` | P1 |
| UT-21 | 降级请求成功不按 HTTPDNS 续期（**修正⑬**，防控制台漏配永不回切） | `degraded resolution success does not renew fallback ttl` | P0 |
| UT-22 | 降级/陈旧失败不强刷缓存（**修正⑮**，防失败风暴清掉预热）×3 | `degraded resolution failure keeps fallback without force re-resolve` + `in-flight system request failure after fallback entry does not force re-resolve` + `degraded mark for another host is ignored` | P1 |
| UT-23 | host 间状态隔离 | `hosts are isolated from each other` | P1 |

### B3 组：远程配置解析与灰度（`HttpDnsRemoteConfigTest`，10 例）

| # | 用例（§14 条目） | 落点测试 | 优先级 |
|---|------|----------|--------|
| UT-24 | httpdns 段全字段解析 / 字段缺失取默认 | `parse full httpdns section` + `missing fields fall back to defaults` | P0 |
| UT-25 | 旧格式（无 httpdns 段）/ JSON 损坏返回 null 不抛 | `old format without httpdns section returns null` + `corrupt json returns null instead of throwing` | P0 |
| UT-26 | gray_percent 越界收敛 0–100 | `gray percent is coerced into 0-100` | P1 |
| UT-27 | 灰度边界 0/100 + 同设备分桶稳定 + 单调性 | `gray bucket boundary values` + `gray bucket is stable for the same device` + `gray bucket monotonic` | P0 |
| UT-28 | 设备号缺失不命中部分灰度（**修正⑭**，防空串恒落 0 号桶）；全量/全关不受影响 | `empty device id never hits partial gray` + `empty device id follows full rollout and full off` | P0 |

### B4 组：Manager/Config 补缺（本轮新增 13 例）

| # | 用例（§14 条目） | 落点测试 | 优先级 |
|---|------|----------|--------|
| UT-29 | 启动早期内置域名即受管（不依赖 accessor，§6.3 修复④） | `AliHttpDnsManagerTest.builtin hosts are managed before any pool bootstrap` | P0 |
| UT-30 | bootstrap（SP 恢复池）计入受管集合 + 归一化 | `bootstrap pool hosts become managed with normalization` | P0 |
| UT-31 | 池热更新替换快照（摘除域名不再受管，内置不受影响） | `updateDomainPool replaces snapshot instead of accumulating` | P1 |
| UT-32 | SDK 未初始化 lookup 返回空 → 降级系统 DNS | `lookup before init returns empty for system dns degradation` | P0 |
| UT-33 | 密钥缺失 ensureInitialized → DISABLED，整体不可用 | `ensureInitialized without credentials disables httpdns entirely` | P0 |
| UT-34 | PENDING_INIT：预解析挂起去重不崩（§6.1 修复⑩） | `preResolve before init parks host without crash` | P1 |
| UT-35 | 未初始化 forceReResolve 安全 no-op | `forceReResolve before init is a safe no-op` | P2 |
| UT-36 | 生效集合 = 受管 ∩ hosts；收窄不放大（§4.2） | `HttpDnsConfigTest.effective set is managed intersect narrowing hosts` | P0 |
| UT-37 | hosts 空 = 不收窄（受管全集生效） | `empty narrowing list means full managed set` | P0 |
| UT-38 | 收窄匹配大小写不敏感 | `narrowing match is case insensitive` | P2 |
| UT-39 | 从未拉到配置 → 默认 enabled=false 总开关关（§9 兜底链） | `never fetched config defaults to switch off` | P0 |
| UT-40 | 密钥缺失时远程 enabled=true 也不开 | `switch stays off without credentials even if remote enables` | P0 |
| UT-41 | Config 层错误隔离：损坏/旧格式内容沿用已有配置（§10） | `corrupt content keeps previous config` | P0 |

---

## C 层：集成测试（MockWebServer，✅ 已实现）

> §14 集成测试清单。落点：`business/BaseBusiness/src/test/.../httpdns/AliHttpDnsDnsIntegrationTest.kt`
> （MockWebServer + okhttp-tls，9 用例，验证 OkHttp 4.12.0 真实建连语义——B 层纯逻辑覆盖不到的）。
> 运行命令同 B 层（`-Dorg.gradle.configureondemand=false`）。

| # | 用例 | 验证标准 | 落点测试 | 优先级 |
|---|------|----------|----------|--------|
| IT-01 | URL host / SNI 保持原域名（HTTPS 证书按原域名校验，非 IP 直连） | requestUrl.host=debox.pro + 握手成功 | `IT01 host and SNI stay original domain over https not ip` | P0 |
| IT-02 | UnknownHostException 只标记不重放 POST | 请求失败 + server 收到 0 请求 + 降级改写来源 | `IT02 unknown host on post does not replay request` | P0 |
| IT-03 | FALLBACK 窗口内后续请求走 HTTPDNS | 2 请求都 200 + 都走 HTTPDNS 解析 | `IT03 subsequent requests in fallback go through httpdns` | P0 |
| IT-04 | 坏 IP + 好 IP 轮换实测（OkHttp 4.12.0，§7 边界） | 同一请求成功连上第二 IP；候选全坏则失败（IT04b） | `IT04 okhttp rotates from bad ip to good ip` + `IT04b` | P0 |
| IT-05 | 持续污染不震荡（系统 DNS 持续坏 + HTTPDNS 可用，连续 5 请求） | 5 请求全 200、全走 HTTPDNS，无失败 | `IT05 sustained pollution stays on httpdns without flapping` | P0 |
| IT-06 | 开关关闭全链路 SDK 0 调用 | 走系统 DNS + httpDnsLookup 0 次 | `IT06 switch off never calls httpdns sdk` | P0 |
| IT-07 | TTL 过期 PROBE 回切两分支 | 系统恢复→200（IT07a）/ 系统仍坏→失败（IT07b） | `IT07a probe success` + `IT07b probe failure` | P0 |

> **关键实测结论（IT-04，方案 §7 不依赖文档表述）**：OkHttp 4.12.0 下，HTTPDNS 候选列表
> `[坏IP 192.0.2.1, 好IP]` 时，同一次请求 connect 坏 IP 超时后**自动轮换到好 IP 成功**；
> 候选全坏且不追加系统 DNS 时请求失败（IT04b，证明轮换不是无条件成功）。
> 踩坑：测试 client 必须 `.proxy(Proxy.NO_PROXY)`（本机代理会拦截致 502）；验证"每请求都
> 走 HTTPDNS"需禁用连接池（否则 OkHttp 复用连接不触发 lookup，§4.4）。

---

## D 层：真机验证（AI 驱动，mobile-mcp + adb logcat）

> **前置依赖**：① 设备解锁（当前两台均安全锁屏，需人工解锁一次）；② debox `feat/ali-dns`
> 构建安装（装包后按惯例 dex 验真：`strings classes*.dex | grep AliHttpDnsDns`）；
> ③ MV-03 起额外需要 P0 外部项（密钥注入 + 控制台域名 + OSS `conf_test.json` httpdns 段）。
> DNS 异常模拟：`settings put global private_dns_mode hostname` + 坏 specifier（可逆，
> 测后必须恢复）；禁止清数据/登出/切环境。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| MV-01 | 禁用态基线（密钥未注入构建） | 安装分支包 → 正常浏览 → 抓 logcat | 行为与现状一致：全程系统 DNS，无 SDK init 日志，无 `ensureInitialized` 之外的 httpdns 活动 | P0 |
| MV-01b | 禁用态 + DNS 异常：不触发 HTTPDNS、现有容灾接管 | 坏私有 DNS → 重启触发请求失败 → 抓 logcat → 复原 | 出现连通性异常但 0 条 `enter_fallback`、0 条 SDK init；DomainManager 故障计数照常累计（两道机制不干扰） | P0 |
| MV-02 | 开关打开但网络正常：仍系统 DNS | 密钥注入 + conf_test `enabled=true` → 重启浏览 | SDK init（懒初始化）但 0 次解析；解析来源全 system | P0 |
| MV-03 | DNS 异常 → HTTPDNS 兜底且无周期失败 | 坏私有 DNS → 触发失败 → 观察 ≥1 个 TTL 周期 | 进 FALLBACK 后请求走 httpdns 成功；无 §4.4 震荡 | P0 |
| MV-04 | HTTPDNS 解析错误 IP → 现有容灾接管 | EMAS 控制台配错误 IP | 失败进入 DomainSwitch 统计，域名切换可达（§11 两道自愈协作） | P1 |
| MV-05 | EMAS 解析记录只来自异常用户 | 跑完 MV-01~03 查控制台 | 仅 MV-03 时段有解析记录 | P1 |
| MV-06 | 测后环境复原 | 恢复 private_dns → 重跑 MV-01 基线 | 设备配置复原，App 正常 | P0 |

---

## 统计

| 分类 | 用例数 | P0 | P1 | P2 | 本轮可执行 |
|------|--------|----|----|----|------------|
| 框架前置检查 | 3 | 3 | 0 | 0 | ✅ |
| A 层 静态核对 | 9 | 8 | 1 | 0 | ✅（PRE-09 需设备解锁） |
| B 层 JVM 单测 | 41 条目 / 50 测试 | 29 | 9 | 3 | ✅ |
| C 层 集成测试 | 7（9 测试） | 7 | 0 | 0 | ✅ 已实现 9/9 |
| D 层 真机验证 | 7 | 5 | 2 | 0 | ✅ 禁用态子集已执行（MV-02~05 待密钥） |
| **合计** | **67** | **52** | **12** | **3** | — |
