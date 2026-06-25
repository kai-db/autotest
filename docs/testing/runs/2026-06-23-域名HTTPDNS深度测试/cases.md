# 域名动态切换 + 阿里云 HTTPDNS 异常兜底 — 深度测试用例

> 被测对象：**debox-android `dev` 分支**（版本 2.13.2 / versionCode 21300002），域名容灾与 HTTPDNS 两条优化线。
> 方案文档：`docs/aliyun-httpdns-integration-plan.md`（§4 状态机 / §11 协作 / §12 降级 / §14 验证）、`docs/domain-failover.md`。
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景
>
> **⚠️ 增量提示**：本链路后续又做了一轮自愈鲁棒性增强（OSS 纠偏即时生效 / 单接口兜底切换 / 请求自动重发 / 切换前可达优选 / 网络变化二次探测 / 自愈事件落盘），用例见
> `docs/testing/runs/2026-06-25-域名HTTPDNS自愈鲁棒性增强/cases.md`。本文档已就地修正受其影响的失真项（PRE-02/07、MV-04/10/11/13、取证 grep）。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | 域名动态切换（故障自愈 / 启动健康探测 / 兜底域名池）+ 阿里云 HTTPDNS 异常兜底（per-host 三态状态机 / OkHttp Dns 适配 / 配置兜底链） |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | A 层静态核对 + B 层 JVM 单测 + C 层 MockWebServer 集成 + D 层真机 AI 驱动（mobile-mcp / adb logcat） |
| 设备 | 三星 SM-S9210（`RFCYA0F9SSZ`，主测）+ 小米 25067PYE3C（`402714f0`，交叉验证）；均装 dev DEBUG 构建 2.13.2，日志全开 |
| 前置条件 | 设备解锁 + 已登录 + 测试环境 + WiFi 在线；**危险红线**：不清数据/不卸载/不登出/不切环境 |
| 实现仓库 | `/Users/xiaochengcheng/StudioProjects/debox-android`（B/C 层单测落点） |

---

## 本次"优化"范围（dev 分支，相对上轮 06-12/06-13 测试的增量）

> 上轮（06-12 HTTPDNS / 06-13 域名切换）测试时 **HTTPDNS 密钥未注入 → 构建产物整体禁用**，D 层 HTTPDNS 主链路（MV-02~05）无法执行。
> 本轮运行态探测确认：**密钥已注入、SDK 已激活**（`AliHttpDnsManager: doInit: HTTPDNS SDK 初始化完成`），HTTPDNS 真机全链路首次可实测——这是本轮"深度"的核心增量。

| 提交 | 优化点 | 重点回归 |
|---|---|---|
| `926b14e` feat | 接入阿里云 HTTPDNS 异常兜底（P1 主链路） | 三态状态机 / Dns 适配 / 懒初始化 |
| `cabeff8` fix | 域名容灾故障切换 DNS-only → 全连通性异常（UHE/Connect/NoRoute/SSL/connect超时） | 故障上报口径 |
| `aa6c43a` fix | 补回域名故障计数成功衰减并按当前域名 gating | onRequestSuccess 衰减 |
| `302b4f0` fix | 域名容灾补内置兜底池 + 启动健康探测，根治 ConnectException 钉死 | fallbackDomains / checkCurrentDomainHealth |
| `09d8483` fix | OSS 配置解析兼容扁平 `httpdns_*` 字段，修复生产配置静默失效 | HttpDnsRemoteConfig.parseSection / extractFlatSection |
| `2a4e940` fix | **HTTPDNS 受管集合纳入兜底域名，与域名切换池保持一致** | builtinHosts 含 dbxsocial.com / fetchOssDomains 用 domainListSnapshot() |
| `92250d4` fix | **PR#57：HttpDnsFallbackPolicy getOrPut→computeIfAbsent 原子初始化**，杜绝同 host 多 HostState 状态机分裂 | decide / markNetworkAbnormal 并发 |

---

## 改动逻辑分析（测试依据）

### 1. 域名动态切换全链路（DomainManager）

```
启动 → DomainManager.init
  ├─ restoreFromCache()      读 SP: domain_list / domain_current / domain_failures(启动衰减减半)
  │    └─ ensureFallbackDomains()  始终注入兜底池 [debox.pro, dbxsocial.com]（不仅缓存空时）→ domainList 永不空
  ├─ seedFromBundledConfig() 内置 res/raw/httpdns_conf_v2_fallback.json 兜底：仅本地无 OSS/SP 配置时 seed httpdns 开关+收窄+域名池
  ├─ AliHttpDnsManager.bootstrap(domainListSnapshot())  受管集合纳入 SP 恢复池+兜底；开关缓存为 true → 懒初始化+启动预热
  ├─ checkCurrentDomainHealth()  后台线程 TCP connect currentDomain:443（2.5s 超时）
  │    ├─ 可达 → 无操作
  │    ├─ 不可达 + 有可达备选 → 切过去 + saveCurrentDomain + clear窗口 + resetUrl
  │    └─ 全不可达（疑似离线）→ 保留当前域名，交请求级切换接管（不再误清已生效选择）
  └─ fetchOssDomains()       OSS conf 拉 hosts[] → 覆盖 domainList → ensureFallbackDomains() → HttpDnsConfig.updateFromOssContent() → updateDomainPool(domainListSnapshot())

运行期每请求经 DomainSwitchInterceptor（OkHttp 首个 application 拦截器）：
  ├─ 成功(任意HTTP码) + 受管host → onRequestSuccess(host)  仅 host==当前域名才清窗口+故障-1
  └─ IOException + 连通性故障 + 受管host → onConnectivityFailure(host, path)
       连通性口径 = UnknownHost / Connect / NoRouteToHost / SSLException / SocketTimeout(仅 connect 阶段)
       └─ 累计故障++ → 滑动窗口(10s)按 path 去重 → 不同path≥3 且过30s冷却 → selectBestDomain(非当前、故障计数最小) → 切 + resetUrl
```

### 2. HTTPDNS 异常兜底（per-host 三态状态机）

```
            连通性异常+开关开                TTL到期(默认10min)
 NORMAL ───────────────────→ FALLBACK ───────────────────→ PROBE
 系统DNS ←─ probe_success ─── HTTPDNS ←── probe_fail ───── 系统DNS单飞试探
（成功清理必须感知解析来源 §4.4：FALLBACK 期 HTTPDNS 成功只续期；只有 PROBE 系统DNS成功才清回 NORMAL）

AliHttpDnsDns.lookup(host)（OkHttp .dns()）：
  非受管host → 系统DNS直通（零开销，第三方RPC/DApp不被接管）
  decide(host)!=HTTPDNS → 系统DNS
  decide==HTTPDNS → AliHttpDnsManager.lookup（SyncNonBlocking 只查缓存）
       空结果 → markDegraded(改写来源=SYSTEM，不按HTTPDNS续期) + 系统DNS
       有结果 + appendSystemDns → HTTPDNS候选 + 系统候选(去重，try-catch容错)
```

### 3. 配置兜底链与开关（HttpDnsConfig）

```
优先级 OSS > SP > 内置 conf；从未拉到 → enabled=false（新装坏网保守不启用）
switchOn() = 密钥就绪 +（debugForce[仅dev/beta] 或 enabled+grayBucketHit(deviceId, grayPercent)）
isEffectiveHost(host) = 受管host(内置∪池) ∩ hosts收窄列表（hosts空=不收窄）
OSS 段兼容：嵌套 httpdns 段优先，回退扁平 httpdns_* 字段；段缺失/损坏 → 沿用缓存/默认值，不影响 hosts 解析
```

### 4. 本轮真机运行态实测前提（影响 D 层设计，必须据实）

| 事实（三星 RFCYA0F9SSZ，06-23 冷启动 logcat 实测） | 对测试的影响 |
|---|---|
| `doInit: HTTPDNS SDK 初始化完成` | HTTPDNS 已激活，D 层主链路可执行 |
| OSS `conf_test.json`：`enabled=true, grayPercent=100, hosts=[t.debox.pro], abnormalTtlMs=600000` | 开关全量开；HTTPDNS **生效域名收窄为 `t.debox.pro`**；FALLBACK TTL=10min |
| 域名池 `[t.debox.pro, t.dbxsocial.com, debox.pro, dbxsocial.com]`，currentDomain=`debox.pro` | **默认 debox.pro 流量不在 HTTPDNS 收窄集 → 不触发 HTTPDNS**；须流量落到 `t.debox.pro` 才进 FALLBACK |
| 健康探测 `当前域名 debox.pro 可达，无需处理` | 健康探测可达分支基线成立 |
| DEBUG 包日志全开 | logcat 可取证全部状态迁移 |

> **D 层 HTTPDNS 触发路径（据上）**：bad DNS 注入 → debox.pro 请求失败 → DomainSwitch 累计→切到 `t.debox.pro` → t.debox.pro 请求失败且 `isEffectiveHost=true` → `enter_fallback` → 后续请求走 HTTPDNS。即 HTTPDNS 触发与域名切换天然串联，正好深度验证 §11 两道自愈协作。

---

## 0. 框架前置检查（autotest 框架自身）

| # | 检查项 | 步骤 | 验证标准 | 优先级 |
|---|--------|------|----------|--------|
| TC-P-001 | 框架编译 | `./gradlew :autotest:compileReleaseKotlin` | BUILD SUCCESSFUL | P0 |
| TC-P-002 | 框架单测 | `./gradlew :autotest:test` | 0 failures | P0 |
| TC-P-003 | 发布 mavenLocal | `./gradlew :autotest:publishToMavenLocal` | aar 生成成功 | P0 |

---

## A 层：dev 分支接入点与优化落地静态核对

> 优化只要漏挂接一处即整条链路静默失效。验证 §13-P1 接入点 + 本轮新提交真实落地。

| # | 用例 | 验证标准 | 依据 | 优先级 |
|---|------|----------|------|--------|
| PRE-01 | HTTPDNS 密钥已注入（本轮关键前提） | `local.properties` 含非空 `HTTPDNS_ACCOUNT_ID`/`SECRET_KEY`/`AES_SECRET_KEY` → 构建产物 `hasCredentials()=true` | §10 | P0 |
| PRE-02 | `RetrofitFactory` 挂接 `.dns(AliHttpDnsDns(log=...))` + `addInterceptor(DomainSwitchInterceptor())` + `.proxy(NO_PROXY)` | 三者均在；拦截器顺序为 `CrashContextInterceptor`(首个) → `DomainSwitchInterceptor` → Head → Log（CrashContext 仅记录上下文、不触碰 DNS，不影响 DomainSwitch 的解析上下文处理） | §13-P1.7 | P0 |
| PRE-03 | DomainManager 三处 HTTPDNS 挂接 | `bootstrap(domainListSnapshot())` / `HttpDnsConfig.updateFromOssContent()` / `updateDomainPool(domainListSnapshot())` 均存在 | §13-P1.8/13 | P0 |
| PRE-04 | **`2a4e940` 落地**：受管集合纳入兜底域名 | `AliHttpDnsManager.builtinHosts` 含 `dbxsocial.com`；`fetchOssDomains` 用 `domainListSnapshot()`（含兜底）刷新受管集合 | 2a4e940 | P0 |
| PRE-05 | **`92250d4` 落地**：状态机原子初始化 | `HttpDnsFallbackPolicy.decide`/`markNetworkAbnormal` 用 `computeIfAbsent`（非 getOrPut） | 92250d4 | P0 |
| PRE-06 | **`302b4f0` 落地**：兜底池 + 启动健康探测 | `DomainManager.fallbackDomains=[HOST, dbxsocial.com]`；`ensureFallbackDomains()` 始终注入；`checkCurrentDomainHealth()` 后台线程 | 302b4f0 | P0 |
| PRE-07 | **`cabeff8` 落地**：连通性口径 | 拦截器切换/HTTPDNS 用 `NetworkFailures.isDomainSwitchSignal`，覆盖 UHE/Connect/NoRoute/SSL/SocketTimeout(connect)；读写超时不计入（请求层重发另用更窄的 `isReplaySafe`，排除 SSL，见 06-25 PRE-A6） | cabeff8 | P0 |
| PRE-08 | 内置兜底 conf 打包 | `res/raw/httpdns_conf_v2_fallback.json` 存在且 `httpdns_enabled=true`、`hosts/httpdns_hosts` 合法 | §9 | P1 |
| PRE-09 | release 强校验密钥 | `BaseBusiness/build.gradle` 在 minify 包缺密钥时 `throw`（防静默禁用上线） | §10 F7c | P1 |
| PRE-10 | 混淆 keep | `-keep class com.alibaba.sdk.android.httpdns.**` 存在 | §13-P1.5 | P1 |

---

## B 层：JVM 单测回归（debox-android dev 分支，全自动）

> 落点 `business/BaseBusiness/src/test/.../network/httpdns/`（**56 单测 + 9 集成**）与 `business/BaseModule|BaseBusiness` 域名单测（**16**）。
> 较上轮 +6 例（RemoteConfig 10→13 / Config 6→8 / Manager 7→8），含本轮提交补强。
> 运行命令（必须关 configure-on-demand，否则 react-native-screens 配置期失败）：
> ```bash
> ./gradlew :business:BaseBusiness:testDebugUnitTest \
>   --tests "com.app.base.business.network.httpdns.*" -Dorg.gradle.configureondemand=false
> ./gradlew :business:BaseModule:testDebugUnitTest \
>   --tests "*Domain*" -Dorg.gradle.configureondemand=false
> ```

### B1 HTTPDNS 状态机 + Dns 适配 + 配置（56 单测）

| # | 覆盖簇 | 关键用例 | 优先级 |
|---|------|----------|--------|
| UT-A | Dns 适配（AliHttpDnsDnsTest 8） | 非受管直通不触碰 policy / NORMAL/PROBE 走系统DNS / FALLBACK 返回完整候选 / 空结果降级且改写来源 / SDK 异常吞掉 / append 去重 / **append+系统DNS抛UHE 保留HTTPDNS结果** | P0 |
| UT-B | 三态状态机（HttpDnsFallbackPolicyTest 19） | **FALLBACK 成功只续期不清除(§4.4)** / TTL→PROBE 单飞 / PROBE 成功清回NORMAL / PROBE 失败重置TTL / 租约超时可重租 / **竞态:generation 拦截陈旧回调** / 降级成功不续期 / host 间隔离 | P0 |
| UT-C | 远程配置（HttpDnsRemoteConfigTest 13） | 嵌套段全字段解析 / **扁平 httpdns_* 兼容(09d8483)** / 旧格式/损坏返回null不抛 / gray 越界收敛 / 灰度边界 0/100+同设备稳定 / 空设备号不命中部分灰度 | P0 |
| UT-D | Manager 受管（AliHttpDnsManagerTest 8） | **启动早期内置域名即受管(不依赖accessor)** / **dbxsocial.com 始终受管(2a4e940)** / bootstrap 归一化 / 池热更新替换快照 / 未初始化 lookup 空 / 密钥缺失 DISABLED / PENDING_INIT 挂起不崩 | P0 |
| UT-E | Config 开关（HttpDnsConfigTest 8） | 生效集合=受管∩收窄 / hosts 空=不收窄 / 从未拉到 enabled=false / 密钥缺失即便 remote=true 也关 / 错误隔离沿用旧配置 | P0 |

### B2 域名切换单测（16）

| # | 覆盖簇 | 关键用例 | 优先级 |
|---|------|----------|--------|
| UT-F | DomainManagerTest 4 + HttpConstantTest 4 | parseHostsJson 解析/去重/非法返回空 / currentBaseUrl 正确 / resolveSelectedUrl 托管域名 | P0 |
| UT-G | DomainSwitchInterceptorTest 8 | DNS失败上报精确 path / Connect/SSL/connect超时触发、read超时不触发 / 第三方 host 不上报 / 成功回报衰减入口 / 无 accessor 异常透传不崩 | P0 |

> B 层判定：**全部 PASS** 为通过；任一 FAIL 阻断（核心网络链路 block 级）。

---

## C 层：集成测试（MockWebServer + okhttp-tls，9，OkHttp 4.12.0 真实建连）

> 落点 `AliHttpDnsDnsIntegrationTest.kt`，覆盖纯逻辑层覆盖不到的真实建连语义。运行命令同 B 层。

| # | 用例 | 验证标准 | 优先级 |
|---|------|----------|--------|
| IT-01 | URL host/SNI 保持原域名（HTTPS 证书按原域名校验，非 IP 直连） | requestUrl.host=debox.pro + 握手成功 | P0 |
| IT-02 | UHE 只标记不重放 POST | 请求失败 + server 收到 0 请求 + 降级改写来源 | P0 |
| IT-03 | FALLBACK 窗口内后续请求走 HTTPDNS | 2 请求都 200 + 都走 HTTPDNS | P0 |
| IT-04 | 坏 IP+好 IP 同请求轮换（§7 边界）；候选全坏则失败(IT04b) | 同请求连上第二 IP | P0 |
| IT-05 | 持续污染不震荡（系统DNS持续坏+HTTPDNS可用，连续5请求） | 5 请求全 200、全走 HTTPDNS、无失败 | P0 |
| IT-06 | 开关关闭全链路 SDK 0 调用 | 系统DNS + httpDnsLookup 0 次 | P0 |
| IT-07 | TTL 过期 PROBE 两分支（系统恢复→成功 / 仍坏→失败重回FALLBACK） | 两分支均符合 §4.3 | P0 |

> **C 层意义**：on-device PROBE 回切受 OSS `abnormalTtlMs=600000`（10min）制约难快速观测，PROBE 双分支的确定性覆盖以 IT-07 为准。

---

## D 层：真机深度验证（HTTPDNS 已激活，AI 驱动 + adb logcat 取证）

> **取证命令**（见文末速查）。**断网/污染手段（均可逆，测后必复原）**：
> - DNS 污染（触发 UHE 进拦截器）：`settings put global private_dns_mode hostname` + `private_dns_specifier <不存在的DoT主机>`
> - 整机断网（仅用于健康探测/守卫短路场景，**不触发拦截器**，见已知限制）：`svc wifi disable` + `svc data disable`
> 危险红线：不清数据/不卸载/不登出/不切环境。

### D1 基线与初始化

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| MV-01 | 冷启动冒烟 | terminate → launch → 进首页 | 无 crash，进首页，登录态/测试环境正常 | P0 |
| MV-02 | HTTPDNS 懒初始化 + 启动预热 | 冷启动抓 logcat | `doInit: HTTPDNS SDK 初始化完成`；开关 true 触发 `ensureInitialized`；对收窄域名预解析（无 `密钥缺失`） | P0 |
| MV-03 | 启动配置链取证 | 冷启动抓 logcat | `restoreFromCache: 缓存域名池(含兜底)` 非空；`updateFromOssContent: ...enabled=true...hosts=[t.debox.pro]`；`fetchOssDomains: OSS域名池更新成功` | P0 |
| MV-04 | 启动健康探测·可达分支 + 后台线程 | 网络正常冷启动抓 logcat | `verifyAndHealCurrentDomain(startup): 当前域名 X 可达，无需处理`；探测 tid≠主线程（`domain-health-check`）；UI 不被阻塞 | P0 |
| MV-05 | 正常网络仍走系统 DNS（负向） | 正常浏览/下拉刷新 30s | 解析来源全系统 DNS；**0 条 `enter_fallback`**、0 次 HTTPDNS 解析（正常用户不被接管） | P0 |
| MV-06 | 非受管 host 零开销直通 | 浏览触发第三方/RPC 请求（行情/EVM 节点） | 第三方 host 无 `enter_fallback`、无域名切换上报（不污染主决策） | P1 |

### D2 域名层故障切换（DomainManager）

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| MV-10 | 连通性故障上报 + 窗口/阈值/冷却判定 | DNS 污染注入 → 下拉刷新×N 触发多接口请求 | `onConnectivityFailure: ...累计故障` 累加；`窗口内不同path=X/同path=Y, 未达阈值(distinct=3/same=3)`/`冷却期中`/`域名切换 A -> B (可达优选)` 判定链可见（单接口 same-path 兜底见 06-25 DV-01） | P0 |
| MV-11 | 达阈值真实切换到备选域名 | 同上持续触发至阈值且过冷却 | `域名切换 debox.pro -> t.debox.pro (可达优选)`（或其他可达备选）+ `resetUrl`；currentDomain 持久化 | P0 |
| MV-12 | 成功衰减按当前域名 gating（aa6c43a） | 切换/恢复后对当前域名成功请求 | `onRequestSuccess` 仅当前域名清窗口+故障-1；池内其他域名成功不误清 | P1 |
| MV-13 | 断网启动→健康探测全不可达分支（302b4f0） | 先 `svc wifi/data disable` → 冷启动 → 抓 logcat → 复原 | `isReachable: X 不可达`；`verifyAndHealCurrentDomain(startup): X 不可达且无可达备选（疑似设备离线），保留当前域名`（不再误清已生效选择）；不 crash | P1 |
| MV-14 | 探测不可达域名超时上限 | MV-13 中观察探测耗时 | 单域名探测 ≤ ~2.5s，不长时间卡启动 | P2 |
| MV-15 | 恢复网络后自愈 | `svc wifi/data enable` → 触发请求 | 请求恢复成功；故障计数衰减；App 正常用 | P0 |

### D3 HTTPDNS 异常兜底全链路（本轮核心）

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| MV-20 | DNS 污染 → t.debox.pro 进 FALLBACK | DNS 污染 → 触发请求至切到 t.debox.pro → 继续触发 | t.debox.pro 出现 `enter_fallback: t.debox.pro reason=UnknownHostException`；触发预解析（`fallback_pending_init` 或 setPreResolveHosts） | P0 |
| MV-21 | FALLBACK 后续请求走 HTTPDNS 解析 | 进 FALLBACK 后继续请求 t.debox.pro | `AliHttpDnsDns` 对 t.debox.pro decide=HTTPDNS；**控制台已配** → 拿到 IP、请求 200；**控制台漏配** → `httpdns_empty_fallback_system`（据实记录，属 §8.1 运维项） | P0 |
| MV-22 | 持续污染无周期失败（§4.4 核心修复） | 系统 DNS 持续坏、观察 ≥2~3min（< TTL 10min） | FALLBACK 稳定保持，HTTPDNS 成功只续期；**无 "成功→清标记→回系统DNS→失败" 周期性震荡** | P0 |
| MV-23 | HTTPDNS 空结果降级且不续期（§4.4 修正） | 控制台漏配 / 收窄域名无解析 时观察 | `httpdns_empty_fallback_system` + `markResolutionDegraded`；该次系统DNS成功不按HTTPDNS续期（不会永不回切） | P1 |
| MV-24 | PROBE 回切（TTL 到期，best-effort） | 恢复 DNS → 等 FALLBACK TTL（10min）到期 → 触发请求 | `probe_success` → 回 NORMAL，后续走系统 DNS；（受 10min TTL 限制，主覆盖以 C 层 IT-07 为准） | P1 |
| MV-25 | PENDING_INIT 窗口（best-effort） | 若捕捉到 SDK init 在途时的异常标记 | `fallback_pending_init`；init 完成后补发预解析；FALLBACK 标记照常 | P2 |

### D4 两道自愈协作（§11）与边界

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| MV-30 | HTTPDNS + 域名切换协同（§11） | DNS 污染持续，观察完整链 | debox.pro 失败→切 t.debox.pro→t.debox.pro 进 FALLBACK 走 HTTPDNS；**HTTPDNS 解析与域名切换计数互不替代、口径一致**（同一 IOException 同时驱动两者） | P0 |
| MV-31 | HTTPDNS 候选全失败 → 现有容灾接管 | HTTPDNS 解析出但 IP 全不可达（污染下） | `fallback_request_failed` → forceReResolve 强刷缓存；异常继续进 DomainSwitch 统计 → 域名切换接管 | P1 |
| MV-32 | 网络切换 WiFi↔蜂窝 SDK 自动刷新 | FALLBACK 态下切换 WiFi/蜂窝 | SDK `setPreResolveAfterNetworkChanged` 自动刷新预解析；切换后请求正常 | P2 |
| MV-33 | 受管集合一致性（2a4e940 真机侧） | 抓启动 + OSS 更新后受管快照 | 切到 dbxsocial.com 类兜底域名时其仍受 HTTPDNS 管理（不脱管） | P1 |

### D5 测后复原

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| MV-40 | 环境复原 | `settings delete global private_dns_specifier`；`private_dns_mode` 复原；`svc wifi/data enable`；`settings put global http_proxy :0` | 设备 DNS/网络/代理复原 | P0 |
| MV-41 | 复原后基线回归 | 重跑 MV-01/MV-04/MV-05 | 全程系统 DNS、健康探测可达、App 正常；无残留 FALLBACK | P0 |

---

## 统计

| 分类 | 用例数 | P0 | P1 | P2 | 可执行性 |
|------|--------|----|----|----|----------|
| 框架前置检查 | 3 | 3 | 0 | 0 | ✅ |
| A 层 静态核对 | 10 | 7 | 3 | 0 | ✅ |
| B 层 JVM 单测 | 7 簇 / 72 测试 | — | — | — | ✅ 全自动 |
| C 层 集成测试 | 7（9 测试） | 7 | 0 | 0 | ✅ 全自动 |
| D1 基线初始化 | 6 | 4 | 1 | 1 | ✅ |
| D2 域名层切换 | 6 | 3 | 2 | 1 | ✅ |
| D3 HTTPDNS 兜底 | 6 | 3 | 2 | 1 | ✅（MV-21 happy 路径依赖控制台配置） |
| D4 协作与边界 | 4 | 1 | 2 | 1 | ✅ |
| D5 复原 | 2 | 2 | 0 | 0 | ✅ |
| **合计** | **50 条目 + 81 自动化测试** | — | — | — | — |

---

## 已知不可黑盒覆盖项（诚实标注）

| 场景 | 原因 | 替代验证 |
|---|---|---|
| 新装首启注入兜底池 / 内置 conf seed（cache 空路径） | 触发需 `clear data`，违反铁律（丢登录态+切正式环境） | 代码分析 + B 层 UT-D/UT-E + 不变量 MV-03（池永不空） |
| 健康探测"切到可达备选"分支（一坏一好选择性封堵） | 非 root 真机不能只封堵单域名 | 代码分析 + 断网全坏回退分支 MV-13 + C 层 IT |
| HTTPDNS happy 路径成功（拿到正确 IP 连通） | 依赖阿里云 EMAS 控制台已托管 t.debox.pro（§16.2-1 外部项） | 控制台已配 → MV-21 直接验；漏配 → MV-23 记录 `empty_fallback` 并由域名切换接管 |
| PROBE 回切快速观测 | OSS `abnormalTtlMs=600000`（10min），on-device 观测慢 | C 层 IT-07 确定性覆盖；MV-24 best-effort |
| 整机断网触发请求级 HTTPDNS/切换 | App 层守卫以 `-100 网络未连接` 短路，不进 OkHttp 拦截器 | 用 DNS 污染（private_dns）而非整机断网触发拦截器路径 |
| EMAS 控制台"仅异常用户产生解析记录" | 需控制台日志查看权限 | 跑完 D 层后人工查（§14 手工验证项） |

---

## 取证命令速查

```bash
S=RFCYA0F9SSZ   # 三星主测；小米用 402714f0

# 启动 + 域名/HTTPDNS 全量日志
adb -s $S logcat -c
adb -s $S shell am force-stop com.tm.security.wallet
adb -s $S shell monkey -p com.tm.security.wallet -c android.intent.category.LAUNCHER 1
adb -s $S logcat -d | grep -aiE "DomainManager|AliHttpDnsManager|HttpDnsConfig|HttpDnsFallbackPolicy|AliHttpDnsDns|FLogger|domain-health|verifyAndHealCurrentDomain|enter_fallback|probe_success|probe_fail|fallback_pending_init|httpdns_empty_fallback_system|httpdns_error_fallback_system|域名切换|可达优选|自动重发|累计故障"

# DNS 污染（可逆，触发 UHE 进拦截器）
adb -s $S shell settings put global private_dns_mode hostname
adb -s $S shell settings put global private_dns_specifier dns.invalid-debox-test.example

# 整机断网（可逆，仅用于健康探测/守卫场景）
adb -s $S shell svc wifi disable && adb -s $S shell svc data disable

# 复原（测后必做）
adb -s $S shell settings delete global private_dns_specifier
adb -s $S shell settings put global private_dns_mode off   # 或恢复原值 opportunistic
adb -s $S shell svc wifi enable && adb -s $S shell svc data enable
adb -s $S shell settings put global http_proxy :0
```
