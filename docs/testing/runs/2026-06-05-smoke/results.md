# DeBox 冒烟测试结果（2026-06-05）

## 概要

| 项 | 值 |
|---|---|
| 设备 | SM_S9210 (RFCYA0F9SSZ)，1080×2340 |
| 被测 | `com.tm.security.wallet`（正式包 v2.12.2，**测试环境**，已登录） |
| 方式 | adb 黑盒（screencap + uiautomator dump + input tap） |
| 轮次 | 第 1 轮（迭代测试） |
| 结果 | **PASS 8 / FAIL 0**（P0×3 + P1×5） |

---

## 用例结果

| # | 用例 | 结果 | 证据 | 说明 |
|---|------|------|------|------|
| TC-S-001 | 冷启动 | ✅ PASS | `tc-s-001_cold_start.png` | force-stop→start；焦点=MainActivity 无崩溃；消息页「全部/私信/群组/俱乐部/P2P」齐全；测试环境数据可见（测试club112 / 担保师测试群 / 测试通知2） |
| TC-S-002 | 底部 4 Tab 导航 | ✅ PASS | `tc-s-002_friend.png` / `_browse_retry.png` / `_mine.png` / `_back_msg.png` | 消息✓ 朋友✓ 发现✓ 我的✓，回退消息✓。发现页（关注/行情/活动/DApp）首截在 loading，等 3s 后正常加载动态流 |
| TC-S-003 | 登录态校验 | ✅ PASS | `tc-s-002_mine.png` | 「我的」页显示 Lv.16、41关注 / 484粉丝 / 152动态、总资产、转账/收款 —— 确证已登录 |

底部 4 Tab 元素（resource-id + 点击坐标）：
`tvDao`(消息, 135,2211) / `tvFriend`(朋友, 405,2211) / `tvBrowse`(发现, 675,2211) / `tvMine`(我的, 945,2211)

### 功能测试（P1）

| # | 用例 | 结果 | 证据 | 说明 |
|---|------|------|------|------|
| TC-F-001 | 消息分类切换 | ✅ PASS | `tc-f-001_{dm,group,club,p2p,all}.png` | 私信/群组/俱乐部/P2P/全部 逐个切换，列表内容正确变化（P2P→USDT 订单） |
| TC-F-002 | 进入会话查看 | ✅ PASS | `tc-f-002_chat.png` | 首个会话→`io.rong.debox.ui.MessageListActivity`（融云 IM），群标题/公告/消息流/输入栏完整；BACK 返回，**未发送** |
| TC-F-003 | 搜索 | ✅ PASS | `tc-f-003_result.png` | 顶部搜索→`SearchAllActivity`，输入"BOX"响应，结果分用户/群组 |
| TC-F-004 | 关注列表 | ✅ PASS | `tc-f-004_following.png` | 朋友页→`FriendItemActivity`，关注列表（含"互关好友"），返回正常 |
| TC-F-005 | 发现页 Tab 切换 | ✅ PASS | `tc-f-005_{follow,market,activity,dapp}.png` | 关注/行情/活动/DApp 全切换；行情有币种、活动空态、DApp 列表正常 |

**关键 Activity 情报**（供 M2 白盒固化）：
- 主页 `com.currency.wallet.main.activity.MainActivity`
- 聊天 `io.rong.debox.ui.MessageListActivity`（融云 RongCloud IM SDK）
- 搜索 `io.rong.debox.ui.search.SearchAllActivity`
- 好友 `com.currency.wallet.main.activity.FriendItemActivity`
- 底部 Tab id：`tvDao`/`tvFriend`/`tvBrowse`/`tvMine`（ImageView, clickable）

---

## 观察 / 发现

1. **uiautomator dump 偶发 `could not get idle state`**：DeBox 消息页常处非 idle（消息轮询/角标动画），adb dump 有时失败、需重试。截图始终可靠。→ mobile-mcp 的 `list_elements`（accessibility 直读）可能更稳，这是它相对裸 adb 的真实价值。
2. **主流程原生、部分功能 RN**：消息/朋友/我的 均原生 XML，resource-id 齐全（底部 Tab、列表项 tvTitle/tvMessage 等）；发现页动态含 `debox://rn/c2c/market|create` 路由 → c2c 等子功能走 RN，到那些页面再验取元素能力。
3. **发现页加载较慢**：首次进入需 >1s。自动化应 **wait 内容出现** 而非固定 sleep。
4. **脚本注意（zsh）**：`for x in $VAR` 在 zsh 默认不做单词分割，循环只取整串。本轮 TC-F-005 曾因此只点中首个 Tab，改硬编码坐标后修复；后续脚本避免依赖未引用变量分词。
5. **会话/搜索均融云 SDK**：聊天与搜索页是 `io.rong.debox.*`（RongCloud），其内部消息项/搜索结果项的元素结构可能与原生主页不同，白盒固化进聊天页时需单独验证选择器。

---

## M2 白盒固化（connectedAndroidTest 真机回归）

把上面 3 条黑盒冒烟固化成可回归白盒用例：`autotest/src/androidTest/java/com/autotest/debox/DeBoxSmokeTest.kt`。
形态 = 独立 test APK（`com.autotest.test`）经 **UiAutomator + resource-id 跨进程**操作已登录的正式包，**不重装/不覆盖、复用登录态**。命令 `./gradlew :autotest:connectedDebugAndroidTest`。

| 用例 | 结果 | 耗时 |
|---|---|---|
| tcS001_coldStart_landsOnMessageTab | ✅ PASS | 9.1s |
| tcS002_bottomTabNavigation | ✅ PASS | 30.2s |
| tcS003_loginState_mineTabShowsUserInfo | ✅ PASS | 18.8s |

**tests=3 / failures=0 / errors=0，BUILD SUCCESSFUL。**

### 固化过程暴露并修复的 3 个框架级真实 bug

| # | bug | 现象 | 修复 |
|---|-----|------|------|
| 1 | `BaseUiTest.launchApp` 缺 `FLAG_ACTIVITY_NEW_TASK` | 从 instrumentation 非 Activity context 启动外部 App 会崩 | 加 `NEW_TASK or CLEAR_TASK` |
| 2 | 报告/日志/截图默认写 `/sdcard/Pictures/autotest` | Android 10+ scoped storage **EPERM**，致 tearDown 抛错、3 条全 FAILED（但 body 已 PASS） | `TestConfig.screenshotDir` 默认改 `getExternalFilesDir`（app 私有目录免权限） |
| 3 | UiAutomator 默认 `waitForIdleTimeout=10s` × DialogDismissInterceptor 每步 23 次 `findObject` | 被测 App 永不 idle → 每条用例卡 **334–484s** | `BaseUiTest.setUp` 置 `Configurator.waitForIdleTimeout=0` → 降至 **9–30s（−97%）** |

> 这 3 个都是**影响所有项目**的框架缺陷，**只有真机固化（进框架代码路径）才暴露得出来**——纯 adb 黑盒探索碰不到，这正是固化桥的价值。

---

## 框架优化（基于实战，A 类）

修完 3 个 bug 后，针对实战暴露的性能/证据点继续优化（3 项均仍 3/3 PASS）：

| 项 | 内容 | 效果 |
|---|------|------|
| A1 弹窗扫描提速 | `DialogDismissInterceptor` 每步 23 次 `findObject` → 1 次 `findObjects` 本地匹配 | 见下表 |
| A3 截图策略 | 成功零图、失败才截并回填 `StepResult`/`Failure`（证据可追溯） | 图量大减，"成功不截、失败再看" |
| A2 报告回流 | 测试结果由 gradle 主机报告 `build/reports/androidTests/connected/debug/index.html` 自动回流 | 无需 adb pull（test APK 跑完即卸 + scoped storage 不可行）；框架富报告/截图回流需 TestStorage，暂缓 |

A1 提速实测（connectedAndroidTest，仍 3/3 PASS）：

| 用例 | 优化前 | 优化后 | 降幅 |
|---|---|---|---|
| tcS001 冷启动 | 9.1s | 7.1s | −22% |
| tcS002 4-Tab 导航 | 30.2s | 6.3s | −79% |
| tcS003 登录态 | 18.8s | 5.4s | −71% |
| **合计** | **58s** | **19s** | **−67%** |

---

## 结论

黑盒探索 **8/8 PASS**（P0×3 + P1×5）；白盒固化 **3/3 PASS**（connectedAndroidTest 真机）。
**M0（环境）→ M1（黑盒探索）→ M2（白盒固化回归）完整闭环已跑通**，修复 3 个框架级真实 bug，并完成 A 类性能/证据优化（整体提速 67%）。

严守硬约束：全程仅 terminate→launch，未 clearAppData / 登出 / 切环境 / 发送消息。
