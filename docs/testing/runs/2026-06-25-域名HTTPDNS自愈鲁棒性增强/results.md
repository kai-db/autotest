# 域名容灾 + HTTPDNS 自愈鲁棒性增强 — 测试结果

> 用例来源:`cases.md`（同目录）。被测:debox-android 工作区分支 `dev`，含本轮 9+1 改动 + 3 处 review 修复（review-①/②/③）+ 测试中发现并修复的 base URL 同步问题。
> 执行日期:2026-06-25 | 执行方式:AI 驱动（A 静态 + B JVM 单测 + D 真机 adb/FLogger）

---

# ⭐ 最终报告（正式环境全量 D 层）

> 本节为权威结论。下方「第一~四轮」为探索过程明细与证据附录（测试环境 + 正式环境），保留留痕。

## 范围与环境
- **被测链路**：debox-android 域名容灾 / HTTPDNS 自愈（9 项改动 + 1 重构 + review-①②③ + base URL 同步修复）。
- **设备**：小米 25067PYE3C `402714f0`（**正式环境**全量 D 层，主报告依据）；三星 `RFCYA0F9SSZ`（测试环境 A/B/D，中途切环境后离线）。
- **正式环境拓扑**：`domainList=[debox.pro, dbxsocial.com]`，HTTPDNS 收窄 `hosts=[debox.pro]`、`abnormalTtlMs=600000`、`enabled=true`，密钥已注入。
- **故障注入**：DNS 污染（strict private DNS + 坏 specifier），每轮测后已复原（两台 `dns_mode=off / proxy=:0`）。

## 各层结论
| 层 | 结论 |
|---|---|
| **A 静态**（PRE-A1~A13） | **13/13 PASS** |
| **B JVM 单测**（UT-N1~N6） | **UT-N1~N5 PASS**，UT-N6 竞态守卫代码审计；BUILD SUCCESSFUL |
| **C 集成** | 本轮无新增（沿用 06-23 `AliHttpDnsDnsIntegrationTest`） |
| **D 真机**（正式环境小米） | 见下表 |

## D 层逐条（正式环境实测）
| DV | 判定 | 证据（正式环境小米） |
|---|---|---|
| DV-01 ② same-path | UT 证 + 计数器实测 | `窗口内不同path=X/同path=Y` 计数器在跑；多接口刷新 distinct≥3 恒先触发，纯 same-path 隔离不可黑盒 → **UT-N1 确定性证明** |
| DV-02 ④ 自动重发 | ✅ **PASS** | 结构化 flog `domain_retry_dispatch path=trade/chains attempt_host=dbxsocial.com current_host=debox.pro` + `自动重发: trade/chains / check_token_channel`，多次复现 |
| DV-03 B1 可达优选 | ✅ **PASS** | `域名切换 dbxsocial.com -> debox.pro (可达优选)` / 反向，多次；全候选不可达退回最小计数 |
| DV-04 B3 网络变化 | ✅ **PASS** | WiFi onAvailable → `network-available: debox.pro 可达，无需处理`，线程数 0（loop-drain 不堆） |
| DV-05 ① OSS 纠偏 | ✅ PASS（无伪纠偏） | OSS 更新后 currentDomain 在池、不误纠偏；真纠偏分支需 OSS 控制（PRE-A1 静态证） |
| DV-06 flog 落盘 | ✅ **PASS** | 正式环境完整自愈链 JSON 结构化落 `dbx_log/flog`，含 `domain_retry_dispatch` |
| DV-07 基线回归 | ✅ **PASS** | 复原后冷启 `debox.pro 可达，无需处理`、无残留 FALLBACK、OSS 成功、App 正常 |
| DV-08 ⑦ proactive | ✅ 幂等正确 | 收窄 host debox.pro 切换前已经正常路径进 FALLBACK → markProactiveFallback **幂等 no-op**（review-② 生效）；happy-path（`reason=proactive-domain-switch`）结构性不可复现（收窄 host 恒先收流量） |
| DV-09 TLS 边界 | ✅ PASS（可 root 模拟器补测 2026-07-01） | 原标 N/A（非 root 不可构造）；后用可 root 模拟器 + iptables DNAT 到 Mac 假 TLS 服务器实测三层全中：`isTcpReachable` TCP-only 误判可达 / 业务 `SSLException` / SSL 触发切换但 `domain_retried=false` 无重发。详见 06-23 `results.md` 第 3 轮 |
| DV-10 full URL 不重发 | ✅ **PASS** | 同轮 `official`（getOfficialDomain full-URL）失败无 `自动重发`，相对路径全重发 |

**Bonus（review 修复真机佐证）**：review-③ `switchInProgress`（`已有切换探测在途, 跳过本次`）；竞态保护（`当前域名已变更，跳过回退`）；全不可达保留当前；新诊断日志 `enter_fallback_skip`（非收窄 host 正确跳过 HTTPDNS）。

## 测试中发现并修复的问题（2 处）
1. **误导性 OSS 日志**（log-correctness）→ ✅ 已修：`fetchOssDomains` 锁内快照真实池 `domainList=$effectivePool (OSS hosts=$domains)`。
2. **🔴 持久化 currentDomain 不回灌主 API 路由**（回归相关，置信度 88）→ ✅ 已修 + 正式环境实测验证：
   - 现象：持久化 currentDomain=dbxsocial.com，但主 API 冷启仍打 debox.pro（**修复前 100:4**）；启动健康探测在保护错 host。
   - 修复：`DomainManager.init()` 恢复后仅当 `getHost()≠currentDomain` 时 `resetUrl()` 同步 base URL。
   - 验证：修复后 `init: base URL ... resetUrl 同步` + 主 API host **102:14（dbxsocial.com 占主），翻转**。
   - 设计注记：修复让切换跨启动「粘住」（符合避免钉死坏域名意图），副作用是不会自动回主域名——是否要「重启优先试主域名」由产品定。

## B4（自动重发）覆盖率评估
- **覆盖**：`DeBoxHttpRequest` 相对路径 = `ApiMethods` ~335 个 API 常量（核心钱包/交易/社交/DAO），正式环境实测重发生效。
- **盲区**：`official`/sysConfig（full-URL，有 OSS/健康探测兜底）；**AI 代理**（`AiProxyService` 裸 OkHttpClient，无切换/无 HTTPDNS/无 B4）；**RN 通道**（未定制 OkHttpClientProvider，独立栈）；OSS 对象传输（无关）。
- **建议**：AI 代理复用 `RetrofitFactory` client；核实 RN 业务请求量评估是否接入容灾。

## 已知边界（非缺陷）
DV-01 纯 same-path 隔离 / DV-08 proactive happy-path / DV-09 TLS 误判 / DV-05 真纠偏分支 —— 均受「多接口/收窄拓扑/非 root/需 OSS 远端」结构性限制不可黑盒，由 UT/静态覆盖。

> **2026-07-01 可 root 模拟器补测评估**:DV-09 TLS 误判已于 07-01 补测 PASS（见本文件 D 层 DV-09 行 + 06-23 第 3 轮）。DV-01（启动先打满 distinct≥3）/ DV-02（探测在途早抛时序）/ DV-05（真纠偏分支）/ UT-N6（纯 JVM 落锁竞态）经评估**非「root 可解锁」**——卡点是时序/结构性/纯 JVM，非 non-root，root 模拟器不额外解锁,维持 UT-N1/PRE-A4/静态覆盖结论。

## 终判
**本轮 9+1 改动 + review-①②③ 全部通过，无 block 级缺陷。测试另发现 2 处问题（1 log + 1 回归相关 base URL 同步），均已修复，base URL 修复经正式环境实测验证（100:4 → 102:14）。** 两台设备环境已复原。

---

## 环境确认（Phase 1）

| 项 | 值 |
|---|---|
| 主测设备 | 三星 SM-S9210 `RFCYA0F9SSZ`（在线） |
| 交叉设备 | 小米 25067PYE3C `402714f0`（在线，本轮未交叉） |
| App 版本 | 2.13.2 / versionCode 21300002 |
| 构建含新代码 | ✅ 证实：启动日志含 `hostsConfiguredButEmpty=false`（review-① 今日新增字段）+ `verifyAndHealCurrentDomain` + `seedFromBundledContent` + 新版 `fetchOssDomains` 格式 + flog 落盘文件 |
| 启动域名状态 | `currentDomain=t.dbxsocial.com`，`domainList=[t.debox.pro, t.dbxsocial.com]`，HTTPDNS `hosts=[t.debox.pro]`、`abnormalTtlMs=600000`、`enabled=true` |
| HTTPDNS SDK | `doInit: HTTPDNS SDK 初始化完成`（密钥已注入） |
| 危险红线 | 本轮仅 DNS 污染 + 日志取证 + 列表页下拉，无清数据/登出/转账/切环境，安全 |

---

## A 层：静态核对（PRE-A1 ~ A13）— 全 PASS ✅

> A1~A9、A13 由子代理审计（带行号证据）；A10~A12 由本人在 review 修复时自验。

| # | 用例 | 判定 | 证据 |
|---|---|---|---|
| PRE-A1 | ① OSS 纠偏 resetUrl | PASS | `DomainManager.kt:206-219` `previous` 记旧域名，urlChanged 锁外调 `HttpConstant.resetUrl()` |
| PRE-A2 | ② same-path 阈值 | PASS | `DomainManager.kt:674-683` distinct≥3 OR same≥3；`samePathThreshold=3`（:56）；窗口保留重复（:88/380） |
| PRE-A3 | B1 切换前可达优选 | PASS | `:407` 故障计数升序；`:428` 锁外 `isTcpReachable`；`:597-604` Socket.connect(443) 仅 TCP，不宣称 TLS |
| PRE-A4 | ④ 请求层重发 | PASS | `DeBoxHttpRequest.kt:63/70` domainRetried+attemptHost；`:256/279` block 内重算同刻写；`:365-371` 五条件重发一次 |
| PRE-A5 | B3 网络变化探测 + loop-drain | PASS | `:566` CAS 注册一次；`:576` NET_CAPABILITY_INTERNET；`:579-581` 仅 onAvailable；`:493-509` loop-drain + 单轮 try/catch；`:130` applicationContext |
| PRE-A6 | 故障判定两套口径 | PASS | `NetworkFailures.kt:31-39` isDomainSwitchSignal 含 SSL；`:42-49` isReplaySafe 排除 SSL；connect-timeout 都计、read-timeout 都不计 |
| PRE-A7 | FLogger 落盘接线 | PASS | `NetFlog.kt:22-35` 防御性 try/catch；自愈链路无 LogUtils-only；`RetrofitFactory.kt:84` 注入 `AliHttpDnsDns(log=NetFlog.w)` |
| PRE-A8 | ⑤ host 归一化 | PASS | `HostNormalizer.kt:20-51` 去 scheme/port/path、小写、LABEL 正则拒非 ASCII/空段/首尾`-`；`:33-35` 裸 `@` 拒绝、URL 形式取 host |
| PRE-A9 | ⑥ OSS 拉取失败落盘 | PASS | `DomainManager.kt:185-187/193-195/231-234` 空内容/空 hosts/下载失败三类各落 NetFlog.w，标注沿用兜底 |
| PRE-A10 | ⑦ markProactiveFallback（review-②） | PASS | `HttpDnsFallbackPolicy.kt:204-219` 不 consume ThreadLocal、生效域名 gating、按「下一次 decide 是否 HTTPDNS」幂等：未过期 FALLBACK/有效租约 PROBE 才 no-op，过期态刷新 |
| PRE-A11 | 切换落锁竞态 + 冷却提交（review-③） | PASS | `DomainManager.kt:414-455` switchInProgress 防并发、try/finally 必释放；lastSwitchTime+清窗口只在真切换提交 |
| PRE-A12 | HTTPDNS hosts 归一化 + 全非法安全失败（review-①） | PASS | `HttpDnsRemoteConfig.kt` hostsConfiguredButEmpty；`HttpDnsConfig.kt:130-137` isEffectiveHost 命中安全失败返回 false |
| PRE-A13 | full URL 请求边界 | PASS | `DeBoxHttpRequest.kt:137-147` resolveFullUrl http(s) 直返；`:367` 重发条件排除 startsWith("http") |

**A 层结论:13/13 PASS，无结构性缺陷。**

---

## B 层：JVM 单测（UT-N1 ~ N6）— 全 PASS ✅

> 命令（关 configure-on-demand）：
> `./gradlew :business:BaseModule:testDebugUnitTest --tests "*Domain*" -Dorg.gradle.configureondemand=false`
> `./gradlew :business:BaseBusiness:testDebugUnitTest --tests "com.app.base.business.network.*" -Dorg.gradle.configureondemand=false`

| # | 覆盖簇 | 判定 | 备注 |
|---|---|---|---|
| UT-N1 | `reachedSwitchThreshold` ×5 | PASS | 含 same-path≥3 触发（B2） |
| UT-N2 | `NetworkFailuresTest` ×4 | PASS | SSL 是 switch signal 非 replay-safe；read-timeout 两者都不计 |
| UT-N3 | `normalizeHost` + 脏值解析 | PASS | 逐 label 拒绝 + `@` 防截断 |
| UT-N4 | `markProactiveFallback` ×6 + 拦截器 dnsLevel ×2 | PASS | **新增**：过期 FALLBACK 刷新 / 过期 PROBE 刷新 / 有效租约 PROBE no-op（review-②） |
| UT-N5 | `HttpDnsRemoteConfig` hosts 归一化 + 全非法标记 | PASS | **新增**：全非法→hostsConfiguredButEmpty=true；缺失/显式空→false（review-①） |
| UT-N6 | 切换落锁竞态守卫（纯 JVM 不可测） | N/A | 靠 PRE-A11 代码审计 + D 层观察 |

两模块 **BUILD SUCCESSFUL**，无 FAIL。**B 层结论:UT-N1~N5 全 PASS，UT-N6 代码层守卫已审计。**

---

## D 层：真机验证（DV-01 ~ DV-10）

> 主测机三星 `RFCYA0F9SSZ`。故障注入=DNS 污染（strict private DNS + 坏 specifier），测后已复原。
> 一次冷启动在全量 DNS 污染下产出完整切换链，覆盖多条 DV。

### 实测切换链（flog 权威落盘，DV-06）

```
19:40:02 累计故障=1 (check_token_channel)
19:40:02 enter_fallback: t.debox.pro reason=UnknownHostException
19:40:02 httpdns_empty_fallback_system: t.debox.pro      ← EMAS 未托管,空结果降级(预期)
19:40:02 累计故障=2 (sync/skills) / =3 (sync/prompts)     ← distinct≥3 触发切换探测
19:40:02 fetchOssDomains: OSS 下载失败 UnknownHostException，沿用 SP/内置兜底   ← ⑥
19:40:06 累计故障=4~7 → 已有切换探测在途, 跳过本次 ×4       ← review-③ switchInProgress 防并发
19:40:15 域名切换 t.dbxsocial.com -> debox.pro (可达优选), failureMap={t.debox.pro=7}   ← B1
19:40:19 verifyAndHealCurrentDomain: 当前域名已变更为 debox.pro，跳过回退   ← 健康探测/请求级切换竞态保护
19:40:36 verifyAndHealCurrentDomain: debox.pro 不可达且无可达备选（疑似设备离线），保留当前域名   ← 全不可达保留分支
```

| # | 用例 | 判定 | 证据 / 说明 |
|---|---|---|---|
| DV-01 | ② same-path 兜底切换 | UT 证明 + 机制实测 | 计数器文案 `窗口内不同path=X/同path=Y, 未达阈值(distinct=3/same=3)` 实测在跑；但启动/后台先打满 distinct≥3，纯 same-path 隔离黑盒不可控 → 逻辑由 **UT-N1 确定性证明（PASS）** |
| DV-02 | ④ 切换请求自动重发 | PARTIAL（已知边界） | 并发洪流下失败请求走「探测在途跳过」**早于切换落定**就抛回 call.catch，此刻 host 未变 → 不满足 `getHost()≠attemptHost` 重发条件，即时重发被时机击败（cases 已标已知边界）。**PRE-A4 静态已证**；无伪重发（负向成立） |
| DV-03 | B1 切换带可达优选 | ✅ PASS | `域名切换 t.dbxsocial.com -> debox.pro (可达优选)`；全量污染全候选 `isTcpReachable 不可达` → **退回最小计数域名仍切换**（退化路径，交 HTTPDNS 接管） |
| DV-04 | B3 网络变化二次探测 | ✅ PASS | WiFi onAvailable → `verifyAndHealCurrentDomain(network-available): debox.pro 可达，无需处理`；`domain-health-check` 线程数=0（收尾正常，loop-drain 不堆线程） |
| DV-05 | ① OSS 纠偏即时生效 | ✅ PASS（无伪纠偏分支） | currentDomain=debox.pro 经 `ensureFallbackDomains` 仍在池 → **正确不纠偏**；真纠偏分支需 OSS 剔除当前域名（P2 黑盒难触发，PRE-A1 静态已证 resetUrl 接线） |
| DV-06 | 自愈轨迹落本地文件 | ✅ PASS | flog `dbx_log/flog/1782385200000.log` 含上方完整链；关键事件 JSON 结构化落盘、重启不丢 |
| DV-08 | ⑦ DNS 级切换提前置 FALLBACK | ✅ PASS（gating 正确） | 本轮 target=debox.pro **非收窄 host**（hosts=[t.debox.pro]）→ `markProactiveFallback` 正确 **no-op**（无 `proactive-domain-switch`）；happy-path（target=t.debox.pro）受故障计数升序制约（t.debox.pro 计数最高排最后），非根真机不可确定性复现 → review-② 逻辑由 **UT-N4 证（PASS）** |
| DV-09 | TCP 可连但 TLS/SNI 失败边界 | N/A（代码审计） | 非 root 设备无法构造「443 可连 + TLS 失败」；接受为已知边界，文案不宣称 TLS 可用 |
| DV-10 | 启动 full URL 不自动重发 | ✅ PASS | `url=https://t.debox.pro/debox/official`（full URL）UHE 后**无 `自动重发`** → full URL 边界成立 |
| DV-07 | 复原后基线回归 | ✅ PASS | DNS mode=off/specifier=null/proxy=:0/wifi=1 全复原；干净冷启**无残留 FALLBACK/异常切换**，全程 `可达，无需处理` + OSS 成功；App UI 正常渲染（消息列表，截图存档） |

### Bonus 实测确认（超出用例，本轮 review 修复的真机佐证）

- **review-③ `switchInProgress` 防并发**：`已有切换探测在途, 跳过本次` ×4 实测生效 —— 并发失败线程不重复探测/切换。
- **健康探测 vs 请求级切换竞态保护**：`当前域名已变更为 debox.pro，跳过回退`（PRE-A11 同源思想，健康探测不覆盖请求级切换的新状态）。
- **健康探测「全不可达保留当前」分支**：`debox.pro 不可达且无可达备选（疑似设备离线），保留当前域名，待网络恢复后由请求级切换接管`。

### Minor 观察 → ✅ 已修复

- `DomainManager.fetchOssDomains` 日志原打 `domainList=$domains`（OSS 原始 hosts，不含 ensureFallbackDomains 并入的兜底），导致 `domainList=[2项], currentDomain=[第3项]` 的误导日志。
- **修复**：锁内快照真实池 `effectivePool = domainList.toList()`，日志改为 `domainList=$effectivePool (OSS hosts=$domains)`，两者并列。新包实测：`domainList=[t.debox.pro, t.dbxsocial.com, debox.pro, dbxsocial.com] (OSS hosts=[t.debox.pro, t.dbxsocial.com]), currentDomain=debox.pro` —— currentDomain 一眼在池，歧义消除。单测护栏通过。

---

## 第二轮（日志修复后重测 + 深挖欠测 case）

> 用户手动安装新包（含 review-①②③ + 日志修复）到三星+小米。重点把第一轮标 PARTIAL 的 DV-01/DV-02 用对的场景再打，并做小米交叉。

### 新包确认
- 三星启动日志 `domainList=[...4 项...] (OSS hosts=[...2 项...])` = 我修的日志格式生效；`hostsConfiguredButEmpty=false` + 新增 `switch_eval` 结构化诊断日志（团队同步补的开关评估留痕）均在。

### DV-01 / DV-02 深挖（多场景尝试 → 根因定性）

试了三种触发场景，结论是**本测试环境结构性打不出干净的 same-path 切换与 B4 重发，非代码缺陷**：

1. **启动洪流**（冷启）：并发失败，distinct≥3 由随机请求跨越；跨越者多非 DeBoxHttpRequest 相对路径 → 无重发。
2. **稳态空载**（前台 idle 污染 45s）：主界面几乎无流量，0 失败累积。
3. **UI 下拉刷新 ×5**（消息列表，污染下）：实测 `dao_favorite_list / session_list / did/theme/query / batch_user_info` —— **消息列表刷新发多个不同接口**，distinct≥3 仍先于 same-path 触发；切换成功 `域名切换 debox.pro -> t.dbxsocial.com (可达优选)`，但**仍无 `自动重发`**。

**DV-02 无法触发的根因**（读 `DeBoxHttpRequest.kt:366-377` 重发条件 + 实测交叉验证）：
- 重发要求 `!path.startsWith("http") && getHost()≠attemptHost`（相对路径 + 发出后域名被切）。
- 但本测试环境失败请求实际 **pin 到 `t.debox.pro`（HTTPDNS 收窄 host）的 full-URL/固定 host**，`attemptHost=HttpConstant.getHost()`=currentDomain 占位（debox.pro/t.dbxsocial.com）。full-URL 请求按设计（DV-10/PRE-A13）**本就不重发**。
- **生产环境**业务流量走真实 currentDomain 的相对路径 → 切换改 currentDomain → 重发会生效。测试环境的 t.debox.pro pin 是环境特有产物。
- 结论：**DV-02 = PRE-A4 静态已证 + 负向（full-URL/pin 请求正确不重发，实测成立，强化 DV-10）**；正向即时重发本环境不可黑盒，生产可生效。

**DV-01 同理**：消息列表/启动均为多接口 → distinct≥3 恒先触发，纯 same-path 隔离不可黑盒；`同path=` 计数器实测在跑（`窗口内不同path=X/同path=Y`），same-path≥3 触发逻辑由 **UT-N1 确定性证明**。

### 第二轮额外实测确认
- **第二次独立切换** `域名切换 debox.pro -> t.dbxsocial.com (可达优选)`（首轮是 →debox.pro）→ 跨不同 target 切换均正常。
- **review-③ switchInProgress** 再次实测（`已有切换探测在途, 跳过本次`）。
- **日志修复** 新格式实测生效。

### 小米交叉验证（402714f0）
- DNS 污染冷启，复现核心链：`enter_fallback: t.debox.pro reason=UnknownHostException` + `fetchOssDomains: OSS 下载失败...沿用 SP/内置兜底`（⑥）+ `累计故障=1~5` + **`已有切换探测在途, 跳过本次`（review-③）** → 自愈机制**非设备特定**，小米一致。
- 复原后冷启 `当前域名 debox.pro 可达，无需处理`，恢复正常。
- （切换结果行因复原 DNS 早于探测完成未抓到，三星已两次确认，不影响结论。）

### 环境复原
- 两台设备最终：`private_dns_mode=off / specifier=null / http_proxy=:0`；各自冷启 `可达，无需处理` + OSS 成功，无残留 FALLBACK。

---

## 总判定（终版）

| 层 | 结果 |
|---|---|
| A 层（静态 PRE-A1~A13） | **13/13 PASS** |
| B 层（JVM 单测 UT-N1~N6） | **UT-N1~N5 全 PASS，UT-N6 代码守卫已审计** |
| C 层 | 本轮无新增（沿用 06-23 `AliHttpDnsDnsIntegrationTest`） |
| D 层（真机 DV-01~10，三星+小米） | **PASS：DV-03/04/05/06/07/08/10**（DV-03 三星两次 + 小米链路）；**UT/静态覆盖 + 环境受限**：DV-01（UT-N1 + 计数器实测）、DV-02（PRE-A4 + 负向，测试环境拓扑限制）、DV-09（代码审计边界） |

### D 层逐条终判

| DV | 判定 | 一句话 |
|---|---|---|
| DV-01 same-path | UT 证 + 机制实测 | `同path=` 计数器在跑；多接口环境 distinct≥3 恒先触发，纯隔离不可黑盒 → UT-N1 确定性证明 |
| DV-02 自动重发 | ✅ PASS（正式环境） | **正式环境实测**：`dapps`/`check_token_channel` 相对路径切换后 `自动重发` 到新域名；同轮 full-URL `official` 不重发（边界正确）。测试环境 t.debox.pro pin 才是之前打不出的原因，非缺陷（见第三轮） |
| DV-03 可达优选 | ✅ PASS | 三星两次 `域名切换 X->Y (可达优选)` + 全候选不可达退回最小计数；小米复现 |
| DV-04 网络变化探测 | ✅ PASS | WiFi onAvailable → `network-available: 可达，无需处理`，线程不堆 |
| DV-05 OSS 纠偏 | ✅ PASS | 无伪纠偏（兜底域名在池）；真纠偏分支需 OSS 控制，PRE-A1 静态证 |
| DV-06 flog 落盘 | ✅ PASS | 完整自愈链 JSON 结构化落本地文件 |
| DV-07 基线回归 | ✅ PASS | 两台复原后无残留 FALLBACK、全程可达、App 正常 |
| DV-08 proactive | ✅ gating PASS | 非收窄 host target 正确 no-op；happy-path（target=t.debox.pro）结构性不可达（t.debox.pro 恒最高计数），UT-N4 证 |
| DV-09 TLS 边界 | N/A 审计 | 非 root 不可构造，文案不宣称 TLS |
| DV-10 full URL 不重发 | ✅ PASS | pin 到 t.debox.pro 的 full-URL 失败无 `自动重发`，强化佐证 |

## 第三轮（正式环境）：DV-02 B4 自动重发实测 PASS + 覆盖率评估

> 用户手动切到**正式环境**（业务流量走真实 currentDomain 相对路径，解开测试环境 t.debox.pro pin 的死结）。小米 `402714f0` 实测。

### DV-02 B4 自动重发 → ✅ PASS（正式环境实测）

正式环境 `domainList=[debox.pro, dbxsocial.com]`、`currentDomain=debox.pro`、HTTPDNS `hosts=[debox.pro]`。DNS 污染冷启：

```
20:29:13 域名切换 debox.pro -> dbxsocial.com (可达优选), failureMap={debox.pro=3}
20:29:13 域名已自愈切换(debox.pro -> dbxsocial.com)，自动重发: dapps              ← B4 重发 ✅
20:29:13 onConnectivityFailure: domain=dbxsocial.com, path=debox/dapps           ← 重发打到新域名
20:29:14 域名已自愈切换(debox.pro -> dbxsocial.com)，自动重发: check_token_channel  ← B4 重发 ✅
```

- **相对路径请求**（`dapps`/`check_token_channel`）切换后**自动重发到新域名** → B4 生效。
- **同轮 full-URL 的 `official`**（getOfficialDomain 拼）失败但**无 `自动重发`** → 边界正确。
- 验证了第二轮的根因判断：测试环境 t.debox.pro pin（业务流量固定 host）才是 B4 打不出来的原因，**非代码缺陷**；生产拓扑下 B4 正常工作。

### B4 / 域名容灾覆盖率评估

| 通道 | 域名切换(A) | B4 重发(B) | 量级 | 说明 |
|---|---|---|---|---|
| `DeBoxHttpRequest` 相对路径（`ApiMethods` ~335 个 API 常量：assets/、trade/、dao/、debox/…） | ✅ | ✅ | **绝大多数业务流量** | 钱包/交易/社交/DAO 核心接口；正式环境实测重发生效 |
| sysConfig / `official`（`getOfficialDomain()`+full-URL，AppConfigManager:251） | ✅（同 RetrofitFactory client） | ❌ full-URL | 1（启动配置） | 启动期，另有 OSS/健康探测纠偏兜底，影响有限 |
| AI 代理 completion（`AiProxyService:41`，`sharedAiHttpClient` **裸 OkHttpClient**+full-URL） | ❌ | ❌ | 1 类（AI 聊天） | **完全在容灾体系外**：无切换/无 HTTPDNS/无重发 |
| RN 通道请求（RN 默认 OkHttp，**未定制 OkHttpClientProvider**） | ❌ | ❌ | 取决于 RN 业务占比 | **完全在容灾体系外**，最值得关注 |
| OSS 对象上下传（`$ossUrl/${objectKey}` full-URL） | 不适用 | ❌ | 少量 | 打 OSS 非 API 域名，切换本就无意义 |
| 区块链浏览器类（`/txs/push`、`/addrs/...`） | 取决于 base | 取决于 | 少量 | 第三方链 API |

**评估结论**：
- **B4 对核心业务 API（相对路径，~335 个 `ApiMethods`）覆盖完整，正式环境已实测生效**。
- 漏网集中在**边缘/独立通道**：启动配置（有兜底）、OSS 传输（无关）、**AI 代理 + RN 通道（这两类完全在域名容灾体系外，是真正的覆盖盲区）**。
- **建议**：① AI 代理 `sharedAiHttpClient` 可复用 `RetrofitFactory` 的 client（或加同款 `DomainSwitchInterceptor`+`AliHttpDnsDns`）以纳入容灾；② 核实 RN 通道实际业务请求量，评估是否需要把 RN 网络栈接入域名切换/HTTPDNS（定制 `OkHttpClientProvider` 复用原生 client）。

---

## 第四轮（正式环境小米全量 D 层）+ 🔴 新发现

> 小米 `402714f0` 正式环境逐条跑 D 层。

| DV | 正式环境判定 | 证据 |
|---|---|---|
| DV-02 自动重发 | ✅ PASS（多次） | `域名已自愈切换(debox.pro -> dbxsocial.com)，自动重发: dapps/check_token_channel`，重发打到新域名 |
| DV-03 可达优选 | ✅ PASS | `域名切换 X -> Y (可达优选)`，多次（debox.pro↔dbxsocial.com 双向） |
| DV-04 网络变化探测 | ✅ PASS | `verifyAndHealCurrentDomain(network-available): debox.pro 可达，无需处理`，线程数 0 |
| DV-06 flog 落盘 | ✅ PASS | 正式环境完整链落 flog |
| DV-08 proactive | ✅ 幂等 no-op 正确 | target=debox.pro 已先经正常路径进 FALLBACK → markProactiveFallback 正确幂等 no-op（review-② 生效）。happy-path（`reason=proactive-domain-switch`）**结构性不可复现**：唯一收窄 host（debox.pro）恒为收流量/先失败的 host，切换 TO 它之前它已在 FALLBACK |
| DV-10 full URL 不重发 | ✅ PASS | 同轮 `official`（getOfficialDomain full-URL）失败无 `自动重发` |
| review-③ switchInProgress | ✅ | `已有切换探测在途, 跳过本次`（正式环境复现） |

### 🔴 新发现（高置信度，本轮回归相关）：持久化 currentDomain 对主 API 路由不生效（启动 base URL 不同步）

**现象**：污染切到 dbxsocial.com 并持久化 → 复原 → 干净冷启。`init: currentDomain=dbxsocial.com` + `verifyAndHealCurrentDomain(startup): dbxsocial.com 可达，无需处理`，但实际请求 host 统计：**`https://debox.pro`=100 次（主 API），`https://dbxsocial.com`=仅 4 次**。

**根因**（读码确认）：
- `HttpConstant.baseSetUrl()` 缓存 `mCurrencyUrl`（`HttpConstant.kt:52-57`）；`mCurrencyUrl` 在 `currentDomain` 被 `restoreFromCache` 恢复前若已被首次调用（RetrofitFactory `baseUrl(baseSetUrl())` 早于 DomainManager.init），就缓存成 `currentBaseUrl()` 的默认值 `BASE_URL`=debox.pro（`DomainManager.kt:671-676` currentDomain 空→BASE_URL）。
- `init`/`restoreFromCache` 路径**不调 `HttpConstant.resetUrl()`**（resetUrl 只在 OSS 纠偏 `:224` / 请求级切换 `:473` / 健康探测切换 `:585` 调用）→ 恢复出的持久化 currentDomain 不回灌 base URL。
- 内部不一致：`getOfficialDomain()` 用 `currentBaseUrl()`→dbxsocial.com，主 API 用 `baseSetUrl()`（缓存）→debox.pro。

**影响**：
1. 本轮「持久化 currentDomain + 启动健康探测，避免重启钉死坏域名」目标对**主 API 被架空**——上次切到 dbxsocial.com（因 debox.pro 坏），下次冷启主 API 仍打 debox.pro，重新走失败→切换循环。
2. 启动健康探测校验的是 `currentDomain`（dbxsocial.com），主 API 实际走 debox.pro → **健康探测在保护错误的 host，给假「可达，无需处理」**。

**修复（已应用 + 正式环境实测验证 ✅）**：`DomainManager.init()` 里 `restoreFromCache()` 之后，仅当 `HttpConstant.getHost() != 持久化 currentDomain` 时调 `HttpConstant.resetUrl()` 同步 base URL（与 OSS 纠偏 `:224`、切换 `:473` 同一手法；env 覆盖下按覆盖值重算不受影响）。

**决定性验证**（小米正式环境，污染切到 dbxsocial.com 持久化 → 复原 → 干净冷启）：
```
init: 缓存恢复完成, currentDomain=dbxsocial.com
init: base URL host=debox.pro 与持久化 currentDomain=dbxsocial.com 不一致，resetUrl 同步   ← 修复触发
主 API host 分布: dbxsocial.com=102, debox.pro=14
```
**修复前 100:4（debox.pro 占主）→ 修复后 102:14（dbxsocial.com 占主），翻转。** 持久化切换跨启动对主 API 生效。残留 14 debox.pro = init.resetUrl 之前的极早期请求（已知边界，init 后全部纠正）。

**设计注记（供产品决策，非缺陷）**：修复让域名切换跨启动「粘住」（符合「避免重启钉死坏域名」的设计意图）。副作用：用户一旦切到 dbxsocial.com，即使 debox.pro 主域名恢复，也会一直用 dbxsocial.com 直到它失败，**不会自动回主域名**。若产品希望「重启优先试主域名」，需额外逻辑（启动先健康探测主域名、可达则优先）。

---

**结论：本轮 9+1 改动 + 3 处 review 修复（review-①/②/③）全部通过，无 block 级缺陷；测试中另发现 1 处回归相关的启动 base URL 不同步（持久化 currentDomain 不回灌主 API 路由），已修复并正式环境实测验证（100:4 → 102:14 翻转）。**

- 真机额外佐证：**review-③ switchInProgress 防并发**（三星+小米）、**健康探测/请求级切换竞态保护**、**全不可达保留当前**三条自愈分支。
- 第一轮发现的 1 处误导性日志 **已修复**（锁内快照真实池）。
- DV-01/DV-02 的正向即时行为受**测试环境拓扑**（业务流量 pin t.debox.pro + 多接口刷新）限制不可黑盒，已由 UT-N1/PRE-A4 覆盖，生产环境可生效。
- 两台设备环境已完全复原。
