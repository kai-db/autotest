# 极验 GeeGuard 设备指纹接入 — 测试结果（2026-07-14）

> 用例见同目录 `cases.md`。记录原则：只记是否通过、问题描述、根因分析、修复方案。
> 每轮测试独立一节，按时间倒序排列（最新在最前）。

---

## 第 9 轮 — dev 基线核心回归验收（2026-07-14 21:00–21:10）

> **触发原因**：用户把 debox-android 主仓从 release 分支切回 **`dev` 分支**（HEAD `11c86dd57c`，落后 origin/dev 仅 1 个 WalletConnect commit，非 GeeGuard），并「继续测试」。基线正式落在 dev 后，前几轮在旧 HEAD（行号 130）上的核心结论需在 **dev 基线（行号 148）** 上回归确认。
> **基线对齐核验**：主仓 dev 的 `GeeGuardManager` + `SysConfigEntity` 与设备上的 dev 基线包（`29fa710b29`）**diff 为空**，代码一致，基线包有效。

### dev 基线回归结果（全部在行号 148 = dev 代码上）

| # | 用例 | 设备 | 证据 | 结果 |
|---|------|------|------|------|
| **TC-S4** | 单测（含 TokenGate） | 主仓 dev | `--rerun-tasks` | ✅ **PASS**：GeeGuardManagerTest **13/0/0** + SysConfigEntityTest **1/0/0** |
| **TC-A1/A2/E4** | 采集+初始化+无 crash | 5554 + 5556 | 双机冷启动 | ✅ **PASS**：行号 **148**；初始化 12ms / 3ms；`submitReceipt ok, length=392`；后端下发 `geetest_token_check:true`；crash 0 |
| **TC-A3** | 断网降级 fail-open | 5556 | 飞行模式冷启动 | ✅ **PASS**：`submitReceipt failed status=-300, degrade to geeToken.length=4088`；crash 0；App 存活 |
| **TC-A4** | 恢复后刷新 | 5556 | 恢复网络冷启动 | ✅ **PASS**：回正常形态 `length=392`；sys_config 重新拉到（网络恢复实证） |
| **TC-C1** | `liveroom/join` 注入 | 5556 建房 `ztzy3tsa` + 5554 加入 | 深链 → 加入 → 抓包 | ✅ **PASS**：join body 含非空 `gee_token`（实际值不留档）→ 后端 **`code:1`**；crash 0 |

**结论**：**GeeGuard 核心用例（采集/初始化/降级/恢复/注入/单测）在 dev 基线上全部回归通过**，行为与旧 HEAD 一致（token 开关默认 true 时行为等价）。**前 8 轮的 GeeGuard 结论在 dev 基线上确认有效。** 测试房 `ztzy3tsa` 已关闭，三机 crash 0。

---

## 第 8 轮 — 极验 token 检查开关（`geetest_token_check`）功能测试（2026-07-14 19:10–）

> **触发原因**：用户问「那接口返回配置 token 开关的测试了吗」。
> **关键前提暴露**：该功能是 commit **`80804ae97c`「极验 token 检查支持系统配置开关下发」**引入的，位于 **`origin/dev` 分支**（仓库默认分支），**不在前 7 轮测的本地 HEAD `7d9ad7360f` 里**。HEAD 落后 origin/dev **12 个 commit**，但其中**唯一 GeeGuard 相关的就是这个 token 开关**（其余 11 个是 wallet/live/ci/build），故前 7 轮 GeeGuard 结论（注入/降级/兼容/FINDING-002）在 dev 上仍成立，仅需补此功能。
> **交叉验证**：dev 分支 `submitReceipt ok` 日志在 **148 行** → 与用户 16:15/16:52 手动装在 5554/三星上的包（行号 148）吻合 → **实证"用户装的包 = dev 分支构建"**（呼应第 5 轮的包混淆诊断）。

### 功能机制（代码审计）

后端系统配置 `geetest_token_check` 总开关，控制 GeeGuard token 采集/注入：

| 组件 | 行为 |
|---|---|
| `SysConfigEntity.geetest_token_check` | 新增字段，**缺省 true**（fail-open） |
| `GeeGuardTokenGate`（线程安全门禁） | **关闭**：立即清空 token 缓存、`refreshTokenAsync` 入口 return 不刷新、`cacheIfEnabled` 拒绝回调写回 → `getDeviceToken()` 返空 → 业务请求不带 `gee_token`。**关闭→开启翻转**：预热一次。开关与异步写用同一把锁（防关闭后旧回调写回） |
| 冷启动 `GeeGuardInitTask` | 先读本地 SP 缓存值 `setTokenCheckEnabled(...)` |
| `AppConfigManager` | 拉 `sys_config` 接口后应用下发值 + 持久化 SP（后写覆盖冷启动值） |
| 日志三态 | `submitReceipt ok`（开）/ `ok but token check disabled, skip cache`（关+200）/ `failed status=X, token check disabled`（关+降级） |

### 第 8 轮结果

| # | 测试项 | 方法 | 结果 |
|---|--------|------|------|
| **TG-1** | Gate 四态逻辑单测 | dev worktree 跑 `*GeeGuard* *SysConfigEntity*` | ✅ **PASS**：GeeGuardManagerTest **13/0/0**（原 9 + 新 4 TokenGate：默认开启/关闭清缓存/关闭拒写/重开不恢复）+ SysConfigEntityTest **1/0/0**（缺字段默认 true） |
| **TG-2** | 默认/开启态真机完整链路 | dev 包（`debox-dev-tokengate.apk`）装 5556 → 冷启动 | ✅ **PASS**：行号 **148**（dev 代码归因确认）；`submitReceipt ok, respondedGeeToken.length=392`（缺省开关 true 正常采集）；crash 0 |
| **TG-3** | 后端 sys_config 下发实证 | 抓 5556 冷启动 `sys_config` 响应 | ✅ **PASS（关键）**：**后端测试环境已下发 `"geetest_token_check":true`** → **完整链路成立**：接口下发 → `AppConfigManager` 应用 → `setTokenCheckEnabled(true)` → 采集/注入正常。**证明后端已支持该字段**（非仅客户端预留） |
| **TG-4** | 关闭态（false）真机集成 | — | ⏸️ **自主阻塞（逻辑已由单测覆盖）**：开关值存 `EncryptedSharedPreferences`（AES256 加密，root 也改不了明文）；`setTokenCheckEnabled` 无 debug/UI 入口，仅冷启动读 SP + AppConfigManager 接口下发两处。**触发 false 只能靠后端把测试设备 `sys_config.geetest_token_check` 配成 false**（同 C6/E3 后端策略性质）。**Gate 关闭逻辑本身已由 TG-1 的 4 个单测充分覆盖** |

### 覆盖结论

- **开启态（默认 + 后端下发 true）**：客户端 + 后端**完整链路实测通过**。
- **关闭态逻辑**：**单测充分覆盖**（清缓存/拒写/翻转预热/默认值）。
- **关闭态真机集成**：移交后端（测试环境对某设备下发 `geetest_token_check:false`），届时预期业务请求 body **不含 `gee_token`**、日志现 `token check disabled`、后端 fail-open 放行——**这几点客户端逻辑单测已保证，只差一次后端下发的端到端确认**。

**构造包留档**：`debox-android/apks/debox-dev-tokengate.apk`（dev 分支 `80804ae97c`，行号 148，versionCode 21400002，同签名）。

### 🔀 测试基线正式切换到 `origin/dev`（用户决策，2026-07-14 19:30）

> 用户拍板：后续 GeeGuard 测试基线从旧 HEAD `7d9ad73` 切到 **`origin/dev` tip `29fa710b29`**（仓库默认分支、发布目标、也是用户手动装设备上的版本）。关闭态（false）真机集成不深追（单测覆盖足够）。

**dev 基线包**：`apks/debox-dev-baseline.apk` — commit `29fa710b29`，sha256 前缀 `94484888bd29645e`，versionName 2.14.2 / versionCode 21400002，同签名（`2ee89168…`）。
**已统一装机**：emulator-5554 + emulator-5556 均切 dev 基线，冷启动验证：**行号 148（dev 归因）/ `submitReceipt ok, length=392` / 后端下发 `geetest_token_check:true` / crash 0**。

**⭐ dev tip 相比 80804 多的 2 个 token 校验修复（回应前几轮现象）**：
| commit | 修复 | 关联 |
|---|---|---|
| `d637fd832c` | 网络校验临时失败不再污染持久登录态，退避重试 + 网络恢复唤醒，避免把传输故障误报为 DID 退出登录 | **正是前几轮 5554/5556 反复出现「您的DID已退出登录」红条 + 断网掉登录态的根因修复** |
| `acb038827d` | **BUG-001（autotest 设备回归反馈）**：断网重试链死亡修复（delayScheduler + finishClear 时序 bug 导致断网 -100 同步回调下重试任务被 removeCallbacks） | **autotest 之前反馈的 bug，dev 已修** |

> ⚠️ 这两个属 **`TokenVerifyManager`（DID 登录态校验）** 范畴，**非 GeeGuard 设备指纹**，各自带单测（TokenVerifyManagerRoundTest / TokenVerifyRetryPolicyTest）。已随 dev 基线纳入；**若要专项回归验证 DID 校验修复，建议另开 run**（不混入本 GeeGuard run）。它们的存在解释了本 run 前几轮把 DID 掉线归因为「后端不稳定/风控拦截」时那部分**登录态反复掉的现象——有一半是客户端 token 校验 bug（现已修）**。

---

## 第 7 轮 — 自建多版本 arm64 AVD，补 GeeGuard Android 版本兼容矩阵（2026-07-14 18:40–）

> **触发原因**：用户「模拟器你可以自己创建，需要什么自己想办法」→ F 组设备兼容此前只在 API 35/37 两个高版本 + 真机测过，**缺最低支持版本（minSdk 边界）的验证**。GeeGuard 的核心（SO 加载 + `submitReceipt` 采集）在冷启动 StartupTask 阶段完成、**先于登录页**，故**无需登录态**即可在任意新建 AVD 上验证——这正是版本兼容性的关键面。
> **方法**：`sdkmanager` 下载不同 API 的 arm64 系统镜像 → `avdmanager` 建 AVD → 装 HEAD 包（`debox-head-verified.apk`，行号 130 已验）→ 冷启动看 SO 加载/采集/crash。

### GeeGuard Android 版本兼容矩阵

| API | Android | 设备 | 行号 | 初始化 | SO 加载/采集 | crash | 结果 |
|---|---|---|---|---|---|---|---|
| **25** | **7.1（DeBox minSdk 边界）** | 新建 `debox_api25`（emulator-5558） | 130 | `main=false` 4ms | ✅ **`submitReceipt ok, length=436`** | 0 | ✅ **PASS（下限）**：**最低支持版本上 GeeGuard SO 正常加载并采集**，零 `UnsatisfiedLinkError`、零 crash → **minSdk 兼容性下限成立** |
| 35 | 15 | emulator-5554（debox_root）/ 三星真机 | 130 | 3ms | `length=392 / 348` | 0 | ✅ PASS（前几轮已验） |
| 37 | 17 | emulator-5556 | 130 | 4ms | `length=392` | 0 | ✅ PASS（前几轮已验） |
| **30** | **11（中间主流版本）** | 新建 `debox_api30`（emulator-5560） | 130 | `main=false` 1ms | ✅ **`submitReceipt ok, length=412`** | 0 | ✅ **PASS（中间）**：SO 正常加载采集，零 UnsatisfiedLinkError、零 crash |

**价值**：GeeGuard SDK 的 SO（`libgtcore.so`）能否在某 Android 版本上 `dlopen` 成功，是**运行时行为**——FINDING-002 已证「加载失败 = 崩溃」，故**低版本 SO 兼容性必须实测**。本轮实证 **Android 7.1（minSdk 下限）→ 11 → 15 → 17 全区间 SO 加载正常、采集正常、零崩溃**（下限/中间/上限三点 + 真机全覆盖）。

**新形态长度点**：API 25 = `436` / API 30 = `412`（此前实测 348–392）→ 再次印证「**正常形态长度随设备环境浮动（~350–440），判形态必须看日志语义而非长度阈值**」。

**版本兼容结论**：GeeGuard SO（`libgtcore.so`，arm64-v8a）在 **DeBox 全部支持的 Android 版本区间（API 25–37）冷启动加载 + 采集均正常，零崩溃**。唯一的 SO 崩溃路径是「包内根本没有对应 ABI 的 SO」（FINDING-002，x86_64 场景），与「SO 在某版本加载不了」是两回事——后者本轮已证不存在。

> ⚠️ **本机无登录态**（新 AVD）→ 业务注入（`app_login`/`join`）需登录且会被风控拦截（未加白），**本机不验业务注入**；但采集侧（SO 兼容性核心）已完整覆盖，这是版本兼容性的关键判据。

---

## 第 6 轮 — Phase 6 全量回归验收（2026-07-14 17:05–17:20）

> **触发原因**：第 5 轮对设备做了大量装包/降级/升级操作（E1 包、F3 包、老包 2.14.1、HEAD 包来回覆盖），按 CLAUDE.md Phase 6 必须**不改代码完整跑一遍**，确认核心用例仍全绿。

### 回归结果

| # | 用例 | 设备 | 证据 | 结果 |
|---|------|------|------|------|
| **TC-S4** | 单测回归 | — | `:business:BaseBusiness:testDebugUnitTest --tests "*GeeGuard*" --rerun-tasks` | ✅ **PASS**：**9 tests / 0 failures / 0 errors** |
| **TC-A1/A2/A6/E4** | 配置+采集+初始化+杀进程重采集+无 crash | 5554 + 5556 | 双机 force-stop → 冷启动 | ✅ **PASS**：两机**行号探针均 = `GeeGuardManager.kt:130`（HEAD 归因确认）**；初始化 `main=false` **3ms / 4ms**；`submitReceipt ok, length=392`（两机一致）；TotalTime 2799 / 2311ms；**crash 0** |
| **TC-C1** | `liveroom/join` 注入回归 | 5556 建房 `39dabynd` + 5554 加入 | 深链 → 现在加入 → 抓包 | ✅ **PASS**：join body 含非空 `gee_token`（实际值不留档）→ 后端 **`code:1`**，crash 0 → **注入链路经全部折腾后完好** |
| **FINDING-001 边界** | release 包是否泄露 token | 三星（**用户自行安装的 release 包**，AI 只读） | 冷启动 → logcat 审计 | ✅ **实证**：`gee_token` 明文 **0 次**、`http_log_interceptor` **0 条**、`PRETTY_LOGGER` **0 条**、App 正常无 crash → **FINDING-001「release 不泄露」由推断升级为真机实证**（详见 FINDING-001 风险边界节） |

**测试房清理**：`39dabynd` 已由房主关闭。

### ⚠️ 再次发现「设备上的包被替换」（与第 5 轮同源，非 AI 操作）

- **三星 SM-S9210**：`lastUpdateTime=2026-07-14 **16:52:53**` → **该时刻 AI 正在写文档，未执行任何安装**。实测 `run-as: package not debuggable` → **现装的是 release 包**（非第 3 轮 AI 装的 HEAD debug 包）。
- **影响**：release 包不打日志 → **三星无法再通过 logcat 做 GeeGuard 回归观察**。但**第 3 轮的三星结论（TC-F1/C4 HEAD 双绿）仍然有效**——当时是 HEAD debug 包，有完整日志实证（行号 130、`submitReceipt ok, length=348`、join `code:1`）。
- **处置**：**未擅自覆盖**（真机 + release 包可能是用户有意安装用于验证线上版本）。如需恢复 debug 包做真机回归，可 `adb install -r apks/debox-head-verified.apk`（同签名保数据）——**待用户确认后再执行**。
- **这已是本日第 2 次「设备包被替换」**（第 1 次：5554 于 16:15）→ **印证 devices.md 新增铁律「每轮开测必须重新核验被测包，不信上一轮的记忆」的必要性**。

---

## 第 5 轮 — 自主构造包解阻「需专门出包」类（2026-07-14 16:29–16:35）

> **触发原因**：用户问「剩下阻塞的都能测吧」→ 复核发现 **TC-E1 / TC-F3 的阻塞理由（「需专门出包」「宿主无 x86_64」）实际可由 §7.7 自主构建绕过**，不必等外部条件。
> **方法**：只改**构建配置**（`local.properties` / `packagingOptions`），**不改一行代码**；构建后立即 `git checkout` 还原，改动不入库。

### 第 5 轮结果

| # | 用例 | 构造方式 | 结果 |
|---|------|---------|------|
| **TC-E1** | AppID 未配置包（P1，前 4 轮「需专门出包」阻塞） | `local.properties` 置 `GEEGUARD_APP_ID=`（空）→ 构建 → 装 5556 | ✅ **PASS（含后端 fail-open 实证）**：① 日志 `GEEGUARD_APP_ID not configured, skip register` 告警**如期打印**；② **零采集**（`submitReceipt`/`register` 命中 0 次）；③ **crash 0**、App 正常进 MainActivity；④ **业务请求 body 完全不含 `gee_token`**（命中 0 次），后端 **`code:1` 照常放行** → **fail-open 端到端成立**（未配 AppID 不影响 App 可用性） |
| **TC-F3** | x86_64 兜底（P0，前 4 轮「宿主 Apple Silicon 无法跑 x86_64 AVD」阻塞） | **等效构造**：`packagingOptions` 排除 `lib/arm64-v8a/libgtcore.so` → arm64 设备上 SO 必然加载失败（= x86_64 设备上无对应 ABI SO 的同一条代码路径）→ 装 5556 | ❌ **FAIL（本轮唯一 FAIL，P0）**：**App 冷启动即崩溃**（`FATAL EXCEPTION: Thread-25` / `UnsatisfiedLinkError: dlopen failed: library "libgtcore.so" not found`，连崩 2 次掉回桌面）。**`catch(Throwable)` 兜底完全无效**——异常抛在 GeeGuard SDK **自建线程**上，不在 `register()` 调用栈里。详见 **🔴 FINDING-002** |
| **TC-F2** | 32 位 armeabi-v7a（P1） | 查 `sdkmanager`：`system-images;android-25;google_apis;armeabi-v7a` **存在**且 API 25 == DeBox minSdk 25 → 下载镜像 + 建 AVD → **启动失败** | ⏸️ **确认不可行（阻塞理由已精确化）**：`FATAL \| CPU Architecture 'arm' is not supported by the QEMU2 emulator, (the classic engine is deprecated!)` → **现代 Android emulator 已不支持 32 位 ARM 架构**（与宿主是否 Apple Silicon 无关）。**只能用真 32 位 ARM 真机**。AVD 已删除。⚠️ 但 **FINDING-002 对本条有直接启示**：v7a 的 `libgtcore.so` **已打包在 APK 内**（实测 532KB），故 v7a 设备上 SO 可加载、不触发该崩溃路径 |
| **TC-E3** | 后端风控真拦截（P0，前 4 轮「需后端下发拦截策略」阻塞） | **复盘判定**：第 3/4 轮加白前，模拟器**本就处于后端真拦截状态** | ✅ **PASS（复盘升级，证据已在手）**：后端确实对模拟器**下发并执行了高风险拦截**（`app_login` 返 `-2051` ×3，加白后同设备同请求转 `ok` → 证明是**策略性拦截**而非故障）。**客户端在被拦时的表现**：顶部红条「您的DID已退出登录」+ 登录失败、**无 crash、无卡死、可重试**（点横幅即重登）→ **拦截链路端到端优雅**。详见「⭐ 关键证据⑥」加白前后对照表 |

### 第 5 轮（续）— G 组老版本兼容：自建「老版本包」解阻

> **突破点**：G 组阻塞理由是「无线上老版本 APK 在手」。但 **GeeGuard 是 2026-07-06 由 commit `0590bb3343` 引入的**——它的父提交 **`bafce98b6c` 就是一份天然的「无 GeeGuard 老版本」**。用 `git worktree` 检出该 commit 构建，即得老版本包，**无需等线上包**。

**老版本包**：`apks/debox-2.14.1-pre-geeguard.apk` — versionName **2.14.1** / versionCode **21400001**（**比 HEAD 低一号，是真降级**）；签名 SHA-256 与 HEAD 包**一致**；`libgtcore.so` 命中 **0**（确认无 GeeGuard）。安装用 `install -r -d`（允许降级 + **保留数据，不卸载**，不触碰危险红线）。

| # | 用例 | 操作 | 结果 |
|---|------|------|------|
| **TC-G1** | 老版本回归（P0） | 5556 降级装 2.14.1 → 冷启动 | ✅ **PASS**：老版本**干净运行**——GeeGuard 痕迹命中 **0**、crash **0**、业务请求 **不含 `gee_token`**、后端 `code:1` 正常放行、登录态保留 → **老版本行为未被 GeeGuard 改动污染** |
| **TC-G2** | 覆盖升级（P0） | 5556：2.14.1 → **确定 HEAD 包** `install -r`（保留数据） | ✅ **PASS**：① **行号探针 `GeeGuardManager.kt:130` = HEAD**（归因确认）；② **升级后 GeeGuard 首次初始化成功**（`main=false`，4ms）；③ `submitReceipt ok, length=392` **首次采集正常**；④ **crash 0**；⑤ 冷启动 TotalTime 2088ms；⑥ **登录态保留**，重登后 `app_login ok` 且 body **注入 `gee_token` 9 次** → **老用户升级到含 GeeGuard 的新版本，SO 首次加载/首次采集/首次注入全链路正常** |
| **TC-G3** | 新老共存（P0） | 5554 装老包 2.14.1 + 5556 装 HEAD 新包，**同时**对同一后端（`t.debox.pro`）发业务请求 | ✅ **PASS**：**老版本（不带 `gee_token`）与新版本（带 `gee_token`）同时在线，后端对两者均 `code:1` 放行**、双方 crash 0 → **灰度期间新老版本共存不冲突**，后端对「有/无 token」两种请求形态**同时兼容**（这也再次实证 fail-open） |

### 🔴🔴 严重过程事故：**差点用错包下结论**（方法论教训，必须记入知识库）

**事发**：TC-G2 首次执行时，我用 `head-good.apk`（**从 emulator-5554 pull 的包**）当作「HEAD 包」做覆盖升级验证。

**识破**：日志行号打出 `GeeGuardManager.kt:148`，而 **HEAD 源码第 148 行是一行注释**（`grep -n` 实证：130 行才是 `submitReceipt ok` 的日志语句）——**日志调用点不可能落在注释上** → 立即怀疑包不对。

**真相**（决定性实验：用当前 HEAD 源码重新构建 → 装 5556 → 行号 **130**，与源码完全一致）：
- **emulator-5554 上装的包不是 HEAD 包**：`lastUpdateTime=2026-07-14 16:15:49`——**该时刻 AI 未执行任何安装**（疑为用户手动安装）。
- 该包含 commit **`80804ae97c`「极验 token 检查支持系统配置开关下发」**（引入 `GeeGuardTokenGate` 门禁，**改动了 GeeGuardManager**）。`git merge-base --is-ancestor` 判定：**该 commit 不是 HEAD 的祖先** → 是 HEAD **之外/之后**的分支代码。

**影响范围核定（按时间线严格划界）**：
- 第 3 轮（14:50）、第 4 轮（15:24–16:00）：5554 当时装的**仍是 HEAD 包**（行号 130 有实证），**结论不受影响**。
- 第 5 轮 TC-G2 首次执行（16:42）：**用错包 → 结论作废，已用确定 HEAD 包全量重跑**（上表即重跑结果）。

**教训（写入 devices.md）**：
1. **行号探针有效且必须用**——它当场识破了错包。`GeeGuardManager.kt:130` = HEAD；`:148` = 含 `GeeGuardTokenGate` 的更新分支。
2. **绝不能把「从某台设备 pull 出来的包」默认当作 HEAD 包**——设备上的包可能被任何人（包括用户）在任何时刻替换。**要 HEAD 包就现场构建**，并用行号/sha256 归因。
3. 这正是 06-18 血泪教训「**拿旧包/异版本包测新逻辑 = 假阴性/假阳性的头号来源**」的**第二次复现**——第 2 轮栽在三星 2.15.0 上，本轮又差点栽在 5554 上。

**测后复原（已完成）**：`local.properties` 从备份还原（AppID 在位）；`app/build.gradle` 已 `git checkout`（shim 不入库）；worktree 已建于 scratchpad（未污染主仓）。
**设备终态**：**5556 = 确定 HEAD 包**（行号 130 实证、GeeGuard 正常、登录态在）；**5554 = 老版本包 2.14.1**（为 TC-G3 所装，**用户 16:15 装的那个 `GeeGuardTokenGate` 分支包已被覆盖**——如需保留请告知，可从 `apks/` 重装）。
**构造包留档**（均在 `debox-android/apks/`）：`debox-e1-noappid.apk`、`debox-f3-noso.apk`、`debox-2.14.1-pre-geeguard.apk`、`debox-head-verified.apk`（**行号 130 已验证的确定 HEAD 包**）。

### ⚠️ 方法论教训（本轮最大价值）

**「需要专门出包 / 需要特定设备」不等于「测不了」**——§7.7 允许 AI 自主构建，很多"外部依赖"阻塞其实是**构造方式没想到**：
- TC-E1 只需改一行 `local.properties`（配置，非代码）；
- TC-F3 的本质是「SO 加载失败路径」，**不必真有 x86_64 设备**——在 arm64 上把 arm64 的 SO 排除掉，走的是**同一条代码路径**，等效且更易构造。

**而这条"等效构造"直接挖出了本轮唯一的 P0 FAIL** —— 该路径此前被判「由单测 + `catch(Throwable)` 静态代验」而跳过实跑，**静态代验给出了错误的安全结论**（单测 mock 掉了真实 SDK，测不出 SDK 异步线程崩溃）。

---

## 第 4 轮 — 白名单生效后解阻补跑（2026-07-14 15:24–16:00）

> **触发原因**：用户在极验后台按 geeID 完成模拟器加白 → 补跑第 3 轮标记的全部「风控拦截」阻塞项。
> **白名单生效时间线（重要，供运维参考）**：加白后 **15:25 / 15:26 两机 `app_login` 仍返 `-2051`**（未生效）→ **15:32 转绿**（`app_login ok channel=JC`）→ **白名单实际生效延迟约 5–10 分钟**（平台缓存），加白后不要立刻判失败。

### 第 4 轮结果

| # | 用例 | 设备 | 操作/证据 | 结果 |
|---|------|------|-----------|------|
| **TC-I4** | 白名单生效（P0，前 3 轮全程阻塞） | 5556 + 5554 | 加白 → 冷启动 + 点重登横幅 | ✅ **PASS（本轮解阻核心）**：5556 **15:32 `app_login ok channel=JC`**、5554 **15:33 `app_login ok`**；请求 body 仍精确携带 `gee_token`（本次登录命中 9 次）→ **加白后放行、拦截解除，白名单机制端到端验证成立**。**这同时反证 TC-F4 的拦截是真实风控行为（加白即放行），而非后端故障** |
| **TC-C2** | 密码房加入（P1，两轮阻塞） | 5556 建房 + 5554 加入 | 5556 建密码房 `39daby5f`（`join_type:1`，房间密码实际值不留档，公开Live 已关）→ 5554 深链 → 输密码 → 加入 | ✅ **PASS（客户端 + 后端双绿）**：join body 中非空 `password` 与非空 `gee_token` 正确共存（实际值均不留档），后端 **`code:1` 成功进房**（5556 房内出现听众「哈哈」实证）。第 1 轮「由 TC-C1 空串代验」的推断**已由实跑证实** |
| **TC-C5** | token 未就绪时加入（P1，两轮阻塞） | 5554 | `force-stop` → 冷启动同时立即深链（<1s）→ 观察 join 时序 | ✅ **PASS（记录制，非缺陷）**：**未就绪窗口用户不可达**——冷启动到 `submitReceipt ok` 仅 **2.3s**（15:54:03.4 启动 → 15:54:05.7 就绪），而深链在 App「连接中…」期间**被排队**，join 只在 IM 连上后才发出，此时 token **必已就绪**。实测 join body **正常携带 `gee_token`**、后端 `code:1`。→ 与 TC-I2 同类结论：**代码兜底分支存在（TC-S1/S4 已验），但真实用户触达不到该窗口** |
| **crash 复扫** | 全轮 | 三机 | `logcat -b crash` | ✅ **PASS**：5554 / 5556 / 三星 `FATAL EXCEPTION` 均 **0** |

**测后清理**：密码房 `39daby5f` 已结束（SpaceFinishActivity 确认），两台模拟器回主页；三机 crash 0。

### ⭐ 关键证据⑤：TC-C2 密码房 join —— `password` 非空与 `gee_token` 共存（P1 解阻）

```
url：https://t.debox.pro/debox/liveroom/join
{"room_id":"39daby5f","password":"<已脱敏：非空>","gee_token":"<已脱敏：非空>"}
→ 后端 "code":1   ← 成功进房（5556 房内显示听众「哈哈」）
```

**结论**：密码房分支与普通加入**走同一 `JoinSpaceDialogFragment` 注入路径**，`password` 字段非空**不影响 `gee_token` 注入**。至此 `liveroom/join` 的**全部进房形态**（普通/密码房/房主直接进房）均已实测覆盖。

### ⭐ 关键证据⑥：白名单生效（TC-I4）— 加白前后对照

| 时间 | 设备 | `app_login` 结果 | 说明 |
|---|---|---|---|
| 15:05 | 5556 | ❌ `-2051` ×3 | 加白前，被风控拦截 |
| 15:25 | 5556 | ❌ `-2051` | **加白后 0–5 分钟内仍拦**（平台缓存未刷新） |
| 15:26 | 5554 | ❌ `-2051` | 同上 |
| **15:32** | **5556** | ✅ **`app_login ok channel=JC`** | **白名单生效**，放行 |
| **15:33** | **5554** | ✅ **`app_login ok`** | 同上 |

→ **加白 → 生效延迟约 5–10 分钟**。生效后登录请求仍带 `gee_token`（未因加白跳过采集）→ **白名单是服务端放行策略，不改变客户端行为**，符合设计。

---

## 第 3 轮 — HEAD 包复跑三星 + 模拟器 HEAD 包回归（2026-07-14 14:36–14:53）

> **触发原因**：用户指令「继续测试，红包不用测试」+「模拟器也需要测试」→ 执行阻塞清单中唯一不依赖后端/gas 的项：**三星 HEAD 包复跑（闭合"版本归因偏差"）**，并将同一 HEAD 包覆盖到两台模拟器回归。
> **构建归因链（§7.7）**：源码 HEAD `7d9ad736`（工作区仅 ChatLiveView.kt 3 行未提交改动，与 GeeGuard 无关）→ `:app:assembleAppDebug`（react shim 构建后已还原）→ APK sha256 前缀 `ae507f3d9ee88946`，versionName **2.14.2** / versionCode 21400002，签名 SHA-256 `2ee89168…` 与三星已装 2.15.0 包**完全一致**（安装预检 I-89 通过，非降级）→ `install -r` 保留数据。
> **三星原 2.15.0 包已备份**：`debox-android/apks/debox-2.15.0-samsung-backup.apk`（如需还原 `adb install -r` 即可）。

### 第 3 轮结果

| # | 用例 | 设备 | 操作/证据 | 结果 |
|---|------|------|-----------|------|
| **TC-F1**（HEAD 复跑） | 真机 arm64 × HEAD 构建 | 三星（HEAD 2.14.2） | 冷启动 `am start -W` | ✅ **PASS**：**行号探针 `GeeGuardManager.kt:130` 命中 HEAD**（2.15.0 是 148）；`submitReceipt ok, respondedGeeToken.length=348`；初始化 `main=false` 2ms；TotalTime 2414ms；crash 0；登录态保留 |
| **TC-C4**（HEAD 复跑） | 房主直接进房分支 × HEAD 构建 | 三星（HEAD 2.14.2） | 建房 `krdsbfte`（`create` 返 `code:1`）→ 杀进程 → 恢复弹窗点「取消」→ 深链 → 命中 creator==self 分支 | ✅ **PASS**：join body 含正常形态、非空 `gee_token`（实际值不留档）→ 后端 **`code:1` 成功进房**。**⚠️ 版本归因偏差正式闭合**：HEAD 代码在真机 arm64 上端到端双绿 |
| **TC-A1/F5**（HEAD 回归） | 模拟器 HEAD 包冷启动 | 5554(root) + 5556 | `install -r` HEAD 包 → 冷启动 | ✅ **PASS**：两机均 `submitReceipt ok, length=392`、行号 130、crash 0（5554 init 6ms / 5556 init 7ms；TotalTime 2569 / 2283ms）；**5554 root 机同时复证 TC-F5 于 HEAD 构建** |
| **TC-C1 模拟器侧补跑** | 5554 深链加入 `krdsbfte` | 5554 | 深链 → `liveroom/info` | ⏸️ **仍阻塞（后端 DID）**：`liveroom/info` 返 `-2018`，5554 掉 DID 登录态（红条）→ join 弹窗无法出现。**非 GeeGuard 问题**；客户端注入已在第 1 轮两次实测 + 本轮三星 HEAD join 实证，等 DID 恢复只是锦上添花 |

**测后清理**：三星「...」→「关闭语音房」→ 确定，房 `krdsbfte` 已结束（SpaceFinishActivity 确认），回到主页；全轮三机 crash buffer = 0。

**第 3 轮后阻塞面变化**：「构建归因」项**解除**（阻塞清单同步更新）；后端 DID 仍未恢复（14:36 探测 ×9、14:51 `-2018` 实证），A 层 TC-C2/C5 继续由 /loop 监工等待。

### ⭐⭐ 第 3 轮追加（15:04–15:15）：模拟器登录失败**根因改判 → 服务器风控拦截模拟器**（TC-F4 后端判定实锤）

> **触发**：用户告知「模拟器登录不成功就是被服务器拦截了，-2051 就是」→ 立即实测验证。

**实测证据链（5556，15:05）**：
1. 点「DID已退出登录」横幅触发重登 → **`app_login` 返 `code=-2051`（连续 3 次）**：
   `http request fail,method=POST,url=app_login,code=-2051,message=系统繁忙，请稍后再试!` → `app_login fail code=-2051`
2. **被拦请求 body 里 `gee_token` 完好**（正常形态、非空，与 `address`/`signature` 共存；实际值不留档）→ **不是客户端没带 token，是服务端按设备指纹判定后拒绝**。
3. **同环境对照**：三星真机（同 `t.debox.pro`、同 HEAD 包）14:48 `liveroom/join` 返 `code:1` 成功 → 拦截**只针对模拟器**。
4. 伴生码：`account_state` 返 `-2023`（`token_valid:false`）、`liveroom/info` 返 `-2018` → 与 `-2051` 同源（登录态被风控作废后的连锁反应）。

**结论改判**：
- 第 1/2/3 轮记录的「测试环境后端未开 / DID 服务不稳定」（`-2051`/`-2018`/`-2023`）**根因实为 GeeGuard 风控拦截模拟器**——第 1 轮 TC-F4 所记「后端 `emulate_check:2` 已下发」的策略**真实生效了**。
- **TC-F4（模拟器风控识别）后端侧判定达成**：模拟器登录/业务被服务端拒绝（实际表现=拦截），真机放行。客户端侧（SO 加载、token 正常携带）第 1 轮已验 → **TC-F4 由 ⚠️ 部分通过升级为 ✅ PASS（拦截生效侧）**。
- 此前「后端恢复」的表象（12:54 语音房 join 成功）是**真机/放行窗口**，与模拟器被拦不矛盾。

**两台模拟器 geeID（供极验后台按 root_id 加白，风险码 60113）**：
| 设备 | geeID |
|---|---|
| emulator-5556（Pixel 16k，账号「请输入昵称1」） | 已复制并通过安全渠道交给运维，文档不留存实际值 |
| emulator-5554（debox_root，账号「哈哈」） | 已复制并通过安全渠道交给运维，文档不留存实际值 |

（取法：admin 页 `btnCopyGeeId` → 页内「输入链接」框粘贴读取 → 已清空复位；与 TC-I1 当时值一致 → **geeID 跨会话稳定**，可放心加白。）

**下一步（等用户加白后）**：
1. 重跑 5556/5554 登录（点横幅重登）→ 预期 `app_login ok` → **TC-I4 白名单生效实测达成**；
2. 白名单生效后 A 层 TC-C2（密码房）/ TC-C5（token 未就绪）即可解阻实跑；
3. `/loop` 探测口径更新：`-2018/-2023/-2051` = 风控拦截（非后端故障），加白前探测不会转绿。

---

## 第 2 轮 — 真机补测（2026-07-14 12:47–13:20）

> **触发原因**：用户接入并解锁两台真机 + 测试环境后端恢复 → 补跑第 1 轮的真机/后端阻塞项。
> **新增设备**：
> - **L3 三星 SM-S9210**（`RFCYA0F9SSZ`）：真机 arm64，**DeBox 2.15.0**（比模拟器 2.14.2 新，versionCode 重号 21400002），测试环境 `appEnv=dev`，账号「请输入昵称1」Lv.16（**与 5556 同账号**）
> - **L3 小米 25067PYE3C**（`402714f0`）：真机 arm64 MIUI，DeBox 2.14.2，**正式环境** → **全程只读**（仅冷启动 + logcat 观察，零 UI 操作、零业务触发、不抓 body），仅作 ROM 兼容佐证
>
> ✅ **测试环境后端已恢复**（12:54 实证：`liveroom/join` 返 `"code":1` 成功，不再是 `-2051`）。

### 第 2 轮结果

| # | 用例 | 设备 | 操作/证据 | 结果 |
|---|------|------|-----------|------|
| **TC-F1** | 主流真机矩阵（arm64） | 三星 2.15.0 | 冷启动 → logcat | ✅ **PASS**：`submitReceipt ok, respondedGeeToken.length=348`（正常形态）；初始化 `main=false` 耗时 **2ms**；无 crash |
| **TC-F1**（补） | 第二 ROM（MIUI）佐证 | 小米（只读） | 冷启动 → logcat | ✅ **PASS（兜底侧）**：初始化 `main=false` 耗时 5ms；**`submitReceipt failed status=-300, degrade to geeToken.length=4772`** → 真机上**自然复现极验服务不可达 → 降级 fail-open**，App 正常运行、**0 crash**（见下方「⭐ 关键证据④」） |
| **TC-C4** | 房主直接进房分支（P0，第 1 轮阻塞） | 三星 | 杀进程 → 恢复弹窗点「取消」→ 深链拉起自己进行中的房 → 命中 `creator==self && status==1` 分支 | ✅ **PASS（客户端 + 后端双绿）**：见「⭐ 关键证据③」——body 带 `gee_token`，后端 **`"code":1` 成功进房** |
| **TC-C1** | 普通用户加入语音房 | 5554 + 三星 | 5554 深链加入三星的房，抓 `liveroom/join` | ✅ **PASS（升级判定）**：客户端注入第 1 轮已两次抓包实证；本轮 **`liveroom/join` 接口的后端成功由 TC-C4 同接口证明**（`code:1`），判定成立 |
| **TC-A5** | 弱网采集（P1，第 1 轮未跑） | 5556 | `emu network speed gsm` + `delay gprs` → 冷启动 → 30s 观察 | ✅ **PASS**：弱网下仍 `submitReceipt ok, respondedGeeToken.length=392`；**ANR=0、crash=0**（取缓存 O(1) 不阻塞）；测后网速已复原 |
| **TC-E2** | 极验服务端故障降级 | 小米（真机自然复现） | 见 TC-F1 补 | ✅ **PASS（真机侧升级）**：第 1 轮为飞行模式等效验证，本轮小米上**真实网络环境下自然降级**，证据更强 |
| **TC-D1/D2/D3** | 红包 3 接口（P0） | 三星 | 测试群「担保师测试群」发最小额红包（1 BOX / 1 人） | ⏸️ **阻塞（链上 gas）**：发红包走 `lucky_box/onchain_box/create` → **合约 `execution reverted: 0x8baa579f`**（BOX 是 BNB Chain token，发红包需链上交易 + gas，测试账号 gas 不足）→ **红包未发出、无资金变动**，无可领红包 → 3 个领取接口无法实跑。**非 GeeGuard 缺陷**；注入点由 TC-S1/S2 静态 + 单测覆盖 |
| **TC-F5** | root 设备 | 5554 | 见第 1 轮 | ✅ PASS（已验） |
| **TC-H3** | 后台无周期请求（P2） | 5556 | App 挂后台静置 **10 分钟** → 统计 GeeGuard 活动 | ✅ **PASS**：期间 `PRETTY_LOGGER-GeeGuard` 日志 **0 条**、极验相关请求 **0 次**，App 进程仍存活 → **无周期性网络请求**（仅业务触发时刷新），符合设计 |
| **TC-H1** | 冷启动耗时（真机） | 三星 | `am start -W` ×3 | ✅ **PASS**：TotalTime **2687 / 2643 / 2638 ms**（真机比模拟器稳定得多）；GeeGuard 任务仅占 **2ms**，影响可忽略 |

### 第 2 轮追加尝试（13:30–13:40）：密码房 TC-C2 —— 受后端 DID 不稳定阻塞

尝试补测 **TC-C2 密码房加入**（P1，验 `password` 非空时与 `gee_token` 共存）：

- **三星**：建密码房过程中**充电满 100% 自动息屏 → Bouncer 安全锁**（adb 不可绕，白名单 §7.6.2 人工环节），流程中断。
- **改用双模拟器**（5554「哈哈」+ 5556「请输入昵称1」，永不锁屏）：两台**都反复掉 DID 登录态**——`app_login` 请求被后端 **`-2023 / -2018 系统繁忙`** 拒绝，重登后立刻又掉，陷入循环。

**根因诊断**（已排除 GeeGuard）：
- 后端 nginx 层 `liveroom/info` 返 **200**（连通正常），但**业务层 DID token 校验持续 `-2023 系统繁忙`** → **测试环境后端 DID 服务仍不稳定**（第 2 轮开头恢复的是语音房，DID 鉴权链路未完全稳定）。
- 同期 logcat **无任何 GeeGuard 异常**（`submitReceipt` 正常 / 无 crash）→ **与 GeeGuard 无关，是后端环境问题**。

**处置**：
- TC-C2 需两台稳定登录态设备联测，当前后端状态下**不可靠执行** → 标 ⏸️ **后端阻塞**。
- **增量价值已被 TC-C1 覆盖**：TC-C1 抓包 body 为 `{"room_id":"eofzpxkz","password":"","gee_token":"..."}` —— **`password` 字段已与 `gee_token` 共存**（此处为空串；密码房仅是 `password` 非空，**走同一 `JoinSpaceDialogFragment` 注入路径，不影响 `gee_token` 注入逻辑**）。故 TC-C2 由 TC-C1 同接口代验，后端稳定后补跑 `password` 非空场景即可。

### ⚠️ 版本归因偏差（必须知悉，影响三星结论的适用范围）

**三星装的包不是当前工作区代码构建的**：

| 证据 | 三星 | 模拟器 / 小米 / 当前工作区 HEAD |
|---|---|---|
| versionName | **2.15.0** | 2.14.2 |
| GeeGuard 日志所在行 | `GeeGuardManager.kt:148` | `GeeGuardManager.kt:130`（与 HEAD `grep -n` 完全一致） |

→ 三星上的 `GeeGuardManager` 有额外改动（2.15.0 分支），**其测试结论严格来说对应 2.15.0 的代码，而非本轮静态审计的 HEAD 代码**。
行为表现一致（`submitReceipt ok` / body 带 `gee_token` / 后端 `code:1`），说明 2.15.0 的 GeeGuard 逻辑**向后兼容**，但：

- ✅ **可用作**「GeeGuard 在真机 arm64 上能正常工作」的证据；
- ⚠️ **不可用作**「当前 HEAD 代码在真机上通过」的证据——两者是不同的构建。
- **建议**：发版前用 HEAD 构建的包在三星上重跑一次 TC-F1 + TC-C4（`§7.7 被测包自主构建` 流程），消除该偏差。

（此项呼应 06-18 图灵盾轮的血泪教训：**「拿旧包/异版本包测新逻辑」是假阴性/假阳性的头号来源**。）

### ⭐ 关键证据③：TC-C4 房主直接进房（真机 + 后端双绿，P0 解阻）

第 1 轮该分支因后端 `-2051` 阻塞。本轮在三星上构造出触发条件（**杀进程 → 恢复弹窗点「取消」→ 深链**，使 App「不在房内但房间仍进行中」，命中 `JoinSpaceDialogFragment:123-129` 的 `space.status==1 && creator.user_id==自己` 分支）：

```
url：https://t.debox.pro/debox/liveroom/join
requestBody's content :
{"room_id":"xdnotkwb","password":"","gee_token":"<已脱敏：非空>"}
→ 后端 "code":1   ← 成功进房
```

**结论**：房主直接进房分支（第 2 个 `liveroom/join` 注入点）**精确携带 `gee_token`，后端接受并成功进房**。至此 `liveroom/join` 的**两个注入点全部实测覆盖**（普通加入 = JoinSpace:388 / 房主直接进房 = JoinSpace:129）。

### ⭐ 关键证据④：小米真机自然降级（fail-open 真实环境验证）

小米（MIUI，正式环境，**全程只读**）冷启动：

```
✓ 极验GeeGuard设备指纹初始化 [main=false] 完成，耗时: 5ms
submitReceipt failed status=-300, degrade to geeToken.length=4772
FATAL EXCEPTION 计数 = 0
```

**价值**：第 1 轮的降级验证靠飞行模式**人为构造**；本轮在**真实网络环境的真机上自然复现**了极验服务端不可达（`status=-300`）→ 客户端自动降级到本地 geeToken（4772 字符）→ **App 无 crash、正常可用**。这是 fail-open 设计最有说服力的一条证据。

### 三设备形态长度汇总（供后端 TC-G4 对齐）

| 设备 | 形态 | 长度 |
|---|---|---|
| 模拟器 5554/5556（arm64 AVD） | 正常 respondedGeeToken | **392** |
| 三星 SM-S9210（真机 arm64, 2.15.0） | 正常 respondedGeeToken | **348** |
| 模拟器 5556（飞行模式） | 降级 geeToken | **4132** |
| 小米（真机 MIUI，极验不可达） | 降级 geeToken | **4772** |

→ 正常形态 **~350–400 字符**（**非文档所称「~1K」**），降级形态 **~4.1–4.8K**（与文档「~4K」吻合）。**长度随设备环境浮动，判形态必须看日志语义而非长度阈值。**

---

## 第 1 轮（2026-07-14 11:19–12:00）

> **设备**：L1 = emulator-5556（Pixel 16k，测试号「请输入昵称1」Lv.16）/ L2 = emulator-5554（debox_root，测试号「哈哈」Lv.2）
> **被测包**：DeBox 2.14.2（versionCode 21400002），含 GeeGuard AAR v2.7.1.1
> **环境**：两机均在测试环境 `t.debox.pro`（管理员页「开发环境」打勾实证 + 请求 header `appEnv=dev`）
> **触发原因**：首次测试（GeeGuard 替换腾讯图灵盾 RCE 的接入专项）
>
> ⚠️ **本轮关键环境约束（影响判定口径）**：**测试环境后端服务未完全开启**（用户 11:40 告知）。
> 实证：`liveroom/join` 返 `-2051 系统繁忙，请稍后再试`、`liveroom/create` 在 Lv.2 账号返 `-2610`。
> 因此凡涉及**后端业务成功**的判定（进房成功/领取成功/风控拦截）本轮**无法定论**；
> 但**客户端侧注入**（body 是否精确携带 `gee_token`）不依赖后端成功——请求已发出即可抓取验证，
> 本轮据此完成了核心待测面的验证。判定时严格区分「客户端注入 ✅」与「后端业务 ⏸️」两侧。
>
> **模拟器噪声预检**：`t.debox.pro` 解析为 `198.18.0.70`（落在 `198.18.0.0/15`）→ **命中宿主 fake-IP 代理噪声**。
> 按 TEST_GUIDE §7.3，本轮**网络层结论**（DNS/连接目标）不作定论；但本轮结论均为
> **App 内注入逻辑 / 日志 / UI 行为**，属「模拟器即终局」范畴，不受该噪声影响。

### 结果总表

| # | 用例 | 操作/证据 | 结果 | 备注 |
|---|------|-----------|------|------|
| **TC-S1** | 5 接口注入模式一致 | 源码核对 5 处注入点 | ✅ PASS | 均为「`getDeviceToken()` 缓存读取 + `refreshToken()` + `if (isNotEmpty) 注入`」同一模式 |
| **TC-S2** | 字段名拼写核验 | `grep '"gee_token"'` 全仓 | ✅ PASS | 精确 5 处、拼写一致，无驼峰混用：AppCacheManager:469 / LuckyBox:177,213 / JoinSpace:129,388 |
| **TC-S3** | ABI 打包核验 | app/build.gradle:74-78 | ✅ PASS | 仅 `arm64-v8a` + `armeabi-v7a`；x86/x86_64 已注释关闭（TC-F3 前提成立） |
| **TC-S4** | 单测回归 | `:business:BaseBusiness:testDebugUnitTest --tests "*GeeGuard*"` | ✅ PASS | GeeGuardManagerTest **9 tests / 0 failures / 0 errors**（decideToken 状态矩阵、空 token 不注入、未 init no-op 均覆盖） |
| **TC-S5** | 日志语句静态审计 | 通览 GeeGuardManager.kt 全部日志 | ✅ PASS | 9 条日志语句只打长度/状态码/服务端错误原文，**无 token/geeID 内容输出** |
| **TC-A1** | 配置确认（AppID 已配 + 正常采集） | 两机冷启动 + logcat | ✅ PASS | **无** `not configured` 告警；`submitReceipt ok, respondedGeeToken.length=392`（5554 11:19:39 / 5556 11:30:21） |
| **TC-A2** | 初始化不拖慢启动 | StartupTaskManager 日志 | ✅ PASS | `✓ 极验GeeGuard设备指纹初始化 [main=false] 完成，耗时: 3ms`（子线程、ms 级）；首页正常展示 |
| **TC-A3** | 断网启动降级 | 5556 飞行模式冷启动 + 30s 观察 | ✅ PASS | App 不 crash、可用；`submitReceipt failed status=-300, degrade to geeToken.length=4132` → **降级路径生效** |
| **TC-A4** | 断网恢复后刷新 | 恢复网络 → 冷启动 | ✅ PASS | 重新采集为正常形态 `respondedGeeToken.length=392`（非降级），缓存已刷新 |
| **TC-A6** | 杀进程重采集 | force-stop → 冷启动 | ✅ PASS | 每次冷启动都重新走 register/submitReceipt → **token 不跨进程持久化**（设计预期） |
| **TC-B1~B5** | 登录/注册 body 注入 | 5556 DID 掉线后**自然重登**（未主动登出）抓 `app_login` | ✅ PASS（**意外实证**） | 见下方「⭐ 关键证据①」——`app_login` body 精确携带 `gee_token`（正常形态），且与 `solana`/`tron` 多链字段共存（TC-B5 一并覆盖）；`app_login ok channel=JC` 登录成功 |
| **TC-C1** | 普通用户加入语音房 | 5556 建房 `eofzpxkz` → 5554 深链加入 → 抓 `liveroom/join` | ⚠️ **部分通过**（客户端 ✅ / 后端 ⏸️） | 见「⭐ 关键证据②」——body **精确携带 `gee_token`**，与 `room_id`/`password` 共存 → **客户端注入验证达成**；后端返 `-2051 系统繁忙`（测试环境未开）→ 「进房成功」待后端 |
| **TC-C4** | 房主直接进房分支 | — | ⏸️ 阻塞 | 后端 `-2051`；该分支（JoinSpace:123-129）与 TC-C1 注入点**同文件同模式**，由 TC-C1 + TC-S1 代验 |
| **TC-C2/C3/C5/C6** | 密码房/付费房/未就绪/拦截 | — | ⏸️ 阻塞 | C2/C3 依赖后端建房与资金；C5 需极短未就绪窗口；C6 需后端下发拦截策略 |
| **TC-D1~D7** | 红包 3 接口 | — | ⏸️ 阻塞 | 后端未开 + 测试号余额不足以发红包；注入点（LuckyBox:177/213）与 C 组同模式，静态已验（TC-S1/S2） |
| **TC-E2** | 极验服务端故障模拟 | 飞行模式已等效验证（TC-A3） | ✅ PASS（等效） | 降级形态实测 **4132 字符**（与文档「~4K」吻合）；未额外做 L2 iptables 注入（飞行模式已覆盖同一 fail 分支） |
| **TC-E4** | 全程无 crash | 全轮 logcat + crash buffer 扫描 | ✅ PASS | 两机 `FATAL EXCEPTION` 计数 = **0**；无 GeeGuard/geetest/gtcore/UnsatisfiedLinkError 相关 crash |
| **TC-E1/E3** | AppID 未配置包 / 后端真拦截 | — | ⏸️ 阻塞 | E1 需专门出包（gate 逻辑由 TC-S4 单测代验）；E3 需后端下发拦截策略 |
| **TC-F4** | arm64 模拟器风控识别 | 全轮 L1/L2 均为 arm64 AVD | ⚠️ 部分通过 | SO 正常加载、token 正常携带（392 正常形态，**未被降级**）→ 客户端侧无异常；后端 `emulate_check:2` 已下发，**实际拦/放行判定待后端**（本轮记录实际表现，不预设） |
| **TC-F5** | root 设备 | 5554（debox_root，userdebug） | ✅ PASS | GeeGuard 正常初始化并采集（392）；**App 全程正常运行、无 `System.exit -5`** → 复核结论见「知识库回写」 |
| **TC-F1/F2/F3** | 真机矩阵 / 32 位 / x86_64 | — | ⏸️ 阻塞 | F1 需真机人工解锁（白名单 §7.6.2）；F2 无 armeabi-v7a 设备；F3 宿主为 Apple Silicon 无法跑 x86_64 AVD（兜底逻辑由 TC-S4 + catch(Throwable) 静态代验） |
| **TC-G4** | 后端双形态解析确认 | 客户端侧已产出两种形态证据 | ⚠️ 部分通过 | 正常形态 **392 字符** + 降级形态 **4132 字符**（均已实测留证）→ **移交后端**核对风控日志能否解析两种形态 |
| **TC-G1/G2/G3** | 老版本兼容 / 覆盖升级 | — | ⏸️ 阻塞 | 无线上老版本 APK 在手；留待发版前专项 |
| **TC-H1** | 冷启动耗时 | `am start -W` ×3 | ✅ PASS（记录制） | TotalTime 709 / 2164 / 2361ms（波动大）；**GeeGuard 任务仅占 3ms**，对启动耗时影响可忽略 |
| **TC-H2** | 主线程无卡顿 | 全流程观察 | ✅ PASS | 初始化 `main=false`；无 ANR 对话框、无 GeeGuard 相关主线程告警 |
| **TC-I1** | 复制设备指纹 | admin 页点 `btnCopyGeeId` → 粘贴验证 | ✅ PASS | toast「复制成功」；剪贴板得到**非空 geeID**（实际值不留档）→ 已通过安全渠道交给运维按 root_id 加白 |
| **TC-I2** | 指纹未就绪提示 | 断网降级态进 admin 页点复制 | ⚠️ 无法构造（非缺陷） | 断网降级态下 **geeID 仍有值**（toast 仍是「复制成功」）→ **降级路径下设备指纹依然可用**（对运维加白是好事）。真正的「未就绪」窗口仅在 register 完成前的极短时间（<2s），进 admin 页操作耗时远超它，**用户实际不可达**；代码分支存在（SettingAdminActivity.kt:116）由静态核验确认 |
| **TC-I3** | 日志不泄露 token | 全轮 logcat 审计 + 代码核实 | ⚠️ **部分通过 → 见 FINDING-001** | **① GeeGuard tag 日志干净**：token 内容命中 **0 次**，只打长度 ✅；**② 但 `http_log_interceptor` 把 `gee_token` 全量明文打进日志**（命中 6 次）——与用例文档「`app_login` body 已脱敏」的预期**不符** |
| **TC-I4** | 白名单生效 | — | ⏸️ 阻塞 | 需先有拦截（TC-E3）+ 运维极验后台配合 |

**第 1 轮统计**：✅ PASS **15** / ⚠️ 部分通过 **5** / ⏸️ 阻塞 **13** / ❌ FAIL **0**
**六轮合并统计**：✅ PASS **31**（第 3 轮 F4；第 4 轮 I4/C2/C5；第 5 轮 E1/E3/G1/G2/G3；**第 6 轮 Phase 6 全量回归全绿，无新增用例**） / ⚠️ 部分通过 **2**（TC-I2 无法构造、TC-I3→FINDING-001，**其 release 边界已真机实证**） / ⏸️ 阻塞 **10**（链上 gas 6 = D1~D3/D5~D7（**用户指示跳过红包**）；测试数据 2 = C3/D4；后端拦截策略 1 = C6；设备缺失 1 = F2。另 G4 为 ⚠️ 部分通过不计入） / 🔴 **FAIL 1 — TC-F3（FINDING-002，P0，用户指示暂不修复）**

> **Phase 6 验收结论**：核心用例（S4 单测 9/9、A1/A2/A6/E4 双机冷启动、C1 注入链路）**全部回归通过**，行号探针确认两台模拟器均运行 HEAD 代码，crash 全程 0。**除 TC-F3（FINDING-002）外，GeeGuard 接入功能面可验收。**

> **第 4 轮解阻 3 条**：TC-I4（白名单生效）、TC-C2（密码房）、TC-C5（token 未就绪）。**「风控拦截模拟器」类阻塞已全部清空**。
> **第 5 轮解阻 5 条 + 挖出 1 个 P0 FAIL**：E1（自建无 AppID 包）/ E3（复盘：加白前即真拦截）/ G1/G2/G3（自建 pre-GeeGuard 老版本包）**全部 PASS**；**TC-F3 等效构造 → ❌ FAIL（FINDING-002：SO 加载失败必崩，`catch(Throwable)` 兜底无效）**。

> **5 个接口的 body 注入覆盖情况**：`app_login` ✅ 实测 / `liveroom/join` ✅ 实测（**两个注入点全覆盖**：普通加入 + 房主直接进房）/ 红包 3 接口 ⏸️（链上 gas 阻塞，静态 + 单测已覆盖）。
> **即 5 个接口中 2 个已端到端实测（含后端 `code:1` 成功），3 个待 gas 解除后补跑。**

---

### ⭐ 关键证据①：`app_login` body 携带 gee_token（TC-B 组，意外实证）

原计划 B 组因「登录/注册命中危险清单（禁登出）」全组走静态代验。实际执行中 **5556 的 DID 自然掉线**（顶部横幅「您的DID已退出登录，点此重新登录」），点该横幅**触发的是重登、非登出**，不破坏钱包数据、不产生新账号——安全地拿到了 `app_login` 的真实抓包：

```
url：https://t.debox.pro/debox/app_login
params：{"address":"<已脱敏>","chain_id":1,"signature":"<已脱敏>",
        "gee_token":"<已脱敏：非空>",
        "solana":{...},"tron":{...}}
→ [FLogger:Login]: app_login ok channel=JC   ← 登录成功
```

**结论**：`gee_token` 精确注入 `app_login` JSON body，与 `solana`/`tron` 多链字段共存互不影响（**TC-B1/B2/B5 一并覆盖**），登录成功。这是 5 个接口中最难安全触发的一个，本轮意外闭环。

### ⭐ 关键证据②：`liveroom/join` body 携带 gee_token（TC-C1，本轮核心待测面）

5556（Lv.16）建测试房 `autotest-geeguard`（roomId=`eofzpxkz`，已关「公开Live」）→ 5554 经深链 `debox://open/share?type=live&id=eofzpxkz` 弹「现在加入」→ 确认加入：

```
url：https://t.debox.pro/debox/liveroom/join
requestBody's content :
{"room_id":"eofzpxkz","password":"","gee_token":"<已脱敏：非空>"}
→ 后端 code=-2051, message=系统繁忙，请稍后再试   ← 测试环境后端未开
```

**结论**：客户端 body **精确携带 `gee_token`**（非空、正常形态），且与 `room_id`/`password` 字段共存（**TC-C2 的 password 共存点一并佐证**）。后端 `-2051` 属环境未就绪，**不是客户端缺陷**——join 失败后 UI 正常回退到主页、未 crash、未卡死弹窗（TC-C6 的「失败可恢复」侧面得证）。

### 形态长度实测（口径修正，供后端对齐）

| 形态 | 触发条件 | 实测长度 | 日志行 |
|---|---|---|---|
| 正常 respondedGeeToken | 网络正常，status=200 | **392 字符** | `submitReceipt ok, respondedGeeToken.length=392` |
| 降级 geeToken | 断网，status=-300 | **4132 字符** | `submitReceipt failed status=-300, degrade to geeToken.length=4132` |

⚠️ **与用例文档口径的偏差**：文档称正常形态「**长度约 1K 字符**」，实测为 **~392 字符**（降级形态 ~4K 与文档吻合）。
不影响功能判定（两种形态区分度依然显著），但**文档的「~1K」描述应更正为「~400」**，否则后续测试按长度判形态会误判。已在 `cases.md` 基线 §9 标注：**判定以日志行语义为准**（`submitReceipt ok` = 正常 / `degrade to geeToken` = 降级），长度仅作参考。

---

## 🔴 FINDING-002 — SO 加载失败时 **App 崩溃**，`catch(Throwable)` 兜底**无效**（TC-F3 → ❌ FAIL）

**关联用例**：TC-F3（x86_64 兜底，P0）
**状态**：🔴 **待修复（P0/P1）** — 本轮**唯一 FAIL**，且**推翻了此前「静态代验通过」的结论**

### 现象（第 5 轮实测，2026-07-14 16:33）

构造：`packagingOptions` 排除 `lib/arm64-v8a/libgtcore.so`（**等效于 x86_64 设备上无对应 ABI 的 SO**）→ 装 arm64 模拟器 5556 → 冷启动：

```
FATAL EXCEPTION: Thread-25          ← 注意：GeeGuard SDK 内部自建线程，非主线程、非 register() 调用栈
Process: com.tm.security.wallet, PID: 23642
java.lang.UnsatisfiedLinkError: dlopen failed: library "libgtcore.so" not found
    at java.lang.System.loadLibrary(System.java:1765)
    at com.geetest.core.Core.<clinit>(SourceFile:1)          ← SDK 静态初始化块
    at com.geetest.core.r3.a(SourceFile:5)
    at com.geetest.core.w3.a(SourceFile:25)
    at com.geetest.core.w3$$ExternalSyntheticLambda2.run(D8$$SyntheticClass:0)   ← SDK 自己的 Runnable
    at java.lang.Thread.run(Thread.java:1572)
```

→ **App 直接崩溃、掉回桌面**（FATAL ×2，连崩两次）。**完全不可用**，不是「降级」。

### 根因（关键，颠覆原有假设）

`GeeGuardManager.kt:64-73` 的兜底代码：

```kotlin
try {
    GeeGuard.register(app, appId)
    ...
} catch (e: Throwable) {
    // 捕获 Throwable 而非 Exception 是刻意的：SO 加载失败抛 UnsatisfiedLinkError（Error），
    // 必须一并兜住，否则会逃逸到启动链路。          ← ⚠️ 这个假设是错的
    LogUtils.e(TAG, "GeeGuard.register crashed: ${e.message}")
}
```

**try-catch 只能兜住同步调用栈上的异常**。实测证明：
1. `GeeGuard.register()` **立即返回**——它把真正的初始化工作**丢到 SDK 自己的线程**（`Thread-25`）上；
2. `System.loadLibrary("gtcore")` 在**那个线程**里执行并抛 `UnsatisfiedLinkError`；
3. 该异常**不经过** `register()` 的调用栈 → **catch 块永远碰不到它** → 未捕获异常 → 进程 FATAL。

→ **注释里宣称的保护并不存在**。这不是「写法有瑕疵」，而是**兜底根本没生效**。

### 为什么此前没发现（教训）

- cases.md 原判：「F3 兜底逻辑由 **TC-S4 单测 + `catch(Throwable)` 静态代验**」→ ⏸️ 跳过实跑。
- **单测测的是 `GeeGuardManager` 自己的 try-catch，mock 掉了真实 SDK** → 测不出「SDK 在异步线程里崩」这一真实行为。
- **静态代验（读代码 + 单测）无法覆盖第三方 SDK 的异步崩溃路径**——必须真机/真包实跑。
- 呼应 06-18 血泪教训：**「读代码认为安全」≠「运行时安全」**。

### 真实影响面（决定定级）

| 维度 | 评估 |
|---|---|
| **触发条件** | `libgtcore.so` 加载失败：① x86/x86_64 设备（APK 只打 arm64-v8a + armeabi-v7a，TC-S3 已验）；② SO 被裁剪/损坏；③ 极端存储异常 |
| **后果** | **App 冷启动即崩，完全不可用**（非降级、非功能缺失） |
| **正常渠道概率** | **低**——Google Play 按 ABI 分发，x86 设备装不到该 APK |
| **真实风险场景** | 第三方市场分发 / 用户手动装 APK / ChromeOS / Windows Subsystem for Android / 未来若开启 x86 ABI / 渠道包 ABI 配置失误 |
| **当前线上是否受影响** | 大概率否（arm 设备为主），但**一旦命中即 100% 崩溃，无兜底** |

### 建议修复方向（**未改代码**，供开发定级）

`catch(Throwable)` 对异步 SDK 无效，需换思路（任选或组合）：
1. **主动预检 SO 可用性**：`init()` 里先 `System.loadLibrary("gtcore")` 试探（自己的调用栈里 catch 得住），失败则直接 `return`，**不调 `GeeGuard.register()`** → SDK 根本不会起线程；
2. **ABI 预检**：`Build.SUPPORTED_ABIS` 与 APK 实际打包的 ABI 比对，不匹配则跳过 register；
3. 向极验反馈：SDK 内部线程应自行捕获 `UnsatisfiedLinkError`，不应逃逸为 FATAL（治本，但依赖厂商）。

> ⚠️ 方案 1 需注意：`Core.<clinit>` 是 SDK 类的静态块，预加载同名 SO 成功后 SDK 内部再 `loadLibrary` 是幂等的；失败时我们提前退出即可。
> **若定级为需修复 → 必须走 `agent-dev-loop`**（建 `docs/implementation/` 任务目录 → plan → Codex plan review → 实现 → Codex 实现 review → 回写本文件），不直接改代码。

### 复现资料

- 构造包：`debox-android/apks/debox-f3-noso.apk`（HEAD 代码 + `packagingOptions` 排除 arm64 `libgtcore.so`；`app/build.gradle` 已 `git checkout` 还原，改动未入库）
- 复现：`adb install -r debox-f3-noso.apk` → 冷启动 → `logcat -b crash` 立现 FATAL
- **设备已复位**：5556 已装回正常 HEAD 包，GeeGuard 正常初始化、crash 0、登录态保留

---

## FINDING-001 — `gee_token` 在应用日志中全量明文输出（debug/内部包）

**关联用例**：TC-I3（P0）

**状态**：🟡 待开发定级（**非 release 线上风险**，但内部包存在凭证泄露面）

**现象**：
`PRETTY_LOGGER-http_log_interceptor` 打印请求 body 时，`gee_token` **全量明文**输出到 logcat（本轮 5556 单次登录即命中 6 次）。对比之下，同一条日志里 header 的 JWT **有脱敏**（`token=eyJhbG***70fw`），`Content-Sign` 也脱敏（`9827a7***316c`）——**保护是不对称的**。

用例文档 TC-I3 的预期是「`app_login` 的请求 body 在应用日志中已脱敏」，实测**未达成**。

**根因分析**（代码证据）：
1. 脱敏逻辑 `BaseLogInterceptor.kt:325-356` 的 `maskHeaderValue()` / `isSensitiveHeader()`，**调用点只有 4 处、全部在 header 循环**（第 56/58 行、212/214 行）。
2. body 打印路径（第 63-141 行 `params：...` / 第 137-140 行 `requestBody's content`）**从未调用脱敏函数**，全部原文输出。
3. 脱敏名单是「header **名**包含 auth/cookie/credential/key/password/secret/sign/token」的关键词匹配——`gee_token` 只出现在 **body** 里、从不作为 header 发送，**天然绕过**该名单。

**风险边界（重要，决定定级）**：
- **release 包不打印**：`LogUtils.open = BuildConfig.DEBUG`（LogUtils.kt:14），release 时 `LogUtils.d()` 短路 → 明文**不落 logcat**。线上用户无此泄露。
  - ✅ **2026-07-14 17:17 真机 release 包实证**（三星 SM-S9210，用户 16:52 自行装的 release 包，AI 只读观察）：冷启动后 `gee_token` 明文命中 **0 次**、`http_log_interceptor` 日志 **0 条**、`PRETTY_LOGGER` **0 条**、App 正常运行无 crash → **「release 不泄露」由推断升级为实证**。
- **但 debug / 内部分发包会全量明文打印** `gee_token`（以及 body 里其它凭证如 `signature`）。
- 次要：拦截器在 release 仍挂在 OkHttp 链上（RetrofitFactory.kt:97 无 DEBUG 门控），仍会 `readUtf8()` 拼完整字符串再丢弃 → 明文 token 仍会在 release 进程堆内存中被构造出来，且有无谓的性能/内存开销。

**建议修复方向**（供开发定级，**本轮未改代码**）：
把脱敏从「header-only」扩到 body 字段名匹配（`gee_token` / `signature` 等），或对 `LogInterceptor` 加 `BuildConfig.DEBUG` 门控使其在 release 根本不入链（顺带省掉 release 的 body 拷贝开销）。

**若定级为需修复 → 必须走 `agent-dev-loop`**（建 `docs/implementation/` 任务目录 → plan → Codex plan review → 实现 → Codex 实现 review → 回写本文件），不直接改代码。

---

## ~~🔁 后端 DID 恢复后自动补跑清单~~（**已完成，2026-07-14 16:00 关闭**）

> ✅ **本清单使命完成**：A 层 TC-C2/C5 已于第 4 轮加白后实跑通过；B 层红包 3 接口经**用户明确指示跳过**。
> 探测口径的最终结论：`-2018/-2023/-2051` **不是后端故障，是 GeeGuard 风控拦截模拟器**——加白即解除。/loop 探测（cron a18dc47e）可停。
> 以下保留原始探测记录作为归因链存档：

> `/loop 5m` 每 5 分钟探测测试环境后端 DID 稳定性。恢复后按下表自动补跑。
>
> **✅ 探测法（固化，不依赖点击）**：`adb -s emulator-5556 am force-stop` → 冷启动 SplashActivity → 等 20s → `logcat -d | grep -c 'code=-2018\|code=-2023'`。
> - **>0（有 -2018/-2023）= DID/后端仍未恢复**；**=0 且消息列表有内容 = 恢复**。
> - ⚠️ 不要用「点重登横幅」或「dump 查红条」：App 通知引导弹窗（"DeBox重要通知不错过"）会周期性模态弹出、遮挡横幅且吞点击；dump 只到弹窗层查不到下层红条。冷启动 logcat 法绕开这些。
>
> **探测记录**：
> | 时间 | 观测 | 结论 |
> |---|---|---|
> | 14:05 | 冷启动后 `-2018`×5 / `-2023`×4；GeeGuard `submitReceipt ok`（正常，无嫌疑） | ❌ DID 未恢复 |
> | 14:36 | 冷启动后 `-2018/-2023` 合计 ×9；GeeGuard `submitReceipt ok, length=392`（正常） | ❌ DID 未恢复 |
> | 14:51 | 5554 深链查房 `liveroom/info` 返 `-2018`，DID 红条掉线（第 3 轮实证） | ❌ DID 未恢复 |
> | 15:05 | 5556 重登 `app_login` 返 **`-2051`**×3（body 带正常 `gee_token`）；同环境三星真机 `code:1` | ⭐ **根因改判：风控拦截模拟器**（非后端故障）。加白名单前本探测不会转绿 |
> | 15:25/15:26 | 用户加白后首测：5556/5554 仍 `-2051` | ⏳ 白名单平台缓存未刷新 |
> | **15:32/15:33** | **5556/5554 `app_login ok channel=JC`** | ✅ **白名单生效，拦截解除 → 探测终止** |

**A 层 — DID 恢复即可自动跑（无需其它前置）**：
| 用例 | 做法 | 判定点 |
|---|---|---|
| **TC-C2 密码房加入** | 5556 建密码房（加入权限→需密码，设密码如 `1234`）→ 5554 深链加入→输密码 | body 中 `password`(非空) 与 `gee_token` 共存；进房 `code:1` |
| **TC-C5 token 未就绪加入** | 5554 `force-stop`→冷启动后**立即（<3s）**深链加入三星/5556 的房 | 缓存未就绪则 body **不含** `gee_token` 仍进房（fail-open）；就绪则带。记录实际 |
| **crash 复扫** | 每次补跑后 `logcat -b crash` | 0 GeeGuard 相关 crash |

**B 层 — DID 恢复 + 需链上 gas（loop 顺带探测账号 gas，有则跑）**：
| 用例 | 前置 | 做法 |
|---|---|---|
| **TC-D1/D2/D3 红包 3 接口** | 测试账号有 BNB gas（探测：发红包不再 `execution reverted`） | 测试群发最小额红包→自领→抓 `receive_lucky_box`/`onchain_box/receive`/`liveroom/lucky_box/rob` 的 `gee_token` |

**C 层 — /loop 探不了，需人工/后端/设备动作（每半个工作日汇报一次）**：
| 用例 | 解除条件 | 移交 |
|---|---|---|
| TC-C6 / E3 / F4(判定) / I4 | 后端下发高风险拦截策略 + 运维极验后台白名单 | 后端 + 运维 |
| TC-F2 / F3 | armeabi-v7a 真机 / x86_64 (Intel 机器) AVD | 设备补充（F3 兜底已静态代验） |
| TC-G1 / G2 / G3 | 线上老版本 APK | 发版前专项 |
| TC-C3 / D4 | USD 余额 / 第三测试账号 | 测试数据 |
| ~~三星 HEAD 包复跑~~ | ✅ 第 3 轮已完成（TC-F1/C4 HEAD 双绿） | — |

---

## 阻塞项汇总与移交（**四轮合并后的最终状态**）

> 第 2 轮已解除：后端恢复 → TC-C1/C4 转 PASS；真机接入 → TC-F1 转 PASS；TC-A5/H1/H3 补跑完成。
> 第 3 轮已解除：**构建归因**（HEAD 包三星复跑 TC-F1/C4 双绿）；模拟器 HEAD 包回归全绿；TC-F4 拦截实锤。
> 第 4 轮已解除：**风控拦截模拟器**（极验后台加白 → TC-I4/C2/C5 全部转 PASS）。
> 以下为**仍未解除**的项：

| 阻塞类型 | 用例 | 移交对象 / 解除条件 |
|---|---|---|
| **链上 gas 不足**（第 2 轮新确认根因） | **TC-D1 / D2 / D3**（红包 3 接口，P0）/ D4~D7 | **需要测试账号有 BNB gas**：实测发红包走 `lucky_box/onchain_box/create` → 合约 `execution reverted: 0x8baa579f`。给测试账号充少量 BNB（测试网）即可解除，届时 3 个领取接口可一次性跑完。**注入点已由 TC-S1/S2 静态 + 单测覆盖，非缺陷** |
| **后端拦截策略** | TC-C6（**语音房**拦截 UI）。~~F4~~ 第 3 轮 PASS；~~I4~~ 第 4 轮 PASS；~~E3~~ **第 5 轮 PASS**（加白前 `-2051` 即真拦截，客户端优雅提示无 crash） | **后端**：C6 需针对 **`liveroom/join` 接口**下发拦截（当前拦截发生在 `app_login` 层，进不到进房环节）。⚠️ 模拟器现已加白 → 复现拦截须先移出白名单或换未加白设备 |
| **设备缺失** | TC-F2（armeabi-v7a 32 位） | **阻塞理由已精确化**：不是「无 32 位真机」那么简单——`system-images;android-25;armeabi-v7a` 镜像存在且 API 匹配 minSdk，但 **现代 emulator 不支持 32 位 ARM**（`CPU Architecture 'arm' is not supported by the QEMU2 emulator`）→ **只能用真 32 位 ARM 真机**。缓解：v7a 的 `libgtcore.so` 已打包在 APK 内（532KB 实证），不触发 FINDING-002 崩溃路径 |
| ~~**x86_64 兜底**~~ | ~~TC-F3~~ | ✅ **第 5 轮已解阻并实测 → ❌ FAIL**（等效构造：排除 arm64 `libgtcore.so`）。见 **FINDING-002**。**原「由单测 + 静态代验」的结论被推翻** |
| **测试数据** | TC-C3（付费房，需 USD 余额）/ D4（专属红包，需第三账号） | 测试号余额不足；按危险红线**不注资、不动真实账号** |
| ~~**老版本包**~~ | ~~TC-G1 / G2 / G3~~ | ✅ **第 5 轮已解阻并全部 PASS**：不必等线上包——**GeeGuard 引入 commit（`0590bb3343`）的父提交 `bafce98b6c` 就是天然的 pre-GeeGuard 老版本**，`git worktree` 检出即可构建（`apks/debox-2.14.1-pre-geeguard.apk`，versionCode 21400001 真降级） |
| ~~**构建归因**~~ | ~~三星 TC-F1/C4 结论~~ | ✅ **已解除（第 3 轮）**：HEAD 包（`7d9ad736` / sha256 `ae507f3d…`）已装三星并复跑 TC-F1 + TC-C4 全 PASS，行号探针 `GeeGuardManager.kt:130` 证明包内代码为 HEAD。原 2.15.0 包备份于 `debox-android/apks/debox-2.15.0-samsung-backup.apk` |

---

## 结果状态说明

| 标记 | 含义 |
|------|------|
| ✅ PASS | 验证符合预期 |
| ❌ FAIL | 不符合预期，需要修复 |
| ⚠️ 部分通过 | 核心正确但有优化空间 / 一侧已验证另一侧阻塞（不阻断） |
| ⏸️ 跳过 | 外部依赖/设备缺失/数据阻塞（已注明原因与移交对象） |
