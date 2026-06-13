# 阿里云 HTTPDNS 异常兜底接入 — 测试结果

> 用例见同目录 `cases.md`；被测分支 **debox** 仓库 `feat/ali-dns`（实现提交 `926b14ed55`）

---

## 第 1 轮（首次测试）

> 测试日期：2026-06-12 | 测试环境：macOS JVM（B 层）+ 静态核对（A 层）
> 触发原因：首次测试（实现提交当日）
> 范围说明：C 层（MockWebServer 集成）当前提交未含实现，待补；D 层真机被
> 两个前置卡住——①两台设备均安全锁屏需人工解锁，②密钥未注入（HTTPDNS 禁用态，
> 设计内），本轮标 ⏸️。

### 框架前置检查（3/3 PASS）

| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-P-001 | 框架编译 | ✅ PASS | BUILD SUCCESSFUL |
| TC-P-002 | 单元测试 | ✅ PASS | 0 failures |
| TC-P-003 | 发布 mavenLocal | ✅ PASS | `~/.m2/repository/com/autotest` 生成 |

### A 层 静态核对（8/9 PASS，1 ⏸️）

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| PRE-01 | Maven 镜像含 SDK 2.6.9 | ✅ PASS | public 镜像 metadata 含 `<version>2.6.9</version>`，与 config.gradle 声明一致（releases 仓库亦 200，无需补仓） |
| PRE-02 | minSdk=25 / targetSdk=35 | ✅ PASS | config.gradle:16-17 |
| PRE-03 | OkHttp 4.12.0 | ✅ PASS | config.gradle:77（§4.4 ThreadLocal 前提成立） |
| PRE-04 | RetrofitFactory `.dns()` 挂接 | ✅ PASS | RetrofitFactory.kt:81 `.dns(AliHttpDnsDns())` |
| PRE-05 | 混淆 keep 规则 | ✅ PASS | BaseBusiness/proguard-rules.pro `-keep class com.alibaba.sdk.android.httpdns.**`（含 AAR 不带 consumer rules 的 why 注释） |
| PRE-06 | DomainManager 三处挂接 | ✅ PASS | DomainManager.kt:83 bootstrap / :98 updateFromOssContent / :114 updateDomainPool |
| PRE-07 | 拦截器同口径双驱动 | ✅ PASS | DomainSwitchInterceptor.kt:46 proceed 前清 ResolutionContext；:52-53 成功、:63-64 失败回调均经 `AliHttpDnsManager.isManagedHost`（不依赖 accessor） |
| PRE-08 | 密钥缺省禁用 | ✅ PASS | local.properties 无 HTTPDNS 密钥 → BuildConfig 空串 → `hasCredentials()=false`；UT-33/40 单测同步验证禁用路径 |
| PRE-09 | 真机基线 | ⏸️ 跳过 | 两台设备安全锁屏（小米 AOD 指纹 / 三星 Bouncer），adb 无法绕过，需人工解锁后补测 |

### B 层 JVM 单测（50/50 PASS，0 失败 0 跳过）

```bash
./gradlew :business:BaseBusiness:testDebugUnitTest \
  --tests "com.app.base.business.network.httpdns.*" -Dorg.gradle.configureondemand=false
```

| 测试类 | 用例数 | 结果 | 覆盖 |
|---|---|------|------|
| `AliHttpDnsDnsTest` | 8 | ✅ 全 PASS | Dns 适配层：直通/降级/append 容错/完整候选（UT-01~08） |
| `HttpDnsFallbackPolicyTest` | 19 | ✅ 全 PASS | 三态状态机：续期/单飞/租约/generation 竞态/修正⑬⑮（UT-09~23） |
| `HttpDnsRemoteConfigTest` | 10 | ✅ 全 PASS | 配置解析/旧格式兼容/灰度分桶/修正⑭（UT-24~28） |
| `AliHttpDnsManagerTest` | 7 | ✅ 全 PASS | **本轮补缺**：受管集合/未初始化降级/密钥禁用/PENDING_INIT（UT-29~35） |
| `HttpDnsConfigTest` | 6 | ✅ 全 PASS | **本轮补缺**：hosts 收窄语义/兜底默认/错误隔离（UT-36~41） |

**覆盖缺口分析（本轮补缺的依据）**：实现提交自带的 37 测试对 `AliHttpDnsManager`
（210 行）与 `HttpDnsConfig`（111 行）无专属覆盖——受管 host 集合（§6.3 修复④的
落点）、生效域名收窄语义（§4.2）、密钥缺失禁用（§10）、PENDING_INIT 挂起（§6.1）
均为方案 §14 点名条目。本轮新增 13 个单测补齐，全部一次通过（实现正确，纯覆盖补充，
非 Bug 修复）。新增测试文件已放入 debox 仓库：
`business/BaseBusiness/src/test/.../httpdns/AliHttpDnsManagerTest.kt` + `HttpDnsConfigTest.kt`
（**未提交**，等用户确认后随分支提交）。

### C 层 集成测试（7 条 ⏸️ 待实现）

当前提交未含 MockWebServer 集成测试。其中 IT-04（坏 IP+好 IP 轮换边界）方案明确要求
"OkHttp 4.12.0 实测、不依赖文档表述"（§7），建议联调前补上。

### D 层 真机验证（6 条 ⏸️）

| 阻塞项 | 影响用例 | 解除条件 |
|---|---|---|
| 设备安全锁屏 | 全部 | 人工解锁一次（解锁后 AI 可接管） |
| 分支构建未安装 | 全部 | `feat/ali-dns` 构建 + 双机安装 + dex 验真 |
| 密钥/控制台/OSS 段未配置（P0 外部项） | MV-02~05 | 按方案 §17-A/C 完成外部配置 |

MV-01（禁用态基线）只需前两项解除即可执行。

**本轮统计**：PASS 61（框架 3 + A 层 8 + B 层 50）/ FAIL 0 / ⏸️ 14（PRE-09 + C 层 7 + D 层 6）

---

## 第 2 轮（真机：禁用态全路径验证）

> 测试日期：2026-06-12 21:40–21:45 | 测试环境：三星 SM-S9210（RFCYA0F9SSZ）+ t.debox.pro 测试环境
> 被测包：用户从 Android Studio 安装的 `feat/ali-dns` 构建（2.12.3，21:40:40 安装）
> 触发原因：设备就绪（用户解锁并装包）；范围 = D 层禁用态可执行子集（MV-02~05 仍待密钥等 P0 外部项）

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 装包验真 | dex 字符串验真（ENV-001 教训） | ✅ PASS | `strings classes*.dex`：`AliHttpDnsDns` 18 处命中、`HttpDnsFallbackPolicy` 存在 |
| PRE-09 | 真机基线：正常网络系统 DNS 路径 | ✅ PASS | 启动进 MainActivity，首页消息列表数据正常；**全部请求已经过 `AliHttpDnsDns` 直通路径**（RetrofitFactory 已挂接），零回归 |
| MV-01 | 禁用态基线 | ✅ PASS | logcat 仅 1 条 httpdns 相关：`HttpDnsConfig: updateFromOssContent: httpdns 段缺失或损坏，沿用缓存/默认值`（OSS 挂接活着 + 错误隔离生效）；无任何 SDK init 日志；`ss -tn` 0 个到 HTTPDNS 调度 IP（203.107.1.x）的连接 |
| MV-01b | 禁用态 + DNS 异常不触发 HTTPDNS | ✅ PASS | 坏私有 DNS 注入后重启：103 处 `UnknownHostException`，但 0 条 `enter_fallback`、0 条 SDK init——开关关闭时异常路径与现状完全一致（与 UT-10/UT-33 吻合） |
| 容灾协同 | 现有 DomainManager 不受影响 | ✅ PASS | 坏 DNS 期间 `onConnectivityFailure` 故障计数照常累计（累计=1/2，窗口阈值判断正常）；SP 缓存池 `[t.debox.pro, t.dbxsocial.com]` 恢复成功（即 bootstrap 喂给受管集合的池） |
| MV-06 | 测后环境复原 | ✅ PASS | `private_dns_mode/specifier` 已删除（回系统默认 null）；重启后 0 个 UnknownHostException，首页正常 |
| MV-02~05 | 开关打开链路 / EMAS 验证 | ⏸️ 跳过 | 待 P0 外部项：密钥注入 local.properties + 控制台域名配置 + OSS conf_test httpdns 段 |

**本轮统计**：PASS 6 / FAIL 0 / ⏸️ 4（MV-02~05）

**结论**：禁用态（密钥未注入）下 HTTPDNS 链路全程静默、零回归，符合提交说明
"全路径行为与现状一致，可安全合入"。剩余验证依赖 §17-A/C 外部配置就绪后跑
MV-02~05（开关打开的兜底实链路）。

---

## 第 3 轮（真机：OSS httpdns 段模拟 + 开关打开实链路）

> 测试日期：2026-06-12 21:50–22:08 | 设备：三星 SM-S9210 | 被测包：22:05 本地构建
> 前置变化：① 用户已注入密钥三件套到 `local.properties`；② 用户指定模拟 OSS 数据
> `httpdns:{enabled:true, gray_percent:100, hosts:[], abnormal_ttl_ms:600000, append_system_dns:true, enable_https:true, enable_aes:false}`
> 模拟方式：临时把 `DomainManager.ossUrl`（仅测试环境分支）指向 `http://127.0.0.1:8000`
> + `adb reverse tcp:8000`（127.0.0.1 在明文白名单内）+ Mac 本地 HTTP 服务 serve
> "真实 conf_test.json hosts + 用户给定 httpdns 段"。`HTTPDNS_DEBUG_FORCE=false`
> 确保激活完全由远程开关驱动（不走联调强制通道）。

### JVM 回归（密钥注入后）

50 测试 / 0 失败 / 4 跳过（assume 守卫：密钥+force 环境下合理跳过）。
修正 1 处测试自身环境依赖：`never fetched config defaults to switch off` 的
switchOn 断言对"联调强制开关已开"加 assume 隔离（测试问题，非实现问题）。

### 用例结果

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 配置链路 | 模拟 OSS 段逐字段解析正确 | ✅ PASS | `updateFromOssContent: HttpDnsRemoteConfig(enabled=true, grayPercent=100, hosts=[], abnormalTtlMs=600000, appendSystemDns=true, enableHttps=true, enableAes=false)`——与用户给定数据逐字段一致 |
| MV-02 | 远程开关驱动懒初始化；网络正常仍系统 DNS | ✅ PASS | `doInit: HTTPDNS SDK 初始化完成`（拉取后 12ms，debugForce=false 下纯远程驱动）；正常浏览 0 条 fallback/解析日志 |
| §9 缓存兜底 | SP 缓存开关值驱动启动期 init | ✅ PASS | 第二次冷启动 `doInit`（22:06:35.287）早于 OSS 拉取回调（35.661）——开关缓存兜底链实际生效 |
| MV-03 | DNS 异常 → 进 FALLBACK，后续请求走 HTTPDNS | ✅ PASS（客户端侧） | `enter_fallback: t.debox.pro reason=UnknownHostException` → 后续请求全部进入 HTTPDNS 查询路径 |
| MV-03b | HTTPDNS 空结果降级 + 埋点观测（§8.1 控制台漏配场景） | ⚠️ 部分通过 | SDK 与解析服务保持 HTTPS 直连（47.57.77.232/47.56.119.115，坏 DNS 下 IP 调度正常）但持续空结果 → `httpdns_empty_fallback_system` 埋点连续打出 + 安全降级系统 DNS。**根因：EMAS 控制台未添加 `t.debox.pro`（P0 外部项），非客户端缺陷**——客户端对漏配场景的降级与可观测兜底完全符合 §8.1 设计 |
| §11 协作 | 两道自愈协作：HTTPDNS 降级后域名切换接管 | ✅ PASS | `onConnectivityFailure: 域名切换 t.dbxsocial.com -> t.debox.pro` + 冷却期判定（15ms < 30000ms 拒绝二次切换） |
| MV-06 | 环境复原 | ✅ PASS | private_dns 删除恢复默认；重启 0 UnknownHostException；首页正常 |
| MV-04/05 | EMAS 错误 IP 演练 / 控制台解析记录核对 | ⏸️ 跳过 | 依赖控制台域名添加（同 MV-03b 根因） |

**本轮统计**：PASS 6 / ⚠️ 1 / ⏸️ 2

**结论**：用户给定的 OSS `httpdns` 段经真实链路（下载→解析→持久化→远程开关激活→
异常触发→HTTPDNS 查询→降级→容灾接管）全链路验证，客户端行为全部符合方案。
唯一卡点是 **EMAS 控制台未添加测试域名**（方案 §17-A.2 的 P0 外部项）：添加
`t.debox.pro` / `t.dbxsocial.com` 后重跑 MV-03 预期可见"坏系统 DNS 下请求仍成功"，
并解锁 MV-04/05。

### 联调现场状态（待清理项，重要）

| 项 | 状态 | 还原方式 |
|---|---|---|
| `DomainManager.ossUrl` 指向 `127.0.0.1:8000`（带 TODO 注释） | 未提交临时改动 | `git checkout -- business/BaseModule/.../DomainManager.kt`，还原后需重新构建安装 |
| Mac 本地 HTTP 服务（:8000，serve `/tmp/oss-sim`） | 运行中 | 三星上当前包依赖它拉配置（SP 已有缓存，停掉只影响热更新）；`pkill -f "http.server 8000"` |
| `adb reverse tcp:8000` | 生效中 | adb 重启自动失效 |
| `local.properties`：`HTTPDNS_DEBUG_FORCE=false` | 新增行 | 联调若需强制开关可改 true（仅 debug 包生效） |
| 三星上的联调专用包（22:05） | 已安装 | 测完后装回正常构建 |

---

## 第 4 轮（真机：控制台域名生效后兜底实链路 + 真实 OSS 配置验真）

> 测试日期：2026-06-13 10:30–10:40 | 设备：三星 SM-S9210 | 被测包：6-12 22:05 联调包
> 触发原因：用户反馈"OSS 配好了、EMAS 控制台域名全加好了"，重跑 MV-03 兜底实链路
> 并核对真实 OSS 生产配置。

### 用例结果

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| MV-03 | **坏 DNS 下 HTTPDNS 兜底——业务请求真正成功** | ✅ PASS | private_dns 指向 invalid host（系统 DNS 全坏），`enter_fallback: t.debox.pro` 后 **3 个 `https://t.debox.pro/debox/...` 请求 code：200 成功**（dao_favorite_list 耗时 113ms / batch_user_info / session_list）。系统 DNS 全坏仍能成功，只可能是 HTTPDNS 拿到真实 IP——控制台域名已生效，兜底闭环打通 |
| MV-03 边界 | HTTP 链路覆盖、IM 长连接不覆盖（§13-P3 范围外） | ✅ 符合设计 | 坏 DNS 期 HTTP 业务请求 200，但顶部 IM 显示"连接中"（WebSocket 不走 RetrofitFactory，HTTPDNS 不接管）——与方案"首期只覆盖 RetrofitFactory HTTP 链路"一致 |
| 无震荡（§4.4 核心） | DNS 恢复后请求成功无周期性失败 | ✅ PASS | 恢复 DNS 后切 tab 触发 7 个 t.debox.pro 请求，3×200，0 enter_fallback 复发、0 httpdns_empty——无"成功→清标记→回坏 DNS→失败"震荡 |
| PROBE 回切 | TTL 到期试探系统 DNS 回 NORMAL | ⚪ 现场未触发（设计预期） | 缩短 TTL=15s 后：DNS 恢复 + TTL 过期，后续请求**复用 FALLBACK 期 HTTPDNS 建立的连接池连接**（OkHttp keep-alive），未触发 `lookup`→`decide`，故无 `probe_success` 日志。正是 §4.4"连接复用来源未知不变更状态"的设计；PROBE 状态转移由 JVM 单测 UT-13/14/15 覆盖，真机难在 keep-alive 窗口内快速复现 |
| MV-06 | 环境复原 | ✅ PASS | private_dns 删除恢复默认 |

### 真实 OSS 生产配置验真（发现 BUG-001）

拉取线上 `conf_test.json` 与实现期望格式逐字段比对，本地服务回放该真实内容到 App，
确证 **HTTPDNS 配置格式契约不匹配**——详见 BUG-001。

**本轮统计**：PASS 4 / ⚪ 1（PROBE 设计预期）/ 🔴 1 Critical（BUG-001）

---

## 第 5 轮（BUG-001 代码修复 + 端到端回归）

> 测试日期：2026-06-13 10:40–10:50 | 设备：三星 SM-S9210 | 被测包：含修复的本地构建（10:48）
> 触发原因：用户指示"代码有问题的也要修复代码"——采用方案 B 改实现兼容扁平格式。

### 代码改动（debox 仓库，未提交）

| 文件 | 改动 |
|---|---|
| `HttpDnsRemoteConfig.kt` | `parseSection` 增加扁平 `httpdns_*` 回退 + `extractFlatSection` + `FLAT_FIELD_MAPPING`（嵌套优先、标准化持久化） |
| `HttpDnsRemoteConfigTest.kt` | +3 单测：扁平解析 / 嵌套优先 / 标准化持久化 |
| `docs/aliyun-httpdns-integration-plan.md` | §10 修订：两种写法等价说明 + 段↔扁平字段对照表 + TTL 毫秒/AES 运维提示；顶部加实施期修正⑯ |

### 回归结果

| # | 验证 | 结果 | 证据 |
|---|------|------|------|
| JVM 套件 | 5 个测试类全回归 | ✅ 53/53 PASS | AliHttpDnsDns 8 + FallbackPolicy 19 + RemoteConfig 13 + Manager 7 + Config 6 |
| dex 验真 | 修复进包 | ✅ PASS | `strings classes*.dex` 含 `extractFlatSection` / `httpdns_enabled` |
| FIX-01 | **修复后识别真实 OSS 扁平配置**（直连真实 OSS，非本地模拟） | ✅ PASS | `updateFromOssContent: HttpDnsRemoteConfig(enabled=true, grayPercent=100, hosts=[t.debox.pro], abnormalTtlMs=600, appendSystemDns=true, enableHttps=true, enableAes=true)`——对比修复前"段缺失或损坏" |
| FIX-02 | 修复后坏 DNS 兜底（真实扁平配置 + 收窄 hosts=[t.debox.pro]） | ✅ PASS | `enter_fallback: t.debox.pro` + 7 个 `code：200`（系统 DNS 全坏仍成功 = HTTPDNS 兜底） |
| FIX-03 | ttl=600 实际影响观测（强化运维提醒） | ⚠️ 观察 | abnormalTtlMs=600（0.6s）下 FALLBACK 窗口几乎瞬间过期、状态机频繁翻转；兜底仍生效（请求密集 + 重入 FALLBACK）但非理想，**建议 OSS 改 600000** |

**本轮统计**：PASS 4 / ⚠️ 1 观察（ttl=600，OSS 侧改）

**结论**：BUG-001 代码侧已修复并端到端验证——修复后直连**真实 OSS 的扁平配置**能正确
识别并激活 HTTPDNS，坏 DNS 下业务请求 200 成功。剩 2 个 OSS 配置值问题（ttl=600→600000、
enable_aes 计费确认）属运维侧，不阻断代码。

### 现场状态（已复原）

| 项 | 状态 |
|---|---|
| `DomainManager.ossUrl` 临时 hack | ✅ 已 `git checkout` 还原，指回真实 OSS |
| 本地 OSS 模拟服务（:8000）+ adb reverse | ✅ 已停止 / 已撤销 |
| 三星设备 private_dns | ✅ 已删除恢复系统默认 |
| 三星当前包 | 含 BUG-001 修复的联调包（直连真实 OSS），可作后续回归基线 |
| `local.properties` | 保留用户密钥三件套 + `HTTPDNS_DEBUG_FORCE=false`（默认值，无害） |
| debox 未提交改动 | `HttpDnsRemoteConfig.kt` + 测试 + 文档 + 2 个补缺测试类（待用户确认随分支提交） |

---

## 第 6 轮（修正配置验证：ttl 600→600000 + aes 测试）

> 测试日期：2026-06-13 10:55–11:00 | 设备：三星 SM-S9210 | 被测包：含修正配置的本地构建（10:58）
> 触发原因：用户指示"调整 ttl（第 1 点），aes（第 2 点）测试用"。线上 OSS 无写权限，
> 用本地模拟 serve 修正后的真实格式配置：扁平格式 + `httpdns_abnormal_ttl_ms=600000`
> （第 1 点修正）+ `httpdns_enable_aes=true`（第 2 点，测 AES 加密通道）。

### 用例结果

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| TTL-01 | 修正配置解析正确 | ✅ PASS | `updateFromOssContent: HttpDnsRemoteConfig(enabled=true, grayPercent=100, hosts=[t.debox.pro], abnormalTtlMs=600000, appendSystemDns=true, enableHttps=true, enableAes=true)`——ttl 已是 600000 |
| TTL-02 | **ttl=600000 下 FALLBACK 稳定（对比 ttl=600 翻转）** | ✅ PASS | 坏 DNS 持续 25s：`enter_fallback` **仅 1 次**（单次进入稳定保持）、`probe` **0 次**（未到 10min TTL，无翻转）——对比第 5 轮 ttl=600 的频繁翻转，修正效果明确 |
| TTL-03 | AES 加密通道兜底成功 | ✅ PASS | `enableAes=true` + `doInit` 成功；坏 DNS 下 **19 个 `code：200`**（AES 加密解析通道工作，业务请求成功） |

**ttl=600 vs 600000 对比**：

| 指标 | ttl=600（第 5 轮） | ttl=600000（本轮修正） |
|---|---|---|
| enter_fallback | 频繁翻转 | 1 次稳定保持 |
| probe 翻转 | 频繁 | 0 次 |
| 业务 200 | 7 | 19 |

**本轮统计**：PASS 3 / FAIL 0

**结论**：第 1 点修正（ttl=600000）端到端验证——FALLBACK 单次进入后稳定保持、无频繁
翻转，兜底高效。第 2 点（enable_aes=true）AES 加密通道正常工作。修正后的完整正确配置
（扁平格式 + ttl=600000 + aes=true）端到端打通。

### 现场最终复原

| 项 | 状态 |
|---|---|
| `DomainManager.ossUrl` 第二次临时 hack | ✅ 已 `git checkout` 还原指回真实 OSS |
| 本地 OSS 模拟服务 + reverse | ✅ 已停止 / 已撤销 |
| 三星 private_dns | ✅ 已删除恢复默认 |
| 三星当前包 | 重新构建的真实 OSS 包（11:00 构建装机），代码含 BUG-001 修复 |
| debox 未提交改动 | `HttpDnsRemoteConfig.kt`（修复）+ `HttpDnsRemoteConfigTest.kt`（+3）+ `AliHttpDnsManagerTest.kt` / `HttpDnsConfigTest.kt`（补缺）+ `docs/aliyun-httpdns-integration-plan.md`（§10 修订）|

> ⚠️ **线上 OSS 仍需运维改两处值**（代码已兼容扁平格式，但值在线上）：
> ① `httpdns_abnormal_ttl_ms: 600 → 600000`；② 确认 `httpdns_enable_aes: true` 的计费意图。
> 本轮在本地用 600000 验证了修正后的稳定行为；线上改值后即为最终生产态。

---

## 第 7 轮（正式环境 / 生产 OSS 真机验证）

> 测试日期：2026-06-13 21:15–21:18 | 设备：**小米 25067PYE3C（402714f0）** | 环境：**正式环境**
> 被测包：小米 21:11 构建 / 21:12 安装的含 BUG-001 修复包（dex 验真 `extractFlatSection` 命中 2）
> 触发原因：用户切到正式环境，要求在生产 OSS（`conf_v2.json`）上完整回归。
> 安全声明：钱包 App 生产环境，全程仅冷启动 + 浏览首页等**只读操作**触发网络请求；
> **不碰转账/签名/钱包/登出/切环境**（dangerous-ops 红线）；DNS 异常模拟可逆，测后恢复。

### 正式环境 OSS 配置（生产 conf_v2.json，扁平格式）

```json
{ "hosts": ["debox.pro", "dbxsocial.com"], "httpdns_enabled": true,
  "httpdns_hosts": ["debox.pro"], "httpdns_abnormal_ttl_ms": 600000,
  "httpdns_enable_https": true, "httpdns_enable_aes": false }
```
> 生产配置值已正确：**ttl=600000（不再是测试环境的 600）、aes=false**。运维已修正前几轮提示的值问题。

### 用例结果

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 不用编译 | 现有包即含修复 | ✅ | `apks/debox-debug.apk`(21:11) dex 验真 `extractFlatSection`×2；小米 21:12 已装该包；`isRelease()` 运行期判断（base URL），debug 包切正式环境即拉 conf_v2 |
| MV-01 | 正式环境基线 | ✅ PASS | 走 `conf_v2.json`（debox-oss）、域名池 `[debox.pro, dbxsocial.com]`、currentDomain=debox.pro、首页正常 |
| MV-02 | **正式扁平配置识别（BUG-001 修复生产验证）** | ✅ PASS | `updateFromOssContent: HttpDnsRemoteConfig(enabled=true, grayPercent=100, hosts=[debox.pro], abnormalTtlMs=600000, appendSystemDns=true, enableHttps=true, enableAes=false)`——与生产配置逐字段一致；`doInit` 成功。**没有 BUG-001 修复这里就是"段缺失"** |
| MV-03 | 正式坏 DNS 兜底 | ⚠️ 部分通过 | `enter_fallback: debox.pro` 触发正常、probe 翻转 0（ttl=600000 稳定）；但 **9 次 `httpdns_empty_fallback_system` + 0 个 200** → HTTPDNS 空结果安全降级。**根因：正式域名 debox.pro 未加入 EMAS 控制台**（见下方发现，非客户端缺陷） |
| MV-06 | 环境复原 | ✅ PASS | DNS 删除恢复默认、`UnknownHostException=0`、恢复后 **26 个 code:200**、首页正常 |

**本轮统计**：PASS 4 / ⚠️ 1（控制台漏配，外部项）

**结论**：BUG-001 修复在**生产环境验证通过**——生产 OSS 的扁平配置被正确识别激活。
客户端在正式环境的全部行为正确：配置识别 → 状态机触发 → 控制台漏配时安全降级 + 埋点 →
DNS 恢复后业务正常。**上线前唯一阻塞项是 EMAS 控制台未配生产域名**（运维侧）。

### 🔶 发现 FOUND-01：正式域名 debox.pro / dbxsocial.com 未加入 EMAS HTTPDNS 控制台

**性质**：外部配置缺失（非代码缺陷），上线前必办（方案 §17-A.2 + §8.1）。

**证据**：正式环境坏 DNS 下 `debox.pro` 进 FALLBACK 后，HTTPDNS 连续 9 次返回空结果
（`httpdns_empty_fallback_system`），对比测试环境 `t.debox.pro`（控制台已配）坏 DNS 下
19 个请求 200 成功。说明生产域名在 HTTPDNS 控制台无解析记录。

**影响**：若就此上线，正式环境 HTTPDNS 兜底**形同虚设**——真实用户遇 DNS 劫持时
HTTPDNS 拿不到 IP、降级回被污染的系统 DNS，且线上只表现为 `httpdns_empty_fallback_system`
埋点偏高（正是 §8.1 预警的隐蔽失效）。

**待办（运维侧）**：在阿里云 HTTPDNS 控制台为生产账号添加 `debox.pro`、`dbxsocial.com`
（及未来 OSS 池备用域名），再灰度放量。添加后重跑 MV-03 预期可见生产坏 DNS 下业务请求成功。

**✅ 复测验证（2026-06-13 21:28，运维已加生产域名后）**：重跑 MV-03，生产坏 DNS 下
`debox.pro` 进 FALLBACK 后 **8 个业务请求 200 成功**（check_token_channel / dapps /
trade/chains）——系统 DNS 全坏仍成功，证明 HTTPDNS 已能拿到 `debox.pro` 真实 IP。

| 指标 | 控制台加域名前（21:17） | 控制台加域名后（21:28） |
|---|---|---|
| 坏 DNS 下 code:200 | **0** | **8** ✅ |
| enter_fallback | 1 | 1 |
| probe 翻转 | 0 | 0 |

（剩余 10 次 `httpdns_empty` 集中在冷启动瞬间——预解析缓存未热的正常窗口 §6.4，
预解析完成后请求全部命中。）**FOUND-01 已解决，正式环境 HTTPDNS 兜底闭环打通。**
恢复 DNS 后正式环境验证：`UnknownHostException=0`、26 个 200、首页正常。

**状态**：🟢 已解决已验证

---

## Bug 记录

### BUG-001 — 真实 OSS 用扁平字段，实现读嵌套 `httpdns` 段，配置静默失效 🔴 Critical

**关联用例**：MV-02 / §10 配置解析 / §8.1 隐蔽失效

**状态**：🟢 已修复已验证（代码 + 单测 + 真机 + 文档，见第 5 轮）

**现象**：用户在线上 `conf_test.json` 配置的 HTTPDNS 段**完全不生效**，App 日志恒为
`updateFromOssContent: httpdns 段缺失或损坏，沿用缓存/默认值`。全新设备（无 SP 缓存）
拉到该配置 → HTTPDNS 永不激活，且线上仅表现为一条 debug 日志，**无任何报错**——正是
§8.1 担心的"最需要时静默失效、难发现"那类问题。

**根因分析**（基于证据）：

线上真实配置是**扁平格式**：
```json
{
  "hosts": ["t.debox.pro", "t.dbxsocial.com"],
  "httpdns_enabled": true,
  "httpdns_hosts": ["t.debox.pro"],
  "httpdns_abnormal_ttl_ms": 600,
  "httpdns_enable_https": true,
  "httpdns_enable_aes": true
}
```

但实现 `HttpDnsRemoteConfig.parseSection()` 读的是**嵌套 `httpdns` 对象**：
```kotlin
fun parseSection(content: String): JSONObject? =
    JSONObject.parseObject(content)?.getJSONObject("httpdns")  // 线上无 httpdns 对象 → null
```
`getJSONObject("httpdns")` 在扁平结构下返回 null → `parseSection` 返回 null →
`updateFromOssContent` 判定"段缺失"沿用缓存/默认（enabled=false）。

**实测对照**（本地服务回放两种格式到同一 App）：
- 扁平格式（线上真实内容）→ `httpdns 段缺失或损坏，沿用缓存/默认值` ❌
- 嵌套格式（方案 §10 规范）→ `HttpDnsRemoteConfig(enabled=true, grayPercent=100, ...)` ✅

误用来源：方案 §10 正文用 `httpdns_enabled`/`httpdns_hosts` 这些扁平名描述字段语义
（原文标注为"逻辑名，统一对应 `httpdns` 段内字段"），配置人员照正文扁平名直接配了 OSS，
漏掉了"实际 JSON 必须嵌套在 `httpdns` 对象内"。

**附带的配置值问题（即使格式改对仍需修）**：
- `httpdns_abnormal_ttl_ms: 600` —— 600ms 异常窗口，方案默认是 **600000ms（10 分钟）**，
  疑似漏 3 个 0。600ms 会让 FALLBACK 窗口几乎瞬间过期，频繁 PROBE 试探坏 DNS。
- `httpdns_enable_aes: true` —— 开了 AES 内容加密，需配套 `HTTPDNS_AES_SECRET_KEY`
  且影响计费（§16.2-4 待确认项）；确认是否有意开启。
- 缺 `gray_percent`（实现默认 100=不收窄，OK）、`append_system_dns`（默认 true，OK）。

**修复方案（已采用方案 B：改实现兼容扁平）**：

按用户指示"代码有问题的也要修复代码"，采用方案 B——`HttpDnsRemoteConfig.parseSection`
增加扁平 `httpdns_*` 字段回退解析，既支持方案 §10 规范的嵌套段，也兼容已配的扁平
OSS：

```kotlin
fun parseSection(content: String): JSONObject? = try {
    val root = JSONObject.parseObject(content) ?: return null
    root.getJSONObject("httpdns") ?: extractFlatSection(root)  // 嵌套优先，回退扁平
} catch (e: Exception) { null }

// extractFlatSection：把顶层 httpdns_* 字段按 FLAT_FIELD_MAPPING 改写为规范段
// 字段名，组装等价 JSONObject，复用 fromJson 与 SP 持久化（持久化即标准化段）
```

设计要点（防回归）：
- **嵌套段优先**：两种写法并存时规范嵌套段权威（单测 `nested ... takes precedence`）
- **标准化持久化**：扁平抽取后用规范字段名，SP 缓存与下次 `fromJson` 字段名一致
- **纯旧格式不误判**：`{"hosts":[...]}` 无 httpdns_ 字段仍返回 null（段缺失语义不变）
- 改动隔离在 `parseSection`，未触碰嵌套/损坏 JSON 现有分支

**附带配置值问题（代码兼容了格式，但值仍需 OSS 侧修正）**：
- `httpdns_abnormal_ttl_ms: 600` —— 600ms 太短，**建议改 600000**（漏 3 个 0）。
  代码不擅自纠正（远程可调是设计），仅提醒。
- `httpdns_enable_aes: true` —— 需配 `HTTPDNS_AES_SECRET_KEY` + 确认计费意图。

**检查清单**：
```
- [x] 根因基于证据验证（双格式实测对照）
- [x] 修复代码：parseSection 兼容扁平 httpdns_* 字段
- [x] 补单测 3 个（扁平解析 / 嵌套优先 / 标准化持久化），JVM 套件 53 全绿
- [x] 真机回归：还原 ossUrl 指回真实 OSS，验证真实扁平配置激活（见第 5 轮）
- [ ] OSS 侧修正 abnormal_ttl_ms 量级 + 确认 enable_aes 计费（运维侧，不阻断代码）
```

**验证结果**：见第 5 轮。

---

### 环境注意事项留痕（非 Bug）：

### ENV-A — 单模块 gradle 构建需关 configure-on-demand

`gradle.properties` 里 `org.gradle.configureondemand=true` 时单独构建子模块，
`react-native-screens` 配置期找不到 app 工程回退 node 解析并失败（与 RTC转CDN
轮次 BUG-001 同根因，debox / debox-android 两仓库同病）。所有单测命令必须带
`-Dorg.gradle.configureondemand=false`。

### ENV-B — 两台测试机均处于安全锁屏

小米 AOD 指纹、三星 Bouncer，`wm dismiss-keyguard` 无法绕过（安全键盘锁设计如此，
不属于可自动化项）。真机轮次开始前需人工解锁一次。

---

## 结果状态说明

| 标记 | 含义 |
|------|------|
| ✅ PASS | 验证符合预期 |
| ❌ FAIL | 不符合预期，需要修复 |
| ⚠️ 部分通过 | 核心正确但有优化空间（不阻断） |
| ⏸️ 跳过 | 被前置条件阻塞 |
