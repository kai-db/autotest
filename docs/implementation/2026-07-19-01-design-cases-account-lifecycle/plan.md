# Plan — 设计 debox account-lifecycle 分支全量回归用例（b8fd28d..bd165ef 净生效）

> ① 计划 / 方案。当前状态以 `index.md` 为准；本文件存方案与各轮 plan-review **过程历史**。
> 当前版本：**Proposal v2**（针对 R1 六条 finding 全量修订，findings 原文与处置表见 `review.md`）。

## User Request

```text
针对 debox-android refactor/account-lifecycle 分支，从 b8fd28ddffca966c4cd9ff3b97e6dd1c1107e51f（含）
到 HEAD bd165ef3ee0a4def2a2a4e6b50a11909bdd89d17 的全部「净生效」代码行为，完全重新设计一套独立测试
用例，之后按 TEST_GUIDE 做全量测试。旧 cases 及旧 PASS/FAIL 结论一律不得继承；旧实现文档、历史失败、
知识库只作影响面与风险证据。影响到的功能都要测。本阶段只产出 Plan Proposal（T2），等待 Codex plan-review。
```

## Background

- 规则底座：autotest `TEST_GUIDE.md` v1.5（§5/§7/§8/§9 全部适用）；`app-knowledge/`（dangerous-ops /
  devices / network-domain / screens）已通读。debox `AGENTS.md`/`CLAUDE.md` core 模块 block 名单
  （`business/moduleWallet/`、`business/BaseBusiness/network/`、RN walletsigner、`Application.kt`）与本区间改动高度重叠，回归标准从严。
- 账本条目应用见 index.md 导航节。

## Goals / Non-goals / Constraints

- Goals:
  - [ ] 产出对 `b8fd28ddff^..bd165ef3ee` 全部净生效代码行为的**全新**用例体系设计（TC-AL-*），覆盖改动
        本身 + 影响链判定「行为会变」的既有功能，每条 case 六要素齐全（§8.4），判据写明所数信号。
  - [ ] 影响面分析按 §8.2 六层落盘，每个传播停止点写明行为屏障与证据（无覆盖类 TODO 残留）。
  - [ ] 执行计划完整覆盖 Phase 0~6、防卡死、三分类、证据、统计自校验、危险红线、provenance 门禁。
- Non-goals: 本阶段不生成 cases.md、不跑设备测试、不改 debox 代码、不自行调用 Codex；不继承旧 case 与旧结论（仅作证据）；不修复将来发现的 FAIL（Phase 4 各自任务目录承接）。
- Constraints（本任务特有）: debox 工作区 4 个脏文件（ImBridgeHandler / SessionFragment / SessionListFragment / MainBusinessModel）不属基线、绝不修改、不纳入范围；被测包只从隔离 clean worktree 构建；生产设备（小米）只读；危险红线不放松。

---

## Plan Proposal（v2）

### 1. 精确基线、净生效甄别与构建接线

- **基线**：`git diff b8fd28ddff^..bd165ef3ee`，分支 `refactor/account-lifecycle`（HEAD 已核，exit 0）。
  46 个 first-parent 提交 + merge（f53b834142）侧链 3 提交。
- **净生效甄别**（逐提交 `git show --stat` 复核）：
  - **净生效代码提交 22 个**（§3 按域归组）。
  - **revert 净零 2 对**：`2b60ebeef0`↔`fd6ce9426a`（登录反馈）；`09b735b27a`↔`470c4bb2b4`（BTC 兼容基线，androidTest-only）。`66cf2fbc1c`（诊断钩子）未被 revert，属净生效。
  - **merge 带入无产品行为**：`d2d0036182`（androidTestImplementation autotest 1.8.1→1.8.2）、`c540527d38`（仓库根 2 个杂散 SVG，非资源目录）、`06fdd27732`（docs+png）。屏障：不进产品 sourceset。
  - **docs-only ≈17 提交**：逐个 stat 确认，不建 case。
  - **测试缝 `4a00d908ce` 已定性（R-2 闭合）**：`AccountAuthInjectionHarness` 位于
    `app/src/androidTestAutotest/`（androidTest 变体专属 sourceset），app/build.gradle 改动仅
    androidTestImplementation 升级（1.6.0→1.8.2）。**行为屏障：androidTest sourceset 不参与产品 APK 编译打包**，
    不建守卫 case；harness 作为 D11/D5 注入设施（能力：`setWallet` burst、post `AccountTokenState(code)` 等，
    走 `am instrument` 同 UID，符合 L-004「禁 adb 广播钩子」配套）。
- **脏工作区保护与 clean worktree 构建接线（PL-BUILD-BASELINE-88 修复）**：
  1. `git -C /Users/xiaochengcheng/StudioProjects/debox-android worktree add /tmp/debox-al-test bd165ef3ee`
     （worktree 目录固定 `/tmp/debox-al-test`，全程只对该路径操作）。
  2. 未跟踪配置接线（**只引用不打印内容，不落日志**）：
     `ln -s /Users/xiaochengcheng/StudioProjects/debox-android/local.properties /tmp/debox-al-test/local.properties`
     （只读复用原仓 secret 承载文件；不 cat、不 echo）。
  3. RN 依赖不复制：构建时显式
     `NODE_PATH=/Users/xiaochengcheng/StudioProjects/debox-android/ReactNative/node_modules`
     （指**原 debox 仓**的 node_modules，只读消费；worktree 内不装依赖）。
  4. 构建前门禁：`git -C /tmp/debox-al-test status --porcelain` 必须为空 → 注入 §7.7 `react` 扩展 shim →
     `:app:assembleAppDebug` → `git -C /tmp/debox-al-test checkout app/build.gradle` 还原 shim →
     再验 status 为空（shim 未残留）。
  5. 装包 I-89 预检（apksigner 签名一致 + versionCode 不降级 + `install -r` 保数据）+ 归因三件套
     （versionName/versionCode + 运行时行号探针 + APK sha256）。
  6. 全轮结束：`git worktree remove /tmp/debox-al-test`（有产物需保留时先归档 APK 到 runs evidence 再 remove）。
  7. 原仓零写入：所有构建命令 `-C /tmp/debox-al-test`；4 个脏文件全程不触碰（每轮抽查
     `git -C <原仓> status --porcelain` 仍仅原 4 行）。

### 2. Phase 0 非终态任务扫描（已执行）

`grep -L "Final VERDICT: \(PASS\|FAIL\|PASS_WITH_ACCEPTED_RISK\)" docs/implementation/*/index.md`（exit 0）命中 5，核对后：

| 目录 | 实际状态 | 本轮处置 |
|---|---|---|
| `2026-07-17-01-test-debox-account-revalidation-observability` | 真非终态（planning 停滞，同域旧测试任务，挂账 2 轮） | 本任务接管其目标（超集重设计）；收口时该目录补「superseded by 2026-07-19-01」并终态化；本 plan 即 §5.5 要求的升级报告 |
| `2026-07-18-01-fix-case-cache-key-and-log-assert` | 真非终态（未开始评审，autotest 框架修复） | 限制 B 模式 cache 回放使用面：本轮 AAR 仅用于不受该缺陷影响的确定性用例；Phase 4 窗口择机续跑 |
| `2026-07-06-01-create-techshare-ppt` | 真非终态但与测试无关 | 挂账列入 results.md，不处理 |
| `2026-07-10-01` / `2026-07-11-01` / `2026-07-16-01` | grep 误报（VERDICT 为粗体 `**PASS_WITH_ACCEPTED_RISK**`） | 不处理；扫描命令粗体兼容改进列进化建议（收紧，自主） |
| debox 侧 | `2026-07-18-06-refactor-account-lifecycle`（finalize 刻意不通过）、`2026-07-19-01-fix-cross-device-kick-storm` | 只读证据源；本轮 FAIL 命中同现象/同根因则**续跑既有目录（含 debox 侧）不新建** |

### 3. 净行为影响矩阵（§8.2 六层；R-1/R-2/R-6 已闭合，证据见行内与 review.md 处置表）

| # | 影响域 | 改动符号（提交） | 调用方/入口（已证，@bd165ef3ee） | 下游共享状态 | 跨端边界 | 为何建 case |
|---|---|---|---|---|---|---|
| D1 | HTTP 可观测性与脱敏 | call_id 全链路+失败阶段（b8fd28ddff, c27bf78f54 `NetEventListener`）；navi host 脱敏（c7b17a2d2）；`FlogSanitizer`（9df814edd3） | OkHttp 事件监听全体业务请求（network 底座） | 日志文件/上传通道 | 后端 call_id 检索口径 | 全量请求新增观测与脱敏：call_id 贯穿、失败阶段分类、敏感字段（token/host/密码/助记词）**负向 grep 无命中** |
| D2 | 账号失效周期门禁与重验弹窗 | `AccountRevalidationGate`/`MainAccountCoordinator`±162/`RevalidationReshowPolicy`（7eca437361, 023d0319c4） | `AccountTokenState` 事件总线：posters=IMNetStateView:110（manual=true）/RN WalletBridgeService:1689/RN AccountNativeCallHandler:93/MainBusinessModel:420/harness:98；SessionFragment:573 已**不再发**（b68357ce 修复点，注释为证）；观察方=MainActivity（import :21）→ 协调器门禁链 | 失效周期标记 | RN 桥、服务端 token 失效回包 | 「同周期恰一次登出与重验」+语义化状态机+reshow；RN 桥发的失效事件同受门禁约束（跨端入口必测） |
| D3 | 重验 G1/G2：全码自动重签+认证世代守卫 | 4c220c8a5（AppCacheManager/AccountUtils 区域） | 冷启动/登录链 | token 存储、世代标记、全部本地钱包码 | 多设备同账号世代竞争 | v2.15.0 登录死环域（07-17-02 诊断）：重签成功/部分失败/全失败、世代失配拒旧 token、死环不复现 |
| D4 | 登录 attempt 生命周期 | `LoginAttemptCoordinator`（c27bf78f54）+诊断钩子（66cf2fbc1c）+`SplashScreenActivity`:167（431ba041d9，setWallet(null) 路径修复）+`AppLoginFailurePolicy`/`AppLoginResponsePolicy`+TP/WC 重试恢复（023d0319c4） | Splash 冷启、重登录、TP/WC 回调 | attempt 状态 | TP/WC 外部 App 回跳 | 冷启恰一次 attempt 且成活、并发单飞、失败可重试不死锁、诊断钩子日志出现 |
| D5 | 登录链路加固与熔断 | `AppCacheManager` 净±748、`AuthSyncBreaker`、`CacheService`±71（c27bf78f54, 0def16ab21, 6ca9c74afe） | AppCacheManager 为全局单例，登录链核心收口（读写 WalletRepository :142-152 等） | token/账号缓存、批量 cache key | 服务端异常回包 | 异常登录回包**不删本地钱包**（资产保护负向 case，注入构造）；熔断开/合边界；批 key 正确性；冷启持久化 |
| D6 | 建/导钱包落库链路 | `WalletAddGuard`/`WalletRepository`±106/`WalletVO`/`WalletCandidateRetryPolicy`/`NativeCallGuard`/`EncryptWalletContract`/`MnemonicMatchPolicy`/`MnemonicScanPolicy`/`WalletFingerprintMarker`/`CreateWalletDialogFragment`±171/`WalletCreateActivity`±112/`ImportWalletActivity`±35 | **`WalletRepository.add` 入口全清单（PL-CALLGRAPH-96 闭合）**：创建（WalletCreateActivity）、导入（ImportWalletActivity）、建钱包对话框（CreateWalletDialogFragment）、第三方连接（AppCacheManager.saveConnectWallet:976 → D13）；`WalletRepository.kt:104` insert 成功即 `setWallet(wallet)` → 触发 `SwitchWallet(SUCCESS)` → **Application.kt:241 observeForever → RongLoginManager IM 重登**（影响链跨到 IM，判据：建/导成功后 IM 重登恰一次） | Realm 钱包表（单事务无部分失败态）、fingerprint 字段 | native 加密 so | 密集修复区：查重有界重试/拒空地址/native 兜底/加密失败不落空密文/助记词明文比对/fingerprint 脱敏（落库后 grep 无明文支付密码）/单事务/冷启一致 |
| D7 | 删钱包与账号数据作用域 | 62f1a50499、`WalletDeleteObserver`、`AccountDataScopePolicy`（9df814edd3）；`AccountUtils:91` delete 路径 | 删钱包入口（账号详情页=危险页，仅测试专用钱包） | 会话数据表、当前账号缓存 | — | 删非当前钱包后当前账号会话完好（读侧判据）、删除观测日志、失败回滚、幂等 |
| D8 | BTC 原生迁移与转账降级 | `Deriver`/`MessageSigner`/`BtcAddressType`/`BtcNetwork`（61b5b9cc2d）、`WalletCore`±55、`BtcManager`±448（**删 load/query/send transaction**）、删 `WebBTCManager`−216、`BaseTransferActivity`±24（**BTC 分支删 sendTransaction 改 Toast "BTC transfer is not supported yet."**；旧路径 `mBtcBalance` 全仓无赋值恒早退=从未真正发出交易）、SettingAdmin 删 `WebBTCManager.updateNetworkData()`、`WalletConnectFragment`±3、config.gradle（bitcoin-kmp 0.30.0 + secp256k1-kmp 0.23.0 + test-runner） | BTC 派生/签名调用方（登录链 BtcDerivationAggregator、RN 桥面待 cases 期逐条核对入口可达性）；**WebBTCManager 残留仅注释/docs，`btc.html` 已不在 tree（屏障，ls-tree 空）** | 钱包 BTC 地址字段 | RN bridge、BTC 链 | 引擎替换=兼容风险最高：同助记词新旧地址一致、消息签名可验、**BTC 转账不支持的安全 UI case（到 Toast 边界，不签名不广播）**、移除入口静态/单测门禁、SettingAdmin BTC 切链=危险禁测→静态屏障 |
| D9 | JIM 连接周期观测 | `DBXJConnection`±108、`ImConnectionEpisodeTracker`、`DBXJimCenter`±5（c7b17a2d2） | JIM 连接生命周期 | episode 状态 | IM 服务端 | **已确认 `current()` 仍返回最新 episode（:39-40，区间未修）**——归属缺陷（B 类）仍在：连接唯一性判据一律数 `WS onOpen/onClose` 底层信号；episode 观测 case 预期「归属正确或如实记 B 类」 |
| D10 | 群会话通知发送者前缀 | `GroupConversation`±14（d0843b7bbf） | 会话列表最后消息预览 | 会话列表缓存 | 多端口径 | 通知类隐藏前缀 + 普通群消息不误伤 + 多语言边界 |
| D11 | 被踢下线与跨设备互踢 | `SessionFragment`±31（b68357ce）、16aafaf515（MainActivity−8/`IMNetStateView`±23/SettingAdmin−4）：堵重启/切tab/下拉刷新三条重登路径；**新增用户契约（PL-KICK-CONTRACT-97 闭合）**：`AccountTokenState(manual=true)` 手动恢复绕过终态门禁（IMNetStateView:108-110）、1500ms 连点去抖（:230，`netstate_click_debounced` 日志 :57） | 被踢事件→UI 横幅（IMNetStateView）；三重登路径 | 登录态、IM 连接 | 多设备同账号服务端仲裁（11011） | 已修 P0 风暴：三路径零自动重登（HTTP 层登录请求计数=0）、manual=true 恢复可用且恰一次、去抖窗口内多击仅一次+日志出现、双机不再互踢风暴（双机计数）；**AR-A3**（业务错误早于 11011 的一次自动重签/无本地上界）→harness/L2 注入构造；**AR-A5**（终态写盘失败 fail-open）→L2 注入；**AR-A4**（旧连接迟到 11011 缺 connection identity）→客户端不可注入构造（需服务端时序），登记 **verification gap**，替代门禁=源码级证据+双机时序观测尽力取证 |
| D12 | 钱包 OBS 与支付密码终态 | `SessBootTrace`±6、`DeBoxUtils`±12 加密失败根因、`PwdResetActivity`±8 终态日志（3d15cb9500, 9df814edd3） | 支付密码重置流程 | 日志上传通道 | — | 观测补全：「该打的都打了、不该出的不出」（正向出现+脱敏负向）；重置仅 L2 测试专用钱包 |
| D13 | 第三方连接/导入钱包（新增域，PL-THIRD-PARTY-WALLET-92 / PL-CALLGRAPH-96） | `saveConnectWallet`（AppCacheManager:976，新增 onError 日志=净行为）、`wallet_add_failed scene=third_party_import`（AppCacheManager:1024）、TP/WC 失败重试恢复（023d0319c4） | 调用方：WalletConnectFragment:208 + MainAccountCoordinator:363；`setWallet` 其余入口定性：SwitchChainDialogFragment:214（**切链=危险禁测**→静态屏障+单测证据）、WalletAddressActivity:220 与 MainAccountCoordinator:290/394（地址/协调器切换面→动态验证）、AccountUtils:124 与 SplashScreenActivity:167（setWallet(null) 清空路径→D4/D7 判据覆盖）、WalletConnectDialogFragment:103/147 为 `WalletConnectV2Manager.setWallet` 不同符号（屏障）；`SwitchWalletDialogFragment` grep 未直接命中 AppCacheManager.setWallet，保守并入切换面动态验证 | Realm 钱包表、connect 钱包记录 | WalletConnect/TP 外部钱包 App | 连接成功落库+IM 重登恰一次、异常回包保钱包、`third_party_import` 失败可观测、导入态 timeout 删**临时钱包**（仅本轮临时测试钱包）与非 timeout refresh 恢复的契约区分 |

**行为屏障汇总**（每条一行依据）：androidTest/单测 sourceset（harness、兼容基线 revert 对、15+ *Test.kt、autotest 依赖升级）不进产品包；`c540527d38` 仓库根 SVG 非资源目录；docs-only 17 提交无代码；`WebBTCManager` 残留仅注释/docs 且 `btc.html` 不在 tree；`WalletConnectV2Manager.setWallet` 与 `AppCacheManager.setWallet` 不同符号；SettingAdmin BTC 切链入口保留但属危险禁测（走静态 diff 证据：仅删 WebBTC 网络更新调用）；`ReStartApp`/`UnReadCount`/`ImLoginSuccess` 三个 Application 订阅与本区间改动正交（事件的发布方未被改动）。

### 4. 用例域与预算（v2，逐行机械汇总；新 ID 体系 TC-AL-*，不继承旧 ID）

每条 case 生成时六要素齐全，判据写明所数独立信号（L-006 红线）。

| 域 | 条数 | P0 | P1 | P2 | 设备 | 覆盖形态要点 |
|---|---|---|---|---|---|---|
| TC-AL-P 框架/包前置 | 5 | 5 | 0 | 0 | 本机+L1 | autotest 编译/单测/发布 + clean worktree 构建 + 包归因三件套 |
| D1 TC-AL-HTTP | 4 | 2 | 2 | 0 | L1 | call_id 贯穿、失败阶段、脱敏负向×2 |
| D2 TC-AL-GATE | 6 | 3 | 2 | 1 | L1(+L2) | 单周期恰一次、reshow、跨冷启、RN 桥入口、负向不误伤 |
| D3 TC-AL-GEN | 5 | 3 | 2 | 0 | L2 | 重签成功/部分失败、世代守卫、死环不复现 |
| D4 TC-AL-ATT | 6 | 3 | 2 | 1 | L1 | 冷启单飞、自作废修复、TP/WC 重试、诊断钩子、并发幂等 |
| D5 TC-AL-HARD | 5 | 3 | 2 | 0 | L2 | 异常回包保钱包、熔断开/合、批 key、冷启持久化 |
| D6 TC-AL-WCR | 8 | 4 | 3 | 1 | L1+L2 | 建/导正向（含 IM 重登恰一次）、拒空地址、native 兜底、加密失败回滚、助记词、fingerprint 脱敏、单事务、冷启一致 |
| D7 TC-AL-WDEL | 4 | 2 | 2 | 0 | L2 | 删非当前钱包会话完好、观测、回滚、幂等（仅测试专用钱包） |
| D8 TC-AL-BTC | 8 | 4 | 3 | 1 | L1 | 派生一致、消息签名、**转账不支持 Toast 边界**、移除入口静态门禁、WebView 无死入口、so 加载 |
| D9 TC-AL-JIM | 4 | 2 | 1 | 1 | L1 | onOpen 恰一次（底层信号）、episode 归属观测、重连计数、脱敏 |
| D10 TC-AL-GRP | 3 | 1 | 1 | 1 | L1 | 前缀隐藏、不误伤、多语言 |
| D11 TC-AL-KICK | 11 | 6 | 5 | 0 | L1×2/注入 | 三路径零自动重登、被踢 UI、手动重登、**manual=true 绕行**、**1500ms 去抖**、双机风暴计数、**AR-A3 注入**、**AR-A5 注入**（AR-A4=gap） |
| D12 TC-AL-OBS | 4 | 1 | 2 | 1 | L1/L2 | 密码重置终态、加密失败根因、SessBootTrace、上传通道脱敏 |
| D13 TC-AL-3PW | 5 | 3 | 2 | 0 | L1+L2 | 连接落库+onError、异常回包保钱包、third_party_import 可观测、timeout 删临时钱包、非 timeout refresh |
| **合计** | **78** | **42** | **29** | **7** | — | 42+29+7=78，与逐行和一致（awk 自校验通过） |

### 5. 设备路由与串行策略（v2）

- 阶梯：L1 `Pixel_10_Pro_XL`（默认）→ L2 `debox_root`（注入+破坏性，AVD 快照兜底）→ L3 真机仅复核（不在线不阻塞）。
- 红线与串行：emulator-5554 含 2 个真实账号——开测先核身份=测试号 `2309b9ea`；「切换账号」弹层/账号详情页绝对禁触。三星 SM-S9210 与 emulator-5556 同账号（「请输入昵称1」）：涉登录态/会话/钱包写操作的用例两机**互斥串行**（分片表互斥组 `MUTEX-ACC-5556SAM`）。小米=生产真实资产，只读观察。模拟器风控：5554/5556 已加白；新 AVD 预期 `-2051` 按 devices.md 鉴别。网络噪声预检每轮必做，fake-IP 环境网络层结论只标【模拟器实测，待真机复核】。
- **D11 跨设备互踢（PL-KICK-CONTRACT-97 修复）**：双机组合 = **emulator-5556 + 三星 SM-S9210**（已知同账号，测试账号）。前置：三星现场重核 debuggable+versionName+行号探针+sha256（历史记录视为过期），两机登录态基线留证 + 5556 打 AVD 快照。后置：恢复单机在线稳定态并验 IM `onOpen` 恰一次；全程不登出/不清数据，被踢态只由服务端仲裁产生。**fallback（三星签名/版本不兼容或不在线）**：单机 L2 上用 `AccountAuthInjectionHarness` post `AccountTokenState` 注入构造被踢链路——结论标注【注入构造，证明客户端处理逻辑；双机真实仲裁面未闭合】记 gap，不冒充端到端 PASS。

### 6. 危险操作与执行边界

- 点击前比对 `dangerous-ops.md`；命中未明确要求→规避记录。禁止：`pm clear`/卸载/登出/切环境/**切链（含 SettingAdmin BTC/EVM 测试链按钮）**/切账号/账号详情页/导出助记词私钥/真实资金转账确认。
- D7/D13 删除类：仅删**本轮新建的临时测试钱包**（L2 现场创建），绝不动既有钱包；AVD 整盘快照先行。
- D8 转账：只推进到「BTC transfer is not supported yet.」Toast 边界（密码校验层之前/之中不发起签名广播）；EVM 转账不进确认页。SwitchChainDialogFragment 切链路径不做 UI 执行，用静态 diff+单测作门禁。
- D12 支付密码重置：仅 L2 临时测试钱包且用例明确要求；否则只验观测面。

### 7. Provenance 门禁

1. 被测包=目标代码三件套（clean worktree 构建 + versionName/Code + 行号探针 + sha256），任一不对齐→停。
2. 注入即声明：用 harness/root 注入/构造回包的 case 标 `INJECTED` + 注入面 + 恢复步骤（快照回滚/iptables -F/settings delete）；注入类结论只证客户端逻辑，不冒充端到端 PASS。
3. 单测/静态 ≠ 动态 PASS：结论分级【单测】【模拟器实测】【真机复核】；无法动态证明的分支给替代门禁或显式 gap（本轮已知 gap：AR-A4）。
4. 判据独立性：连接数 `WS onOpen/onClose`、登录数 HTTP 层请求计数、落库读 Realm/读侧 UI；不用被质疑机制自身埋点作唯一判据。

### 8. 执行计划（Phase 1~6 与失败处理）

- Phase 1：设备在线→保活→噪声预检→身份核验→worktree 构建+装包+归因→分片（互斥组串行；L2 注入独占）。
- Phase 2/5/6 **只测不修**，P0→P1→P2 全量；>15min 无进展判 `FAIL-超时` 推进；崩溃重试 1 次；一切卡点按防卡死铁则自决，白名单五项外不停。⛔ 测试阶段禁改码/禁 review。
- FAIL：①原始日志线路落盘（`evidence/phaseN/`，过滤不摘要，留时间戳/线程号/计数）②A/B/C 三分类（独立证据；C 类只改判据走进化日志）③分析记录模式建/续任务目录 ≤10min（命中 debox `2026-07-19-01-fix-cross-device-kick-storm` 等既有目录则续跑）→下一条。
- Phase 3 统计行 awk 生成自校验；Phase 4 全部 FAIL 走 agent-dev-loop 闭环；Phase 5 分层（P0+受影响面+历史 FAIL 域：D3 死环/D9 episode/D11 风暴）；Phase 6 全量+严格 flaky 门（P0 不得按 flaky 消化）。监工 `/loop 5m`。

### 9. 保守假设

- A1：bd165ef3ee 为唯一基线，测试期分支前进不追新提交。
- A2：三星/模拟器装机状态一律 Phase 1 现场重核，历史记录视为过期。
- A4：服务端不可控行为以双机真实触发为主、注入为辅，证据强度如实标注。
- A5：任务目录建在 autotest 仓，debox 仓零写入。
- （v1-A3 已解除：harness 确认 androidTestAutotest sourceset，见 §1。）

### 10. 剩余开放项（供 Round 2 裁决）

- **无阻塞性覆盖 TODO**（R-1/R-2/R-3 补证/R-6 均已闭合入 §3）。
- 显式 verification gap（非 TODO）：**AR-A4**（旧连接迟到 11011 缺 connection identity）客户端无法注入构造，替代门禁=源码级证据+双机时序尽力观测；若 Round 2 认可则在执行后转 Accepted Risk 四要素登记。
- 请 Round 2 复核：D8 派生一致性取证方案（testnet 向量+已知助记词测试钱包地址对照，不动真实资产）、D11 双机判据窗口定义、78 条在产能模型下的分片排程。

## Plan Review Log（per-round，含 VERDICT 快照）

### Plan Review Round 1 — 2026-07-19（主会话 Codex）

```text
VERDICT: FAIL
```

Critical：PL-CALLGRAPH-96 / PL-KICK-CONTRACT-97 / PL-BTC-TRANSFER-95；Important：PL-THIRD-PARTY-WALLET-92 / PL-BUILD-BASELINE-88 / PL-BUDGET-87。**findings 原文与逐条处置表见 [review.md](./review.md)（不在此复述，0.1.3）**。

## Plan Revision Log

- v1 · 初版 Proposal（2026-07-19）。
- v2 · PL-CALLGRAPH-96：§3 D6/D13 调用方逐项定性（saveConnectWallet/setWallet 全入口+屏障），R-1/R-2/R-6 闭合，删除全部覆盖类 TODO。
- v2 · PL-KICK-CONTRACT-97：D11 增 manual=true 绕行、1500ms 去抖 case；AR-A3/A5 注入 case、AR-A4 显式 gap；双机改 5556+Samsung + 单机注入 fallback 与证据强度定义（§5）。
- v2 · PL-BTC-TRANSFER-95：D8 增 BTC 转账不支持安全 UI case + 移除入口静态门禁；SettingAdmin BTC 切链危险禁测→静态屏障（§3/§6）。
- v2 · PL-THIRD-PARTY-WALLET-92：新增 D13 第三方连接/导入域 5 条（timeout 删除仅限临时测试钱包）。
- v2 · PL-BUILD-BASELINE-88：§1 构建接线重写（local.properties symlink 不打印、NODE_PATH 指原仓、status clean 门禁、shim 还原、worktree remove、原仓零触碰）。
- v2 · PL-BUDGET-87：§4 逐行机械汇总统一为 14 行 78 条 = P0 42 / P1 29 / P2 7（awk 核对）。

## Accepted Plan

（保持空白，待 Plan Review Round 2）

- Deviation Policy: 改变用例域划分、危险边界、设备路由、provenance 门禁的偏离，必须先 consult Codex。
