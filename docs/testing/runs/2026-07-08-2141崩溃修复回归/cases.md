# 用例：DeBox 2.14.1 崩溃修复回归（dev `edfc624867..HEAD`）

> 优先级：P0 = 核心/启动链必须过 · P1 = 高频路径 · P2 = 边界/注入/难触发
> 设备：L1 普通模拟器（默认）· L2 root 模拟器（注入）· L3 真机（复核）
> 本批全部为**崩溃修复回归**：每条两个角度——① 崩溃复现后不再崩（修复生效）② 该功能无回归。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 2.14.1 崩溃修复回归（7 处 fix，全 Crashlytics 2.14.1 issue） |
| App 包名 | `com.tm.security.wallet` |
| 代码范围 | 本地 `/Users/xiaochengcheng/StudioProjects/debox` dev 分支 `edfc624867`(含)..`HEAD`（**未推送**） |
| 测试方式 | Claude Code + mobile-mcp（模式 A · AI 驱动黑盒） |
| 前置条件 | 已登录 + **测试环境优先**（L2 debox_root 测试钱包 / 三星 L3 测试环境）；正式环境（小米 L3）只做只读冷启动复核，**不碰资金** |
| 崩溃观测 | 每条跑测同时开 logcat crash 抓取 + `mobile_list_crashes`；全局验收 = 无新 FATAL + 上线后对应 Crashlytics issue ≥新 build 归零 |

### 被测修复清单（逐 commit）

| P | commit | 模块 | Crashlytics | 崩溃点 / 根因 | 修法 |
|---|---|---|---|---|---|
| P-6 | 54f6e62893 | realm | `37cc8779`（88 次·最大头） | 钱包库 `db_tm_dp_w_c.realm` Keystore base-key 读空→造新 key 解不开旧库→`Realm.getInstance` 零兜底硬崩 boot-loop | 地板：不崩（chokepoint try/catch + probeHealthy fail-closed）/不造错 key/不删库 |
| P-4 | d00fb761ca | app | `9b1ab16f`（2 次） | `SplashActivity.goMain` `startActivity` 撞系统 WM `transferStartingWindow` 竞态 NPE `getDisplayContent` | goMain 返 Boolean，失败 250ms 重试≤3 次 + `try_again` Toast，超限 finish |
| P-5 | 8cdc6b6e3d | im | `85c61098`（3 次） | 图片预览 `asBitmap` 销毁时 Glide 释放对已 recycle bitmap 调 `getBitmapByteSize` ISE | 该请求加 `.skipMemoryCache(true)`（不入 LruCache） |
| P-2 | 580b071f41 | common | `653a8c16`（2 次） | `SmoothLayoutManager.smoothScrollToPosition` 异步 post 回来 RV 已 detach → `mViewFlinger` NPE | 前置 `if (!recyclerView.isAttachedToWindow) return` |
| P-7 | 43cda378ba | base-business | `574292b7`（1 次） | `TextWithImageView.setIcons` 超长会话名/预览 `SpannableStringBuilder(originalText)` 巨量 char[] OOM | 截断到 2000 字上限再构 |
| P-1 | edfc624867 | im | `016bd750`（4 次） | `ReplyMarkup.setKeyBoard` `strPeople.length()<7` 时 `replace(负,…)` StringIndexOutOfBounds | 起点 `Math.max(0, len-7)` clamp |
| P-3 | 5e77725718 | base-module | `7c5850d5`（1 次·**签名路径**） | `WebFragment.handleSignMessage` 非 V4 分支 `userMessage.toString()` null NPE | 入口级空值校验→`onSignError(callbackId)` 中止，各钱包分支共享 abort-on-null |

> 溯源：debox `docs/implementation/2026-07-08-02-fix-2141-crash-sweep/{index,plan}.md`、`2026-07-08-01-analyze-realm-decrypt-crash/index.md`。

### 危险红线（本批专项）

- **不清数据 / 不卸载 / 不登出 / 不切环境**——尤其 realm 用例：**绝不 `pm clear`、绝不删/改真机正式钱包的 `.realm`**（含私钥，不可逆）。realm 注入复现仅在 **L2 debox_root 测试钱包**做，且只 rename 加密 SP 后**必须复原**。
- **P-3 DApp 签名路径**：只验证正常签名弹窗出现且**可取消**，**绝不点「签名 / Approve / 确认」**。畸形空签名需 mock DApp 注入 → 代码级已验证，黑盒不强行触发。
- 选资产/选链页（P-2）只进选择页，**不发起转账、不点确认支付**。

---

## 0. 前置检查（P0，阻断）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-P-001 | 环境与登录基线 | 选设备（默认 L1 emulator-5554）→ `svc power stayon true` → 网络噪声预检 → 启动 App | 设备在线、屏幕保活、App 正常起、**已登录 + 测试环境**（不符则停下报告） | P0 | L1 |
| TC-P-002 | 装的是被测 build | 确认设备安装的 `com.tm.security.wallet` 为含本批修复的 build（版本/构建号或本地打包安装） | 版本号对应本批修复（否则用例只能验回归面、不能验修复生效） | P0 | L1 |

> ⚠️ 关键前提：这 7 处修复**尚未推送、可能未打进设备现有 build**。若设备装的是旧包，本批只能测「功能无回归」，**不能验证「崩溃已修」**——须先由用户在 Android Studio 打含本批改动的 debug 包并安装（P-4/realm 编译还依赖 AS 解 RN classpath，见 debox 任务记录）。开跑前 TC-P-002 必须先确认。

---

## 1. 启动链崩溃回归（P0 · boot-loop 影响面最大）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-S-001 | **P-6 健康钱包冷启动无崩溃（关键回归）** | 健康已登录态下 `terminate → launch` 冷启动 App **连续 ≥10 次**；每次等首页加载，进资产/钱包页确认钱包数据可加载 | 每次都正常进主页、**无 boot-loop**；logcat **无** `RealmFileException`/`RealmUnavailableException`；钱包/资产列表非空（地板改动**不误伤健康用户**：不误判库不可用、不造新 key） | P0 | L1 → **L3 复核**（小米正式环境·只读冷启动，验真实钱包不受影响） |
| TC-S-002 | **P-4 冷启动 Splash→Main 无崩溃** | 冷启动 App **连续 ≥10 次**，观察 Splash 过渡到 MainActivity；若命中首次引导态则走 GuideActivity | 每次正常进主页/引导页；logcat 无 `NullPointerException getDisplayContent`/WM 相关崩溃；启动过渡动画正常 | P0 | L1 |
| TC-S-003 | **P-6 realm 注入复现·不崩不毁库**（高级注入） | **仅 L2 debox_root + 测试钱包**：记录 `.realm` 与加密 SP 现状 → 模拟 base-key 读空（rename `EncryptedSharedPreferences` xml，保留 `db_tm_dp_w_c.realm`）→ 冷启动 → **复原 xml 冷启动** | 注入后启动**不崩**（`probeHealthy` fail-closed，不 boot-loop）；`.realm` 文件**未被删/改/重建**、**未生成新 key**；复原后正常进主页。**绝不在真机正式钱包上做** | P2 | L2 |

---

## 2. 高频路径崩溃回归（P1）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-F-001 | **P-5 图片预览快速开关不崩** | 进含图片的会话（测试群，图片可带 `[autotest]`）→ 点图进全屏预览 → 立即返回/关闭 → 快速反复 **≥10 次**；切换多张大图预览；加载中途按 Home 切后台/旋转屏 | 无 `IllegalStateException`（`getBitmapByteSize`/Glide recycled bitmap）；预览图正常显示、返回正常；反复开关无崩溃/无残留 | P1 | L1 |
| TC-F-002 | **P-2 选资产/选链页快速进退不崩** | 进「选币/选链」页（收款或转账入口的选 token 页——**只到选择页**）→ 立即返回 → 快速反复进退 **≥10 次**；页内快速切链 tab / 快速上下滑动 | 无 `NullPointerException`（`mViewFlinger`/`startSmoothScroll`）；选择页正常渲染、链横向列表滚动定位正常。**红线：只进选择页，不发起转账、不点确认** | P1 | L1 |
| TC-F-003 | **P-7 超长会话名/预览不崩 OOM** | **测试群内**：把群名设为超长文本（>2000 字，粘贴构造）或发一条 >2000 字单行消息 → 回会话列表看该会话名/预览渲染；反复进出列表、滚动 | 无 `OutOfMemoryError`（`TextWithImageView.setIcons`/`SpannableStringBuilder`）；会话列表正常渲染（文本按 2000 上限截断显示、图标 badge 正常）；不卡死 | P1 | L2（测试群构造） |

---

## 3. 边界 / 签名路径 / 难触发（P2）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-T-001 | **P-1 Bot 内联键盘消息渲染不崩** | 打开会有内联键盘（reply-markup 按钮 + 参与人数展示）消息的 bot 会话，浏览/点按其键盘按钮，观察按钮上「xx 人」文案渲染 | 键盘消息渲染无 `StringIndexOutOfBoundsException`（`ReplyMarkup.setKeyBoard`）。**说明**：精确边界（参与人名串 <7 字）黑盒难构造，本条以冒烟观察 + 上线 Crashlytics `016bd750` 归零为主 | P2 | L1 |
| TC-T-002 | **P-3 DApp 签名弹窗回归（签名红线）** | 打开 DApp 浏览器 → 触发一个**正常**签名请求 → 观察签名弹窗出现 → **点取消**（**绝不确认签名**） | 正常签名弹窗出现且可取消；`handleSignMessage` 无 `NullPointerException`（`String.toString()`）；取消后 web 侧收到回调不卡死。**畸形空 userMessage 需 mock DApp 注入→代码级已验证（plan R2 三类输入），黑盒不强行触发** | P2 | L3 测试钱包 |
| TC-T-003 | **P-4 startActivity 失败重试路径**（需注入） | goMain `startActivity` 抛异常路径无法黑盒自然触发 → 需 instrument/hook 注入让其抛一次 | 失败时不立即退出 → 弹 `try_again` Toast → 250ms 重试≤3 次内成功进主页 → 超 3 次 `FLogger.e` + finish。**本条黑盒不可注入，标注需 Android Studio instrumented 冒烟 / 代码级已验证** | P2 | L1（instrumented，本轮或转 follow-up） |

---

## 执行说明

- **每条**：`terminate → launch` 重置 → 操作 → 验证 → 截图 → 全程 `mobile_list_crashes` + logcat FATAL 抓取。长测开 `/loop 5m` 监工。
- **修复生效 vs 无回归**：仅当 TC-P-002 确认设备装的是**含本批修复的 build** 时，"崩溃不再复现"才证明修复生效；否则各条只证明"该功能路径无回归、当前无崩溃"，须在装新包后复跑验证修复。
- **发现 FAIL / 崩溃**：先写 `results.md`（附 logcat 根因 + 截图），修复走 agent-dev-loop（在 debox 仓建 `docs/implementation/`），**不直接改码**。
- **设备路由**：默认 L1；realm 健康回归加 L3 小米正式环境只读复核；realm 注入 + 超长文本构造走 L2 debox_root 测试钱包；DApp 签名走 L3 测试钱包。真机不在线不阻塞，标 `⏸️-待真机`。
