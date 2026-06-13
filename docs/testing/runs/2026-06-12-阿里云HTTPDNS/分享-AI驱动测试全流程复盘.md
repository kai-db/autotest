# AI 驱动测试全流程复盘：阿里云 HTTPDNS 异常兜底

> 分享主题：一次"从代码改动到发现 Critical 缺陷再到修复验证"的 AI 驱动真机测试完整实录
> 被测对象：debox `feat/ali-dns` 分支（阿里云 HTTPDNS 异常兜底接入）
> 测试方式：Claude Code + MCP（AI 自主驱动）+ 真机（三星 SM-S9210）
> 时间：2026-06-12 ~ 06-13
> 一句话结论：**测出一个会让 HTTPDNS 整个功能静默失效的线上配置缺陷（BUG-001），并完成代码修复 + 端到端验证 + 方案文档修订。**

---

## 0. 这次分享想讲清楚的事

不是"跑了多少用例、过了几条"，而是 **AI 驱动测试是怎么一步步逼出一个隐蔽 Critical 缺陷的**：

1. 用例不是拍脑袋写的——从**代码改动 + 方案文档**机械映射出来；
2. 测试分层推进——便宜的先跑（静态/单测），贵的后跑（真机），层层加码；
3. 真机测试用一套**可逆、不碰生产数据**的手法模拟异常；
4. 发现缺陷后**先报告、给选项、等决策**，而不是擅自改；
5. 修复后**端到端回归 + 同步修订方案文档**，闭环。

---

## 1. 输入：拿到了什么

| 输入 | 内容 | 作用 |
|---|---|---|
| **代码改动** | `feat/ali-dns` 分支，16 文件 +1338 行 | 测试对象——测的是这些改动 |
| **方案文档** | `docs/aliyun-httpdns-integration-plan.md`（800+ 行） | 用例依据——"应该是什么行为"的唯一标准 |
| **项目规范** | autotest 的 `TEST_CASES.md` / `TEST_GUIDE.md` / `dangerous-ops.md` | 用例格式、优先级、危险操作红线 |

被测代码的核心结构（先读懂再测）：

```
business/BaseBusiness/.../network/httpdns/
├── AliHttpDnsManager     SDK 懒初始化、自维护受管 host 集合、预解析
├── AliHttpDnsDns         OkHttp Dns 适配层（默认系统 DNS，FALLBACK 才走 HTTPDNS）
├── HttpDnsFallbackPolicy per-host 三态状态机（NORMAL/FALLBACK/PROBE）
├── HttpDnsConfig         配置统一入口（开关评估 + 缓存兜底）
└── HttpDnsRemoteConfig   OSS httpdns 段解析 + 灰度分桶   ← BUG-001 就在这
接入点：RetrofitFactory.dns() / DomainSwitchInterceptor / DomainManager
```

---

## 2. 第一步：怎么生成测试用例

**核心方法：方案文档的"验证方案"章节 = 用例骨架，代码改动 = 用例落点。**

### 2.1 映射规则

方案 §14（验证方案）+ §12（降级表）+ §4.3/§4.4（状态机）天然就是用例清单。把它们按"执行成本"分层：

| 层 | 测什么 | 成本 | 依据 |
|---|---|---|---|
| **A 静态核对** | 接入前提 + 接入点是否真落地（漏挂接=整链路失效） | 最低 | §3.1 / §13-P1 |
| **B JVM 单测** | 状态机/解析/降级的纯逻辑契约 | 低（无需设备） | §14 单元测试清单 |
| **C 集成测试** | OkHttp 真实建连语义（多 IP 轮换边界） | 中 | §14 集成测试 |
| **D 真机验证** | 端到端兜底实链路 | 高（需设备+外部配置） | §14 手工验证 |

### 2.2 遵循项目规范

- 优先级 **P0/P1/P2**（核心流程必过 / 重要非阻断 / 边界探索）
- 每条用例写明：**步骤 + 验证标准 + 方案依据章节号**（可追溯）
- 标注外部依赖（如 D 层需密钥/控制台/OSS 配置），未就绪标 SKIP

> 产出：`cases.md`，67 条用例。最终落到 `business/BaseBusiness/.../httpdns/` 下的 5 个测试类。

---

## 3. 第二步：怎么开始测试（层层加码）

### 3.1 框架前置（保证测试工具自身可用）

```bash
./gradlew :autotest:compileReleaseKotlin   # 编译
./gradlew :autotest:test                   # 单测
./gradlew :autotest:publishToMavenLocal    # 发布
```
→ 3/3 PASS，才往下走。

### 3.2 A 层静态核对（最便宜，先排除"低级硬伤"）

逐项核对方案断言与接入点是否真落地：
- Maven 仓库能否解析 SDK `2.6.9`、minSdk/targetSdk、OkHttp 4.12.0
- `RetrofitFactory.dns(AliHttpDnsDns())` 挂接存在
- `DomainManager` 三处挂接（bootstrap / OSS 回调 / 池热更新）
- `DomainSwitchInterceptor` 同口径驱动状态机

→ 8/9 PASS（1 条真机基线待设备）。**接入点全部真实落地**——这步能挡住"代码写了但没接上"的低级失效。

### 3.3 B 层 JVM 单测（发现并补齐覆盖缺口）

```bash
./gradlew :business:BaseBusiness:testDebugUnitTest \
  --tests "com.app.base.business.network.httpdns.*" \
  -Dorg.gradle.configureondemand=false   # ← 关键坑，见 §7.1
```

**审查时发现覆盖缺口**：实现自带 37 个单测，但对 `AliHttpDnsManager`（210 行）和
`HttpDnsConfig`（111 行）**零专属覆盖**——而这俩承载了"受管 host 集合""生效域名收窄"
"密钥缺失禁用""PENDING_INIT"等方案点名条目。

→ **补了 13 个单测**（`AliHttpDnsManagerTest` 7 + `HttpDnsConfigTest` 6），全部一次通过
（说明实现正确，纯覆盖补充）。最终 **53 个单测全绿**。

> 教训：覆盖率数字不等于覆盖质量。要对着"方案要求验证的行为"逐条核对，而不是看代码行覆盖。

---

## 4. 第三步：测试手法详解（真机怎么测）

真机测试最难的是"如何在不破坏生产数据的前提下模拟异常"。这次用了 4 个手法：

### 4.1 装包验真（dex 字符串验真）— 防止测错包

```bash
adb -s <设备> pull <base.apk 路径> /tmp/x.apk
unzip -o /tmp/x.apk "classes*.dex" -d /tmp/dex
strings /tmp/dex/classes*.dex | grep -c "AliHttpDnsDns"   # >0 才是含改动的包
```
> 由来：历史上吃过"测了半天发现装的是旧包"的亏（见 RTC转CDN 轮次 ENV-001）。装包后第一件事就是验真。

### 4.2 DNS 异常模拟（可逆，不碰 App 数据）

```bash
# 打坏系统 DNS（私有 DNS 指向不可达主机）
adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_specifier invalid.autotest.dns
# 测完必须复原
adb shell settings delete global private_dns_mode
adb shell settings delete global private_dns_specifier
```
> 为什么不用清数据/改 hosts：清数据会丢登录态+切环境（危险操作红线）；改 hosts 需 root。
> private_dns 是系统级、可逆、不碰 App 的最干净手法。

### 4.3 本地 OSS 模拟（联调线上还没配好的配置）

```bash
# Mac 起本地 HTTP 服务 serve 自定义 conf_test.json
cd /tmp/oss-sim && python3 -m http.server 8000
# adb reverse 让手机的 127.0.0.1:8000 指向 Mac（127.0.0.1 在 App 明文白名单内）
adb reverse tcp:8000 tcp:8000
# 临时把 DomainManager.ossUrl 指向 http://127.0.0.1:8000/...（带 TODO，测完 git checkout 还原）
```
> 用途：在真实 OSS / EMAS 控制台还没配好时，先用本地模拟把客户端链路跑通；
> 也用来对照测试"不同配置值"（如 ttl=600 vs 600000）。

### 4.4 logcat 锚点抓取（看行为，不看猜测）

```bash
adb logcat -c        # 清缓冲
adb shell am force-stop <pkg> && am start -n <pkg>/<Splash>   # 冷启动
adb logcat -d | grep -E "updateFromOssContent|enter_fallback|probe_|httpdns_empty|code：200"
```
关键锚点：
- `updateFromOssContent: HttpDnsRemoteConfig(...)` — 配置解析结果
- `enter_fallback: <host>` — 进入 HTTPDNS 兜底
- `httpdns_empty_fallback_system` — HTTPDNS 空结果降级
- `probe_success/probe_fail` — 状态机回切
- `code：200` + `url：https://...` — 业务请求成败

### 4.5 危险操作红线（全程遵守）

**不登出、不清数据、不切环境、不碰转账/签名**。钱包 App 误操作不可逆，宁可 SKIP 不误点。

---

## 5. 第四步：测出了什么问题

### 5.1 测试推进的几轮

| 轮次 | 环境 | 场景 | 结果 |
|---|---|---|---|
| 1-2 | 测试 | 禁用态基线（密钥未注入） | ✅ 全程系统 DNS，零回归，符合"安全合入" |
| 3 | 测试 | 密钥注入 + 本地模拟 OSS（嵌套格式） | ✅ 远程开关驱动懒初始化、缓存兜底链生效 |
| 4 | 测试 | 坏 DNS 兜底实链路 | ✅ **系统 DNS 全坏，3 个业务请求仍 200 成功**（HTTPDNS 拿到真实 IP） |
| 5 | 测试 | **核对真实线上 OSS 配置** | 🔴 **发现 BUG-001** |
| 6 | 测试 | ttl 修正后回归 | ✅ FALLBACK 稳定，对比鲜明 |
| 7 | **正式** | 生产 OSS（conf_v2）真机回归（小米） | ✅ 修复生产生效；🔶 **发现 FOUND-01**（控制台漏配生产域名） |
| 7+ | **正式** | 运维加生产域名后复测 | ✅ **坏 DNS 下 8 个 debox.pro 请求 200**，生产兜底闭环打通 |

> **测试环境 → 正式环境的递进**很关键：测试环境逼出了**代码缺陷 BUG-001**（配置格式契约不匹配），
> 正式环境逼出了**外部配置缺陷 FOUND-01**（生产域名未加 EMAS 控制台）。两类问题都隐蔽——
> 前者只表现为一条 debug 日志、后者只表现为埋点偏高，**都是"拿真实环境配置去核对"才暴露的**。

### 5.2 🔴 BUG-001（Critical）：配置格式契约不匹配，HTTPDNS 静默失效

**怎么发现的**：第 4 轮用本地模拟（嵌套格式）测通后，去拉**真实线上 OSS** 核对，发现格式对不上。

**线上真实配置（扁平格式）**：
```json
{ "httpdns_enabled": true, "httpdns_hosts": ["t.debox.pro"], "httpdns_abnormal_ttl_ms": 600, ... }
```
**实现期望（嵌套段）**：
```kotlin
JSONObject.parseObject(content)?.getJSONObject("httpdns")  // 线上没有 httpdns 对象 → null
```

**后果**：`getJSONObject("httpdns")` 返回 null → 当成"段缺失" → **HTTPDNS 永不激活**，
线上只有一条 debug 日志、**无任何报错**。正是方案 §8.1 担心的"最需要时静默失效、难发现"。

**实测对照（同一 App 回放两种格式）**：
```
扁平格式（线上真实）→ httpdns 段缺失或损坏，沿用缓存/默认值        ❌
嵌套格式（方案规范）→ HttpDnsRemoteConfig(enabled=true, ...)        ✅
```

**根因**：方案 §10 正文用 `httpdns_enabled` 这类扁平"逻辑名"描述字段，运维照着正文配了 OSS，
漏了"实际 JSON 要嵌套在 httpdns 对象里"。**文档表述歧义 → 配置踩坑 → 功能静默失效。**

**附带发现的两个配置值问题**：
- `httpdns_abnormal_ttl_ms: 600` — 600ms 太短（应 600000=10min），疑似漏 3 个 0
- `httpdns_enable_aes: true` — 需配 AES 密钥 + 影响计费，需确认意图

> 这个 BUG 单靠单测发现不了（单测用的是规范格式），单靠"功能测试"也容易漏（本地模拟恰好用对了格式）。
> **是"拿真实线上配置去核对"这一步逼出来的**——这是最有分享价值的一点。

---

## 6. 第五步：怎么找用户确认（决策不擅自做）

发现 BUG 后**没有直接改**，而是报告 + 给选项 + 等决策：

| 决策点 | AI 提供的选项 | 用户决策 |
|---|---|---|
| BUG-001 怎么修 | **A. 改 OSS**（零代码风险）/ **B. 改实现兼容扁平**（兼容已配置） | 选 B："代码有问题的也要修复代码" |
| ttl=600 / aes=true 怎么处理 | 提醒两个值需修正 | "1（ttl）调整下，2（aes）测试用" |

**原则**：
- 配置契约/计费这类**用户领域的决策**，AI 不替用户拍板；
- 但要把**选项、取舍、推荐**讲清楚，让决策有依据；
- 可逆的技术动作（建用例、跑测试、改代码后回归）AI 自主推进，不卡流程。

---

## 7. 第六步：怎么修复 + 验证闭环

### 7.1 代码修复（方案 B：兼容扁平格式）

`HttpDnsRemoteConfig.parseSection` 增加扁平字段回退（**纯增量，不动现有分支**）：
```kotlin
fun parseSection(content: String): JSONObject? = try {
    val root = JSONObject.parseObject(content) ?: return null
    root.getJSONObject("httpdns") ?: extractFlatSection(root)   // 嵌套优先，回退扁平
} catch (e: Exception) { null }
```
设计要点：嵌套段优先（规范权威）、扁平抽取后**标准化为段字段名**（持久化/下次解析一致）、
纯旧格式 `{"hosts":[...]}` 仍返回 null（语义不变）。

### 7.2 回归验证（端到端，直连真实 OSS）

```
修复前: httpdns 段缺失或损坏                                          ❌
修复后: HttpDnsRemoteConfig(enabled=true, hosts=[t.debox.pro], ...)   ✅
       坏 DNS 下 enter_fallback + 19 个 code:200 业务请求成功         ✅
```

**ttl 修正前后对比**（最直观的验证证据）：

| 指标 | ttl=600 | ttl=600000 |
|---|---|---|
| enter_fallback | 频繁翻转 | **1 次,稳定保持** |
| probe 翻转 | 频繁 | **0 次** |
| 业务 200 | 7 | **19** |

### 7.3 同步修订方案文档（治本，防再次踩坑）

`aliyun-httpdns-integration-plan.md` §10：
- 明确**两种写法等价**（嵌套推荐 / 扁平兼容），各给完整 JSON 示例
- 加**段字段 ↔ 扁平键对照表**
- 加运维提示（TTL 单位毫秒、AES 计费）
- 顶部加实施期修正⑯留痕

> 修代码解决了"已配的 OSS 能用"，改文档解决了"以后不再有人配错"。两手都要。

---

## 8. 关键收获（可复用的方法论）

1. **用例从文档机械映射，不拍脑袋**：方案的"验证章节"就是用例骨架，每条用例可追溯到章节号。
2. **分层推进，便宜的先跑**：静态核对 → JVM 单测 → 集成 → 真机。低成本层能挡住大部分低级失效。
3. **覆盖质量 ≠ 覆盖率**：对着"应验证的行为"核对，发现并补了 13 个缺口单测。
4. **真机异常要可逆、不碰生产数据**：private_dns 模拟、本地 OSS 模拟、dex 验真、危险操作红线。
5. **看行为不看猜测**：所有结论都有 logcat 锚点或日志证据，对照表说话。
6. **拿真实线上配置去核对**：最隐蔽的 BUG-001 正是这一步逼出来的，本地模拟反而会"恰好用对格式"掩盖问题。
7. **发现问题先报告给选项，不擅自决策**：用户领域的事（配置/计费）让用户拍板。
8. **修复要闭环**：代码 + 单测 + 端到端真机 + 文档修订 + 现场复原，缺一不可。

---

## 9. 附录

### 9.1 现场复原清单（联调后必做）

| 项 | 复原方式 |
|---|---|
| 临时改的 `ossUrl` | `git checkout DomainManager.kt` |
| 本地 OSS 模拟服务 | `pkill -f "http.server 8000"` |
| adb reverse | `adb reverse --remove tcp:8000` |
| 设备 private_dns | `adb shell settings delete global private_dns_mode/specifier` |
| 测试包 | 装回真实 OSS 包 |

### 9.2 踩过的坑

- **单模块 gradle 构建要关 configure-on-demand**：`-Dorg.gradle.configureondemand=false`，
  否则 react-native-screens 配置期找不到 app 工程、node 解析失败。
- **app 整体构建反而要用默认参数**（开 configure-on-demand），否则 RN bundle 任务装配失败。
- **重定向输出的 APK 增量打包会 zip 冲突**：构建前 `rm -f apks/debox-debug.apk`。
- **设备安全锁屏 adb 绕不过**：真机轮次前需人工解锁一次。

### 9.3 交付物

| 文件 | 内容 |
|---|---|
| `cases.md` | 67 条分层用例（A/B/C/D） |
| `results.md` | 6 轮测试结果 + BUG-001 完整记录 |
| 本文档 | 全流程方法论复盘（分享用） |
| debox 代码改动 | `HttpDnsRemoteConfig.kt` 修复 + 5 个测试类 + 方案文档 §10 修订 |

### 9.4 遗留待办（运维侧）

1. 线上 OSS 改 `httpdns_abnormal_ttl_ms: 600 → 600000`
2. 确认 `httpdns_enable_aes: true` 的计费意图
3. debox 未提交改动待 review 后提交
