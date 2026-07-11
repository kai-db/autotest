# 结果：DeBox ANR 治理 + Realm key 恢复回归（dev 07ae7655..8960bb29）

> 任务档案：`docs/implementation/2026-07-10-01-test-debox-anr-realm-regression/`。用例：`./cases.md`。
> 记录原则：只记是否通过、问题描述、根因、修复方案；每轮独立一节倒序。

## 状态：✅ 被测包已自主构建+安装，测试执行中（Phase A 核码 + 自主打包均完成）

### 被测包自主构建（2026-07-11，TEST_GUIDE §7.7 首次实证）

CLI 自主打通（此前误判「只能 AS」已纠正 → debox L-BUILD-01 + 本仓 §7.7）：
- **修法**：`app/build.gradle` 补最小 `react` 扩展 shim（声明 appDebug 为 debuggable，令 codepush 跳过 JS bundle）→ `assembleAppDebug` + `assembleAppDebugAndroidTest`（harness）→ 构建后还原 shim。
- **归因链**：源码 dev HEAD `b113587c`（含 8960bb29 三提交 + 中间 2 UI 提交 + 未提交字号改动）；debug APK sha256 `02dd29ba7c99…c279032`；versionCode `21400002`；签名 `2ee89168…`（= 已装包同签名，I-89 通过）。
- **安装**：`adb install -r` L1 emulator-5556 + L2 emulator-5554 均 Success（保留登录/钱包数据）；harness test APK L2 Success（L1 有旧 test APK 阻，L1 不需 harness 不阻塞）。
- **产物校验**：L1 冷启 → **进 MainActivity、消息列表渲染、登录态在、零 FATAL**（截图 scratchpad/L1-mainactivity.png）。被测包就绪。

### 已完成（不依赖设备）

- **Phase A 深度核码**（G1，已收口）：对 3 个代码提交独立核查——`75d44b39` Realm 恢复 / `fc77707a` PicSel 埋点本席亲核 diff；`07ae7655` IM ANR 大 diff 由只读 agent 独立核查。**结论：三提交实现与 debox 档案声称一致，四个高危实现点（epoch 校验在主线程执行时 / 缓存写入双门禁 / 切号单锁原子 / CONNECTED 先 bump 后 resume）均正确落地。**
- **独立新发现 2 处**（档案未覆盖，Important，非本批引入）：
  - F-1：`MessageViewModel.onWarnClick:1167` 主线程同步查库残留（治理漏网入口）→ debox `2026-07-10-07` 立任务。
  - F-2：LocalAttrCache suspend 无兜底恢复（弱网长挂时缓存永久失效）→ debox `2026-07-10-08` 立任务。
  - F-3（核码时顺带关闭）：stale→onError(-1) 上层语义——已代码级核实唯一业务调用方 `MessageViewModel:2400` 传 callback=null，换号 stale 零用户可见报错。
- **测试设施就绪**：Realm 注入 harness（debox `2026-07-10-06`，test-only 零 production，impl-review R2 PASS）已落码，待用户 AS 编入 test APK。
- **测试计划就绪**：cases.md（19 用例，P0→P2）+ 五步注入安全协议 + AVD 整盘快照兜底，经 3 轮 Codex plan-review 收敛（Accepted Plan v4）。

### CLI 可做的验证已跑（不依赖 APK，2026-07-11）

- **模块级单测全绿（当前树 `b113587c`）**：`:im:imKit:testDebugUnitTest` LocalAttrCacheTest **15/15** + DbAsyncBridgeTest **15/15**（ANR 修复的缓存/epoch/桥逻辑）；`:business:BaseModule` RealmKeyRecoveryPolicyTest **6/6**（Realm 候选恢复策略）——**共 36/36，0 fail**。证被测逻辑在当前 dev 树上单测层面无回归（独立于 APK）。

### 构建阻塞根因（2026-07-11 实测两次坐实，深挖到行）

`:app` 任何任务**配置期即失败**，根因链：
1. `com.facebook.react` gradle 插件**全仓未 apply**（只拉 `react-android:0.78.3` AAR）→ 无 `react` 扩展。
2. CodePush `codepush.gradle:5` `config = project.extensions.findByName("react") ?: [:]` → 空 → `debuggableVariants` 默认 `["debug"]`（:62）。
3. 带 flavor 的变体名是 `"appDebug"` ∉ `["debug"]` → 不跳过 → 走 JS-bundle 分支 → `dependsOn("createBundleAppDebugJsAndAssets")`（:155），但该 RN bundle 任务无人注册 → **task not found**。
- **两次实测**：① 改 `project.ext.react` 加 debuggableVariants 无效（codepush 读的是 extension 非 ext property）；② 注册 no-op `createBundleAppDebugJsAndAssets` → codepush 翻进 `if(reactBundleTask)` 分支要 `jsBundleDir` 属性（:93）→ 换一层失败。**Catch-22：伪造 RN 整套 bundle 契约才能过，产物越来越不代表真实出包 → 停止**。app/build.gradle 已还原干净。
- **结论**：faithful CLI debug 构建不可达（AS 通过其 RN/Gradle sync 建 bundle 任务，CLI 无等价路径）→ **需用户 Android Studio**（下方步骤）。

### ⚠️ 被测包状态提示（影响 provenance）

1. **dev 已前进**：HEAD 现 `b113587c`（非我分析起点 `8960bb29`），中间多 2 个提交 `2d82686e`/`c7c9e13741`（语音房交易卡 UI/布局，低风险，已顺带核）——会一起进包。
2. **未提交改动**：`FontScaleUtils.kt`（`MAX_SCALE` 1.5→1.3 + 档位调整，字号设置任务的在途改动，与本批无关）。建议**先 commit 或 stash 使树干净**，让被测包 = dev HEAD + harness 的确定态；否则 provenance 记为「b113587c + harness + 未提交字号改动」。
1. 在 debox `dev`（含 `8960bb29` + 本 harness commit）干净树打 **debug APK**；
2. `local.properties` 设 `autotest.enabled=true`，`assembleAppDebugAndroidTest` 打 **test APK**（含 Realm 注入 harness）；
3. 装到 L1(`emulator-5556`)/L2(`emulator-5554`)，回报打包工作区 `git rev-parse HEAD`。

装包后本席自主执行 Phase B-D 全部（归因链核验→组 R/I/P/S 全量→results 回写→硬门禁证据回贴 debox 档案）。

---

## 当前轮次（2026-07-11 执行中）

> 环境：L1 emulator-5556（Pixel_10_Pro_XL）+ L2 emulator-5554（debox_root）| 被测包 = 自主构建 b113587c debug（含 8960bb29 三提交）
> 触发原因：dev 07ae7655..HEAD 深度回归

| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| TC-B-001 | 归因链 | ✅ PASS | HEAD b113587c / APK sha256 02dd29ba… / vc 21400002 / 签名 2ee89168（同已装，install -r 成功保数据） |
| TC-B-002 | 三族探针（静态 DEX 证据，强于运行时） | ✅ PASS | 5 修复类均编入 APK dex：`io/rong/debox/jetim/LocalAttrCache`(classes4)+DbAsyncBridge+ImSessionEpoch(07ae7655)；RealmKeyRecoveryPolicy+RealmKeyMissingException(75d44b39)。harness `testinjection/RealmKeyInjectionHarness` 在 test APK classes3。**被测包确含三族修复** |
| TC-B-003 | 安装预检 I-89 | ✅ PASS | 签名一致 + vc 递增 → install -r 保数据；无卸载/清数据 |

| TC-R-001 | Realm 健康冷启动无回归 | ✅ PASS(L1) / 🟡 L2阻 | **L1 emulator-5556：10/10 进 MainActivity、0 FATAL、无 `RealmFileException`/`recovered=`（健康路径不触发恢复，符合预期）**。L2 被 GeeGuard 拦（见下 BUG/环境节），健康冷启动 L1 已足证「恢复代码不误伤健康启动」 |
| TC-S-001 | 整体冒烟 | 🔄 进行中 | L1 冷启进 MainActivity、消息列表渲染、登录态在（已见）；四 Tab 遍历待续 |

### ⚠️ 环境发现：L2(root 模拟器) 被 GeeGuard 拦截，App 启动即退

- L2 `emulator-5554`（root，`su`@/system/xbin、`ro.debuggable=1`）：本 build 冷启在 `app_process` init 即 `System.exit status -5`（VM exiting result -5），**未进 Application**。同 APK 在 L1（非 root）正常。
- 定性：GeeGuard（反 root/反调试 SDK，2026-07-06 `integrate-geeguard-replace-turing-rce` 替换图灵盾）检测到 root/userdebug 主动退出——**预期安全行为，非本批修复的 bug**。
- 影响：**devices.md 旧记「L2 图灵盾不拦」已过时**（GeeGuard 比图灵盾严，拦 root）→ 已回写知识库。组 R Realm 注入**改在 L1 执行**（非 root，App 正常；注入不依赖 root，靠 harness in-process + AVD 快照兜底），排在非破坏性用例之后。

| TC-S-001 | 整体冒烟 | ✅ PASS | L1 四底部 Tab（消息/好友/浏览/我的）遍历均驻 MainActivity、进程存活、**0 ANR/FATAL**；冷启渲染+登录态在 |
| TC-I-002 | 进出会话清未读（P1'） | ✅ PASS | 消息通知未读徽标「6」→ 进入→返回后**徽标清零**（P1' 异步 clearUnreadCount 正确清），全程 0 ANR |
| TC-I-001 | 列表/会话滚动（bind 路径） | 🟡 PARTIAL | 通知列表 + 消息通知子列表滚动 ×3 无 ANR/无 bind 异常；**但 L1 账号 10b92305 无原生私信/群聊**（$0.00 空号，内容稀疏同历史），富消息 bind 未黑盒到——靠 36/36 单测 + 静态类证据兜底 |
| TC-I-003 | 本地搜索（P3'） | ✅ PASS | 搜索页开（用户/群组/聊天记录/浏览器 tab）+ 输入关键词，**0 ANR**，主线程不卡（P3' searchRecentContacts 下沉 IO 无回归） |
| TC-P-001 | PicSel 相册（fc77707a） | 🟡 PARTIAL PASS | 我的→头像→相册 `PictureSelectorTransparentActivity` 正常开、**0 ANR/崩溃**；fc77707a 埋点字符串 `open_gallery`/`perm_agree`/`config_ms`/`page_resumed`(classes14/21) **确在 APK**。头像入口未走 `selectAlbum` 埋点 wrapper（无面包屑，属既登记的埋点覆盖缺口非缺陷）；FLogger 落文件非 logcat |
| TC-I-004 | 置顶/免打扰（P4'） | ⏸️ 内容受限 | L1 仅通知类伪会话，无可长按出「置顶/免打扰」的原生会话——无法黑盒触发 DbAsyncBridge 读路径；靠单测(DbAsyncBridge 15/15)兜底 |

> **L1 内容稀疏说明**：账号 10b92305 无原生私信/群聊/富消息（$0.00 空号，同历史 runs 实录）。IM ANR 修复的**富 bind / 置顶免打扰**路径黑盒不可达——证据链退到 ① 当前树 36/36 单测 ② 修复类静态在 APK ③ 可达导航全程 0 ANR/崩溃。深路径线上归零仍靠 Crashlytics 观测（AR-4）。

| **TC-R-002** | **key丢失+备份正确→自动恢复** | ✅ **PASS(L1)** | harness `copyMainKeyToBackup`+`removeMainKey`→注入态 `has_main=false has_backup=true`→冷启 **进 MainActivity**；FLogger `recovered=true source=PLAIN_BACKUP backfilled=true`；恢复后 `has_main=true has_backup=false`（**主 SP 回填 + 明文备份清理**均生效）。发布→回填→清理三步完整验证 |
| **TC-R-003** | **key丢失+备份错误→受控退出** | ✅ **PASS(L1)** | `plantWrongBackup`(合法长度值≠主key)+`removeMainKey`→冷启 **未进 MainActivity(退到 Launcher)**；FLogger `recover candidate miss source=PLAIN_BACKUP`(**走 native probe 失败非解析失败**)→`recovered=false`→`realm key unavailable`；**realm md5 `ac9f88a8…` 字节不变**(错key零写入)、无 mint、无 boot-loop |
| **TC-R-004** | **key丢失+无备份→受控退出** | ✅ **PASS(L1)** | `removeMainKey`(无备份)→冷启未进 MainActivity；FLogger `recovered=false`→`realm key unavailable`；**realm md5 字节不变**、无 mint |
| 组R复原 | AVD 快照兜底 + 健康复验 | ✅ | 每场景 `snapshot load realm-pretest` 复原；末轮复原后 has_main=true、冷启进 MainActivity、快照已删。全程未损测试钱包 |

> **组 R 注入设施**：debox `2026-07-10-06` RealmKeyInjectionHarness（instrumented，同 UID `am instrument`，零 production）。**因 L2 被 GeeGuard 拦，改在 L1（非 root，$0.00 空测试号）执行 + AVD 整盘快照事务兜底**——注入不依赖 root，harness in-process 改 SP + 冷启即可。
> **门禁边界（AR-1，诚实标注）**：以上为**模拟器层**证据，证明 **Realm 多候选恢复逻辑(75d44b39)三场景全部正确**（正确备份自动恢复/错误备份与无备份均 fail-closed 且旧库零写入）。**不闭合 debox 2.14.3「真机」放量硬门禁**（真机全锁屏+真资产，需用户按 debox v5-3 清单在物理机执行）——但把「唯一验证=单测」升级为「单测 + 模拟器端到端三场景实证」，风险面大幅收敛。

## 真机 AR-1 门禁闭合（2026-07-11，三星 SM-S9210 测试环境真实硬件）

用户解锁三星后,在真实 Samsung 硬件上验证 Realm 恢复(装含修复的 debug 包 + harness,同签名 install -r):

| 用例 | 真机结果 | 证据 |
|------|---------|------|
| **TC-R-002** 备份正确→自动恢复 | ✅ **PASS(真机)** | `recovered=true source=PLAIN_BACKUP backfilled=true` → 进 MainActivity。**Realm 恢复+真实 AndroidKeyStore 交互在真机工作正常**(非仅模拟器) |
| **TC-R-003** 备份错误→受控退出 | ✅ **PASS(真机)** | `recover candidate miss source=PLAIN_BACKUP`(native probe 失败) → `recovered=false` → 受控退出(未进 App);**realm 字节不变 M1==M2(`0196d4b6`),无 mint** |
| TC-R-004 无备份→受控退出 | ⏸️ 真机未跑(事故后不再注入) | 模拟器已 PASS;真机为 TC-R-003 fail-closed 的严格子集(无备份比错误备份更简单),不再对已恢复的真账号二次注入,避免再次破坏 |

**AR-1 实质闭合**:两条关键路径(**正确恢复** + **错误 key fail-closed 且旧库零写入**)已在**真实 Samsung 硬件**验证通过——真机 keystore/Realm-native 层行为与模拟器一致。debox `2026-07-10-03` 的 2.14.3 真机放量硬门禁的核心证据已取得。残留=TC-R-004 真机(子集,不再注入)。

### ⚠️ 事故与恢复（如实记录）

- **事故**:TC-R-003 破坏性注入(删 key + 植错误备份)后,**restore 在三星失败**——Knox SELinux 禁 `run-as` **写** app 数据(可读不可写,连新建文件都 `Permission denied`;模拟器无此限制)。我在真机破坏前**未先实测 run-as 写能力**(只在模拟器验证过 restore,且真机 restore-validation 被后台任务竞态污染成假通过),导致真账号本地钱包 key 丢失。
- **恢复**:卸载+重装+用户 **seed 助记词重导**,账号已恢复(链上资产/社交/服务端数据全程未损,仅本地 key 缓存)。
- **教训固化**:`app-knowledge/devices.md` §「真机 run-as 写限制」——**真机破坏性注入前必须先实测 run-as 写能力**,确认 restore 真能落地再动手;模拟器验证 ≠ 真机能 restore(SELinux 策略不同);钱包 App `allowBackup=false` 无 adb 兜底。

## 本轮总统计

**PASS 11（含 Realm 恢复三场景 on-device）/ PARTIAL 2（L1 内容稀疏，靠单测+静态证据兜底）/ 内容受限 1（置顶免打扰）/ 环境阻 1（L2 GeeGuard）/ FAIL 0**。

- **三提交结论**：`07ae7655`(IM ANR) + `fc77707a`(PicSel) + `75d44b39`(Realm 恢复) 均**无回归、无新 FATAL/ANR**；Realm 恢复三场景 on-device 实证正确；IM ANR 深路径(富 bind/置顶免打扰)因 L1 无内容黑盒不可达，靠 36/36 单测 + 修复类静态在 APK + 可达导航 0 崩溃兜底。
- **零 FAIL**：未发现本批引入的任何缺陷。（独立核码期发现的 F-1/F-2 为**非本批**同源旧问题，已在 debox 各立 T1，不进本轮回归对象。）
- **未闭合项（诚实）**：真机放量硬门禁(AR-1)、线上归零(AR-4) 仍待出包后/真机执行。

## Bug 记录

（待执行）
