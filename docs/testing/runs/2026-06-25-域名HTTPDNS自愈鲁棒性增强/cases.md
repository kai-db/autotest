# 域名容灾 + HTTPDNS 自愈鲁棒性增强 — 测试用例

> 被测对象：**debox-android 域名容灾 / HTTPDNS 自愈链路**——相对 06-23 那轮(7 个 commit `926b14e…92250d4`)的**新增量**。
> 本轮 9 项改动 + 1 个重构来自对该链路的对抗性源码分析(回答「还会存在用户无法使用的情况吗」),修复了几类自愈失灵 / 取证盲区。
> **状态**：改动在 debox-android 工作区(分支 `debox`),提交后回填 commit hash。
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | 自愈鲁棒性增强:OSS 纠偏即时生效 / 单接口页面兜底切换 / 连通性失败请求自动重发 / 切换前可达性优选 / 网络变化二次探测 / 自愈事件本地落盘 / host 归一化 / OSS 失败留痕 / DNS 切换提前 HTTPDNS FALLBACK |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | A 层静态核对 + B 层 JVM 单测 + D 层真机(mobile-mcp / adb logcat + FLogger 文件) |
| 设备 | 三星 SM-S9210(`RFCYA0F9SSZ`,主测)+ 小米 25067PYE3C(`402714f0`,交叉);dev DEBUG 构建,日志全开 |
| 前置条件 | 设备解锁 + 已登录 + 测试环境 + WiFi 在线;**危险红线**:不清数据/不卸载/不登出/不切环境 |
| 实现仓库 | `/Users/xiaochengcheng/StudioProjects/debox-android` |

---

## 本轮改动范围(9 项 + 1 重构)

| 编号 | 改动 | 文件 | 解决的问题 |
|---|---|---|---|
| ① | OSS 纠偏即时生效 | `DomainManager.fetchOssDomains` | currentDomain 不在新池时纠偏漏 `resetUrl()` → 服务端下线域名后本会话仍打旧域名 |
| ② | 单接口页面兜底切换(B2) | `DomainManager.onConnectivityFailure` + `reachedSwitchThreshold` | 旧逻辑只按"不同 path≥3"切;单接口页面(登录引导/长轮询)不同 path 恒为 1 → 永不切换、用户钉死 |
| ④ | 连通性失败请求自动重发(B4) | `DeBoxHttpRequest.call/get/post` | 触发切换那次请求仍走 onFail;切换只惠及下一请求 |
| B1 | 切换前同步可达性优选 | `DomainManager.onConnectivityFailure` | 旧 `selectBestDomain` 只按故障计数选、不探可达 → 可能切到同样不可达的域名"坏→坏"空转 |
| B3 | 网络变化二次健康探测 | `DomainManager.registerNetworkChangeHealthCheck` + loop-drain | 健康探测只冷启一次;会话中途主域名被封无主动探测入口 |
| 落盘 | 自愈事件 FLogger 落本地文件 | `NetFlog` + 各自愈点 | 原 `LogUtils` 仅 Logcat 且受 `BuildConfig.DEBUG` 门控 → release 包无任何自愈轨迹、重启即丢 |
| ⑤ | OSS/SP host 归一化防污染 | `DomainManager.normalizeHost` + parseHostsJson/parseCsvDomains | OSS 误配 `https://x/debox/`/带路径/非法字符直接进池 → `currentBaseUrl()` 拼出脏 baseUrl + 污染持久化池 |
| ⑥ | OSS 拉取失败落盘 | `fetchOssDomains` onFail/空内容/空 hosts | 原 `loadFile` 只传 onSuccess → OSS 被阻断/下载失败/解析空全部无痕,现场只见"没成功"看不到"被阻断" |
| ⑦ | DNS 级切换时对新域名提前置 HTTPDNS FALLBACK | `onConnectivityFailure(…, dnsLevelFailure)` + `HttpDnsFallbackPolicy.markProactiveFallback` | HTTPDNS fallback 是 per-host:切到 B 后 B 仍 NORMAL,全量 DNS 污染下重发到 B 先撞系统 DNS 再失败才进 FALLBACK。DNS 级失败时提前把 B 置 FALLBACK+预热,省一轮(§11 两机制显式协作) |
| 重构 | 故障判定两套口径分离 | `NetworkFailures.isDomainSwitchSignal`(宽) / `isReplaySafe`(窄) | 拦截器切换/HTTPDNS 用宽口径,请求重发用窄口径(SSL 不证明未送达,POST 防重复提交) |

> **B1 实现要点(关键)**:可达性优选**前移到切换决策内、同步完成**(锁外探测),而非异步 post-switch 校验。
> 因 `onConnectivityFailure` 在拦截器 catch 内同步调用、返回后异常才上抛,故请求层 B4 重发时 `currentDomain`
> 已是**验证可达**的域名——B1 与 B4 时序不再打架。
>
> **B1 验证边界(新增)**:若实现仍是 `Socket.connect(host, 443)` 级探测,只能证明 TCP 可连,不能证明 TLS/SNI/证书/HTTP 可用。
> 该边界必须在用例里显式验收或标注为已知限制,避免把"TCP 可达"误写成"业务可用"。

---

## 改动逻辑分析(测试依据)

### 1. 域名层切换(增强后)

```
onConnectivityFailure(domain, path)  [拦截器 catch 内同步调用]
  ├─ 累计故障 + 滑动窗口(保留重复 path)
  ├─ reachedSwitchThreshold = 不同 path 数 ≥3  或  同一 path 重复数 ≥3   ← ② 新增 same-path 兜底
  ├─ 冷却期 30s 守卫
  ├─ 候选 = 池内非当前域名,按故障计数升序
  └─ 锁外可达性优选(B1):isTcpReachable(候选, 1500ms) 选首个 TCP 可达 → currentDomain + resetUrl
       ├─ 全部不可达(active 网络下全域名被封,如全量 DNS 污染)→ 退回故障计数最小者(让 HTTPDNS 接管)
       └─ 若本次失败是 DNS 级(UnknownHostException)→ markProactiveFallback(target):提前把新域名置 FALLBACK+预热(⑦/§11)
  日志:`域名切换 X -> Y (可达优选)`

请求层(DeBoxHttpRequest.call):  ← ④
  IOException → catch:若 isReplaySafe(窄口径,排除SSL) 且 path 相对 且 getHost()≠attemptHost 且未重发过
       → domainRetried=true,call(param, block) 自动重发一次(block 内重算 resolveFullUrl 拿新 host)
  attemptHost = 本请求**真实发出**的 host(block 内与 resolveFullUrl 同刻记录,非协程启动前快照)→ 并发切换下不伪重发
  日志:`域名已自愈切换(X -> Y)，自动重发: <path>`
```

### 2. 健康探测(增强后,loop-drain)

```
verifyAndHealCurrentDomain(reason)   触发:startup / network-available(B3)
  ├─ running CAS 抢锁;抢不到只置 pending 后返回(在跑线程收尾补跑,loop-drain 不丢触发)
  └─ runHealthCheckOnce:isTcpReachable(current)? 可达→无操作;不可达→fallbackDomains+池里找可达切过去;全不可达→保留当前(疑似离线)
registerNetworkChangeHealthCheck:NetworkCallback(NET_CAPABILITY_INTERNET).onAvailable → verifyAndHealCurrentDomain("network-available")
  (运行期请求级切换已在 onConnectivityFailure 内同步做可达优选,不再走 post-switch)
```

### 3. OSS 纠偏(①)

```
fetchOssDomains:OSS hosts 覆盖池 → ensureFallbackDomains → 若 currentDomain 不在新池 → currentDomain=池首
  → urlChanged 时 HttpConstant.resetUrl()    ← ① 补;否则 baseSetUrl() 缓存旧域名,本会话纠偏不生效
```

### 4. 自愈事件落盘(FLogger)

```
NetFlog.i/w → FLogger(本地文件 dbx_log/flog,可 uploadLog 上传 OSS)+ Logcat;防御性吞异常(不打断业务/不崩 JVM 测)
落盘事件:启动池基线 / 累计故障 / 域名切换(可达优选) / 无可用备选 / 健康探测自愈·离线保留 / OSS 纠偏·池更新 /
         enter_fallback / probe_success / probe_fail / fallback_request_failed / SDK init 成败 / 密钥缺失 /
         fallback_pending_init / 预解析·强刷·补发失败 / httpdns_empty·error_fallback_system / 生效配置 / 段解析失败
自愈链路内**所有日志统一走 NetFlog**(原高频 trace:未达阈值/冷却中/可达无需处理/探测期跳过/非法IP/启动基线 等也一并落盘)——
不再保留"只进 Logcat、release 不可见、重启即丢"的 LogUtils-only 路径(应「LogUtils 处也要落 flog」要求)
```

---

## A 层：接入点与落地静态核对

| # | 用例 | 验证标准 | 优先级 |
|---|------|----------|--------|
| PRE-A1 | ① OSS 纠偏 resetUrl | `fetchOssDomains` 用 `previous` 记旧域名,`urlChanged` 时锁外调 `HttpConstant.resetUrl()` | P0 |
| PRE-A2 | ② same-path 阈值 | `reachedSwitchThreshold(windowPaths, path, 3, 3)`=不同 path≥3 **或** 同 path≥3;`failureWindow` 保留重复;`samePathThreshold=3` | P0 |
| PRE-A3 | B1 切换前可达优选 | `onConnectivityFailure` 候选按故障计数升序、**锁外** `isTcpReachable(c, switchProbeTimeoutMs)` 选首个可达;无 `selectBestDomain` 盲选;探测不在锁内;若 `isTcpReachable` 为 TCP-only,日志/文档不得宣称 TLS/HTTP 可用 | P0 |
| PRE-A4 | ④ 请求层重发 | `DeBoxHttpRequest`:`domainRetried` + `attemptHost` 字段;get/post 的 URL 在 block 内**每次重算**且**同刻**写 `attemptHost`;catch 内仅 `isReplaySafe && 相对路径 && attemptHost非空 && getHost()≠attemptHost && !domainRetried` 才 `call()` 重发 | P0 |
| PRE-A5 | B3 网络变化探测 + loop-drain | `registerNetworkChangeHealthCheck`(NET_CAPABILITY_INTERNET,只 `onAvailable`,`networkCallbackRegistered` 仅注册一次);`verifyAndHealCurrentDomain` 用 `healthCheckRunning`+`healthCheckPending` loop-drain,**单轮 `runHealthCheckOnce` 包 try/catch**(防线程死、`healthCheckRunning` 卡 true);`init` 用 `applicationContext` 注册 | P0 |
| PRE-A6 | 故障判定两套口径分离 | `NetworkFailures.isDomainSwitchSignal`(宽,含 SSL)供拦截器切换/HTTPDNS;`isReplaySafe`(窄,**排除 SSL**)供请求重发——SSL 不保证未送达,POST 重放防重复副作用;read timeout 两者都不含(已知边界) | P0 |
| PRE-A7 | FLogger 落盘接线 | `NetFlog`(防御性 try/catch)存在;**自愈链路 `DomainManager`/`AliHttpDnsManager`/`AliHttpDnsDns`/`HttpDnsConfig`/`HttpDnsFallbackPolicy` 内无 `LogUtils`-only 路径,日志统一走 `NetFlog`(含原高频 trace)→ 全部落盘**;`RetrofitFactory` 注入 `AliHttpDnsDns(log={NetFlog.w})` | P1 |
| PRE-A8 | ⑤ host 归一化 | `parseHostsJson`/`parseCsvDomains` 经 `normalizeHost`:去 scheme/port/path,转小写,校验合法主机名(含点),非法项丢弃;非 ASCII 不做 IDN 转换直接判非法;逐 label 拒绝空段/首尾 `-`(`a..b.com`/`a.-b.com`/`a-.b.com`);裸 host 含 `@` 直接拒绝(不把 `debox.pro@evil.com` 截成 `evil.com`) | P1 |
| PRE-A9 | ⑥ OSS 拉取失败落盘 | `fetchOssDomains` 传 `onFail`(下载/HTTP 失败)+ 空内容 + 空 hosts 三类各落 `NetFlog.w`,标注沿用 SP/内置兜底 | P1 |
| PRE-A10 | ⑦ DNS 级切换提前置新域名 FALLBACK | `onConnectivityFailure(…, dnsLevelFailure)` 透传(拦截器按 `exception is UnknownHostException` 判定);切换成功且 dnsLevelFailure 时调 `HttpDnsFallbackPolicy.markProactiveFallback(target)`;该方法**不 consume ThreadLocal**(属旧域名)、按生效域名 gating;**幂等口径按「下一次 decide 是否已直接走 HTTPDNS」判:未过期 FALLBACK / 有效租约 PROBE 才 no-op,NORMAL / 过期 FALLBACK / 过期 PROBE 一律刷新为新一轮 FALLBACK+重新预热**(否则重发先撞一轮系统 DNS,削弱核心收益)[review-②] | P0 |
| PRE-A11 | 切换落锁竞态保护 | `onConnectivityFailure` 锁外探测后,最终落锁切换前重新校验 `currentDomain` 仍是本次失败时的域名、`target` 仍在当前 `domainList`;若 OSS 更新/健康探测/并发切换已改状态,不得用旧候选覆盖新状态(跳过或重选)。**冷却(`lastSwitchTime`)+ 清窗口只在真正切换成功时提交,探测期用独立 `switchInProgress` 标志防并发(try/finally 必释放)**——放弃切换不烧 30s 冷却、不清失败窗口,否则 OSS 移除 target 而域名仍坏时会锁死 30s+重攒窗口 [review-③] | P0 |
| PRE-A12 | HTTPDNS hosts 共用归一化 + 全非法安全失败 | `HttpDnsRemoteConfig` 解析 `httpdns_hosts` 时复用与 OSS 域名池一致的 host normalizer;`https://debox.pro/debox/` → `debox.pro`,`bad host` 丢弃,避免域名池可用但 `isEffectiveHost("debox.pro")` 不命中。**配了非空 hosts 但归一化后全非法 → `hostsConfiguredButEmpty=true`,`isEffectiveHost` 安全失败返回 false**(区分「未配置/显式空=不收窄」与「配置了却全非法」,避免误配把 HTTPDNS 作用域从空放大到全部受管域名)[review-①] | P1 |
| PRE-A13 | full URL 请求边界显式化 | `DeBoxHttpRequest` full URL 仍不自动重发;若 `sysConfig` 等启动请求继续提前拼 full URL,case/日志需明确它不享受 B4 自动重发,且可能早于 OSS/健康探测纠偏 | P1 |

---

## B 层：JVM 单测(全自动;基础已落地,新增 review 子项建议补齐)

> 运行命令(同 06-23,必须关 configure-on-demand)：
> ```bash
> ./gradlew :business:BaseModule:testDebugUnitTest --tests "*Domain*" -Dorg.gradle.configureondemand=false
> ./gradlew :business:BaseBusiness:testDebugUnitTest --tests "com.app.base.business.network.*" -Dorg.gradle.configureondemand=false
> ```

| # | 覆盖簇 | 关键用例 | 优先级 |
|---|------|----------|--------|
| UT-N1 | `reachedSwitchThreshold` ×5(DomainManagerTest) | 不同 path≥3 触发 / 都未达不触发 / **同一 path≥3 触发(B2)** / 同 path<3 不触发 / 同 path 阈值只计本 path | P0 |
| UT-N2 | `NetworkFailuresTest` ×4 | DNS/Connect/Route 两口径都计 / **SSL 是 switch signal 但非 replay-safe** / connect timeout 两者都计、read timeout 两者都不计 / 普通 IO·业务异常都不计 | P0 |
| UT-N3 | `normalizeHost` + 脏值解析(DomainManagerTest;归一化已下沉共享 `HostNormalizer`)**[已落地]** | 去 scheme/path/port + 小写归一 / 拒绝无点·空格·首尾 -·非 ASCII / **逐 label 拒绝 `a..b.com`·`a.-b.com`·`a-.b.com`** / **裸 `debox.pro@evil.com` 拒绝、URL 形式 `https://user@debox.pro/x`→`debox.pro`** / `parseHostsJson` 丢弃脏项(⑤+[84]) | P1 |
| UT-N4 | `markProactiveFallback` ×6(HttpDnsFallbackPolicyTest)+ 拦截器 dnsLevel ×2 **[已落地]** | switch on→进 FALLBACK+预热一次 / switch off→no-op / **未过期 FALLBACK→幂等不重复预热** / **过期 FALLBACK→刷新为新 FALLBACK+再预热一次(否则重发先 PROBE_SYSTEM)** / **过期 PROBE 租约→刷新为 FALLBACK** / **有效租约 PROBE→no-op 不打断在途单飞**;拦截器 UHE→dnsLevel=true、Connect→false(⑦/review-②) | P0 |
| UT-N5 | `HttpDnsRemoteConfig` hosts 归一化 + 全非法标记 **[已落地]** | `httpdns_hosts` 输入 `https://debox.pro/debox/`、`T.DEBOX.PRO`、`bad host` → 只保留合法归一化 host(`["debox.pro","t.debox.pro"]`);与域名池共用 `HostNormalizer`。**全非法(`["bad host","@@@"]`)→ hosts 空 + `hostsConfiguredButEmpty=true`;部分非法→保留合法项 + flag=false;缺失/显式空数组→flag=false** [review-①] | P1 |
| UT-N6 | 切换落锁竞态(代码已加守卫,纯 JVM 难测) | 代码:落锁提交前校验 `currentDomain==from` 且 `target∈domainList`,否则放弃。`onConnectivityFailure` 耦合 SP/Socket 无法纯 JVM 测 → 靠 PRE-A11 代码审计 + D 层观察(无"旧候选覆盖新状态") | P0 |

> 判定:UT-N1~N5 均已落地,全部 PASS 为通过;UT-N6 竞态守卫在代码层(纯 JVM 不可测,见 PRE-A11)。任一 FAIL 阻断(核心网络链路 block 级)。
> 说明:`onConnectivityFailure`/`fetchOssDomains`/`verifyAndHealCurrentDomain`/`DeBoxHttpRequest.call` 整体耦合
> SharedPreferences/Socket/ConnectivityManager/协程,**纯 JVM 不可测**;故 ② 抽纯函数测、④ 抽 `NetworkFailures` 测,
> 其余靠 A 层静态 + D 层真机。

---

## C 层：集成测试

> 本轮无新增 MockWebServer 集成:B1/B4/B3/① 分别耦合 Socket 探测 / 协程重发 / NetworkCallback / OSS 远端,
> 不在纯 JVM+MockWebServer 可覆盖范围。HTTPDNS 真实建连语义沿用 06-23 C 层(`AliHttpDnsDnsIntegrationTest` 9 例)。

---

## D 层：真机验证(AI 驱动 + adb logcat / FLogger 文件取证)

> 污染/断网手段(均可逆,测后必复原)见文末速查。危险红线:不清数据/不卸载/不登出/不切环境。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| DV-01 | ② 单接口页面兜底切换 | DNS 污染 → **只反复触发同一个接口**(停在单一列表页连续下拉,不切页) | logcat 出现 `同path=` 递增;达 3 后 `域名切换 X -> Y (可达优选)`(旧逻辑此场景 `不同path=1` 永不切) | P0 |
| DV-02 | ④ 切换请求自动重发 | DNS 污染触发切换的那一刻观察该请求 | `域名已自愈切换(X -> Y)，自动重发: <path>` 且重发后该请求成功;**负向**:正常态/读超时/业务错误/**SSL 失败**/完整 URL(如 sysConfig)均不重发;并发切换下用真实 attemptHost、无伪重发 | P0 |
| DV-03 | B1 切换带可达优选 | DNS 污染持续触发切换 | 切换日志带 `(可达优选)`;全量 DNS 污染下所有候选不可达 → 退回最小计数域名(仍切换,交 HTTPDNS 接管)。一坏一好选择性封堵非 root 难构造 → best-effort+代码分析 | P1 |
| DV-04 | B3 网络变化二次探测 | 正常态 `svc wifi disable` → `enable`,或 WiFi↔蜂窝切换 | `verifyAndHealCurrentDomain(network-available): ...`(当前可达 → `可达，无需处理`);连续切网不崩、不堆线程(loop-drain) | P1 |
| DV-05 | ① OSS 纠偏即时生效 | 黑盒难触发(需 OSS 剔除当前域名);若 dev 可改 `conf_test.json` 剔除 currentDomain → 冷启动 | `fetchOssDomains: 域名不在池中或为空, X -> Y` 后**本会话**后续请求即用新域名(非等下次冷启) | P2 |
| DV-08 | ⑦ DNS 级切换提前置新域名 FALLBACK | DNS 污染 → 触发到切换 debox.pro→t.debox.pro | 切换**当刻**即出现 `enter_fallback: t.debox.pro reason=proactive-domain-switch` + 对其 `setPreResolveHosts`,**无需 t.debox.pro 先失败一次**;随后重发对 t.debox.pro `decide=HTTPDNS`(命中与否取决于 preResolve 回填时机) | P1 |
| DV-09 | TCP 可连但 TLS/SNI 失败边界 | 通过测试代理/证书拦截/可控测试域名构造 443 可连但 TLS handshake 或证书失败;若设备条件不满足则仅做代码审计 | 若实现仍为 TCP-only,健康探测/可达优选可能误判可达;后续请求表现为 SSL failure:可触发域名切换,但不自动重放。该结果按已知边界记录,不得判为"B1 已证明 TLS 可用" | P2 |
| DV-10 | 启动 full URL 请求不享受自动重发 | DNS 污染后冷启动,重点观察 `sysConfig`/`getOfficialDomain()+API_OFFICIAL` 类完整 URL 请求 | full URL 失败不出现 `自动重发`;后续相对 path 请求在域名纠偏后可恢复。若产品要求启动配置也自愈,需把该请求改成相对 path 或单独重试机制 | P2 |
| DV-06 | 自愈轨迹落本地文件 | 跑完 DV-01~04 后拉文件日志 | `dbx_log/flog` 内含完整轨迹:`累计故障 → 域名切换(可达优选) → enter_fallback → (probe/fallback_request_failed) → verifyAndHealCurrentDomain(...)`;关键事件即便 release 也应落盘(本测 DEBUG 下文件存在即可) | P0 |
| DV-07 | 复原后基线回归 | 复原 DNS/网络/代理 → 重跑冷启动 | 全程系统 DNS、健康探测 `可达，无需处理`、无残留 FALLBACK、App 正常 | P0 |

---

## 已知不可黑盒覆盖 / 接受的边界(诚实标注)

| 场景 | 原因 | 替代验证 |
|---|---|---|
| **握手期 read-timeout 形态的 TLS 阻断**不触发切换/重发 | `NetworkFailures` 仅计 connect-timeout;区分握手期 vs 响应期 read-timeout 需 EventListener,盲纳会把"服务端慢"误判触发切换 | **接受为已知边界**;后续若做 EventListener 归因再纳入 |
| **TCP 443 可连但 TLS/SNI/证书/HTTP 不可用**会被 TCP-only `isTcpReachable` 误判可达 | `Socket.connect` 只覆盖传输层,不做 TLS handshake 和 HTTP 请求;B1 只能降低"完全不可达"切换概率,不能证明业务层可用 | **接受为当前边界或改造为 HTTPS/TLS 探测**;DV-09 记录,文案不得把 TCP 可达等同业务可用 |
| **SSL 失败可切换但不自动重放**(POST 重复提交风险) | SSLException 可能发生在握手之后(写请求体/读响应),不能证明请求未送达;无 OkHttp phase 信息无法精确判定 | **接受的设计取舍**:`isReplaySafe` 排除 SSL,只对保证未送达的 DNS/connect/no-route/connect-timeout 重放;UT-N2 锁定 |
| 全量 DNS 污染下 B4 重发的**即时**成功仍依赖预热时机(⑦ 已大幅缓解) | ⑦ 已在切换时把新域名置 FALLBACK + 触发 preResolve(异步 SDK 拉取);但 `lookup` 是 SyncNonBlocking 只查缓存,若重发发生在 preResolve 完成前,这一次仍 `httpdns_empty_fallback_system` 降级系统 DNS | **已大幅缓解**:省掉"新域名先失败一次才进 FALLBACK"一轮、预热提前到切换时刻 → 恢复更快;即时重发是否命中取决于 preResolve 是否已回填,未命中则下一请求命中。SyncNonBlocking 是有意为之(不阻塞),不改 |
| B1 一坏一好选择性封堵 | 非 root 真机不能只封单域名 | 代码分析 + 退化路径 DV-03(全坏退回最小计数) |
| ① OSS 纠偏 resetUrl 即时生效 | 需 OSS 下发剔除当前域名,香港 OSS 远端不可控 | A 层 PRE-A1 静态 + 代码分析;dev 可改 conf 时 DV-05 |
| ④ 请求层重发的协程/Android 路径 | 耦合协程+Retrofit+ConnectivityManager,纯 JVM 不可测 | A 层 PRE-A4 静态 + D 层 DV-02 |
| 启动早期 full URL 请求可能早于 OSS/健康探测纠偏 | `sysConfig` 等请求若提前拼完整 URL,不走相对 path 的动态域名解析与 B4 自动重发;纠偏完成后只惠及后续请求 | DV-10 记录;若要覆盖启动配置链路,需改请求形态或加专用重试 |
| 全量 DNS 污染下 B1 可达优选 | 系统 DNS 全坏 → 所有候选探测不可达 → 优选退化为 no-op(预期) | DV-03 退化路径 + HTTPDNS 接管(沿用 06-23 MV-20~22) |

---

## 取证命令速查

```bash
S=RFCYA0F9SSZ   # 三星主测;小米用 402714f0

# 启动 + 域名/HTTPDNS 全量日志(关键字含本轮新日志)
adb -s $S logcat -c
adb -s $S shell am force-stop com.tm.security.wallet
adb -s $S shell monkey -p com.tm.security.wallet -c android.intent.category.LAUNCHER 1
adb -s $S logcat -d | grep -aiE "DomainManager|AliHttpDnsManager|HttpDnsConfig|HttpDnsFallbackPolicy|AliHttpDnsDns|FLogger|enter_fallback|proactive-domain-switch|probe_success|probe_fail|httpdns_empty_fallback_system|域名切换|可达优选|自动重发|verifyAndHealCurrentDomain|累计故障"

# FLogger 本地文件(debug 包):自愈轨迹的权威持久化来源(重启不丢、release 也留痕)
adb -s $S shell run-as com.tm.security.wallet ls files/dbx_log/flog
adb -s $S shell run-as com.tm.security.wallet cat files/dbx_log/flog/<最新文件>

# DNS 污染(可逆,触发 UHE 进拦截器)
adb -s $S shell settings put global private_dns_mode hostname
adb -s $S shell settings put global private_dns_specifier dns.invalid-debox-test.example

# 复原(测后必做)
adb -s $S shell settings delete global private_dns_specifier
adb -s $S shell settings put global private_dns_mode off
adb -s $S shell svc wifi enable && adb -s $S shell svc data enable
adb -s $S shell settings put global http_proxy :0
```

---

## 与 06-23 文档的关系(就地修正失真项)

06-23 `cases.md` 因本轮代码改动产生以下**失真项**(已在该文件就地修正,照旧文跑会对不上日志):

| 06-23 Case | 失真点 | 修正 |
|---|---|---|
| PRE-02 | `.dns(AliHttpDnsDns())`;"DomainSwitchInterceptor 为首个拦截器" | 改为 `AliHttpDnsDns(log=...)`;**CrashContextInterceptor 现为首个**(DomainSwitch 次之) |
| PRE-07 | 核对 `DomainSwitchInterceptor.isConnectivityFailure` | 该方法已抽到 `NetworkFailures.isDomainSwitchSignal`(拦截器/HTTPDNS);请求重发另用更窄的 `isReplaySafe` |
| MV-04 | `checkCurrentDomainHealth: 当前域名 X 可达` | 重命名为 `verifyAndHealCurrentDomain(startup): 当前域名 X 可达` |
| MV-10 | `未达阈值` 文案 | `窗口内不同path=X/同path=Y, 未达阈值(distinct=3/same=3)`;且新增 same-path 触发(见本轮 DV-01) |
| MV-11 | `域名切换 A -> B` | 现带 `(可达优选)` 后缀 |
| MV-13 | `...保留当前域名` | 现带 `verifyAndHealCurrentDomain(...)` 前缀 |
| 取证 grep | 含已不存在的 `checkCurrentDomainHealth` | 加 `verifyAndHealCurrentDomain|可达优选|自动重发` |
