# HTTPDNS 回环/bogon 兜底 + accessor 早注册 + 连接埋点 + IM 多 navi 容灾 — 测试用例

> 被测对象：**debox 项目 `dev` 分支今晚(2026-06-30 ~ 07-01)未提交工作区改动**。
> 来源工单：用户「用手机网络打不开」——系统 DNS 把 `debox.pro` / `ws.debox.pro` 解析到回环
> (`::1` / `127.0.0.1`),App 两套抗 DNS 污染自愈机制对「回环污染」全部失效,16h/25 次重启彻底打不开。
> 本轮修 **P0-A 回环/bogon 兜底过滤** + **P0-B 切域名 accessor 早注册** + **P1-C4 连接级诊断埋点** + **P0-C3 IM 多 navi 容灾**。
> 实现分析见 `debox/docs/implementation/2026-06-30-analyze-httpdns-ipv6-loopback-5g/`(P0-A/B + C4 Codex 实现读审 PASS,单测 15/0;P0-C3 为后续增量)。
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景
>
> **更新记录**：2026-07-01 首版(P0-A/B + P1-C4,41 例)→ 2026-07-01 增补 **P0-C3 IM 多 navi 容灾**(第 7 节,+9 例)。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | 受管公网域名「系统 DNS / HTTPDNS 结果解析到回环/bogon」时不交给 OkHttp,主动升级 FALLBACK;切域名 accessor 启动早注册;连接级埋点可见;**IM 支持多 navi(主+备用不同 hostname),主域名被 DNS 黑洞时切备用** |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | A 层静态接线核对 + B 层 JVM 单测(`AliHttpDnsDnsTest`)+ D 层真机(mobile-mcp / adb logcat + FLogger `dbx_log/flog` 文件) |
| 设备 | 三星 **SM-S9210(Galaxy S25)**`RFCYA0F9SSZ`,Android 16/SDK 36,1080×2340,**未 root**,主测;小米 25067PYE3C `402714f0` 交叉 |
| 前置条件 | 设备解锁 + 已登录 + 测试环境 + WiFi 在线;dev DEBUG 构建,日志全开;**危险红线**:不清数据/不卸载/不登出/不切环境 |
| 实现仓库 | `/Users/xiaochengcheng/StudioProjects/debox`(分支 `dev`,**未提交**) |

> ⚠️ **设备 APK 与改动不同步(必读)**：S25 上 DeBox `2.14.0` lastUpdate `2026-06-30 23:48`,**早于本轮改动完成(07-01 00:09)**,
> 设备上 APK **大概率不含**这批未提交改动 → **真机层(TC-D/B/C/R)开测前必须重建并安装含本改动的 dev DEBUG 包**,
> 否则测的是旧代码(假性 PASS/FAIL)。判断设备是否跑新代码**别只看版本号**(版本号没动),看启动日志是否出现新签名关键字
> `bootstrapEarly: 早注册域名管理(off-main)`(P0-B 新代码标志)。

---

## 本轮改动范围(4 项)

| 编号 | 改动 | 文件 | 解决的问题 |
|---|---|---|---|
| **P0-A** | 回环/bogon 兜底过滤 | `AliHttpDnsDns.kt`(+ `AliHttpDnsDnsTest.kt` +7 例) | NORMAL 态直通系统 DNS,把污染的 `::1`/`127.0.0.1` 原样交给 OkHttp → 秒拒 `ECONNREFUSED`;HTTPDNS append 模式也混入回环;全网络层无任何回环过滤 |
| **P0-B** | 切域名 accessor 早注册 | `DomainManager.kt`(`bootstrapEarly`)+ `HighPriorityTasks.kt`(`DomainBootstrapTask`)+ `Application.kt`(HIGH 组首位注册) | `domain_switch_skip reason=domain_accessor_null`:accessor 惰性注册晚于早期 HTTP 失败 → 早期切域名/FALLBACK 全跳过 |
| **P1-C4** | 连接级诊断埋点 | `NetEventListener.kt`(新增)+ `RetrofitFactory.kt`(`.eventListenerFactory{}`) | 此前只能从 `http_request fail` 反推「解析到哪个 IP / DNS 还是连接失败」;下次同类事故可一眼定位 |
| **P0-C3** | IM 多 navi 容灾(增量) | `AppConstant.kt`(`getAppNaviListForJ`)+ `DBXJimCenter.java`(list 接线)+ `build.gradle`(注释) | IM 单 navi 走系统 DNS,`ws.debox.pro` 被按 hostname 黑洞到回环时永久重连失败;改为多 navi(主+备用**不同 hostname**),备用域名绕过针对主域名的污染 |

> **P0-C3 仅做了「多 navi 备用域名」一项**——分析文档 P0-C3 原含三件事:① 多 navi 异线路、② IM 接 HTTPDNS/兜底 IP、③ `ws.debox.pro` 纳入 `httpdns_hosts`。本次**只落 ①**;IM 仍走系统 DNS、不接 HTTPDNS。`getAppNaviForJ()`(旧单值方法)改后**已无引用 = 死代码**(代码质量小尾巴,可清理,非测试阻断)。

### P0-A 控制流(测试依据,`AliHttpDnsDns.lookup`)

```
lookup(host)
 ├─ 非受管 host                       → systemDns.lookup() 原样直通(零开销,不过滤)         [TC-D-07/TC-U-07]
 ├─ decision != HTTPDNS (NORMAL/PROBE/关/未init)
 │     → systemLookupSanitized(host, decision):
 │         raw = systemDns.lookup()
 │         usable = raw.filter(isRoutable)
 │         ├─ raw 非空 & usable 空(全回环/bogon)→ log `system_dns_poisoned`
 │         │     + onSystemDnsPoisoned(markProactiveFallback) + **throw UnknownHostException**   [TC-U-01/TC-D-01]
 │         ├─ usable < raw(部分回环)            → log `system_dns_partial_bogon`,返回 usable    [TC-U-02/TC-D-04]
 │         └─ 全可路由                          → 返回 raw(健康路径,无新日志,不刷量)            [TC-D-06/TC-R-01]
 └─ decision == HTTPDNS
       rawHttpDns = httpDnsLookup()(SDK 异常吞为 emptyList,log `httpdns_error_fallback_system`)
       httpDnsAddresses = rawHttpDns.filter(isRoutable)
       ├─ rawHttpDns 非空 & 过滤后空(HTTPDNS 全 bogon)→ log `httpdns_bogon_filtered`
       │     + onHttpDnsBogon(forceReResolve 清坏缓存)→ 并入下方「空」分支                       [TC-U-05/TC-U-06]
       ├─ httpDnsAddresses 空 → log `httpdns_empty_fallback_system` + markDegraded
       │     + systemLookupSanitized()(同样滤回环;系统也污染则抛+升级)                          [TC-U-03]
       ├─ append=false → 仅返回 HTTPDNS 结果
       └─ append=true  → systemDns.lookup().filter(isRoutable) 追加(系统侧也滤回环)             [TC-U-04]

isRoutable(addr) = !isLoopback && !isAnyLocal && !isLinkLocal && !isMulticast
   过滤:回环(::1 / 127.0.0.0/8)、通配(0.0.0.0 / ::)、链路本地(169.254/16 / fe80::)、组播
   保留:site-local(不滤,避边角误伤)、NAT64 / global IPv6 / DNS64 合成(正常 unicast)
```

> **关键不变量(必验)**：回环污染 → **不连 `::1`/`127.0.0.1`,而是抛 `UnknownHostException`**;
> 该异常由 `DomainSwitchInterceptor` 当作 DNS 故障 → 触发切域名 + HTTPDNS FALLBACK 升级(自愈链路接管)。
> 这是把「HTTPDNS 只防解析错 IP」扩展到「防解析到回环」的核心补丁。

### P0-B 时序(测试依据)

```
Application.registerStartupTasks → StartupTaskManager HIGH 组首位注册 DomainBootstrapTask
  DomainBootstrapTask(priority=HIGH, needMainThread=false).execute()
    └─ ExecutorHelper.networkIO().execute { DomainManager.bootstrapEarly(app) }   ← offload 后台,避免主线程读加密 SP(splash-keystore ANR 家族)
         DomainManager.bootstrapEarly: 访问 instance 即注册 DomainManagerBridge.accessor
           bootstrapEarlyInternal: synchronized(lock){ restoreFromCache() } + seedFromBundledConfig() + AliHttpDnsManager.bootstrap()
           (只做同步非网络子集;不做 resetUrl/网络回调/OSS,留给 Activity 绑定的 init;与 init 幂等共存)
```

> **关键不变量(必验)**：① accessor 在「早期 HTTP 第一次失败」之前已就绪 → 不再有 `domain_switch_skip reason=domain_accessor_null`;
> ② bootstrapEarly 在**后台线程**跑(不得在主线程读加密 SP)→ 不引入冷启动 ANR;③ 自捕获异常,失败不影响启动。

### P1-C4 埋点(测试依据,`NetEventListener`)

```
RetrofitFactory.eventListenerFactory { NetEventListener() }   ← per-call 新建,无共享状态,并发不串号
  仅对受管 host(AliHttpDnsManager.isManagedHost)打;第三方 RPC/DApp 零噪声
  dnsEnd       → NetFlog.i "dns_end"      host=<h>, ips=[全部解析IP]      ← 判断是否回环 ::1/127.0.0.1
  connectStart → NetFlog.i "connect_start" host=<h>, addr=<建连IP>
  connectEnd   → NetFlog.i "connect_end"   host=<h>, addr=<建连IP>
  connectFailed→ NetFlog.w "connect_failed" host=<h>, addr=<IP>, err=<ECONNREFUSED/ETIMEDOUT...>
  callFailed   → NetFlog.w "call_failed"    host=<h>, err=<错误类:message>
  只记 host/IP/错误类;EventListener 本就不暴露 URL path/header/body
```

### P0-C3 IM 多 navi 容灾(测试依据)

```
AppConstant.getAppNaviListForJ():List<String>
  raw = isRelease() ? JIM_NAVI_SERVER_PRODUCT : JIM_NAVI_SERVER_TEST   ← 单个 BuildConfig 字符串
  return raw.split(",", ";").map{trim}.filter{notEmpty}.distinct()      ← 去空/去重/保序(主在前)

DBXJimCenter.init:
  serUris = ArrayList(getAppNaviListForJ())   ← 多 navi 列表
  JIM.getInstance().setServerUrls(serUris)    ← 交给 JuggleIM(二进制 AAR)
  JIM.getInstance().init(ctx, juggleAppKey, initConfig)

配置现状(local.properties):
  JIM_NAVI_SERVER_PRODUCT = wss://ws.debox.pro,wss://ws.dbxsocial.com   ← 多 navi(主+备用不同 hostname)✅
  JIM_NAVI_SERVER_TEST    = wss://im-s.debox.pro                        ← 单 navi ⚠️(dev 包跑这个)
```

> **关键不变量(必验)**:① `getAppNaviListForJ` 解析正确(多分隔符/去空/去重/保序);
> ② IM 用列表接线、单 navi(测试环境现状)行为**与改前一致**(无回归);
> ③ **核心存疑点(开发者自标"待证")**:JuggleIM `setServerUrls([坏主, 好备])` 是否真会在**主 navi 连不上时切到备用**——
> 二进制 AAR 行为不可静态确认,**必须真机验**;若 JuggleIM 不做 failover,则 P0-C3 ① 无效,需换方案。
>
> **测试环境硬约束**:dev DEBUG 包读 `JIM_NAVI_SERVER_TEST`(当前**单 navi**)→ failover 默认**触发不了**。
> 测多 navi 切换**必须**临时把 `JIM_NAVI_SERVER_TEST` 配成逗号分隔(如 `wss://im-bad.debox.pro,wss://im-s.debox.pro`,
> 坏主 + 真备)再**重建安装**;测完复原配置。

---

## 测试环境拓扑 & 故障注入可行性(关键约束,开测前必读)

### 测试环境域名拓扑(沿用 `app-knowledge/network-domain.md`)
- OSS `conf_test.json`:`domainList=[t.debox.pro, t.dbxsocial.com]` + 内置兜底 `[debox.pro, dbxsocial.com]`;`currentDomain` 随上次持久化。
- HTTPDNS 收窄 `hosts=[t.debox.pro]`、`abnormalTtlMs=600000`、`enabled=true`,密钥已注入(`doInit: HTTPDNS SDK 初始化完成`)。
- **受管 host(会被 sanitize)= 内置 ∪ 池**,含 `debox.pro/t.debox.pro/dbxsocial.com/t.dbxsocial.com`;
  **HTTPDNS 收窄 host(markProactiveFallback 才真升级)仅 `t.debox.pro`**。
- 实际业务流量走 `t.debox.pro`(=HTTPDNS 生效域名)。
- EMAS 控制台未托管 `t.debox.pro` → FALLBACK 后 `httpdns_empty_fallback_system`(空结果降级正确,happy 路径无法真机验)。

### 回环污染注入手段对照(本轮新难点)

| 注入手段 | 系统 DNS 返回 | 触发新行为? | 非 root 可行? | 备注 |
|---|---|---|---|---|
| **VPN-DNS 改写 App**(DNS Changer/AdGuard/Personal DNS,把受管域名 sinkhole 到 `127.0.0.1`/`::1`) | **回环** ✅ | ✅ 触发 `system_dns_poisoned` 全链路 | ✅(VPN 类免 root) | **最高保真**——正是本案真实成因(去广告/安全软件 sinkhole);需 UI 自动化装/配第三方 App,较脆 |
| **Private DNS(DoT)指向自控 sinkhole 解析器** | 回环 ✅ | ✅ | ✅ | 需自建/控制 DoT 服务器,较重 |
| **Private DNS 坏 specifier**(现有手段) | **UnknownHostException**(非回环) | ⚠️ 只测**下游升级**,不测回环→抛的转换 | ✅ | `private_dns_mode=hostname`+坏 DoT 主机;**作为升级链路 cross-check** |
| 整机断网(svc disable) | — | ❌ 被 App 守卫 `-100` 短路,不进拦截器 | ✅ | 仅测启动健康探测分支,**测不到本轮** |
| root 改 `/etc/hosts` | 回环 ✅ | ✅ | ❌ S25 未 root | N/A |

> **结论(分层策略)**：
> 1. **回环→抛→不连 `::1` 的核心新逻辑**:以 **B 层 JVM 单测(TC-U)为权威**(确定性、已 15/0),真机用 **VPN-DNS 改写(TC-D)** 端到端验证(成本/保真权衡,可选执行)。
> 2. **下游自愈升级**(切域名 + FALLBACK + 埋点连接失败)可用**现有 Private DNS 坏 specifier** cross-check(产生 UnknownHostException,走同一升级路径,但**无** `system_dns_poisoned` 签名)。
> 3. **复原**:测完必删 `private_dns_*`、关闭/卸载 VPN-DNS App、`settings put global http_proxy :0`、恢复 WiFi/data。

---

## 0. 框架/构建前置检查(P0)

> 任一失败阻断后续。

| # | 检查项 | 步骤 | 验证标准 | 优先级 |
|---|--------|------|----------|--------|
| TC-P-001 | 单测全绿 | `cd debox && ./gradlew :business:BaseBusiness:testDebugUnitTest --tests "*AliHttpDnsDnsTest"` | `tests=15 failures=0 errors=0` BUILD SUCCESSFUL | P0 |
| TC-P-002 | BaseBusiness 编译 | `./gradlew :business:BaseBusiness:compileDebugKotlin` | BUILD SUCCESSFUL(仅 pre-existing 警告) | P0 |
| TC-P-003 | BaseModule 编译 | `./gradlew :business:BaseModule:compileDebugKotlin` | BUILD SUCCESSFUL | P0 |
| TC-P-004 | app 编译 | `./gradlew :app:compileAppDebugKotlin` | BUILD SUCCESSFUL | P0 |
| TC-P-005 | 构建+安装含改动的 dev DEBUG 包 | `./gradlew :app:assembleAppDebug` → `adb -s RFCYA0F9SSZ install -r <apk>` | 安装成功;启动日志含 `bootstrapEarly: 早注册域名管理(off-main)`(新代码标志) | P0 |

---

## 1. JVM 单测层(P0,P0-A 核心权威覆盖)

> `AliHttpDnsDnsTest` 新增 7 例(全量 15 例)。这是回环/bogon 逻辑的**确定性权威验证**,真机注入再难也不影响这层结论。

| # | 用例 | 输入(decision / system DNS / HTTPDNS) | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-U-01 | 系统 DNS 全回环→抛+升级 | NORMAL / `[::1]` / — | `lookup` 抛 `UnknownHostException`;`onSystemDnsPoisoned` 命中(`poisoned=[host]`);**不返回 `::1`** | P0 |
| TC-U-02 | 系统 DNS 混合→只留可路由 | NORMAL / `[::1, 9.9.9.9]` / — | 返回 `[9.9.9.9]`;**不升级**(`poisoned` 空) | P0 |
| TC-U-03 | HTTPDNS 空 + 系统污染→抛+升级 | HTTPDNS / `[127.0.0.1]` / `[]` | 抛 `UnknownHostException`;`degraded=[host]` 且 `poisoned=[host]` | P0 |
| TC-U-04 | append 模式滤回环再追加 | HTTPDNS / `[127.0.0.1, 9.9.9.9]` / `[1.1.1.1]`,append=true | 返回 `[1.1.1.1, 9.9.9.9]`(`127.0.0.1` 被滤,HTTPDNS 在前) | P0 |
| TC-U-05 | HTTPDNS 全 bogon→reResolve+降级 | HTTPDNS / `[9.9.9.9]` / `[::1]` | 返回 `[9.9.9.9]`;`httpDnsBogon=[host]` + `degraded=[host]`(清坏缓存+降级 sanitized 系统) | P0 |
| TC-U-06 | HTTPDNS 部分 bogon→留可路由不 reResolve | HTTPDNS / `[9.9.9.9]` / `[::1, 1.1.1.1]` | 返回 `[1.1.1.1]`;`httpDnsBogon` 空、`degraded` 空 | P1 |
| TC-U-07 | 非受管 host→回环不过滤直通 | managed=false / `[::1]` / — | 返回 `[::1]`(零开销直通);`poisoned` 空 | P1 |
| TC-U-08 | 既有 11 例无回归 | 全量跑 | 原 8 例(NORMAL 直通/HTTPDNS命中/空降级/append/SDK异常…)仍通过 | P0 |

---

## 2. 静态接线核对(P1,确认改动真生效)

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-A-01 | EventListener 已接线 | 查 `RetrofitFactory.kt` build client | 含 `.eventListenerFactory { NetEventListener() }`(per-call,非 `.eventListener()` 单例);`.dns(AliHttpDnsDns(...))` 仍在 | P1 |
| TC-A-02 | DomainBootstrapTask HIGH 组首位 | 查 `Application.registerStartupTasks` | `DomainBootstrapTask()` 在 HIGH 组**首位**(早于 `JIMCallEngineTask`/`LiveEventBusInitTask`) | P1 |
| TC-A-03 | bootstrap offload 后台 | 查 `DomainBootstrapTask.execute` | 经 `ExecutorHelper.getInstance().networkIO().execute{}`,**非**主线程直跑 | P1 |
| TC-A-04 | bootstrapEarly 只做同步非网络子集 | 查 `DomainManager.bootstrapEarlyInternal` | 仅 `restoreFromCache`+`seedFromBundledConfig`+`AliHttpDnsManager.bootstrap`;**无** resetUrl/网络回调/OSS 拉取;`bootstrapEarly` companion try-catch 全包 | P1 |
| TC-A-05 | EventListener 隐私边界 | 查 `NetEventListener.kt` | 只打 host/IP/错误类;**无** URL path/header/body;`managedHost` 非受管 return null(不打) | P1 |

---

## 3. 真机 — P0-B accessor 早注册(P0)

> 前置:已装含改动的 dev 包(TC-P-005);grep `DomainManager|domain_switch_skip|domain_accessor_null|bootstrapEarly`。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-B-01 | 冷启 accessor 早注册 | `adb shell am force-stop` → 冷启动 App → 抓启动日志 | 启动早期出现 `bootstrapEarly: 早注册域名管理(off-main), accessor 就绪` + `bootstrapEarly: 完成, currentDomain=…, domainList=[…]` | P0 |
| TC-B-02 | 早期失败不再 accessor_null | Private DNS 坏 specifier(注入早期 DNS 故障)→ 冷启动 → 触发早期 HTTP | 早期请求失败时**不再**出现 `domain_switch_skip reason=domain_accessor_null`;切域名/FALLBACK 链路可触发(`域名切换 …` 或 `enter_fallback`) | P0 |
| TC-B-03 | bootstrap 不在主线程(无 ANR) | 冷启动 5 次,观察启动耗时 + ANR | 无 ANR;`bootstrapEarly` 日志线程非 main(看 logcat tid);冷启耗时无明显劣化(对照旧包) | P0 |
| TC-B-04 | bootstrapEarly 异常隔离 | (探索)制造 SP 读异常场景 / 正常路径多次冷启 | 即便 bootstrapEarly 抛,出现 `bootstrapEarly 失败 …` 且 App 正常启动,不崩、不卡 executor | P2 |
| TC-B-05 | 与 init 幂等共存 | 冷启动后进首页 → 触发 Activity 绑定 `AppConfigManager.init` | domainList 不被重置/错乱;currentDomain 一致;无重复 seed 告警 | P1 |

---

## 4. 真机 — P1-C4 连接级埋点(P1)

> 前置:已装含改动的 dev 包;grep `NetEventListener|dns_end|connect_start|connect_end|connect_failed|call_failed`。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-C-01 | 健康路径埋点完整 | 正常网络冷启动 + 进首页/刷新 → 抓日志 | 受管域名出现成对 `dns_end host=… ips=[…]` → `connect_start … addr=…` → `connect_end … addr=…` | P1 |
| TC-C-02 | dns_end 暴露解析 IP | 正常请求 | `dns_end` 的 `ips=[…]` 是真实可路由 IP(非 `::1`/`127.0.0.1`);可据此判断解析来源 | P1 |
| TC-C-03 | 失败路径埋点 | Private DNS 坏 specifier 或 VPN-DNS 回环注入 → 触发请求失败 | 出现 `connect_failed host=… addr=… err=ConnectException: ECONNREFUSED…` 或 `call_failed host=… err=…` | P1 |
| TC-C-04 | 只对受管 host 打(零噪声) | 触发第三方 RPC/DApp 请求(如行情/链上) | 第三方 host **无** NetEventListener 日志;只有受管域名有 | P1 |
| TC-C-05 | 并发不串号 | 首页多接口并发刷新 | 各 call 的 host/addr 自洽(per-call 新建,无跨请求串状态) | P2 |
| TC-C-06 | 无 PII 泄漏(安全) | 检查 NetEventListener 全部日志行 | **绝无** URL path/query/token/header/body/钱包地址/uid;只有 host/IP/错误类 | P1 |

---

## 5. 真机 — P0-A 回环污染端到端行为(P0,核心新行为,VPN-DNS 注入)

> 前置:已装含改动的 dev 包 + VPN-DNS 改写 App 把 **实际流量域名**(测试环境 `t.debox.pro`,及 `debox.pro`)sinkhole 到 `127.0.0.1`/`::1`。
> grep `AliHttpDnsDns|system_dns_poisoned|system_dns_partial_bogon|httpdns_bogon_filtered|enter_fallback|proactive|域名切换|connect_failed`。
> ⚠️ 若 VPN-DNS 注入不可行,本节降级为「依赖 TC-U 单测结论 + TC-D-05 Private DNS cross-check」,并在 results 标注未真机覆盖。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-D-01 | 全回环→不连 `::1`+抛+升级 | VPN-DNS 把 `t.debox.pro`+`debox.pro` 全解析到 `::1` → 触发受管请求 | 出现 `system_dns_poisoned: <host> … addrs=[::1]`;**`connect_start`/`connect_failed` 中无 `addr=::1`/`127.0.0.1`**(没把回环交给 OkHttp);随后触发升级(`t.debox.pro` 出 `enter_fallback reason=proactive-domain-switch`;`debox.pro` 出 `域名切换 …` 或 `proactive_fallback_skip`) | P0 |
| TC-D-02 | 升级后自愈(VPN 关) | TC-D-01 后关闭 VPN-DNS(系统 DNS 恢复正常)→ 重试/重启 | 请求恢复;`connect_end` 出现真实 IP;`probe_success`/回 NORMAL(或下一轮正常解析) | P0 |
| TC-D-03 | IPv4 回环同样拦截 | VPN-DNS sinkhole 到 `127.0.0.1` | 同 TC-D-01:`system_dns_poisoned … addrs=[127.0.0.1]` + 抛 + 不建连 127.0.0.1 | P1 |
| TC-D-04 | 部分回环→留可路由 | VPN-DNS 返回 `[::1, <真实IP>]`(若工具支持多记录) | `system_dns_partial_bogon: <host> kept=1/2`;请求**用真实 IP 成功建连**,不抛、不升级 | P1 |
| TC-D-05 | Private DNS cross-check(升级链路) | `private_dns_mode=hostname` + 坏 specifier(UnknownHostException)→ 触发请求 | 走切域名/FALLBACK 升级(`域名切换 …`/`enter_fallback`);`connect_failed`/`call_failed` 埋点出现;**注意**:此路径**无** `system_dns_poisoned`(非回环,验的是下游升级而非回环转换) | P1 |
| TC-D-06 | 健康路径不误伤(无害) | 正常网络(无注入)冷启动 + 正常使用 | **无** `system_dns_poisoned`/`partial_bogon`/`httpdns_bogon_filtered`;App 正常,不刷异常量 | P0 |
| TC-D-07 | 第三方 host 回环不拦截 | (探索)第三方 RPC 若解析到内网/回环 | 非受管 host **不**走 sanitize、不抛(作用域固化,零开销直通) | P2 |

---

## 6. 回归 / 无害验证(P0,触动网络底座必做)

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-R-01 | 冷启动正常 | 正常网络冷启动 ×3 | 启动成功、首页加载、无 ANR/crash;`mobile_get_crash` 空 | P0 |
| TC-R-02 | 核心业务可用 | 登录态下浏览首页/IM 会话列表/钱包资产页(只读,不点危险项) | 数据正常加载,无大面积请求失败 | P0 |
| TC-R-03 | 既有自愈链路无回归 | 复跑 06-23/06-25 的域名切换 + HTTPDNS FALLBACK 关键用例(Private DNS 污染) | `域名切换 (可达优选)`/`enter_fallback`/`isTcpReachable` 等行为与上一轮一致 | P0 |
| TC-R-04 | IM 启动正常(单 navi 现状) | 确认 IM 启动 server 列表 | IM 用 `getAppNaviListForJ()` 接线,测试环境单 navi(`im-s.debox.pro`)正常初始化、能连上(`onOpen`),与改前等价无回归 | P1 |
| TC-R-05 | 多设备交叉 | 小米 `402714f0` 复跑 TC-B-01/TC-C-01/TC-D-06 | 行为与三星一致(无厂商差异) | P2 |

---

## 7. IM 多 navi 容灾(P0-C3,核心存疑点需真机证)

> 前置:已装含改动的 dev 包;grep `DBXJimCenter|JIM|navi|onOpen|setServerUrls|connect`。
> ⚠️ **测试环境默认单 navi**(`JIM_NAVI_SERVER_TEST=wss://im-s.debox.pro`),failover 用例(TC-N-04/05)**必须**先把该值改成
> 逗号分隔(坏主+真备)再重建安装;测完复原。**最核心的 TC-N-04 直接决定 P0-C3 ① 是否有效**。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-N-01 | navi 解析:多分隔符/去空/去重/保序 | 静态/单测:`getAppNaviListForJ` 喂 `"a, b ;a;;c"` | 得 `[a, b, c]`(`,`/`;` 都拆、trim、去空、去重、主序保留)**(建议补 JVM 单测,当前无覆盖)** | P1 |
| TC-N-02 | navi 解析:单值向后兼容 | 喂 `"wss://im-s.debox.pro"`(无分隔符) | 得单元素列表 `[wss://im-s.debox.pro]`,行为同改前 | P0 |
| TC-N-03 | DBXJimCenter 接线 + 正常连接 | 测试环境(单 navi)冷启动登录 → 抓 IM 日志 | `setServerUrls` 收到 list;IM `onOpen` 握手成功;会话列表正常加载 | P0 |
| TC-N-04 | **JuggleIM 多 navi failover(待证核心)** | 临时配 `JIM_NAVI_SERVER_TEST=wss://im-bad.invalid,wss://im-s.debox.pro`(坏主+真备)→ 重建安装 → 冷启动 | **IM 最终经备用 navi `onOpen` 成功**(主连不上后切备用);若一直钉死在坏主、不切备用 → **P0-C3 ① 失效(JuggleIM 不 failover)**,记 BUG 并标方案需调整 | P0 |
| TC-N-05 | 主域名回环黑洞→备用绕过(真实成因) | 配多 navi(主+备不同 hostname)→ VPN-DNS 仅把**主 navi hostname** sinkhole 到 `127.0.0.1`、备用可解析 → 冷启动 | IM 经备用域名 `onOpen`,不再永久 `ECONNREFUSED`(复现工单 `ws.debox.pro/::1` 场景并验证已被绕过) | P1 |
| TC-N-06 | 备用域名实际异线路核对 | 解析 `ws.debox.pro` vs `ws.dbxsocial.com`(生产配置)的 IP | 确认两者**非同源/同 IP**(否则同遭污染时备用无意义,= 06-25 记录的 `dbxsocial.com 非真异线路` 风险)——运维项,标注 | P2 |
| TC-N-07 | IM 仍走系统 DNS(范围澄清) | 抓 IM 解析链路 | 确认 IM **未**接 HTTPDNS(本次只做多 navi);`ws.debox.pro` 仍**不在** `httpdns_hosts` → P0-C3 ②③ 未做,留后续(非缺陷,范围内) | P2 |
| TC-N-08 | 切 navi 不丢消息/不重连风暴 | TC-N-04 failover 后观察 | 切备用后消息收发正常、无退避顶满 32s 风暴、无重复消息 | P1 |
| TC-N-09 | 配置复原回归 | 测完把 `JIM_NAVI_SERVER_TEST` 改回单值 → 重建安装 | IM 恢复单 navi 正常连接;无残留多 navi 副作用 | P1 |

---

## 取证关键字(grep 速查)

```bash
SER=RFCYA0F9SSZ; ADB=~/Library/Android/sdk/platform-tools/adb
# P0-A 回环/bogon
$ADB -s $SER logcat -d | grep -aiE "AliHttpDnsDns|system_dns_poisoned|system_dns_partial_bogon|httpdns_bogon_filtered|httpdns_empty_fallback_system|enter_fallback|proactive_fallback|域名切换"
# P0-B accessor 早注册
$ADB -s $SER logcat -d | grep -aiE "DomainManager|bootstrapEarly|domain_switch_skip|domain_accessor_null|seedFromBundled|restoreFromCache"
# P1-C4 连接埋点
$ADB -s $SER logcat -d | grep -aiE "NetEventListener|dns_end|connect_start|connect_end|connect_failed|call_failed"
# P0-C3 IM 多 navi(JuggleIM 连接/握手/navi 切换)
$ADB -s $SER logcat -d | grep -aiE "DBXJimCenter|JIM|setServerUrls|navi|onOpen|onConnect|reconnect|ws\.debox|dbxsocial"
# FLogger 文件落盘(release 也可见;dev 同时落 Logcat)
$ADB -s $SER shell "run-as com.tm.security.wallet ls -t files/dbx_log/flog 2>/dev/null | head"
```

## 故障注入 / 复原命令

```bash
# Private DNS 污染(cross-check,产生 UnknownHostException)
$ADB -s $SER shell settings put global private_dns_mode hostname
$ADB -s $SER shell settings put global private_dns_specifier dns.invalid-sinkhole.example
# 复原(测完务必执行)
$ADB -s $SER shell settings delete global private_dns_specifier
$ADB -s $SER shell settings delete global private_dns_mode
$ADB -s $SER shell settings put global http_proxy :0
$ADB -s $SER shell svc wifi enable && $ADB -s $SER shell svc data enable
# VPN-DNS 回环注入:经第三方 App(DNS Changer/AdGuard 自定义规则 域名→127.0.0.1),测完关闭/卸载
```

---

## 统计

| 分类 | 用例数 | P0 | P1 | P2 |
|------|--------|----|----|----|
| 0 框架/构建前置 | 5 | 5 | 0 | 0 |
| 1 JVM 单测层(P0-A 权威) | 8 | 6 | 2 | 0 |
| 2 静态接线核对 | 5 | 0 | 5 | 0 |
| 3 真机 P0-B accessor | 5 | 3 | 1 | 1 |
| 4 真机 P1-C4 埋点 | 6 | 0 | 5 | 1 |
| 5 真机 P0-A 回环行为 | 7 | 3 | 2 | 2 |
| 6 回归/无害 | 5 | 3 | 1 | 1 |
| 7 IM 多 navi 容灾(P0-C3) | 9 | 3 | 4 | 2 |
| **合计** | **50** | **23** | **20** | **7** |

## 关键风险与边界(测前必知)

1. **设备 APK 不含改动**：必须先 TC-P-005 重建安装,否则真机层全部失真。
2. **回环注入是非 root 真机难点**：VPN-DNS 改写是唯一高保真在机手段(较脆),JVM 单测(TC-U)是核心逻辑的权威兜底。
3. **测试环境 EMAS 未托管 t.debox.pro**：FALLBACK 后 `httpdns_empty_fallback_system`,HTTPDNS「救活请求」happy 路径无法真机验,只能验「空结果降级 + 域名切换接管」。
4. **markProactiveFallback 仅收窄 host 升级**：`t.debox.pro` 才出 `enter_fallback reason=proactive-domain-switch`;`debox.pro` 是 `proactive_fallback_skip`(正确 no-op,非缺陷)。
5. **P0-C3 核心是"待证"**:JuggleIM(二进制 AAR)是否真做多 navi failover 不可静态确认,**TC-N-04 是 P0-C3 ① 成败判据**;且测试环境单 navi,failover 用例需改 `JIM_NAVI_SERVER_TEST` 配置重建。本次只做多 navi 域名,IM 未接 HTTPDNS(②③ 留后续)。
6. **P0-C3 备用域名真异线路存疑**:生产 `ws.dbxsocial.com` 若与 `ws.debox.pro` 同源/同 IP,则同遭污染时备用无意义(承接 06-25 `dbxsocial.com 非真异线路` 记录)——运维核对项 TC-N-06。
7. **死代码**:`getAppNaviForJ()`(旧单值)改后无引用,可清理(代码质量,非测试阻断)。
8. **危险红线**：全程不清数据/不卸载/不登出/不切环境;钱包资产页/IM 只读浏览,不点转账/签名/删除。
9. **dev 是主分支**:本改动未提交,测试不涉及 commit;如需提交由用户决策。
