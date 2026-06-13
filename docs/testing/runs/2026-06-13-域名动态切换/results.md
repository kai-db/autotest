# 域名动态切换优化 — 测试结果

> 被测分支：debox-android `feat/fix-domain-switch`（工作区 `DomainManager.kt` +86 行）
> 用例：见同目录 `cases.md` | 证据：见 `evidence/`

---

## 当前轮次（首次测试）

> 测试日期：2026-06-13 21:15~21:23 | 测试环境：三星 SM-S9210（`RFCYA0F9SSZ`）DEBUG 构建 2.12.3（日志全开）
> 触发原因：feat/fix-domain-switch 域名动态切换优化首测

### A 层 — JVM 单测（自动）

| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| UT-01~04 | DomainManagerTest（parseHostsJson 4 例） | ✅ PASS | tests=4 failures=0 |
| UT-05~12 | DomainSwitchInterceptorTest（故障分类/上报 8 例） | ✅ PASS | tests=8 failures=0 |
| UT-13~16 | HttpConstantTest（URL 解析 4 例） | ✅ PASS | tests=4 failures=0 |

**A 层小结：16/16 PASS**（`./gradlew :business:BaseModule:testDebugUnitTest :business:BaseBusiness:testDebugUnitTest`，BUILD SUCCESSFUL）

### B 层 — 三星真机（adb logcat 取证）

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| TC-S-001 | App 冷启动冒烟 | ✅ PASS | topResumed=MainActivity，0 FATAL，46×`code:1` |
| TC-F-001 | 启动健康探测·可达分支 | ✅ PASS | `checkCurrentDomainHealth: 当前域名 debox.pro 可达，无需处理`，无切换 |
| TC-F-002 | 健康探测后台线程不阻塞启动 | ✅ PASS | 探测日志 tid=20812 ≠ 主线程 20615；UI 正常进首页 |
| TC-F-003 | 缓存恢复域名池非空（兜底不变量） | ✅ PASS | `restoreFromCache: 缓存域名池=[debox.pro, dbxsocial.com]`，currentDomain 在池内 |
| TC-F-004 | OSS 拉取 + sysConfig 合并 | ✅ PASS | `fetchOssDomains: OSS域名池更新成功` + `mergeDomains: 新增额外域名 [m.debox.pro, deswap.pro, x.debox.pro, app.debox.pro, open.debox.pro]` |
| TC-F-005 | 正常使用无误切换（负向） | ✅ PASS | 滑动交互 20s：无 `onConnectivityFailure`、无 `域名切换`；6×`code:1` |
| TC-T-001 | 运行中请求级故障切换 | ⏸️ 跳过 | **无法黑盒触发**（见下「测试手段边界」），由 A 层 UT-05~12 + 代码分析覆盖 |
| TC-T-002 | 断网启动→健康探测回退主域名 | ✅ PASS | `isReachable: debox.pro 不可达 - UnknownHostException` → `dbxsocial.com 不可达` → `不可达且无可达备选，清除固化的当前域名并回退主域名`，0 FATAL |
| TC-T-003 | 恢复网络后自愈 | ✅ PASS | 次启 `restoreFromCache: 缓存域名无效(), 回退到 debox.pro`（证明 T-002 已清固化域名并持久化）→ `可达` → 46×`code:1` 完全恢复 |
| TC-T-004 | 探测耗时上限 ≤2.5s/域名 | ✅ PASS（附注） | 离线时 DNS 即时失败（21:16:54.246→.249，2 域名共 3ms ≪ 2500ms）；connect 超时分支因 DNS 先失败未触达，不阻塞启动 |

**B 层小结：9 PASS / 1 跳过 / 0 FAIL**

### 本轮统计

**PASS 25 / FAIL 0 / 跳过 1**（A 16 + B 9 PASS；B 1 跳过）

---

## 测试手段边界（诚实标注，非缺陷）

**结论：设备级断网 / 系统代理均无法在非 root 真机上触发「请求级单域名故障切换」（TC-T-001）。**

证据链：
1. 设备整体断网时，业务请求被 App 层网络守卫提前以 `-100 网络未连接` 短路（`DeBoxHttpRequest.kt:321 RequestCore.call`），**根本不进 `DomainSwitchInterceptor`** → `onConnectivityFailure` 0 次触发。
2. `RetrofitFactory.getOkHttpClient()` 设了 `.proxy(Proxy.NO_PROXY)` → OkHttp **绕过系统代理**，无法用 `settings put global http_proxy` 制造「有网但连不通」。
3. 选择性封堵单域名（保留备选域名可达）需 root 改 hosts 或网络层（VPN/路由）注入，本环境不具备。

→ 该特性的设计场景是「设备有网、但 debox.pro 被 DNS 污染/IP 封堵/SNI 阻断」，此时设备连通性检查通过、请求进 OkHttp 抛连通性异常、`onConnectivityFailure` 触发切换。**这条契约已由 A 层 UT-05~12 直接验证**（各异常类型 → 正确上报 host/path；第三方 host 不污染；read-timeout 不计入），阈值/窗口/冷却/selectBestDomain 决策由代码分析覆盖（模块无 Robolectric，实例逻辑无法纯 JVM 单测）。

| 无法黑盒覆盖项 | 原因 | 替代验证 |
|---|---|---|
| 请求级故障上报 + 窗口/阈值/冷却/切换决策 | App 层 -100 守卫 + Proxy.NO_PROXY | A 层 UT-05~12 + 代码分析 |
| 健康探测「切到可达备选」分支（一坏一好） | 非 root 无法选择性封堵单域名 | 代码分析 + UT-14/15 + 全坏回退分支 TC-T-002 |
| 新装首启注入兜底池（cache 空） | 触发需 clear data，违反铁律 | 代码分析 + 不变量 TC-F-003（池永不空） |

---

## 设计观察（非阻断，建议团队评估）

### OBS-01 — 离线重启会丢弃已生效的非主域名故障切换选择

**现象**：`checkCurrentDomainHealth` 在「当前域名不可达且**无任何可达备选**」时，`remove(DOMAIN_CURRENT)` 并回退 `currentDomain = HttpConstant.HOST`（debox.pro）。

**影响推演**：若某用户因 debox.pro 被针对性封堵已故障切换到 `dbxsocial.com`，随后在**无网环境**（地铁/电梯）重启 App → 健康探测发现两域名都不可达（设备离线）→ 误判并**清除已持久化的 dbxsocial.com，回退到仍被封的 debox.pro**。恢复网络后需重新累积 3 个 path 失败、过冷却，才能再次切回可用域名，期间用户经历一段「网络连接失败」降级。

**根因**：「全部不可达」既可能是「域名被封」也可能是「设备离线」，当前实现对两者同等处理（都回退 HOST）。而「全部不可达」更强烈指向**设备离线**，此时回退 HOST 无收益却有害。

**建议**：当 currentDomain 不可达**且无任何备选可达**（强烈暗示设备离线）时，**保留已持久化的当前域名、不回退**，交由网络恢复后的请求级切换接管；仅当「currentDomain 不可达但存在可达备选」时才切换。这样既保留自愈能力，又不丢失正确的故障切换结果。

**置信度**：约 75%（窄场景、可自愈，故定为非阻断观察项；是否接受取决于团队对「钉死在坏域名」与「丢失好域名」的权衡）。

**状态**：🟡 已修复待验证

**修复方案**（最小改动，仅 `DomainManager.checkCurrentDomainHealth` 的「全不可达」else 分支 + 对应 KDoc）：

改前（销毁式回退）：
```kotlin
} else {
    LogUtils.w(TAG, "...不可达且无可达备选，清除固化的当前域名并回退主域名")
    PreferencesUtils.get().remove(PreferencesConstants.DOMAIN_CURRENT)
    currentDomain = HttpConstant.HOST
    changed = true
}
```
改后（保留持久化选择，不回退）：
```kotlin
} else {
    // 全部备选（含 HOST/内置兜底）也不可达：强烈暗示设备整体离线，而非当前域名被针对性封堵
    // ——只要 HOST 或任一备选可达就会走上面的 healthy 分支，到不了这里。
    // 回退 HOST 无收益（HOST 同样连不上），却会清掉用户已生效的故障切换选择。保留不动，
    // 交由网络恢复后的请求级故障切换接管。
    LogUtils.w(TAG, "...不可达且无可达备选（疑似设备离线），保留当前域名，待网络恢复后由请求级切换接管")
}
```

**为什么最小且安全**：该 else 分支**只在 HOST 也不可达时**才触发（HOST/任一备选可达→走上面 `healthy` 分支），原「回退 HOST」在其真正触发的所有场景里都是**无效操作**，删除零能力损失；`changed` 保持 false → 不写 SP、不 `resetUrl`、currentDomain 原样保留；网络恢复后由请求级 `onConnectivityFailure`（阈值/冷却）继续接管，与原设计一致。无新增 import、无签名变更。

**检查清单**：

```
- [x] 根因基于证据验证（TC-T-002 logcat + 代码路径分析：else 仅 HOST 不可达时触发）
- [x] 评估影响范围，无回归（仅改 else 分支，不动 healthy / 已变更 分支与调用方）
- [x] 确认 UI 正常（不涉及 UI；行为为后台线程内域名状态保留）
- [x] 改动范围最小（净改 2 处：else 分支 + KDoc 第 2 点）
```

**验证结果**：
- A 层：`./gradlew :business:BaseModule:compileDebugKotlin :business:BaseModule:testDebugUnitTest --tests "*DomainManagerTest"` → **BUILD SUCCESSFUL**，DomainManagerTest 回归绿。
- B 层（待真机回归）：修复后该分支需重新打包安装才生效；真机镜像验证 = 断网冷启动后，日志应为新文案 **`不可达且无可达备选（疑似设备离线），保留当前域名`**，且**不出现** `清除固化的当前域名并回退主域名`；恢复网络重启后 `restoreFromCache` 不应再出现 `缓存域名无效()` 的回退（即 `domain_current` 未被清）。该 else 分支依赖 Android SP + Socket，无法纯 JVM 单测，回归取证落 B 层。

---

## 结果状态说明

| 标记 | 含义 |
|------|------|
| ✅ PASS | 验证符合预期 |
| ❌ FAIL | 不符合预期，需要修复 |
| ⏸️ 跳过 | 测试手段无法黑盒触达（已说明替代验证） |
