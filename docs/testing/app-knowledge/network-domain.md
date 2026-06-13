# 网络 / 域名动态切换（DeBox）

> AI 探索沉淀，供网络类特性测试 session 先读。来源：2026-06-13 域名动态切换测试。

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
