# 网络 / 域名动态切换 + 阿里云 HTTPDNS（DeBox）

> AI 探索沉淀，供网络类特性测试 session 先读。来源：2026-06-13 域名动态切换测试 + 2026-06-23 域名/HTTPDNS 深度测试。

## 域名切换运行机制（`DomainManager` + `DomainSwitchInterceptor`）

- **域名池来源**：OSS `conf_v2.json`（正式）/`conf_test.json`（测试）的 `hosts[]` → `domainList`；`sysConfig.host` 进 `extraDomains`（仅白名单，不参与切换）。
- **内置兜底域名池**：`[debox.pro, dbxsocial.com]`，本地缓存为空时注入，保证 `domainList` 永不空。
- **当前域名持久化**：SP key `domain_current`；域名池 `domain_list`；故障计数 `domain_failures`（启动减半衰减）。
- **启动健康探测**：`checkCurrentDomainHealth` 后台线程 TCP connect `currentDomain:443`：可达→无操作；不可达且有可达备选→切；全不可达→清 `domain_current` 回退 `HOST=debox.pro`。
- **请求级故障切换**：`DomainSwitchInterceptor`（OkHttp 首个 application 拦截器）捕获连通性异常（UnknownHost/Connect/NoRouteToHost/SSL/connect 超时）→ `onConnectivityFailure(host,path)`：10s 窗口内不同 path 去重 ≥3 且过 30s 冷却 → 切到故障计数最小的备选域名 + `resetUrl()`。
- **成功衰减**：任意 HTTP 码响应 → `onRequestSuccess(host)`，**仅当 host==当前域名**才清窗口 + 故障计数 -1。

## 启动日志取证关键词（DEBUG 构建日志全开）

```
adb -s <serial> logcat -d | grep -aE "DomainManager|domain-health|isReachable|域名"
```
- `restoreFromCache: 缓存域名池=[...]` / `缓存域名无效(), 回退到 X`
- `checkCurrentDomainHealth: 当前域名 X 可达，无需处理` ／ `X 不可达 - UnknownHostException` ／ `不可达且无可达备选，清除固化的当前域名并回退主域名`
- `fetchOssDomains: OSS域名池更新成功` / `mergeDomains: 新增额外域名 [...]`
- `onConnectivityFailure: ... 累计故障` / `域名切换 A -> B` / `未达阈值` / `冷却期中`

## ⚠️ 网络故障注入限制（非 root 真机）

测「请求级单域名故障切换」时**device 级断网 / 系统代理都无效**：
1. **设备整体断网** → 业务请求被 App 层守卫提前以 `-100 网络未连接` 短路（`DeBoxHttpRequest.kt RequestCore.call`），**不进 OkHttp 拦截器** → `onConnectivityFailure` 不触发。
2. **系统 http_proxy** → `RetrofitFactory` 设 `.proxy(Proxy.NO_PROXY)`，OkHttp 绕过系统代理，无效。
3. **可触发的**：① 启动健康探测「全不可达回退」分支 = 断网后冷启动（`svc wifi disable`+`svc data disable`）；② 自愈 = 恢复网络后重启。选择性封堵单域名需 root 改 hosts 或网络层注入（C 层联调）。

## 可逆断网手段（不破坏登录态/环境，非危险操作）

```
adb -s <serial> shell svc wifi disable && adb -s <serial> shell svc data disable   # 断网
adb -s <serial> shell svc wifi enable  && adb -s <serial> shell svc data enable    # 恢复
```
测完**务必恢复**，并 `settings put global http_proxy :0` 清理任何代理残留。

---

## 阿里云 HTTPDNS 异常兜底（`httpdns` 包，2026-06-23 实测沉淀）

### 机制（与域名切换互不替代、同口径双驱动）
- **触发链**：`DomainSwitchInterceptor` 捕获连通性异常 → 同时调 `DomainManager.onConnectivityFailure`（域名切换）**和** `HttpDnsFallbackPolicy.markNetworkAbnormal`（HTTPDNS 状态机）。
- **per-host 三态状态机**：`NORMAL`(系统DNS) →[连通性异常+开关开]→ `FALLBACK`(HTTPDNS, 默认 TTL 10min) →[TTL到期]→ `PROBE`(系统DNS单飞试探) →[成功]→ NORMAL / [失败]→ 重回 FALLBACK。
- **§4.4 来源感知**：FALLBACK 期 HTTPDNS 成功**只续期不清除**；只有 PROBE 系统 DNS 成功才清回 NORMAL；HTTPDNS 空结果 `markResolutionDegraded` 改写来源=SYSTEM，该次系统成功**不续期**（防控制台漏配时永不回切）。
- **OkHttp 接线**：`RetrofitFactory` `.dns(AliHttpDnsDns())`（无状态可共享）+ `.proxy(NO_PROXY)`；非受管 host 零开销直通系统 DNS。
- **开关/收窄**：`switchOn()` = 密钥就绪 +（dev/beta 强制 或 OSS `enabled` + 灰度命中）；`isEffectiveHost` = 受管(内置∪池) ∩ OSS `hosts` 收窄列表。

### ⚠️ 测试环境关键约束（实测）
- OSS `conf_test.json` 把 HTTPDNS **收窄到 `t.debox.pro`**（`httpdns_hosts=[t.debox.pro]`，`abnormalTtlMs=600000`）。
- 测试环境**实际业务流量走 `t.debox.pro`**（= HTTPDNS 生效域名），而 `DomainManager.currentDomain=debox.pro` 是生产域名占位 → **HTTPDNS 触发与域名切换天然串联**：debox.pro 失败→切到 t.debox.pro→t.debox.pro 进 FALLBACK 走 HTTPDNS。
- **密钥已注入**（2.13.x dev 包）→ 启动即 `doInit: HTTPDNS SDK 初始化完成`，HTTPDNS 真机可测（上一轮 06-12 因密钥缺失整体禁用）。
- **EMAS 控制台当前未托管 t.debox.pro** → FALLBACK 后 HTTPDNS 持续 `httpdns_empty_fallback_system`，正向"救活请求"无法真机验证；客户端空结果降级正确，第二道域名切换照常接管。配置控制台后方可验 happy 路径。

### HTTPDNS 取证关键字
```
adb -s <serial> logcat -d | grep -aiE "AliHttpDnsManager|HttpDnsConfig|HttpDnsFallbackPolicy|AliHttpDnsDns|enter_fallback|probe_success|probe_fail|fallback_pending_init|httpdns_empty_fallback_system|httpdns_error_fallback_system"
```
- `doInit: HTTPDNS SDK 初始化完成` / `ensureInitialized: 密钥缺失`（禁用态）
- `updateFromOssContent: HttpDnsRemoteConfig(enabled=..., hosts=[...], abnormalTtlMs=...)`
- `enter_fallback: <host> reason=UnknownHostException` / `probe_success` / `probe_fail`
- `httpdns_empty_fallback_system: <host>`（空结果降级，多为控制台漏配）/ `httpdns_error_fallback_system`（SDK 异常降级）

## 故障注入手段对照（2026-06-23 实测厘清，重要）

| 手段 | 是否进 OkHttp 拦截器 | 触发什么 | 命令 |
|---|---|---|---|
| **DNS 污染**（strict private DNS + 坏 specifier） | ✅ 进（产生 UnknownHostException） | 请求级故障切换 + HTTPDNS FALLBACK | `settings put global private_dns_mode hostname` + `private_dns_specifier <坏DoT主机>` |
| **整机断网**（svc disable） | ❌ 被 App 守卫 `-100` 短路 | 仅启动健康探测"全不可达保留当前域名"分支 | `svc wifi disable` + `svc data disable` |

- 测「请求级切换 / HTTPDNS FALLBACK」**必须用 DNS 污染**，整机断网测不到（实测断网冷启 0 条 onConnectivityFailure）。
- 复原 DNS 污染：`settings delete global private_dns_specifier` + `settings delete global private_dns_mode`（回原始未设态）。
- **故障计数启动衰减实测**：重启一次减半（15→7→3），`restoreFromCache: 故障计数(衰减后)=...` 可见。
- **健康探测"全不可达保留当前域名"分支**：`checkCurrentDomainHealth: X 不可达且无可达备选（疑似设备离线），保留当前域名，待网络恢复后由请求级切换接管`（302b4f0 不再误清已生效选择）。
- **resetUrl/切换在 domain-health-check 后台线程**，不阻塞启动；探测期 currentDomain 被请求级切换改写则 `当前域名已变更为 X，跳过回退`（防互相覆盖）。

---

## 自愈鲁棒性增强（2026-06-25 实测沉淀，9+1 改动 + 3 review 修复）

### 新版日志关键词（相对 06-23 的增量，取证 grep 必加）
- `onConnectivityFailure: 已有切换探测在途, 跳过本次` —— **review-③ `switchInProgress` 防并发**：释放锁做可达探测期间，并发失败线程跳过（不重复探测/切换）。`lastSwitchTime`（冷却）+清窗口**只在真切换成功时提交**，放弃切换不烧 30s 冷却。
- `域名切换 X -> Y (可达优选)` —— B1：切换前锁外 `isTcpReachable(候选,1500ms)` 选首个可达；全候选不可达（全量污染）则**退回故障计数最小者仍切换**。
- `isTcpReachable: <host> 不可达 - UnknownHostException` —— B1 探测逐候选，TCP-only（不证 TLS）。
- `verifyAndHealCurrentDomain(startup|network-available): ...` —— 原 `checkCurrentDomainHealth` 改名；B3 网络变化（WiFi onAvailable）二次探测入口；`domain-health-check` 线程 loop-drain 不堆积。
- `enter_fallback: <host> reason=proactive-domain-switch` —— ⑦/review-②：DNS 级切换时对**收窄 host** 提前置 FALLBACK。**仅 target 是 HTTPDNS 收窄 host（hosts=[t.debox.pro]）才触发**；target=debox.pro 这类非收窄 host 正确 no-op。
- `HttpDnsRemoteConfig(..., hosts=[...], hostsConfiguredButEmpty=<bool>, ...)` —— review-①：配了非空 hosts 但归一化后全非法 → `hostsConfiguredButEmpty=true` → `isEffectiveHost` 安全失败（不放大作用域）。
- `fetchOssDomains: OSS 下载失败/内容为空/hosts 解析为空，沿用 SP/内置兜底` —— ⑥：三类 OSS 失败各自落痕。

### ⚠️ 取证陷阱
- **`fetchOssDomains: OSS域名池更新成功, domainList=[...], currentDomain=X` 里的 domainList 打的是 OSS 原始 hosts，不含 `ensureFallbackDomains` 并入的内置兜底 `[debox.pro, dbxsocial.com]`**。真实池 = OSS hosts ∪ 兜底。故会出现 `domainList=[t.debox.pro, t.dbxsocial.com], currentDomain=debox.pro` 这种"看似出池"的误导日志——debox.pro 是兜底域名、实际在池，**不是 OSS 纠偏失效**。判断 currentDomain 是否真出池要算上兜底。
- **dev 包 Android Studio Apply Changes 热更**：installed `versionName`/`lastUpdateTime` 可能不变，但代码已是工作区最新。判断设备是否跑新代码**别只看版本号**，看启动日志里的签名字段（如 `hostsConfiguredButEmpty=` 出现 = 含 review-① 新代码）。

### 当前测试环境域名拓扑（conf_test.json）
- OSS `domainList=[t.debox.pro, t.dbxsocial.com]` + 兜底 `[debox.pro, dbxsocial.com]`；`currentDomain` 启动多为 `t.dbxsocial.com`/`debox.pro`（随上次持久化）。
- HTTPDNS 收窄 `hosts=[t.debox.pro]`、`abnormalTtlMs=600000`、`enabled=true`，密钥已注入（`doInit: HTTPDNS SDK 初始化完成`）。
- EMAS 仍未托管 t.debox.pro → FALLBACK 后持续 `httpdns_empty_fallback_system: t.debox.pro`（空结果降级正确，happy 路径待控制台配置）。
