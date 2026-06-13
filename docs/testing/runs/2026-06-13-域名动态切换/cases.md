# 域名动态切换优化 — 测试用例

> 被测分支：debox-android `feat/fix-domain-switch`（工作区改动：`DomainManager.kt` +86 行，基线 `15b9d26a`）
> 改动核心：① 内置兜底域名池 ② 启动期 TCP 健康探测自愈
> 关联既有机制（同分支前序提交）：连通性故障切换（DNS/TCP/TLS/连接超时）+ 滑动窗口阈值 + 冷却期 + 故障计数启动衰减
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 域名动态切换（故障自愈 / 启动健康探测 / 兜底域名池） |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | A 层 JVM 单测（自动）+ B 层单机真机（mobile-mcp / adb logcat） |
| 设备 | 三星 SM-S9210（`RFCYA0F9SSZ`），装本分支 DEBUG 构建 2.12.3（6-13 19:55 更新，日志全开） |
| 前置条件 | 设备已解锁、已登录、正式环境、wifi 在线 |

---

## 改动逻辑分析（测试依据）

### 域名切换全链路

```
启动 MainBusinessModel → AppConfigManager.init → DomainManager.init
  ├─ restoreFromCache()          读 SP: domain_list / domain_current / domain_failures(衰减减半)
  │    └─【新增】domainList 为空 → 注入兜底池 [debox.pro, dbxsocial.com]
  ├─【新增】checkCurrentDomainHealth()  后台线程 TCP 探测 currentDomain:443
  │    ├─ 可达 → 无操作
  │    ├─ 不可达 + 池内有可达备选 → 切过去 + saveCurrentDomain + clear窗口 + resetUrl
  │    └─ 不可达 + 全部不可达 → remove(domain_current) + 回退 HttpConstant.HOST + resetUrl
  └─ fetchOssDomains()           OSS conf 拉 hosts[] → 覆盖 domainList → currentDomain 不在池则取 first

运行期每个请求经 DomainSwitchInterceptor：
  ├─ 成功(任意HTTP码) + 受管host → onRequestSuccess(host)
  │    └─ 仅当 host==当前域名：清窗口 + 当前域名故障计数 -1
  └─ IOException + 连通性故障 + 受管host → onConnectivityFailure(host, path)
       连通性故障口径 = UnknownHostException / ConnectException / NoRouteToHostException
                       / SSLException / SocketTimeoutException(仅 connect 阶段)
       └─ 累计故障计数++ → 滑动窗口(10s)按 path 去重计数
          ├─ 窗口内不同 path < 3 → 不切
          ├─ 距上次切换 < 30s 冷却 → 不切
          └─ 达阈值且过冷却 → selectBestDomain(池内非当前、故障计数最小) → 切 + resetUrl
```

### 受管 host 判定
当前域名 / OSS 域名池成员 / 内置兜底 `debox.pro`、`t.debox.pro`。第三方节点（EvmService 等）故障**不**污染主域名切换。

### 关键不变量
1. `domainList` 永不为空（兜底注入保证 selectBestDomain 始终有候选）
2. 健康探测在后台线程，不阻塞启动；探测耗时上限 2500ms/域名
3. 探测/切换期间若 currentDomain 被 OSS 或请求级切换改写 → 放弃本次回退（防互相覆盖）
4. `onRequestSuccess` 仅对「当前域名」生效（完整 URL 命中池内其他域名的成功不能误清当前域名失败窗口）
5. `resetUrl()` 始终在锁外调用（防与 RetrofitFactory 重建死锁）

---

## A 层：JVM 单测（全自动，无需设备）

> 落点：`business/BaseModule/src/test/.../DomainManagerTest.kt`、
> `business/BaseBusiness/src/test/.../HttpConstantTest.kt`、
> `business/BaseBusiness/src/test/.../DomainSwitchInterceptorTest.kt`
> 说明：兜底注入 / 健康探测 / 阈值衰减等**实例逻辑**依赖 `PreferencesUtils`（加密 SP，需 Android Context）
> 且模块未引入 Robolectric，**无法在纯 JVM 单测**，下沉到 B 层 logcat 取证。

| # | 用例 | 验证标准 | 覆盖点 | 优先级 |
|---|------|----------|--------|--------|
| UT-01 | parseHostsJson 正常解析 hosts 数组 | `[debox.pro, dbxsocial.com]` | OSS 配置解析 | P0 |
| UT-02 | parseHostsJson 过滤空白+去重 | `[debox.pro]` | 域名池来源健壮性 | P1 |
| UT-03 | parseHostsJson 非法 JSON 返回空 | `[]` | 异常不崩溃 | P1 |
| UT-04 | parseHostsJson 缺 hosts 键返回空 | `[]` | 缺字段降级 | P1 |
| UT-05 | DNS 失败(受管host) 上报精确 path | 上报 host=debox.pro, path=debox/feature/publish | 切换触发口径 | P0 |
| UT-06 | ConnectException(受管host) 触发上报 | failureCount=1, host=dbxsocial.com | TCP 连接失败触发 | P0 |
| UT-07 | SSLHandshakeException 触发上报 | failureCount=1 | TLS 握手失败触发 | P0 |
| UT-08 | connect 超时上报 / read 超时不上报 | connect→1，read→仍 1 | 超时阶段区分 | P0 |
| UT-09 | 第三方 host 故障不上报 | failureCount=0 | 切换决策不被污染 | P0 |
| UT-10 | 成功(受管host) 回报 onRequestSuccess | successCount=1, host 正确 | 成功衰减入口 | P0 |
| UT-11 | 成功(第三方host) 不回报 | successCount=0 | 衰减口径正确 | P1 |
| UT-12 | 无 accessor 时异常仍透传不崩溃 | 抛出原异常 | 桥未就绪健壮性 | P1 |
| UT-13 | 常量一致性 BASE_URL/TEST_BASE_URL | 与定义一致 | URL 基线 | P1 |
| UT-14 | Accessor.currentBaseUrl 正确 | `https://dbxsocial.com/debox/` | 当前域名→URL | P0 |
| UT-15 | resolveSelectedUrl 忽略 BASE_URL 缓存回退托管域名 | 返回托管域名 url | 域名切换生效路径 | P0 |
| UT-16 | resolveSelectedUrl 保留手动 override 缓存 | 返回 TEST_BASE_URL | 开发环境切换不被覆盖 | P1 |

---

## B 层：单机真机（三星 RFCYA0F9SSZ，AI 自主执行，adb logcat 取证）

> 取证命令：`adb -s RFCYA0F9SSZ logcat -d | grep -E "DomainManager|domain-health|isReachable|域名"`
> 断网手段：`adb -s RFCYA0F9SSZ shell svc wifi disable` / `svc data disable`（可逆，不破坏登录态/环境）

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-S-001 | App 冷启动冒烟 | terminate → launch → 进首页 | 无 crash，进首页，登录态正常 | P0 |
| TC-F-001 | 启动健康探测·可达分支 | 网络正常冷启动，抓 logcat | 见 `checkCurrentDomainHealth: 当前域名 X 可达，无需处理`，不发生切换 | P0 |
| TC-F-002 | 健康探测后台线程不阻塞启动 | 冷启动抓线程号 | 探测日志 tid ≠ 主线程 tid（独立 `domain-health-check` 线程）；UI 正常出现 | P0 |
| TC-F-003 | 缓存恢复域名池非空（兜底不变量） | 冷启动抓 logcat | `restoreFromCache: 缓存域名池=[...]` 非空；currentDomain 在池内 | P0 |
| TC-F-004 | OSS 拉取 + sysConfig 合并 | 冷启动抓 logcat | `fetchOssDomains: OSS域名池更新成功`；`mergeDomains: 新增额外域名 [...]` | P1 |
| TC-F-005 | 正常使用无误切换（负向） | 首页/会话间正常操作 30s | 无 `onConnectivityFailure`、无 `域名切换` 日志；请求 code 正常 | P0 |
| TC-T-001 | 断网→连通性故障上报+窗口/阈值/冷却 | 运行中 `svc wifi disable`+`svc data disable`，触发多接口请求（下拉刷新×N） | 见 `onConnectivityFailure: ... 累计故障` 累加；窗口内不同 path 计数；未达阈值不切；达阈值且过冷却才切（日志可见判定） | P0 |
| TC-T-002 | 断网启动→健康探测回退主域名分支 | 先断网 → 冷启动 → 抓 logcat | 见 `checkCurrentDomainHealth: X 不可达` 且（无可达备选时）`清除固化的当前域名并回退主域名`；不 crash | P1 |
| TC-T-003 | 恢复网络后自愈 | `svc wifi enable`+`svc data enable` → 触发请求 | 请求恢复成功；`onRequestSuccess` 衰减故障计数；App 可正常用 | P0 |
| TC-T-004 | 探测不可达域名超时上限 | TC-T-002 中观察探测耗时 | 单域名探测 ≤ ~2.5s，不长时间卡启动 | P2 |

---

## 统计

| 分类 | 用例数 | P0 | P1 | P2 |
|------|--------|----|----|----|
| A 层 JVM 单测 | 16 | 8 | 8 | 0 |
| B 层真机功能 | 6 | 5 | 1 | 0 |
| B 层真机边界 | 4 | 2 | 1 | 1 |
| **合计** | **26** | **15** | **10** | **1** |

---

## 已知不可黑盒覆盖项（诚实标注）

| 场景 | 原因 | 替代验证 |
|---|---|---|
| 新装首启注入兜底池（cache 空路径） | 触发需 `clear data`，违反铁律（丢登录态+切正式环境） | 代码分析 + 不变量 TC-F-003（池永不空） |
| 健康探测「切到可达备选」分支（一坏一好） | 需网络层只封堵 debox.pro 保留 dbxsocial.com，非 root 真机不可选择性封堵 | 代码分析 + UT-14/UT-15 + 断网全坏的回退分支 TC-T-002 |
| 请求级阈值真实切换到「可用」新域名 | 同上，断网时所有域名均不可达 | 故障上报+窗口/阈值/冷却判定 TC-T-001（切换决策链已验）+ A 层口径单测 |
