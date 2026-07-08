# 结果：DeBox 2.14.1 崩溃修复回归 —— 基线轮（Baseline，问题包 21400001）

> 轮次：Baseline（模式 B 决策：先在**含崩溃的问题包**上跑基线，装修复包后再复跑对比）
> 日期：2026-07-08 ｜ 设备：L1 `emulator-5554`（普通模拟器 · `sdk_gphone16k_arm64` · user 非 root）
> 被测账号：`904c7698`（测试账号 · 总资产 $0.00 空钱包 · IM 无任何会话/群/图片/bot）

## ⚠️ 两个结构性前提（决定本轮能测到什么）

1. **build 不含修复**：三台设备（emulator-5554 / 三星 S25 / 小米）装的都是 `2.14.1 · versionCode 21400001`——**正是这批崩溃被上报的问题包**，不含任何本批修复（未推送/未提交）。
   → 本轮只能验「健康路径不崩 / 功能无回归 / 尝试复现原崩溃」，**证明不了「崩溃已修」**。修复验证须等含本批改动的 build（P-4/realm 编译还需 Android Studio 解 RN classpath）。
2. **测试账号无内容 + L2/注入设备不在线**：L1 账号 IM 全空、钱包 $0，无图片/群/bot；L2 `debox_root`（注入层）当前不在 `adb devices`。
   → 依赖 IM 内容（图片/超长文本/bot 键盘）与注入（realm/splash）的用例本轮**无法执行**。

## 结果汇总

| # | 用例 | 结果 | 说明 |
|---|------|------|------|
| TC-P-001 | 环境与登录基线 | ✅ PASS | L1 在线、stayon、已登录测试账号 `904c7698`、空钱包。网络噪声预检命中 fake-IP（198.18.0.49）——本批为 UI/崩溃回归、非网络层结论，模拟器即终局 |
| TC-P-002 | 装的是被测 build | ⚠️ 注意 | 装的是问题包 21400001，**不含修复**——见上「前提1」。本轮=基线，非修复验证 |
| **TC-S-001** | **P-6 realm 健康冷启动无崩溃** | ✅ **PASS** | 连续 **10/10** 冷启动均进 MainActivity，**无 boot-loop**、logcat 无 `RealmFileException`/`RealmUnavailableException`；健康钱包启动稳定（realm 崩溃仅 Keystore 抖动边界触发，happy-path 不复现，符合预期） |
| **TC-S-002** | **P-4 Splash→Main 无崩溃** | ✅ **PASS** | 同上 10/10 冷启动 Splash 正常过渡 MainActivity，无 `NPE getDisplayContent`/WM 崩溃（WM 竞态设备/OEM 特异，模拟器未触发） |
| **TC-F-002** | **P-2 选链页快速进退不崩** | ✅ PASS（未复现原崩溃） | 「收款」→ 顶部「选择网络」sheet 正常打开、平滑滚动到选中链（Ethereum ✓，即 `initChainLinearLayout` smoothScroll 正常）；adb 快速开/关 ×25 **无 NPE、进程存活未崩**。原崩溃为异步 post 后 RV detach 时序竞态（生产仅 2 次），黑盒未能触发 |
| TC-S-003 | P-6 realm 注入·不崩不毁库 | ⏸️ 基线轮阻塞 → ✅ **复跑轮 L2 已跑 PASS** | 见「复跑轮补充」节：L2 拉起后注入 PASS |
| TC-F-001 | P-5 图片预览快速开关 | ⏸️ 基线轮阻塞（L1空账号）→ ✅ **复跑轮三星修复包已跑 PASS** | 基线轮 L1 无含图会话；复跑轮三星「莓U烦恼O」群图片开/关×12 无崩。**注意：仅修复包有「后」，问题包上从未复现过（无前/后对照）** |
| TC-F-003 | P-7 超长会话名/预览 OOM | ⏸️ 基线轮阻塞 → 🟡 **复跑轮 L2 尝试，未触达修复路径** | 见 TC-F-003 补充节：长预览走普通 TextView 非 TextWithImageView，黑盒造不出畸形超长会话名 |
| TC-T-001 | P-1 Bot 内联键盘不崩 | ⏸️ 基线轮阻塞 → ✅ **复跑轮三星修复包已跑 PASS** | 基线轮无 bot 会话；复跑轮三星 GroupHelper Bot 键盘卡片渲染+滚动无崩。**同 P-5：无问题包前对照** |
| TC-T-002 | P-3 DApp 签名弹窗回归 | ❌ **两轮均未执行** | 触发签名弹窗需具体 DApp 场景；基线轮空账号无法、复跑轮三星真钱包避签名红线未做。**可在 L2 测试钱包补**（无真钱、开 DApp 触发弹窗后取消） |
| TC-T-003 | P-4 startActivity 失败重试 | ⏸️ 基线/复跑黑盒未执行 → ✅ **L2 组件禁用注入已跑 PASS** | 见「TC-T-003」补充节：`pm disable MainActivity` 触发 goMain 失败链，日志证 重试 3 次→give up→finish、不崩 |

## 关键结论

- ✅ **最高价值 P0 已拿到**：健康钱包冷启动 **10/10 稳定无崩**——这正是 realm 地板修复最需要守住的回归面（**地板改动不能误伤健康用户**）。当前问题包上健康启动本就正常；装修复包后须**复跑 TC-S-001 确认地板改动未引入健康启动回归**。
- 🔍 **原崩溃均未在基线复现**：符合预期——这批都是低频边界崩溃（Keystore 抖动 / WM 竞态 / 时序 race / 特定畸形输入），happy-path 与粗粒度黑盒操作难触发。**它们的真实验证靠：① 装修复包后 ② 上线 Crashlytics 对应 issue 于 ≥新 build 归零**。
- ⛔ **大部分修复本轮测不到**：受「问题包无修复」+「测试账号空 + L2 不在线」双重限制。

## 下一步（建议）

1. **打含修复的 build**：用户在 Android Studio 编译含 `edfc624867..HEAD` 的 debug 包并装到设备（`:app`/realm 需 AS）。装好后**复跑本 cases.md**——重点 TC-S-001（健康启动无回归）+ 尝试复现各原崩溃应已不崩。
2. **补测试数据 / 换设备**：为 TC-F-001/003/T-001 准备内容——① 用有真实会话/图片/群的**三星 L3 测试环境**账号；或 ② 在测试群造数据（发图片、发 >2000 字消息、加一个带内联键盘的 bot）。
3. **拉起 L2 `debox_root`**：跑 TC-S-003 realm 注入（仅测试钱包，改后必复原）。
4. 全程无新 FATAL（`mobile_list_crashes` + logcat 均干净）。

> 修复走 agent-dev-loop：本轮为回归测试，未发现新 bug；如复跑发现 FAIL，在 debox 仓建 `docs/implementation/` 任务、不直接改码。

---

## 基线轮补充（「交给你」后自主推进的坐实，2026-07-08）

用户授权自主推进后，逐一试探两条阻塞是否可自主解除，结论如下：

- **线1 自主打含修复的包 → 坐实不可行**：`./gradlew :app:assembleDebug` 在 `:business:moduleMain:compileDebugKotlin` **FAILED**（44s），正是 debox 任务记录预言的 **RN CodePush classpath 问题**（moduleMain→`RNPreloadManager.reset()`→`ReactInstanceHolder` CLI 下解析失败）。→ **含修复的 APK 必须由用户在 Android Studio 打**，我无法自主产出。
- **三星 L3 测试环境 → 锁屏不可用**：`deviceLocked=1`（安全锁屏 Bouncer，adb 不可绕），唤醒后停在锁屏。真机首次解锁属人工白名单，未尝试绕过。
- **L1 IM 会话列表不同步 → 内容依赖用例无法触发**：探到 `AI 客服` 实为 Bot 账号「测试Test环境」（**佐证当前=测试环境**），成功进入其私聊并 adb 灌入一条 2713 字 `[autotest]` 长消息发送——**聊天页无 OOM/崩溃**（但聊天气泡非 TextWithImageView，不构成 P-7）；返回消息列表「全部/私信」**仍全空**，该会话未进列表、无 IM 连接日志。→ P-7 的真正触发点（会话**列表预览** TextWithImageView.setIcons）拿不到；P-5 图片预览、P-1 bot 内联键盘同样因列表/会话无内容无法触发。
- **L2 `debox_root`（注入层）不在线** → realm 注入（TC-S-003）无法执行。

### 补充结论

自主能拿到的就是已完成的 **P0 冷启动稳定性基线（TC-S-001/002 · 10/10 PASS）** 与 **P-2 选链页正常/无复现**。其余 8 条被四个硬前提锁死（无修复包 / L1 IM 不同步 / L3 锁屏 / L2 离线），**均需用户动作解除**（见上「下一步」1–3）。未做任何绕过锁屏、未在正式钱包注入、未向公开频道发消息。

---

# 复跑轮（修复包 · Fixed Build，2026-07-08 14:xx）

> 用户在 Android Studio 打含 `edfc624867..HEAD` 全部修复的 debug 包，装到**两台真机**（三星 S25 `RFCYA0F9SSZ` L3 测试环境 14:00 装、小米 `402714f0` L3 正式环境 13:59 装；模拟器仍旧包）。用户已解锁三星。
> 复跑设备：**三星 S25（L3 测试环境 · 已解锁 · 修复包 · IM 内容丰富）**。账号 `Kai`（Lv.11，**有真实资产**——全程只读、绝不碰转账/签名）。

## 结果汇总（修复包）

| # | 用例 | 结果 | 说明 |
|---|------|------|------|
| **TC-S-001** | **P-6 realm 健康冷启动无回归** | ✅ **PASS（关键）** | 修复包 **10/10** 冷启动进 MainActivity，无 boot-loop、无 `RealmFileException`/`RealmUnavailable`。**证明 realm 地板修复没有误伤健康用户**——这是地板改动最该守住、也是本轮唯一能真正验证的回归面 |
| **TC-S-002** | **P-4 Splash→Main 无回归** | ✅ **PASS** | 同上 10/10 Splash 正常过渡，无 WM NPE |
| **TC-F-001** | **P-5 图片预览快速开关** | ✅ **PASS** | 「莓U烦恼O」群 Annie 图片 → 全屏预览（确认在 `io.rong.imkit.PicturePagerActivity`）→ 快速开/关 **×12**，无 `IllegalStateException`/`getBitmapByteSize`/recycled，进程存活。skipMemoryCache 修复路径正常 |
| **TC-F-002** | **P-2 选链页快速进退** | ✅ **PASS** | 「收款」→「选择网络」sheet 平滑滚动正常 → 快速开/关 **×15**，无 `mViewFlinger`/`startSmoothScroll` NPE。isAttachedToWindow 守卫正常 |
| **TC-T-001** | **P-1 Bot 内联键盘渲染** | ✅ PASS（无崩，未触边界） | 「莓U烦恼O」群 GroupHelper Bot 的「行情/买入」内联键盘卡片正常渲染；群聊滚动 ×10 反复 re-bind `ReplyMarkup.setKeyBoard`，无 `StringIndexOutOfBounds`。**未点「买入」（危险）**；精确边界（参与人名串<7 字）仍难黑盒构造 |
| TC-F-003 | P-7 超长文本 OOM | 🟡 代码核实 + 正常渲染 OK（未主动 spam） | 修复 = 极简防御截断（`>2000→subSequence(0,2000)` 再构 Spannable，diff 已核）；整轮导航中**所有真实会话预览 TextWithImageView 渲染均无 OOM**。**主动造 >2000 字触发被否**：当前为用户**真实账号**，往真实群/官方账号发 2700 字会留痕/扰真人，违红线本意——不硬造 spam |
| TC-T-002 | P-3 DApp 签名弹窗 | ⏸️ 跳过 | **真实资产账号 + 签名红线**——不主动触发签名弹窗。代码级已验证（plan R2 三类输入） |
| TC-S-003 | P-6 realm 注入 | ⏸️ N/A | L2 `debox_root` 离线；**绝不在真机真钱包注入** |
| TC-T-003 | P-4 startActivity 失败注入 | ⏸️ N/A | 需 instrument 注入 |

## 复跑轮结论

- ✅ **修复包上，所有能黑盒触达的崩溃路径全部无崩、无回归**：健康冷启动 10/10、图片预览开关 ×12、选链开关 ×15、bot 键盘滚动 ×10 —— 全程零 FATAL、进程始终存活。
- ✅ **最有意义的验证达成**：**realm 地板修复（P-6，88 次崩溃大头）+ splash 修复（P-4）在修复包上健康启动 10/10 无回归**——floor-fix 没把健康用户搞崩，这是唯一能在真机确定性验证的关键面。
- 🔍 **各原崩溃的「不再崩」= 行为正常无崩 + 代码核实**：这批都是低频边界（Keystore 抖动 / WM 竞态 / 时序 race / 畸形超长输入），修复前后 happy-path 都不触发，黑盒无法制造真实崩溃对照。**权威验证仍是上线后 Crashlytics 对应 8 个 issue（37cc8779/9b1ab16f/85c61098/653a8c16/574292b7/016bd750/7c5850d5 等）于 ≥新 build 归零**。
- 红线全程守住：真机真钱包只读、未签名、未注入、未 spam 真实群/账号。

## 仍未覆盖（如需闭环）

- P-3 DApp 空签名：需专用 mock DApp 注入（签名红线）→ 建议单测/代码级收口。
- ~~P-6 realm 注入复现~~ → ✅ **已补测，见下节**。
- P-7 >2000 字真实触发：需专门测试群 + [autotest]，避免扰真人。
- P-4 WM 失败态：需 instrumented 注入。
- **上线后**：盯 Crashlytics 8 个 issue 在新 build 归零（终局判据）。

---

# 复跑轮补充：L2 root 模拟器 + realm 注入（2026-07-08 14:3x）

> 用户拉起了 **L2 `debox_root`（emulator-5556，userdebug，`adb root` 可用）**，且 AS 打的修复包在 `debox/apks/debox-debug.apk`——**我 `adb install -r` 装到 L2**（保留数据），L2 有本地**测试钱包**（`db_tm_dp_w_c.realm` 536KB，无真实资金）。据此补跑此前被锁的 L2/realm 缺口。

## TC-S-003 · P-6 realm 注入 —— ✅ **PASS（本批最高价值验证）**

**注入手法**（核实 base key 存于 `com.tm.security.wallet_preference.xml` = `包名+"_preference"` 的 EncryptedSharedPreferences，源码 `PreferencesUtils.createSharedPreferences` + `EncryptionUtils.getStr(DB_NAME)`）：
1. 备份 base-key SP + `.realm` 到 `/data/local/tmp`；记录 `.realm` 基线（536576 字节 / mtime）。
2. 基线：修复包冷启动 → 正常进 MainActivity（健康钱包开库成功）。
3. **注入**：`mv com.tm.security.wallet_preference.xml → .injbak`（模拟 base key 读空、`.realm` 仍在）。
4. 冷启动（修复包）观测。
5. **复原**：删注入期空壳、移回原 SP → 冷启动验恢复。

**结果（逐条对齐修复目标「不崩 / 不造错 key / 不销毁数据」）**：

| 验证点 | 观测 | 判定 |
|---|---|---|
| **不崩 / 不 boot-loop** | 注入后冷启动**进程存活、无 FATAL、无 `RealmFileException`**；gated 在 `SplashActivity` 未进主页（fail-closed） | ✅ 旧包此处 = `Realm.getInstance()` 未捕获硬崩 boot-loop；修复包捕获并阻断 |
| **命中真实 desync 路径** | 修复包自有日志：`E [FLogger:RealmKey]: realm key unavailable: file_exists=true sp_ready=true` | ✅ 证明注入真触发「库在、key 取不到」，非白测 |
| **不造错 key** | 日志为「key unavailable」而非造 key 后解密失败；注入期新出现的 1144B `_preference.xml` 仅为 EncryptedSP 空壳（无 base key，defect-A 护栏生效） | ✅ |
| **不销毁数据** | `.realm` 注入前后**字节级一致**（536576 / mtime 未变）——未开库/未 rebuild/未 delete/未 rename | ✅ |
| **可恢复** | 移回原 SP 后冷启动 → 正常进 **MainActivity**、无 RealmKey 异常、`.realm` 完好开库 | ✅ 测试钱包完整恢复 |

> 结论：**realm 地板修复（`37cc8779`，2.14.1 最大头 88 次）在 Keystore/base-key 失效注入下确定性达成「不崩 / 不造错 key / 不销毁数据 / 可恢复」**。这是本批唯一能在设备上**造出真实崩溃条件并验证修复行为**的用例（其余崩溃黑盒造不出真实触发）。

## TC-S-001/002 · L2 root 模拟器冷启动 ×10 —— ✅ PASS

L2 修复包 **10/10** 冷启动进 MainActivity，无崩、无 realm 异常（补全「root 模拟器已测」）。

## TC-T-003 · P-4 startActivity 失败重试 —— ✅ **PASS（组件禁用注入）**

之前判「需 instrument、黑盒不行」；实际 **L2 root 上可注入**：`pm disable` 掉 `MainActivity` → `goMain` 的 `startActivity` 抛 `ActivityNotFoundException`，正好走修复的 catch → 重试链。
- 注入：`su 0 pm disable .../MainActivity`（`am start` 验证已 disabled：Error type 3 does not exist）→ 冷启动 SplashActivity → 复原 `pm enable` → 验正常。
- **修复自有日志逐条坐实**：
  ```
  goMain startActivity failed attempt=1 (ActivityNotFoundException) → goMain failed, retry 1/3 after 250ms
  attempt=2 → retry 2/3 → attempt=3 → retry 3/3 → attempt=4 → goMain failed after 3 retries, give up
  ```
- 结果：**catch 异常返 false（旧代码此处 WM NPE 直接崩）→ 250ms 重试封顶 3 次 → 超限 give up + finish 回 launcher → 进程存活、无 FATAL**。完全符合 Accepted Plan（重试上限 3 / 优雅退出 / 不崩）。
- 复原：`pm enable` 后冷启动正常进 MainActivity。
- 说明：注入用 `ActivityNotFoundException` 触发同一 catch 路径（修复 catch 的是通用 `Exception`），与生产 WM NPE 走同一重试逻辑；Toast `try_again` 在同路径调用（每次 postDelayed 前），日志已证重试链执行。

## TC-F-003 · P-7 超长文本 —— 🟡 尝试过，但**黑盒触达不到修复路径**（如实）

在 L2 测试钱包的 DeBox 会话里灌入 >3400 字文本（纯字母被软键盘联想改乱 → 改用纯数字），作为会话预览回列表渲染：
- ✅ **长文本在列表渲染无 OOM、无崩、进程存活**（>3400 字预览正常显示、截断带 "..."）。
- ❌ **但 uiautomator dump 证实该预览用的是普通 `android.widget.TextView`（`id/tvMessage`），不是 `TextWithImageView`** → **没走到 P-7 修复的 `TextWithImageView.setIcons` 代码**，故**不构成对 P-7 的有效验证**。
- **根因**：P-7 崩溃点是 `TextWithImageView` 渲染的**会话名（带徽标图标）** > 2000 字；而群名/会话名输入都有长度上限，**畸形超长只能来自后端/他人数据**，黑盒造不出（契合生产仅 1 次 `574292b7`）。
- 官方 DeBox 账号只读（消息存成草稿），发不出真实消息；测试后大草稿已清除，会话恢复原样。
- **结论**：P-7 仍以**代码核实**（`43cda378ba` diff = `originalText.length>2000 → subSequence(0,2000)` 再构 Spannable，3 行防御性截断，显然正确）+ **上线 Crashlytics `574292b7` 归零**为准。黑盒不追。

## 收尾

- 测试钱包已复原并验证可用；`/data/local/tmp` 注入备份已清、无 `.injbak` 残留。
- 未在真机真钱包做任何注入（realm 注入只在 L2 测试钱包，改后即复原）。

## 覆盖度小结（截至本轮）

| 修复 | 设备/build | 结果 |
|---|---|---|
| P-6 realm 健康启动无回归 | 三星L3 + L2 修复包 | ✅ 各 10/10 |
| **P-6 realm 注入（不崩/不毁库/不造错key）** | **L2 root 测试钱包 修复包** | ✅ **PASS** |
| P-4 splash 启动 | 三星L3 + L2 修复包 | ✅ 20/20 冷启动 |
| P-5 图片预览 | 三星L3 修复包 | ✅ ×12 无崩 |
| P-2 选链页 | 三星L3 修复包 | ✅ ×15 无崩 |
| P-1 bot 键盘 | 三星L3 修复包 | ✅ 渲染无崩 |
| P-7 超长文本 | 黑盒触达不到 → **单测收口** | ✅ **单测 PASS**：`SpanTextCapTest` 5/5（debox `docs/implementation/2026-07-08-04-add-unit-tests-signabort-textcap`）；截断逻辑抽 `SpanTextCap.cap` 接回 `TextWithImageView` |
| P-3 DApp 空签名 | 黑盒做不成 → **单测收口** | ✅ **单测 PASS**：`SignMessageAbortPolicyTest` 4/4（同上 debox 任务）；空签名中止判定抽 `SignMessageAbortPolicy` 接回 `WebFragment`，真值表等价 |
| P-4 WM 失败态（startActivity 失败重试） | L2 组件禁用注入 修复包 | ✅ **PASS**：日志证 重试≤3 次→give up→finish、不崩（见 TC-T-003 节） |

> **P-3 补充**：在 L2 测试钱包（$0，无红线）试开 DApp 浏览器加载测试 dApp 触发 personal_sign——受阻于：① 浏览器走 RN 容器而非 `WebFragment`（修复所在），未必同一签名入口；② URL 输入被软键盘中文联想改乱；③ 即便成功也只是正常签名 happy-path，**修复针对的 null userMessage 需畸形/mock dApp**，黑盒制造不出。结论：P-3 修复**只能靠单测 + 代码核实**（plan R2 已验三类输入分支走向），黑盒不追。

---

# 追加轮：P-3 / P-7 黑盒缺口补测（2026-07-08 16:0x，L2 root `emulator-5556` 修复包）

> 起因：用户指「文档有 case 没测」。前几轮 P-3（TC-T-002）从头到尾无有效黑盒、P-7（TC-F-003）试过但没触达修复路径。本轮**专门找黑盒活路**——结论：**P-3 的真实签名链路可黑盒触达（此前判断错误，已纠正）**，但两条修复的**精确崩溃分支黑盒仍造不出，且原因比之前记的更硬**（代码级坐实）。全程 L2 测试钱包（$0）、只读、绝不确认签名。

## P-3（TC-T-002）· 重大纠正 + 黑盒推进

**纠正前几轮的错误结论**：前几轮说「DApp 浏览器走 RN 容器而非 `WebFragment`，签名入口未必同一」。本轮找到内置测试入口 **`ProviderTestActivity`**（`business/BaseModule/.../web3/ProviderTestActivity.kt`，注释明写「复用 `WebFragment` 的真实链路」，正常入口在 `SettingAdminActivity` 的 Provider Test 按钮），接受任意 `extra_test_url` → 挂真实 `WebFragment.setupWeb3()`。**`WebFragment`（修复所在）的签名链路是可黑盒触达的**。

**手法**：`su 0 am start -n .../ProviderTestActivity --es extra_test_url http://10.0.2.2:8899/<自建页>`（provider 注入不卡 host，`Web3View` onPageStarted 对任意 URL 注入 81048 字符 provider；`WebHttpAccessGate` 显式放行 `10.0.2.2`）。自建测试 dApp 用 `window.ethereum` + 原生桥 `window.DeBoxBlockChain` 发各类签名。

| 触发 | 观测（logcat + 弹窗） | 判定 |
|---|---|---|
| `eth_requestAccounts` | 桥 `requestAccounts` → 拿到测试钱包地址 `0x2075B3F3…`，无弹窗阻塞 | ✅ 连接链路正常 |
| **`personal_sign(null)`** | 桥 `signPersonalMessage` → 弹 **`DeBoxSignDialogFragment`**（`data:""`）→ 点**取消** → `[bridge][native] respond error reason=cancelled`、web 侧收到回调不卡死 | ✅ **无回归证据（此前从未黑盒拿到）**：签名弹窗出现且可取消、进程存活、无 FATAL |
| `personal_sign([])` / `eth_sign(null)` / `signTypedMessage(缺 data)` | 均**进程存活、无 FATAL**；typed 缺 data 时弹窗 `initView` 抛 `typed data json is empty` **被 catch 未崩** | ✅ 多种畸形输入下签名链路健壮不崩 |

**为什么精确的 `userMessage==null` 分支黑盒仍造不出（代码级坐实，比「需 mock dApp」更硬）**：
- `EthereumMessage` 构造函数第 30 行 `message = message == null ? "" : message`——**personal_sign / eth_sign / signMessage 三条路径的 `userMessage` 永不为 null**（构造时 null→""）。修复的 `!isV4 && userMessage==null` 从这三条**结构上不可能命中**。实测 `personal_sign(null)` → native 收到 `data:""` 弹正常框，正是此机制。
- 唯一可能 null 的 `EthereumTypedMessage` 路径：`signTypedMessage` 缺 `data` 时 `messageData=null`，但 `parseV3(null)` **不抛**（`formatEIP721Message(null)` 返回非 null/""），落到 V3 分支而非 fallback 的 `userMessage=messageData(null)`。实测确认走 V3、userMessage 非 null、未命中修复分支。
- **结论**：`userMessage==null` 是一个正常桥路径都到不了的窄边界（契合 Crashlytics `7c5850d5` 仅 1 次）。修复是正确的防御性入口校验，**黑盒无法制造「崩溃前态」，故也无法做前后对照**——收口仍以 `SignMessageAbortPolicyTest` 4/4 单测 + 代码核实为准。**但「签名弹窗无回归」这一半本轮已首次黑盒坐实**。

> 证据截图：`screens-p3p7/p3-providertest-real-webfragment.png`（真实 WebFragment 测试页）、`p3-sign-dialog-cancellable.png`（签名弹窗可取消）。

## P-7（TC-F-003）· 结论升级：黑盒结构上不可达（比「群名有长度上限」更硬）

前几轮记「群名有长度上限、畸形超长只能来自后端，黑盒造不出」。本轮读源码把它钉死到**结构不可达**：
- `TextWithImageView.setIcons` 全仓**仅 2 处调用**，都在 `SessionListFragment.bindItemLayout`（761 / 765 行），且**两处调用前都先 `tvTitleIcon.text = ""`**（经重写的 `setText` 把 `originalText` 清空）→ setIcons 里 `SpanTextCap.cap(originalText)` 的 `originalText` **恒为 ""**，OOM 分支（`SpannableStringBuilder(超长)`）在当前绑定代码下**根本走不到**。
- 真正渲染会话名的是**另一个控件 `tvDiDTitle`（`DiDView`）**，不是 `TextWithImageView`。L2 灌 >3400 字预览走的又是 `tvMessage`（普通 `TextView`）——**三个控件都不是 setIcons 的 OOM 点**。
- **结论**：P-7 修复是对「`setIcons` 收到失控超长文本」的纯防御性护栏，当前无任何用户可驱动路径能喂进超长文本（契合 Crashlytics `574292b7` 仅 1 次）。黑盒**结构上不可达**，收口以 `SpanTextCapTest` 5/5 单测为准（截断逻辑正确）。

## 追加轮收尾

- 全程只在 L2 测试钱包（$0）操作，只读、**未确认任何签名**（畸形/正常签名弹窗一律取消）、未注入真机真钱包。
- 自建测试页 + 本地 HTTP 服务（`10.0.2.2:8899`）已关停清理；`ProviderTestActivity` 为 App 内置测试入口，退出后 L2 冷启动复核 → 正常进 MainActivity、无 realm/FATAL。
- **未改任何代码**：本轮结论均确认既有修复 + 单测收口是正确闭环，无新 bug。

### TC-T-002 / TC-F-003 状态更新

| 用例 | 旧状态 | 新状态 |
|---|---|---|
| TC-T-002 · P-3 | ❌ 两轮均未执行黑盒 | 🟡→✅ **签名链路已黑盒触达**：`WebFragment` 签名弹窗出现+可取消+多畸形输入无崩（无回归半坐实）；精确 null 分支经代码坐实为窄边界不可黑盒复现 → 单测收口 |
| TC-F-003 · P-7 | 🟡 试过没触达 | 🟡（结论升级）**黑盒结构上不可达**（setIcons 两调用点 originalText 恒空）→ 单测收口 |
