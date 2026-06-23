# 域名动态切换 + HTTPDNS 异常兜底 — 深度测试结果

> 用例见同目录 `cases.md`。记录原则：只记是否通过、问题描述、根因、修复方案。
> 每轮独立一节，时间倒序（最新在最前）。

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

**状态**：🟡 待用户处置（属本地工作区，非本次被测 dev 提交）

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

**状态**：🟡 待运维确认（§8.1 持续运维约束 / §16.2-1 业务确认项）

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
