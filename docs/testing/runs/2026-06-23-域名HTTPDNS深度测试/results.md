# 域名动态切换 + HTTPDNS 异常兜底 — 深度测试结果

> 用例见同目录 `cases.md`。记录原则：只记是否通过、问题描述、根因、修复方案。
> 每轮独立一节，时间倒序（最新在最前）。

---

## 第 3 轮（可 root 模拟器补测，2026-07-01）

> 触发原因：把 06-23「已知不可黑盒覆盖项」里因「非 root 不能单域名封堵」「需 clear data 违反铁律」而只能靠代码/UT 覆盖的 case，用**可 root 模拟器**补黑盒实测。
> 环境：命令行建 AVD `debox_root`（`system-images;android-35;google_apis;arm64-v8a` = userdebug **可 root**）；`adb install` 真机 pull 的 base.apk（2.14.0）。**钱包 App 在 rooted 模拟器正常跑**（图灵盾 `com.turingfd.sdk` 不拦——`adb root` 不装 su，典型 root 检测不触发）。软键盘（RN 输入框）唤起失败 → 用 `adb shell input text` 注入密码创建测试钱包 → 进 MainActivity、完整域名 init 跑起。

| 原不可黑盒覆盖项（06-23） | 补测手段 | 结果 |
|---|---|---|
| **新装首启注入兜底池 / 内置 conf seed（cache 空路径）** | 卸载重装 → 冷启（SP 空） | ✅ **PASS**：`restoreFromCache: 缓存域名池(含兜底)=[debox.pro, dbxsocial.com]`（ensureFallbackDomains 注入兜底）+ `seedFromBundled: 内置 conf 兜底生效` + `seedFromBundledContent` + `bootstrapEarly: 完成` |
| **健康探测"切到可达备选"分支（一坏一好选择性封堵）** | `adb root` + `iptables -A OUTPUT -d 198.18.0.9 -j REJECT`（模拟器 DNS-NAT 给 debox.pro 分配的假 IP），留 dbxsocial.com（198.18.0.64）可达 | ✅ **PASS**：`isTcpReachable: debox.pro 不可达 - ConnectException` → `候选探测结果=[dbxsocial.com=true], selected=dbxsocial.com, selected_reachable=true` → `debox.pro 不可达，切换到可达域名 dbxsocial.com` → 切后 `dbxsocial.com 可达，无需处理` |
| **可达优选选"好"域名（B1 核心，非退化最小计数）** | 同上 | ✅ **PASS**（顺带）：`selected_reachable=true` 证明选的是**可达**域名而非仅按计数 |
| **DV-09 TCP 可连但 TLS 失败边界（06-25 P2，原标 N/A 非 root 不可构造）** | Mac 起假 TLS 服务器（接受 TCP、回明文使握手失败）；emulator `iptables -t nat DNAT dbxsocial.com 假 IP:443 → 10.0.2.2:8443` | ✅ **PASS（三层全中）**：① `isTcpReachable` TCP-only 误判 `dbxsocial.com 可达，无需处理`（TLS 已断，健康探测抓不到→**TCP 可达≠业务可用**实证）② 业务请求 `SSLException: Unable to parse TLS packet header`（code=-95）③ SSL 触发 `域名切换 dbxsocial.com -> debox.pro`（isDomainSwitchSignal 含 SSL），但 `domain_retried=false`、**无 `自动重发`**（isReplaySafe 排除 SSL，POST 防重放，正确不重发） |

**补测结论**：06-23 标"非 root 难构造/违反铁律"的 3 条 + 06-25 标 N/A 的 DV-09，用**可 root 模拟器 + iptables 单封/DNAT DNS-NAT 假 IP + Mac 假 TLS 服务器 + adb 注入输入**，**全部首次黑盒实测 PASS**。技术路径全通（可 root 镜像 / 钱包 App 可跑 / 单域名精确封堵 / TCP-ok-TLS-fail 可构造 / 无键盘也可输入）。测后 iptables/nat/hosts/假服务器全清、模拟器恢复健康（App 在 debox.pro、可达、OSS 成功、无残留 SSL/切换）。

---

## 第 2 轮（回归，2026-06-25）

> 触发原因：06-25「域名HTTPDNS自愈鲁棒性增强」对同一链路做了 9 项改动 + 1 重构 + review-①②③ + base URL 同步修复，回归确认未破坏 06-23 既有行为。
> 设备：小米 25067PYE3C（`402714f0`，**正式环境**；三星本轮离线）。被测：debox-android dev 工作区（含 06-25 全部改动）。

### 自动化层回归（B 全套 + C 集成 + Domain）— **115 tests / 0 fail / 0 error**

| 测试类 | tests | skip | 备注 |
|---|---|---|---|
| **AliHttpDnsDnsIntegrationTest（C 集成）** | 9 | 0 | URL host/SNI、UHE 不重放 POST、FALLBACK 走 HTTPDNS、坏IP→好IP 轮换、持续污染不震荡、开关关 0 调用、PROBE 双分支 —— **真实建连语义无回归** |
| HttpDnsFallbackPolicyTest | 25 | 0 | 19→25（+6 review-② 过期 FALLBACK/PROBE 刷新、有效租约 no-op） |
| HttpDnsRemoteConfigTest | 17 | 0 | 13→17（+4 review-① 全非法标记/安全失败） |
| AliHttpDnsDnsTest | 8 | 0 | — |
| AliHttpDnsManagerTest | 9 | 3 | skip=密钥缺失分支（密钥已注入，预期跳过） |
| HttpDnsConfigTest | 8 | 1 | skip=同上 |
| DomainSwitchInterceptorTest | 8 | 0 | — |
| NetworkFailuresTest | 4 | 0 | 06-25 新增（两套故障口径分离 isDomainSwitchSignal/isReplaySafe） |
| HttpConstantTest | 4 | 0 | — |
| **DomainManagerTest** | 14 | 0 | **4→14，编译+全部通过（BUG-001 已解决）** |
| 合计 | **115** | 4 | **0 fail / 0 error** |

### 状态更新
- **BUG-001 → ✅ 已解决**：`DomainManager` 已暴露 `internal fun seedFromBundledContent(content: String)` 可测 seam，`DomainManagerTest` 由 4 例增至 14 例、全部编译通过。
- **OBS-001（EMAS 未托管 t.debox.pro）仍为外部运维项**，未变；正式环境收窄 `hosts=[debox.pro]`，EMAS 同样未托管 → `httpdns_empty_fallback_system` 照旧（客户端降级正确）。

### D 层真机回归（正式环境小米 402714f0）

> 正式环境拓扑：`domainList=[debox.pro, dbxsocial.com]`，HTTPDNS 收窄 `hosts=[debox.pro]`（= 收窄 host 即业务流量主域名，与 06-23 测试环境 t.debox.pro 占位不同）。

| # | 用例 | 结果 | 证据（正式环境） |
|---|---|---|---|
| MV-01 | 冷启动冒烟 | ✅ PASS | MainActivity 前台、无 crash、登录态正常 |
| MV-02 | HTTPDNS 懒初始化 | ✅ PASS | `doInit: HTTPDNS SDK 初始化完成` + `switch_eval switch_on=true credentials_ready=true bucket_hit=true` |
| MV-03 | 启动配置链 | ✅ PASS | `缓存恢复完成` + `seedFromBundledContent` + `updateFromOssContent: enabled=true hosts=[debox.pro]` + `OSS域名池更新成功` |
| MV-04 | 健康探测可达 + 后台线程 | ✅ PASS | `verifyAndHealCurrentDomain(startup): 开始探测 candidates=[...] → 可达，无需处理`（tid≠主线程） |
| MV-05 | 正常网络无误切换（负向） | ✅ PASS | 启动 enter_fallback=0、域名切换=0 |
| MV-06 | 非受管 host 直通 | ⚠️ 设计推断 | 同 06-23，纯逻辑 B 层 UT-A 覆盖 |
| MV-10 | 故障上报 + 窗口/阈值/冷却 | ✅ PASS | `onConnectivityFailure 累计故障` + `窗口内不同path=1/2, 未达阈值(distinct=3/same=3)`；review-③ `已有切换探测在途, 跳过本次` |
| MV-11 | 达阈值切换到备选 | ✅ PASS | `域名切换 debox.pro -> dbxsocial.com (可达优选), failureMap={debox.pro=2,...}` + resetUrl |
| MV-12 | 故障计数成功衰减（aa6c43a） | ✅ PASS | `restoreFromCache: 故障计数(衰减后)` 跨重启减半链（06-25 多轮可见）；per-request gating 由 B 层覆盖 |
| MV-13 | 断网→全不可达保留当前（302b4f0） | ✅ PASS | 整机断网冷启：`候选探测全部不可达, fallback=debox.pro`；`dbxsocial.com 不可达且无可达备选（疑似设备离线），保留当前域名，待网络恢复后由请求级切换接管` |
| MV-14 | 探测超时上限 | ✅ PASS | 断网下 ~3ms 内全部快速失败（DNS 立即失败 < 2.5s） |
| MV-15 | 恢复网络自愈 | ✅ PASS | = MV-41：恢复后冷启 `可达，无需处理`、0 FALLBACK/0 切换 |
| MV-20 | DNS 污染 → 收窄 host 进 FALLBACK | ✅ PASS | `enter_fallback: debox.pro reason=UnknownHostException` + `preResolve: debox.pro 已发送` |
| **MV-21** | **FALLBACK 后请求走 HTTPDNS 解析成功（happy 路径）** | ✅ **PASS（正式环境首验，06-23 升级）** | `dns_decision: debox.pro decision=HTTPDNS` → **`httpdns_hit: debox.pro ip_count=2`** → 污染期间请求返回 `"code":1,"msg":"成功","success":true` —— **HTTPDNS 用真实 IP 救活请求，绕过被污染的系统 DNS**。06-23 因 EMAS 未托管 t.debox.pro 阻塞（OBS-001），正式环境 debox.pro 已托管 → 正向效果首次实测 |
| **MV-22** | **持续污染无周期失败（§4.4）** | ✅ **PASS（正式环境）** | HTTPDNS 命中后请求成功、不回退系统 DNS 震荡；FALLBACK 稳定保持 |
| MV-23 | HTTPDNS 空结果降级不续期 | ✅ 机制在（正式环境多为 httpdns_hit） | 正式环境 debox.pro 有解析故走 happy 路径；空结果降级路径由 06-23（t.debox.pro empty）+ C 层 IT-02 覆盖 |
| MV-24 | PROBE 回切（TTL 到期） | ⏸️ C 层 IT-07 覆盖 | OSS TTL=10min on-device 观测慢，确定性由 IT-07a/07b |
| MV-25 | PENDING_INIT 窗口 | ⏸️ 实测到 `fallback_pending_init: debox.pro` + `flushPendingPreResolve` | init 在途挂起补发链路可见；补发逻辑 B 层覆盖 |
| MV-30 | HTTPDNS + 域名切换协同（§11） | ✅ PASS | 同一 UHE 同时驱动 `onConnectivityFailure`（切 dbxsocial.com）与 `enter_fallback`（debox.pro 走 HTTPDNS）——两道并行、口径一致 |
| MV-31 | HTTPDNS 候选全失败 → 容灾接管 | ✅ 机制在 | 部分请求失败累积仍触发域名切换接管（与 HTTPDNS 成功并存）；`fallback_request_failed` 强刷由 B 层 UT 覆盖 |
| MV-32 | 网络切换 WiFi↔蜂窝 SDK 刷新 | ⏸️ 未单独执行 | 06-25 DV-04 已验 WiFi onAvailable → 二次健康探测；SDK `setPreResolveAfterNetworkChanged` 静态确认 |
| MV-33 | 受管集合纳入兜底一致性 | ✅ PASS | `updateDomainPool: pool=[debox.pro, dbxsocial.com]`；切到 dbxsocial.com 仍在受管池 |
| MV-40 | 环境复原 | ✅ PASS | `dns=off proxy=:0 wifi=1` |
| MV-41 | 复原后基线回归 | ✅ PASS | 冷启 `dbxsocial.com 可达，无需处理` + OSS 成功、无残留 FALLBACK |

**D 层回归结论**：MV-01~33 真机行为在正式环境（含 06-25 全部改动 + base URL 修复）**无回归**；**MV-21/MV-22 的 HTTPDNS happy 路径（06-23 被 OBS-001 阻塞）在正式环境首次实测 PASS**——`httpdns_hit ip_count=2` + 污染期请求 `success:true`，HTTPDNS 救活请求的正向效果得证。新诊断日志 `proactive_fallback_skip`（非收窄 host 切换 gating）、`dns_decision`、`httpdns_hit`、`候选探测全部不可达` 均观测到。环境已复原。

---

## 第 1 轮（首测）

> 测试日期：2026-06-23 | 设备：三星 SM-S9210（RFCYA0F9SSZ，主测）/ 小米 25067PYE3C（402714f0）
> 被测：debox-android dev 分支 2.13.2（HTTPDNS 密钥已注入、SDK 已激活）
> 触发原因：首次测试（dev 分支域名/HTTPDNS 优化）

### Phase 1 环境确认

| 项 | 结果 | 备注 |
|---|------|------|
| 设备在线 | ✅ | 三星 RFCYA0F9SSZ / 小米 402714f0 / 模拟器 emulator-5554 |
| App 安装 | ✅ | 三台均 2.13.2 / versionCode 21300002（06-23 更新） |
| HTTPDNS 激活 | ✅ | 三星冷启动 `doInit: HTTPDNS SDK 初始化完成`（密钥已注入） |
| 环境/登录态 | ✅ | 测试环境（OSS conf_test）+ 已登录 |

### 0. 框架前置检查（autotest）

| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-P-001 | 框架编译 `:autotest:compileReleaseKotlin` | ✅ PASS | BUILD SUCCESSFUL |
| TC-P-002 | 框架单测 `:autotest:test` | ✅ PASS | 0 failures |
| TC-P-003 | 发布 mavenLocal | ✅ PASS | `~/.m2/.../autotest/1.6.0/autotest-1.6.0.aar` 生成 |

### A 层 静态核对

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| PRE-01 | HTTPDNS 密钥已注入 | ✅ PASS | local.properties 含非空 ACCOUNT_ID(6)/SECRET_KEY(32)/AES；单测 4 个"密钥缺失"用例因 `hasCredentials()=true` 自动 skip 反证 |
| PRE-02 | RetrofitFactory 接线 | ✅ PASS | `.proxy(NO_PROXY)` + `.dns(AliHttpDnsDns())` + `addInterceptor(DomainSwitchInterceptor())`(首个)；`AllowAllHostnameVerifier` 已注释（TLS 未关闭） |
| PRE-03 | DomainManager 三处挂接 | ✅ PASS | bootstrap/updateFromOssContent/updateDomainPool 均在 |
| PRE-04 | 2a4e940 落地 | ✅ PASS | builtinHosts 含 `dbxsocial.com`；fetchOssDomains 用 `domainListSnapshot()` |
| PRE-05 | 92250d4 落地 | ✅ PASS | decide/markNetworkAbnormal 均 `computeIfAbsent` |
| PRE-06 | 302b4f0 落地 | ✅ PASS | fallbackDomains=[HOST,dbxsocial.com]；ensureFallbackDomains 始终注入；checkCurrentDomainHealth 后台线程 |
| PRE-07 | cabeff8 连通性口径 | ✅ PASS | isConnectivityFailure 覆盖 UHE/Connect/NoRoute/SSL/SocketTimeout(connect)；读写超时不计入 |
| PRE-08 | 内置兜底 conf 打包 | ✅ PASS | res/raw/httpdns_conf_v2_fallback.json：enabled=true、hosts=[debox.pro,dbxsocial.com]、httpdns_hosts=[debox.pro] |
| PRE-09 | release 强校验密钥 | ✅ PASS | build.gradle F7c：minify 包缺密钥 `throw GradleException` |
| PRE-10 | 混淆 keep | ✅ PASS | BaseBusiness `-keep ...httpdns.**`；app `-keep ...alibaba.sdk.android.**` |

### B 层 JVM 单测（debox-android dev 分支）

| 测试类 | tests | failures | skipped | 结果 |
|---|---|---|---|---|
| AliHttpDnsDnsTest（Dns 适配） | 8 | 0 | 0 | ✅ |
| HttpDnsFallbackPolicyTest（三态状态机） | 19 | 0 | 0 | ✅ |
| HttpDnsRemoteConfigTest（远程配置/扁平兼容/灰度） | 13 | 0 | 0 | ✅ |
| AliHttpDnsManagerTest（受管/初始化） | 9 | 0 | 3 | ✅（3 skip=密钥缺失分支，因密钥已注入合理跳过） |
| HttpDnsConfigTest（开关/收窄） | 8 | 0 | 1 | ✅（1 skip=同上） |
| DomainSwitchInterceptorTest（连通性口径/衰减入口） | 8 | 0 | 0 | ✅ |
| HttpConstantTest（URL 基线） | 4 | 0 | 0 | ✅ |
| **小计** | **69** | **0** | **4** | ✅ |

> DomainManagerTest（已提交 4 例：parseHostsJson 解析/去重/非法/缺字段）只读确认健康，未计入运行——见 BUG-001（工作区坏测试阻断该类编译）。

### C 层 集成测试（MockWebServer + okhttp-tls，OkHttp 4.12.0）

| 测试类 | tests | failures | skipped | 结果 |
|---|---|---|---|---|
| AliHttpDnsDnsIntegrationTest（IT-01~07，含 IT04b/07a/07b） | 9 | 0 | 0 | ✅ |

> 覆盖：URL host/SNI 保持原域名、UHE 不重放 POST、FALLBACK 走 HTTPDNS、坏IP→好IP 轮换、持续污染不震荡、开关关闭 SDK 0 调用、TTL 过期 PROBE 双分支。

### D 层 真机深度（主测机 三星 RFCYA0F9SSZ，证据存 evidence/）

> 触发手段实测结论：**整机断网（svc disable）被 App 层守卫 `-100` 短路、不进 OkHttp 拦截器**（断网冷启动 0 条 onConnectivityFailure）；**DNS 污染（strict private DNS + 坏 specifier）能到达拦截器层**（产生 UnknownHostException）。故 FALLBACK/切换链用 DNS 污染触发，健康探测"全不可达"分支用整机断网触发。
> 关键环境事实：测试环境实际业务流量走 **`t.debox.pro`**（= HTTPDNS 收窄生效域名），DomainManager currentDomain=debox.pro 为生产域名占位 → HTTPDNS 触发与域名切换天然串联。

#### D1 基线与初始化

| # | 用例 | 结果 | 证据（evidence/MV-baseline-coldstart.log + 小米交叉） |
|---|------|------|------|
| MV-01 | 冷启动冒烟 | ✅ PASS | 两机均进首页无 crash，登录态/测试环境正常 |
| MV-02 | HTTPDNS 懒初始化 | ✅ PASS | `AliHttpDnsManager: doInit: HTTPDNS SDK 初始化完成`（三星 tid6270 / 小米 tid26844，后台线程）；无"密钥缺失" |
| MV-03 | 启动配置链 | ✅ PASS | 池非空 `[t.debox.pro,t.dbxsocial.com,debox.pro,dbxsocial.com]`；`updateFromOssContent: enabled=true grayPercent=100 hosts=[t.debox.pro] abnormalTtlMs=600000`；`fetchOssDomains: OSS域名池更新成功` |
| MV-04 | 健康探测可达 + 后台线程 | ✅ PASS | `checkCurrentDomainHealth: 当前域名 debox.pro 可达，无需处理`（tid≠主线程，`domain-health-check`）；UI 不阻塞（两机一致） |
| MV-05 | 正常网络无误切换（负向） | ✅ PASS | 启动+浏览 0 条 `enter_fallback`、0 条 `域名切换` |
| MV-06 | 非受管 host 直通 | ⚠️ 设计推断 | 域名/HTTPDNS 日志中无任何第三方 host 事件（未污染主决策）；未单独强制第三方请求，纯逻辑由 B 层 UT-A 覆盖 |

#### D2 域名层故障切换（DomainManager）

| # | 用例 | 结果 | 证据（evidence/MV-pollution-coldstart.log） |
|---|------|------|------|
| MV-10 | 连通性故障上报 + 窗口/阈值/冷却 | ✅ PASS | `onConnectivityFailure: domain=t.debox.pro ... 累计故障` 1→15 累加；`窗口内不同path=1/2/3`；达 3 触发切换后全程 `冷却期中, 距上次切换Xms < 30000ms`（防乒乓） |
| MV-11 | 达阈值真实切换到备选 | ✅ PASS | `域名切换 debox.pro -> t.dbxsocial.com, failureMap={t.debox.pro=3}` + resetUrl；选 selectBestDomain 故障计数最小备选，逻辑正确 |
| MV-12 | 故障计数成功衰减（aa6c43a） | ✅ PASS | 重启衰减减半链：15 →（重启）7 →（重启）3（`故障计数(衰减后)`）；per-request onRequestSuccess gating 由 B 层 DomainSwitchInterceptorTest 覆盖 |
| MV-13 | 断网启动→全不可达保留当前域名（302b4f0） | ✅ PASS | 整机断网冷启动：4 域名 `isReachable: X 不可达`，`checkCurrentDomainHealth: t.dbxsocial.com 不可达且无可达备选（疑似设备离线），保留当前域名，待网络恢复后由请求级切换接管` |
| MV-14 | 探测超时上限 | ✅ PASS | 4 域名探测 ~7ms 内全部快速失败（DNS 立即失败 < 2.5s 上限）；`domain-health-check` 后台线程 |
| MV-15 | 恢复网络自愈 | ✅ PASS | 恢复后冷启动：currentDomain=t.dbxsocial.com **可达**，0 故障/0 FALLBACK/0 切换，App 正常 |

#### D3 HTTPDNS 异常兜底全链路

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| MV-20 | DNS 污染 → t.debox.pro 进 FALLBACK | ✅ PASS | `HttpDnsFallbackPolicy: enter_fallback: t.debox.pro reason=UnknownHostException`；后续请求 decide=HTTPDNS 经 AliHttpDnsDns |
| MV-21 | FALLBACK 后续请求 HTTPDNS 解析成功（happy 路径） | ⏸️ 外部依赖阻塞 | HTTPDNS 持续 `httpdns_empty_fallback_system: t.debox.pro` → EMAS 控制台未托管 t.debox.pro（§8.1/§16.2-1 运维项）。**客户端行为正确**（空→降级系统 DNS），但拿不到 HTTPDNS IP 故无法验证"HTTPDNS 救活请求"。见 OBS-001 |
| MV-22 | 持续污染无周期失败（§4.4） | ⚠️ 部分（C 层 IT-05 已覆盖） | on-device 因 HTTPDNS 空 + 系统 DNS 坏，无成功请求 → 无"成功→清→失败"震荡的素材；§4.4 的 `markDegraded`（空结果不按 HTTPDNS 续期）路径已实测触发 |
| MV-23 | HTTPDNS 空结果降级且不续期（§4.4 修正） | ✅ PASS | `httpdns_empty_fallback_system` + 改写来源为 SYSTEM；FALLBACK 不被该次系统成功续期（防控制台漏配永不回切） |
| MV-24 | PROBE 回切（TTL 到期） | ⏸️ C 层 IT-07 覆盖 | OSS `abnormalTtlMs=600000`（10min），on-device 观测过慢；PROBE 双分支确定性由 IT-07a/07b 覆盖 |
| MV-25 | PENDING_INIT 窗口 | ⏸️ B 层 UT-34 覆盖 | 真机 SDK init 极快（<40ms）未捕捉到 init 在途窗口；挂起补发逻辑由单测覆盖 |

#### D4 两道自愈协作（§11）与边界

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| MV-30 | HTTPDNS + 域名切换协同（§11） | ✅ PASS | 同一 UnknownHostException 在同一时间线同时驱动 `DomainManager.onConnectivityFailure`（切 t.dbxsocial.com）与 `HttpDnsFallbackPolicy.enter_fallback`（t.debox.pro）——两道机制口径一致、互不替代 |
| MV-31 | HTTPDNS 候选全失败 → 容灾接管 | ⚠️ 部分 | HTTPDNS 空（非"候选 IP 全失败"）故 `forceReResolve`/`fallback_request_failed` 未触发；"异常继续进 DomainSwitch → 域名切换接管"已验（MV-11/30） |
| MV-32 | 网络切换 WiFi↔蜂窝 SDK 刷新 | ⏸️ 未执行 | 需蜂窝数据环境，本轮 WiFi 测试机未覆盖；SDK `setPreResolveAfterNetworkChanged(true)` 已静态确认 |
| MV-33 | 受管集合纳入兜底域名一致性（2a4e940） | ✅ PASS | dbxsocial.com 始终在 domainList + builtinHosts；切到该兜底域名时仍受 HTTPDNS 管理（PRE-04 静态 + 运行态池快照含兜底） |

#### D5 测后复原

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| MV-40 | 环境复原 | ✅ PASS | 主测机复原至原始态：`private_dns_mode=null, specifier=null, http_proxy=:0, wifi=1` |
| MV-41 | 复原后基线回归 | ✅ PASS | 恢复后冷启动全程系统 DNS、健康探测可达、0 FALLBACK/0 切换，App 正常 |

**本轮统计**：
- 自动化层（TC-P 3 + A 10 + B 69 跑 + C 9）：**全 PASS**，0 failures
- D 层真机（25 条）：**PASS 16 / 部分 3（C/B 层已覆盖）/ 外部依赖阻塞 1（MV-21）/ 未执行 1（MV-32 需蜂窝）/ 设计推断 1（MV-06）/ 覆盖性跳过 3（MV-22/24/25 → C/B 层）**
- 客户端代码层 **0 FAIL**；2 个非 PASS 项均为外部/本地因素（OBS-001 控制台运维项、BUG-001 本地工作区坏测试），非 dev 分支缺陷

---

## Bug 记录

### BUG-001 — DomainManagerTest 工作区未提交改动编译失败（非 dev 分支缺陷）

**关联用例**：B 层 DomainManagerTest

**状态**：✅ **已解决**（2026-06-25 回归确认）——`DomainManager` 已按建议 1 暴露 `internal fun seedFromBundledContent(content: String)` 可测 seam（读 raw 与解析+并池拆开），`DomainManagerTest` 由 4 例增至 14 例、全部编译通过、0 fail。下方为原始记录留痕。

> 原状态：🟡 待用户处置（属本地工作区，非本次被测 dev 提交）

**现象**：`./gradlew :business:BaseModule:testDebugUnitTest --tests "*DomainManagerTest*"` 编译失败：
`Unresolved reference 'seedFromBundledContent'`（DomainManagerTest.kt:68、:83）。

**根因分析（证据）**：
- `git status` 显示该文件为未提交修改（`M`）；`git show HEAD:...DomainManagerTest.kt` 仅 4 个 @Test、不引用该方法 → **dev 已提交版本健康、可编译**。
- 工作区版本新增了 2 个测试（注释标"Finding 3"）调用 `dm.seedFromBundledContent(content: String)`，但 `DomainManager` 实际只有 `private fun seedFromBundledConfig(context: Context)`（读 `res/raw`，需 Android Context），无该 String 入参方法 → 编译不过。
- 即：某次本地会话想给 `302b4f0` 的内置 conf seed 逻辑补单测，但 production 没有可测的纯字符串 seam，测试被留成坏状态。

**影响范围**：仅阻断 BaseModule DomainManagerTest 编译；不影响 dev 已提交代码、不影响 HTTPDNS/DomainSwitch 运行逻辑。

**处置建议（未擅自改动他人未提交工作区）**：
1. 若要保留该测试：在 DomainManager 暴露可测 seam，如 `@VisibleForTesting fun seedFromBundledContent(content: String)`（把现 `seedFromBundledConfig` 的"读 raw"与"解析+并池"拆开），再让测试调它；
2. 或还原该文件（`git checkout -- .../DomainManagerTest.kt`）回到 dev 已提交的健康版本。

### OBS-001 — EMAS HTTPDNS 控制台未托管 t.debox.pro（外部运维项，非客户端缺陷）

**关联用例**：MV-21（HTTPDNS happy 路径）

**状态**：🟢 **正式环境已验**（2026-06-25 回归）——正式环境 HTTPDNS 收窄 host=`debox.pro` **已被 EMAS 托管**，污染下 `httpdns_hit: debox.pro ip_count=2` + 请求 `success:true`，HTTPDNS 救活请求的 happy 路径首次实测通过（见第 2 轮 MV-21/MV-22）。**测试环境 `t.debox.pro` 仍未托管**（下方原始记录），happy 路径在测试环境仍 empty 降级——这条对测试环境仍成立，运维如需测试环境也验证需在 EMAS 加 `t.debox.pro`。

> 原状态：🟡 待运维确认（§8.1 持续运维约束 / §16.2-1 业务确认项）

**现象**：DNS 污染触发 t.debox.pro 进入 FALLBACK 后，对 t.debox.pro 的后续请求持续记录
`AliHttpDnsDns: httpdns_empty_fallback_system: t.debox.pro`（连续多次、跨秒级窗口均为空），
即 HTTPDNS 对 t.debox.pro 始终返回空候选，未能拿到 IP 救活请求。

**根因分析（证据）**：
- 客户端链路完全正确：进 FALLBACK → decide=HTTPDNS → `AliHttpDnsManager.lookup`（SyncNonBlocking）→ 空 → `markResolutionDegraded` 改写来源 → 降级系统 DNS。这正是方案 §12「HTTPDNS 返回空列表 → 系统 DNS，记 httpdns_empty_fallback_system」的设计路径。
- 持续为空（非首请求缓存未热的偶发）强烈指向 **EMAS 控制台该账号下未托管 `t.debox.pro`**（或权威无解析），即方案风险表「OSS 池新增域名未同步控制台 → FALLBACK 下永远空结果」/「控制台未配置某域名」。
- 设备网络下直连 IP 探测（如 samsung.com.cn generate_204）成功，HTTPDNS 解析服务走 bootstrap IP，排除"SDK 连不上解析服务"。

**影响**：真机无法验证 HTTPDNS 兜底"救活请求"的正向效果；但**第二道自愈（域名切换）照常接管**（MV-11/30 已验），整体可用性不受影响。

**处置建议**：运维在 EMAS HTTPDNS 控制台为对应账号添加托管域名 `t.debox.pro`（及 `debox.pro` / 备用域名池），落实 §8.1「OSS 池增删域名 → 先同步控制台 → 再发布 OSS」checklist；配置后重跑 MV-21/MV-22 即可验证 HTTPDNS 正向兜底与持续污染不震荡。可在 EMAS 控制台核对解析记录是否只来自异常 host（§14 手工验证）。

---

## 本轮结论（Decision）

> **proceed-with-notes**：dev 分支域名动态切换 + HTTPDNS 异常兜底的**客户端实现全部验证通过**，0 客户端代码 FAIL。

- ✅ 域名容灾全链路（启动健康探测可达/全不可达保留当前域名、请求级故障切换窗口/阈值/冷却/防乒乓、故障计数启动衰减、恢复自愈）真机实测通过。
- ✅ HTTPDNS 三态状态机、Dns 适配、配置兜底链、并发原子初始化（92250d4）、受管集合纳入兜底域名（2a4e940）经 A 静态 + B 单测（69 跑 0 fail）+ C 集成（9 fail0）+ D 真机（FALLBACK 触发/空结果降级/两道自愈协作）多层验证通过。
- ⏸️ **两个外部/本地非缺陷项**：OBS-001（EMAS 控制台未托管 t.debox.pro，阻塞 HTTPDNS 正向 happy 路径真机验证，属运维项）、BUG-001（本地未提交工作区坏测试，非 dev 分支）。
- 无需进入修复迭代（无客户端代码 FAIL）；MV-21/MV-22 happy 路径待控制台配置后补验。

---

## 结果状态说明

| 标记 | 含义 |
|------|------|
| ✅ PASS | 验证符合预期 |
| ❌ FAIL | 不符合预期，需要修复 |
| ⚠️ 部分通过 | 核心正确但有优化空间（不阻断） |
| ⏸️ 跳过 | 被前置 Bug 阻塞 / 外部依赖未就绪 |
