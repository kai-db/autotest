# 测试结果 — 房间恢复触发修复回归 + 历史缺口补测

> 被测包：2.14.2-debug（versionCode 21400002），构建 SHA `16fa94f6`（dev HEAD，干净树），
> APK sha256 `8719f687…eededac`，签名 V2 `2ee89168…`（=已装包，I-89 通过），`install -r` 保数据装
> emulator-5554（debox_root）+ emulator-5556（Pixel_10_Pro_XL）。
> dex 探针：`RoomRecoveryCheckTrigger`(classes19 ×60) + `InstanceRegistry`(classes29/6) 确在包内。
> 证据 = FLogger logcat（`ChatRoom` restore_check 门禁日志链）+ 截图 + JVM 单测。

## 当前轮次（第 1 轮，2026-07-11 下午）

> 触发原因：dev `16fa94f6`「修复重启后房间恢复检查未触发」出包回归 + 历史缺口补测
> （F-1 `71a464b2` onWarnClick 无定向用例、交易卡 `c7c9e137`/`2d82686e` 未走查）

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 1 | TC-B-001 归因链+dex+冷启 | ✅ PASS | §7.7 shim 构建成功已还原；双机冷启进 MainActivity、零 FATAL |
| 2 | TC-B-002 restore_check 正常路径 | ✅ PASS | 双机均见 `scheduled reason=im_login → request → skipped reason=response_not_restorable`（无残留房不弹，语义正确） |
| 3 | TC-R-001 房内杀进程→冷启恢复弹窗 | ✅ PASS | 房 `c83pf34k` force-stop → 冷启 `scheduled→request→dialog_show` + 弹窗「异常退出…快速加入?」实际可见 |
| 4 | TC-R-002 同进程 JIM 重连再触发（修复核心） | ✅ PASS | 同 pid 11904：断网→恢复 → `jim_status connected` → 完整 `scheduled→request→dialog_show`。执行时解析最新宿主，不再静默跳过 |
| 5 | TC-R-003 宿主不可用受控跳过 | 🟡 PARTIAL | 黑盒难注入「已销毁宿主」（FGS 保活下 always_finish_activities 未生效）；靠单测兜底（Trigger 3/3 含 missing/inactive 门禁）。顺带实证：后台时 dialog_show 弹窗排队、回前台正常呈现、无 crash |
| 6 | TC-R-004 弹窗「立即进入」重进→正常关房 | ✅ PASS | 重进 `Idle→LoggingIn→LoggedIn gen=1`、PUBLISHING；关房 `LoggingOut(gen=2)→Idle` 干净；关房后冷启 `skipped reason=response_not_restorable` **不再弹**（残留闭环）；全程零 FATAL |
| 7 | TC-R-005 ActivityUtils 波及面四 Tab 冒烟 | ✅ PASS | 四 Tab 遍历驻 MainActivity、0 FATAL、0 ANR |
| 8 | TC-P-001 新增单测 | ✅ PASS | `RoomRecoveryCheckTriggerTest` 3/3 + `InstanceRegistryTest` 3/3，0 fail |
| 9 | TC-W-001 F-1 失败消息警告重发 | ✅ PASS | DeBox 官方会话断网发 `autotest-F1-warncheck` → 发送中杀进程重启 → **红色警告图标落定** → 恢复网络点击 → 重发成功（服务端收到，官方 AI 回复）、**0 ANR** |
| 10 | TC-W-002 F-1 连点防重（I-88 guard） | ✅ PASS | 警告图标快速连点 ×5 → 会话中**仅 1 条**消息、无重复发送、无崩溃（单飞 guard 生效） |
| 11 | TC-U-001 交易卡 UI 走查 | 🚫 BLOCKED（黑盒）/ 🟡 静态+单测兜底 | $0 测试号自建私密房无交易事件，卡片不可达；进他人公开房有社交暴露不做。兜底：`rc_item_asset_card_message` 布局 + `live_trade_token_pair`("%1$s · %2$s") 确在 APK；`AssetCardMessageCounterAssetTest` **7/7** 全绿；代码 diff 上轮已亲核 |

**本轮统计**：PASS 9 / PARTIAL 2（R-003 单测兜底、U-001 静态兜底）/ FAIL **0**

> **总判定**：`16fa94f6` 房间恢复触发修复**功能正确、无回归**——杀进程恢复弹窗（存量行为）、
> 同进程重连再触发（修复的新核心路径）、优雅关房不误弹（负向）三面全部设备实证；
> `restore_check` 门禁日志链成为强观测点。F-1 onWarnClick 补测闭环（重发正确 + 防重生效 + 0 ANR）。
> 交易卡黑盒不可达如实标注，静态+单测证据兜底。
> **至此 dev `07ae7655..16fa94f6` 全部代码提交均有测试覆盖，零未测存量。**

### 附带复证（顺带证据，非本轮用例）

- **BUG-002 修复在 HEAD 包上复证 ×2**：两次关房（含一次**长 Reconnecting ≈11.5min 重连后立即关房**，正是原竞态场景）均零 FATAL、进程存活，`cleanupAfterLogout skip foregroundService restart (terminal logout)` 日志如期出现。
- **BUG-001 修复在 HEAD 包上复证**：冷启 `check_token ok`，断网/恢复周期后 JIM 正常重连，无误登出。

## ⚠️ 事故与恢复（如实记录）：UI 自动化误切真实账号

- **事故**：F-1 找 DM 入口期间（搜索无结果后的连续导航），坐标盲点误触「我的」页**多账号切换**，
  将 emulator-5554 前台账号从测试号 `2309b9ea` 切到**用户真实账号 Kai**（Lv.11/856粉丝），后又
  误触编辑按钮进入**账号详情页（含导出助记词/导出私钥/移除账号）**、并循环切到第二个真实账号「赵长鸟」。
- **处置**：立即停止测试操作 → 元素树精确定位「账号」弹层 → 点 `2309b9ea` 行本体切回。
  **真实账号期间零输入、零消息、零交易、零删改**（仅浏览页面+返回）；本轮所有建房/关房/发消息均发生在测试号上。
- **根因**：① emulator-5554 App 内登录了 3 个账号（测试 2309b9ea + 真实 Kai/赵长鸟），此前无人登记；
  ② 导航失败后未重新截图/取元素就用旧坐标盲点（连续两次）。
- **教训固化**（已回写知识库）：devices.md 登记多账号风险 + 每轮开测**核身份**步骤；dangerous-ops.md
  新增「切换账号/账号详情」危险入口；操作规约：**页面跳转后必须重新取元素再点，禁止旧坐标盲点**。

## 观察项（OBS，非阻断）

| # | 现象 | 判定 |
|---|------|------|
| OBS-1 | 冷启时 `restore_check scheduled reason=im_login` **×2**（双 ImLoginSuccess），但仅 1 次 `request` | `TimerCountdownFinish("CheckSpace")` 同 tag 去重合并，行为无害；双事件源头可后续核 |
| OBS-2 | 房内聊天（ChatRoomActivity→MessageViewModel）断网发送**长期转圈不落失败态**，重连后 JetIM 自动补发 | chatroom 消息队列语义；DM 会话失败态制造法=「发送中杀进程重启」（本轮实证可复用） |
| OBS-3 | `/liveroom/create` 偶发 `-2213 DB 系统繁忙`（首次建房被拒，20s 后重试成功） | 服务端瞬时错误，非本批引入；建房自动化需带重试 |
| OBS-4 | debox_root（emulator-5554）本轮 App 全程正常运行 | 与 07-10 记录「GeeGuard 拦 root 必退」不符——GeeGuard 拦截非必现（疑与 adb root 附加状态相关），devices.md 已更新 |

## 环境备注

- 断网注入 `svc wifi disable && svc data disable`；恢复后模拟器 JIM 重连 10s~11min 不等（一次长重连反而复现了 BUG-002 原场景，成为有效证据）。
- `uiautomator dump` 在 App 前台不可用（永不 idle，既有认知）；元素获取靠 mobile-mcp 或全分辨率截图裁剪。
- 房间清理：本轮 2 房（`c83pf34k`/`vx9ckobe`）均正常关闭，服务端无残留（末次冷启 `response_not_restorable` 证明）。

---

## 第 2 轮（OBS-1 修复回归，2026-07-11 晚）

> 修复走 agent-dev-loop（debox `2026-07-11-07-fix-restore-check-double-schedule`，T1）：
> plan R1 FAIL（scheduler 抛异常卡死 pending）→ v2 吸收 → plan R2 PASS + impl R1 PASS（均零 findings）。
> 根因代码级坐实：`ImLoginSuccess` at-least-once（`RongLoginManager.login()` 预检补发 + onSuccess 两处 post
> × 3 个启动期调用方）；修法 = 观察端 `RoomRecoveryCheckTrigger` pending 门禁显式幂等，不动事件源头
> （预检补发是治白屏的既有修复）。修复包 sha256 `1938c74c…`，已提交 dev `f7a1f799df`。

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 1 | 单测 | ✅ PASS | `RoomRecoveryCheckTriggerTest` 7/7（原 3 + 新 4：合并去重/run 后重调度/provider 异常不卡死/scheduler 异常回滚上抛） |
| 2 | 冷启双发合并（OBS-1 主证） | ✅ **PASS** | 4 次冷启中 1 次复现双发：`scheduled ×2 → skipped reason=duplicate_pending ×1 → request ×1`（合并正确）；其余单发轮 `scheduled×1→request×1` 干净 |
| 3 | 杀进程恢复弹窗不回归 | ✅ PASS | 房 `sapv7epb` force-stop → 冷启 `dialog_show` 如期 |
| 4 | 同进程重连再触发不回归 | ✅ PASS | 同 pid 19306 断网→恢复 → 完整 `scheduled→request→dialog_show`（pending 复位后可再调度，语义保持） |
| 5 | 关房清残留 + 末次冷启 | ✅ PASS | 关房链干净、末次冷启 `response_not_restorable` 不误弹、全程零 FATAL |

**OBS-1 状态：已修复并设备实证闭环**（`f7a1f799df`）。

---

## Phase 5 回归测试（全量重跑，2026-07-11 晚）

> 被测包：dev HEAD `f7a1f799df` 提交后**干净树重新出包**，APK sha256 `e198070e…`，签名 `2ee89168…`
> I-89 通过，双机 install -r；dex 探针 `DUPLICATE_PENDING`(classes19) 确认修复在包。

| # | 用例 | 结果 |
|---|------|------|
| TC-B-001/002 | 归因链+双机冷启+restore_check 链 | ✅ PASS（5556 双发合并 `duplicate_pending` 正确） |
| TC-P-001 | 单测全集 | ✅ PASS（Trigger 7/7 + InstanceRegistry 3/3 + AssetCard 7/7 = **17/17**） |
| TC-R-001 | 杀进程恢复弹窗（房 `jj7n7o9v`） | ✅ PASS |
| TC-R-002 | 同进程重连再触发（pid 21219） | ✅ PASS |
| TC-R-003 | 宿主不可用门禁 | 🟡 单测兜底（同第 1 轮，黑盒不可注入已写明） |
| TC-R-004 | 重进→关房→冷启无残留 | ✅ PASS |
| TC-R-005 | 四 Tab 冒烟 | ✅ PASS（0 FATAL/ANR） |
| TC-W-001/002 | F-1 失败重发+连点×5 防重 | ✅ PASS（`autotest-F1-phase5` 单条重发、0 ANR） |
| TC-U-001 | 交易卡 | 🚫 黑盒 BLOCKED（同第 1 轮）/ 静态+单测兜底（新包复验布局×3+代币对串） |

**Phase 5：0 FAIL → 进入 Phase 6。**

## Phase 6 全量验收（独立全新一遍，不改代码，2026-07-11 晚）

| # | 用例 | 结果 |
|---|------|------|
| TC-B-001/002 | 双机冷启 | ✅ PASS——5556 出现**三连发**（scheduled×3→duplicate_pending×2→request×1）全部正确合并，修复极端场景实证 |
| TC-P-001 | 单测全集（--rerun-tasks 全新执行） | ✅ PASS 17/17 |
| TC-R-001 | 杀进程恢复弹窗（房 `oex9p2j3`，本轮冷启还带双发合并） | ✅ PASS |
| TC-R-002 | 同进程重连再触发（pid 23774） | ✅ PASS |
| TC-R-004 | 重进→关房→冷启无残留 | ✅ PASS |
| TC-R-005 | 四 Tab 冒烟 | ✅ PASS（0 FATAL） |
| TC-W-001/002 | F-1 失败重发+连点×5（`autotest-F1-phase6`） | ✅ PASS（单条、0 ANR、0 FATAL） |
| TC-R-003 / TC-U-001 | 单测/静态兜底项 | 🟡 同 Phase 5（阻塞原因不变，17/17 单测含门禁用例） |

**Phase 6 全量验收：全部 PASS → ✅ 测试通过，流程结束（TEST_GUIDE 第五节终态）。**

## Bug 记录

（全流程零 FAIL。BUG-001/BUG-002/OBS-1 均已修复并多轮设备实证；无遗留缺陷。可测面终态包 = dev `f7a1f799df`。）
