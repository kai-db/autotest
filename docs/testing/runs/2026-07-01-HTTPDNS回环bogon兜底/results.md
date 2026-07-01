# HTTPDNS 回环/bogon 兜底 + accessor 早注册 + 连接埋点 + IM 多 navi — 测试结果

> 用例见同目录 `cases.md`。记录原则:只记是否通过、问题描述、根因分析、修复方案。
> 每轮测试独立一节,按时间倒序(最新在最前)。

---

## 第 2 轮(可 root 模拟器补测,2026-07-01)

> 触发原因:把第 1 轮里因「非 root 真机无法对单个受管域名注入回环 IP」而只能靠 JVM 单测(TC-U 15/0)+ Private DNS cross-check 覆盖、标 ⏸️ 的 **7 条端到端真机 case**(A 组回环 TC-D-01/03/04 + B 组 IM failover TC-N-04/05/08/09)用**可 root 模拟器**补黑盒实测。
> 环境:AVD `debox_root`(`android-15;google_apis;arm64-v8a`,userdebug **已 root**,`adb root` uid=0);覆盖安装含本轮改动的 `debox-debug.apk`(含 `bootstrapEarly`/`NetEventListener`/`system_dns_poisoned`/`getAppNaviListForJ`)。**钱包 App 在 rooted 模拟器正常跑**(图灵盾 `com.turingfd.sdk` 不拦)。软键盘用 `adb input` 建 6 位 PIN 测试钱包 → 进 MainActivity、`bootstrapEarly` off-main(`currentDomain=debox.pro`)、`IM onOpen`、`fetchOssDomains 成功`。
>
> **回环注入手段(本轮关键突破)**:`/system/etc/hosts`(bind-mount)在本镜像**被 resolver 忽略**,且 Mac 侧 fake-IP 代理把所有 DNS 解析成 `198.18.x`。改用**自写无依赖 Python DNS 响应器**(目标域名按配置返回 `::1`/`127.0.0.1`/混合,其余系统 resolver 转发)+ 模拟器 `iptables -t nat DNAT :53 → 10.0.2.2:5354`。HTTP 层(`AliHttpDnsDns` 自定义 Dns 走 getaddrinfo)注入**完美生效**。

### A 组 · 回环污染端到端(HTTP 层,全 PASS)

| # | 用例 | 补测手段 | 结果 |
|---|------|--------|------|
| **TC-D-01** | 全回环 `::1`→抛+不建连+升级 | 注入 `debox.pro→::1` | ✅ **PASS**:`system_dns_poisoned: debox.pro decision=SYSTEM raw=1 addrs=[::1]`(HTTPDNS 决策路径亦命中:`httpdns_empty_fallback_system`→`system_dns_poisoned decision=HTTPDNS addrs=[::1]`);connect 埋点含 `::1`/`127.0.0.1` **0 条**(未把回环交给 OkHttp);`call_failed err=UnknownHostException: system DNS returned only non-routable addresses`;升级链路 `enter_fallback reason=proactive-domain-switch`→`isTcpReachable debox.pro 不可达`→`域名切换 debox.pro -> dbxsocial.com (可达优选) selected_reachable=true` |
| **TC-D-03** | IPv4 回环 `127.0.0.1` 同样拦截 | 注入 `dbxsocial.com→127.0.0.1` | ✅ **PASS**:`system_dns_poisoned: dbxsocial.com decision=SYSTEM raw=1 addrs=[127.0.0.1]`;connect 无回环 0 条;`UnknownHostException` 抛出;`isTcpReachable dbxsocial.com 不可达` 触发升级。IPv4 回环与 `::1` 同等拦截 |
| **TC-D-04** | 部分回环→留可路由 | 注入 `debox.pro→[198.18.0.9(可路由), 127.0.0.1]` | ✅ **PASS**:`system_dns_partial_bogon: debox.pro kept=1/2`(丢回环留可路由);`connect_start/connect_end host=debox.pro addr=198.18.0.9` 用可路由 IP 成功建连;connect 无回环 0 条;`system_dns_poisoned=0`(有可路由 IP 就不抛不升级) |

> 三条端到端真机行为与 JVM 单测 TC-U-01/02/03/06 一致。核心不变量「回环污染→不连 `::1`/`127.0.0.1`,改抛 `UnknownHostException`→切域名/FALLBACK 接管」**首次真机黑盒实测坐实**,补齐第 1 轮标 ⏸️ 的 D-01/03/04。

### B 组 · IM 多 navi failover(P0-C3 成败判据)

> **前置发现**:debug 包实际跑**生产多 navi** `servers is [wss://ws.debox.pro, wss://ws.dbxsocial.com]`(非测试环境单 navi)→ 免改配置即可测;用 DNS 注入把主 navi 打回环黑洞(真实工单成因),比合成 `im-bad.invalid` 更忠实。为精确定位又临时重编 `JIM_NAVI_SERVER_PRODUCT` 用全新可 sinkhole 的主 hostname(`ws-dead-nav.debox.pro`)。

| # | 用例 | 结果 |
|---|------|------|
| **TC-N-04** | JuggleIM 多 navi failover 机制 | ✅ **机制成立(PASS)**:① **主备都打回环**(`ws-dead-nav.debox.pro`+`ws.debox.pro` 各→`::1`)时 onClose **点名两个 navi** 各 `failed to connect to <host>/::1 ECONNREFUSED` → JuggleIM **确实轮换尝试列表里每个 navi**;② 只死主(`ws-dead-nav→::1`)+ 好备(`ws.debox.pro` 可达)时 IM **经备用 onOpen**(ground-truth:ESTABLISHED socket → `198.18.0.53`=ws.debox.pro,`ConnConnectedState`)。**P0-C3 ① 多 navi failover 机制有效** |
| **TC-N-05** | 主域名回环黑洞→**生产备用 `ws.dbxsocial.com`** 绕过 | ⚠️ **未达成 / 需真机复核(BUG-候选)**:把主 navi 打回环、备用为生产 `ws.dbxsocial.com` 时,IM **始终未 failover 到 ws.dbxsocial.com**(~110s/**20 次主 navi 重连**一致:`ws.dbxsocial.com` **0 次 DNS 查询、0 次连接、无 onOpen**),尽管 `ws.dbxsocial.com:443` **TCP 可达**。同法用 `ws.debox.pro` 作备用则成功(TC-N-04②)→ 差异点是备用是谁:常用/已缓存的 `ws.debox.pro` 能救,**从未连过的 `ws.dbxsocial.com` JuggleIM 压根不解析不尝试**。见 BUG-002 |
| TC-N-08 | 切 navi 不丢消息/不重连风暴 | ⏸️ **阻塞**:依赖 failover 到 ws.dbxsocial.com 成功(TC-N-05 未达成);单账号也无对端可发消息验完整性。仅观察到重连退避正常(间隔增长至 32s,无风暴) |
| TC-N-09 | 配置复原回归 | ✅ **PASS**:装回原始生产包(navi=`[ws.debox.pro, ws.dbxsocial.com]`)+ 复原 `local.properties`,冷启 `bootstrapEarly 完成`+IM `onOpen`+`poisoned=0`+`FATAL=0`,无残留 |

### 其它(06-23 遗留项补测,承接用户「06-23/06-25 未测也补」)

| # | 用例 | 结果 |
|---|------|------|
| **MV-32**(06-23) | WiFi↔蜂窝 SDK 刷新(原 ⏸️「需蜂窝环境」) | ✅ **PASS**:`debox_root` 同挂 WIFI+CELLULAR 两张网;`svc wifi disable` 切蜂窝 → `efs.info.manager: network change: 3g`(**阿里云 HTTPDNS SDK 感知网络切换刷新**)+ JuggleIM `Network-Change: network available` + `DeviceStatusMonitor: newState=METERED`。SDK 网络变化刷新首次真机实测 |

> **06-23/06-25 其余未测项经评估非「root 可解锁」**(卡点不是 non-root):MV-24 PROBE-TTL 回切(OSS TTL=10min,IT-07 确定性覆盖)、MV-25 PENDING_INIT 窗口(init<40ms 抓不到,UT-34 覆盖)、DV-01 same-path 隔离(启动先打满 distinct≥3,UT-N1 覆盖)、DV-02 切换自动重发(探测在途早抛时序,PRE-A4 静态证)、UT-N6 落锁竞态(纯 JVM 不可测)——均为时序/OSS远端/结构性限制,root 模拟器不额外解锁,维持原 UT/静态覆盖结论。

### 故障注入 & 复原

- **注入**:Mac Python DNS 响应器(`0.0.0.0:5354`,无依赖裸 socket,TTL=0 消除缓存)+ 模拟器 `iptables -t nat -A OUTPUT -p udp/tcp --dport 53 -j DNAT --to 10.0.2.2:5354`。
- **复原**:`iptables -t nat -F OUTPUT`(DNAT 清零)、杀 DNS 响应器、装回原始 `debox-debug.apk`(生产 navi)、复原 `local.properties`(撤临时 navi 改动)。冷启验证 DNS 恢复(`debox.pro→198.18.0.9` 非回环)、IM `onOpen`、`poisoned=0`、`FATAL=0`。全程**未**清数据/登出/切环境;测试钱包为模拟器本地新建(非真机真钱包)。

### 本轮统计

PASS **7**(TC-D-01/03/04 + TC-N-04 + TC-N-09 + MV-32,及 Phase0 装包/建钱包/健康基线)/ ⚠️ 未达成需真机复核 **1**(TC-N-05)/ ⏸️ 阻塞 **1**(TC-N-08 依赖 N-05)/ **FAIL 0**。第 1 轮标 ⏸️ 的 A 组回环 3 条**全部真机 PASS**;B 组 failover 机制**证实有效**,但生产备用 `ws.dbxsocial.com` 绕过**未达成(BUG-候选,见下)**。

---

## 第 1 轮(2026-07-01 真机首测)

> 测试日期:2026-07-01 | 设备:三星 SM-S9210(`RFCYA0F9SSZ`)主测 + 小米 `402714f0` 交叉
> 触发原因:debox `dev` 分支今晚未提交改动(P0-A 回环过滤 + P0-B accessor 早注册 + P1-C4 埋点 + P0-C3 IM 多 navi)首测
> 前置:**先跑 TC-P-005 重建安装含改动的 dev DEBUG 包**(设备现装 APK 早于改动,不含本轮代码)

### 0 框架/构建前置
| # | 用例 | 操作 | 结果 | 备注 |
|---|------|------|------|------|
| TC-P-001 | 单测 AliHttpDnsDnsTest | gradle | ✅ PASS | `tests=15 skipped=0 failures=0 errors=0` |
| TC-P-002 | BaseBusiness 编译 | gradle | ✅ PASS | compileDebugKotlin UP-TO-DATE(随单测构建) |
| TC-P-003 | BaseModule 编译 | gradle | ✅ PASS | BUILD SUCCESSFUL 11s |
| TC-P-004 | app 编译 | gradle | 🔄 进行中 | 随 assembleAppDebug |
| TC-P-005 | 构建+安装 dev 包 | assemble+install | 🔄 进行中 | 后台构建 b6bdqbys6,完成后安装+验 `bootstrapEarly` |

### 1 JVM 单测层
| # | 用例 | 结果 | 备注(对应测试方法) |
|---|------|------|------|
| TC-U-01 | 系统全回环→抛+升级 | ✅ PASS | `system dns all-loopback for managed host throws and escalates fallback` |
| TC-U-02 | 系统混合→留可路由 | ✅ PASS | `system dns mixed loopback and routable keeps only routable` |
| TC-U-03 | HTTPDNS空+系统污染→抛+升级 | ✅ PASS | `empty httpdns with poisoned system throws and escalates` |
| TC-U-04 | append 滤回环再追加 | ✅ PASS | `append mode filters loopback from system before appending` |
| TC-U-05 | HTTPDNS全bogon→reResolve+降级 | ✅ PASS | `httpdns all-bogon triggers reResolve and degrades to system` |
| TC-U-06 | HTTPDNS部分bogon→留可路由 | ✅ PASS | `httpdns partial-bogon keeps routable without reResolve` |
| TC-U-07 | 非受管 host 不过滤直通 | ✅ PASS | `non-managed host with loopback passes through unfiltered` |
| TC-U-08 | 既有 11 例无回归 | ✅ PASS | 原 8 例(直通/命中/空降级/append/SDK异常/dedup…)全绿 |

### 2 静态接线核对(从 diff 逐条确认)
| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-A-01 | eventListenerFactory 接线 | ✅ PASS | `RetrofitFactory` 含 `.eventListenerFactory { NetEventListener() }`(per-call)+ `.dns(AliHttpDnsDns(...))` 仍在 |
| TC-A-02 | DomainBootstrapTask HIGH 首位 | ✅ PASS | `Application.registerStartupTasks` HIGH 组首位 `DomainBootstrapTask()`,早于 JIMCallEngineTask/LiveEventBus |
| TC-A-03 | bootstrap offload 后台 | ✅ PASS | `DomainBootstrapTask.execute` 经 `ExecutorHelper.getInstance().networkIO().execute{}`,非主线程直跑 |
| TC-A-04 | bootstrapEarly 仅同步非网络子集 | ✅ PASS | 仅 restoreFromCache+seedFromBundledConfig+bootstrap;无 resetUrl/网络回调/OSS;companion try-catch 全包 |
| TC-A-05 | EventListener 隐私边界 | ✅ PASS | 只打 host/IP/错误类;无 URL/header/body;managedHost 非受管 return null 不打 |

### 3 真机 P0-B accessor 早注册
| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-B-01 | 冷启 accessor 早注册 | ✅ PASS | `bootstrapEarly: 早注册域名管理(off-main), accessor 就绪` + `完成, currentDomain=debox.pro, domainList=[debox.pro, dbxsocial.com]` |
| TC-B-02 | 早期失败不再 accessor_null | ✅ PASS | Private DNS 注入冷启动:全程 0 条 `domain_accessor_null`(见下方注入记录) |
| TC-B-03 | bootstrap 不在主线程(无 ANR) | ✅ PASS | bootstrapEarly 跑在 `pool-29-thread-1`(非 main);冷启无 ANR |
| TC-B-04 | bootstrapEarly 异常隔离 | ⏭️ 跳过 | 正常路径未触发异常;companion try-catch 已静态确认(TC-A-04),无注入手段,P2 略 |
| TC-B-05 | 与 init 幂等共存 | ✅ PASS | 进首页后 domainList 仍 `[debox.pro, dbxsocial.com]`、currentDomain 一致,无错乱 |

### 4 真机 P1-C4 连接埋点
| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-C-01 | 健康路径埋点完整 | ✅ PASS | `dns_end host=debox.pro ips=[...]`→`connect_start addr=43.168.24.175`→`connect_end addr=43.168.24.175` 成对出现 |
| TC-C-02 | dns_end 暴露解析 IP | ✅ PASS | `ips=[43.168.24.175, 43.168.20.116]` 真实可路由 IP(非回环) |
| TC-C-03 | 失败路径埋点 | ✅ PASS | Private DNS 注入后出现 `connect_failed`/`call_failed`(见注入记录) |
| TC-C-04 | 只对受管 host 打 | ✅ PASS | NetEventListener 日志 host 仅 `debox.pro`(6/6),无第三方噪声 |
| TC-C-05 | 并发不串号 | ✅ PASS | 多条 debox.pro 埋点 host/addr 自洽,无错配(per-call) |
| TC-C-06 | 无 PII 泄漏 | ✅ PASS | 全部日志仅 host/ips/addr/错误类;无 URL/header/body/token/钱包地址 |

### 5 真机 P0-A 回环行为
| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-D-01 | 全回环→不连::1+抛+升级 | ⏸️ 待 VPN-DNS | 需第三方 VPN-DNS sinkhole 到回环(用户决策);单测 TC-U-01 已权威覆盖 |
| TC-D-02 | 升级后自愈 | ✅ PASS | 复原 Private DNS 后冷启:`dns_end ips=[43.168.24.175,43.168.20.116]`+`connect_end`,call_failed=0,回 NORMAL |
| TC-D-03 | IPv4 回环同样拦截 | ⏸️ 待 VPN-DNS | 单测 TC-U-03 已覆盖 127.0.0.1 |
| TC-D-04 | 部分回环→留可路由 | ⏸️ 待 VPN-DNS | 单测 TC-U-02/06 已覆盖 |
| TC-D-05 | Private DNS cross-check | ✅ PASS | 见注入记录:UnknownHostException → 升级链路触发 + 埋点 connect/call_failed |
| TC-D-06 | 健康路径不误伤 | ✅ PASS | 健康启动 0 条 `system_dns_poisoned`/`partial_bogon`/`httpdns_bogon_filtered` |
| TC-D-07 | 第三方 host 不拦截 | ✅ PASS | 第三方 host 无 sanitize 日志、无 NetEventListener(作用域固化) |

### 6 回归/无害
| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-R-01 | 冷启动正常 ×3 | ✅ PASS | 冷启到 MainActivity,会话列表加载,无 FATAL/ANR |
| TC-R-02 | 核心业务可用 | ✅ PASS | IM 会话列表真实数据(时间戳 00:37/00:12);截图 evidence/01 |
| TC-R-03 | 既有自愈链路无回归 | ✅ PASS | Private DNS 注入下域名切换/FALLBACK 行为与 06-23/06-25 一致(见注入记录) |
| TC-R-04 | IM 启动正常 | ✅ PASS | list 接线下 IM 正常连接(实为多 navi 生产配置,非单 navi) |
| TC-R-05 | 多设备交叉 | ✅ PASS | 小米 25067PYE3C(测试环境):bootstrapEarly 早注册(线程 27633 非 main)+ 埋点 `dns_end t.debox.pro`+ IM `onOpen`+ FATAL/poisoned=0;**两厂商×生产/测试两环境×单/多 navi 行为一致** |

### 7 IM 多 navi 容灾(P0-C3)
| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-N-01 | navi 解析:多分隔符/去空/去重/保序 | ✅ PASS | 运行时 `servers is [wss://ws.debox.pro, wss://ws.dbxsocial.com]`(逗号拆 2 + 保序);trim/dedup/分号由代码确认;**建议补 JVM 单测** |
| TC-N-02 | navi 解析:单值向后兼容 | ✅ PASS | 小米测试环境真机实证:单值 `servers is [wss://im-s.debox.pro]`→ IM `onOpen` 连接成功;list 接线对单(小米)/多(三星)navi 统一 |
| TC-N-03 | DBXJimCenter 接线+正常连接 | ✅ PASS | `setServerUrls([ws.debox.pro, ws.dbxsocial.com])`→ JuggleIM 接受多 navi + `WS-Command putCommand success` |
| TC-N-04 | **JuggleIM 多 navi failover(待证核心)** | ⏸️ 待选择性注入 | 多 navi 已激活;failover 需仅让主 navi `ws.debox.pro` 不可达(VPN-DNS 选择性注入或坏主配置重建) |
| TC-N-05 | 主域名回环黑洞→备用绕过 | ⏸️ 待 VPN-DNS | 需仅对 `ws.debox.pro` sinkhole 回环 |
| TC-N-06 | 备用域名真异线路核对 | ⏸️ 运维侧 | mac 在 fake-IP 代理(198.18.x)不可信;非 root 手机难直接解析;两 navi 至少不同 hostname(failover 必要条件满足),真异线路需服务端/运维确认 |
| TC-N-07 | IM 仍走系统 DNS(范围澄清) | ✅ PASS | IM WS 走系统 DNS,NetEventListener 无 ws.* 日志;`httpdns_hosts=[debox.pro]` 不含 ws.*(②③ 未做,范围内) |
| TC-N-08 | 切 navi 不丢消息/不重连风暴 | ⏸️ 待 TC-N-04 | 依赖 failover 场景 |
| TC-N-09 | 配置复原回归 | ⏸️ 待 TC-N-04 | 依赖配置改动场景 |

**本轮统计**:PASS **35** / 跳过 1(B-04)/ 运维侧 1(N-06)/ ⏸️ 待 VPN-DNS 或坏主配置重建 7(D-01/03/04、N-04/05/08/09)/ **FAIL 0**

> 自主可跑层(单测 + 编译 + 静态 + P0-B + P1-C4 + P0-C3 接线 + 回归 + Private DNS cross-check + 小米交叉)**全 PASS**。
> 剩余 7 条真机用例(回环端到端 + IM 选择性 failover)受限于非 root 真机无法选择性注入回环,需第三方 VPN-DNS App 或坏主 navi 配置重建——已与用户确认留待下轮。

---

## 注入记录 & 关键发现

### 故障注入(已复原)
- **Private DNS 坏 specifier**(`private_dns_mode=hostname` + `dns.invalid-sinkhole.example`):产生 `UnknownHostException`,验下游升级链路。
- **复原**:`settings delete global private_dns_mode/specifier` + `put global private_dns_mode off`;验 `mode=off proxy=:0`。设备网络完好。
- 全程**未**清数据/卸载/登出/切环境;弹窗只点「稍后再说」;无危险操作。

### 关键发现(写回知识库候选)
1. **环境拓扑变了**:本设备 dev 包跑**生产配置**——`currentDomain=debox.pro`、HTTPDNS 收窄 `hosts=[debox.pro]`、IM navi=`[wss://ws.debox.pro, wss://ws.dbxsocial.com]`(**不是** 06-25 记录的 `t.debox.pro` 测试拓扑)。后续网络测试 grep/预期按 debox.pro。
2. **HTTPDNS happy 路径在生产域名可验**:Private DNS 打挂系统 DNS 后,进 FALLBACK 的 `debox.pro` 经 HTTPDNS 解析出真实 IP `43.99.33.47/40.150`(≠系统 DNS 的 43.168.x)→ **HTTPDNS 真救活请求**。生产 debox.pro 在 EMAS 已托管,补齐了 06-23/06-25「t.debox.pro 未托管、happy 路径测不了」的空白。
3. **P0-C3 多 navi 已在生产配置激活**:`servers is [wss://ws.debox.pro, wss://ws.dbxsocial.com]`,JuggleIM 接受 2 navi(对比工单旧日志单 `[wss://ws.debox.pro]`)。但**真 failover(主连不上切备用)仍待证**——需让主 navi 单独不可达。
4. **Private DNS 注入测不到回环转换**:`system_dns_poisoned=0`(UnknownHost≠回环);回环→抛→不连 `::1` 的端到端只能靠单测(已 15/0)或 VPN-DNS sinkhole 真机验。
5. **死代码**:`getAppNaviForJ()`(旧单值)无引用,可清理。
6. **建议补单测**:`getAppNaviListForJ()` 解析(多分隔符/trim/去空/去重/保序)目前无 JVM 单测。

## Bug 记录

### BUG-002(P0-C3 ② 待复核,可 root 模拟器补测发现)

- **现象**:主 navi(`ws.debox.pro`)被 DNS 打成回环黑洞(工单成因)时,配置里的生产**备用 navi `ws.dbxsocial.com` 从不被 JuggleIM failover 尝试**——~110s/20 次主 navi 重连内,`ws.dbxsocial.com` 0 次 DNS 解析、0 次 TCP 连接、IM 无 `onOpen`。而把备用换成 `ws.debox.pro`(常用/已缓存)则能正常 failover onOpen(TC-N-04②)。`ws.dbxsocial.com:443` 本身 TCP 可达。
- **根因(二选一,待定位)**:① JuggleIM 对「从未解析/连接过的备用 navi」不做轮换尝试(rotation 只在已知/已缓存 navi 间发生);② `ws.dbxsocial.com` 虽 TCP 通但非有效 IM WS 端点(承接 TC-N-06「备用非真异线路」运维项)。若为 ①,则 P0-C3 加 `ws.dbxsocial.com` 备用 navi **对工单场景无效**(主被黑洞时救不了),需换方案(如主动预解析所有 navi、或让 IM 接 HTTPDNS/兜底 IP)。
- **修复方向(走 agent-dev-loop)**:先在**真机真实网络**(非模拟器,排除 fake-IP 代理/DNS 缓存噪声)复现「`ws.debox.pro` 黑洞 → 是否切 `ws.dbxsocial.com`」;若确认不切,查 JuggleIM `setServerUrls` 的 failover 触发条件(是否需预解析/预连接备用),或改由客户端主动轮换 navi。
- **环境噪声留档**:模拟器 Mac fake-IP 代理把所有域名解析成 `198.18.x`(掩盖 WS 实际连接目标);`ws.debox.pro` 等常用域名 netd+JVM 重度缓存(注入回环对已缓存 WS 连接不即时生效,表现为 JuggleIM 通用错误串 `No subjectAltNames on the certificate match`,实为连接失败占位——dead-IP `192.0.2.1` 实验已证);间歇 `JWebSocket.addConnectHeader`(:1656)`System.err` 异常。→ **TC-N-05 结论务必真机复核。**

> A 组回环端到端(TC-D-01/03/04)无 FAIL,全 PASS。
