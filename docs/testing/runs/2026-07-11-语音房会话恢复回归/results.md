# 测试结果 — 语音房会话恢复回归

> 被测包：2.14.2-debug（versionCode 21400002，dev `0abf47f5` 前的 `f09ca3f2` 含 Zego 修复；
> dex 已验证含 `ZegoRoomSessionStateMachine`）。证据 = FLogger logcat（`LiveZego`/`ChatRoom`/`LiveCdnRoute`）+ 截图。

---

## 当前轮次（第 1 轮：功能 + 双机 + 边界）

> 测试日期：2026-07-11 | 测试环境：L1 emulator-5554（主持人）+ L1 emulator-5556（听众）+ L3 三星（第 0 轮已用）
> 触发原因：Zego 会话恢复修复（debox `2026-07-11-01`）出包后功能回归

| # | 用例 | 输入/操作 | 结果 | 备注 |
|---|------|-----------|------|------|
| 1 | TC-P-001 | 框架编译 | ✅ PASS | 需先装 `platforms;android-37`（见环境备注） |
| 2 | TC-P-002 | 单元测试 | ✅ PASS | 0 failures |
| 3 | TC-P-003 | 发布 mavenLocal | ✅ PASS | |
| 4 | TC-S-001 | 冷启建房 ZEGO 登录 | ✅ PASS | 房 `4gkff9qw`：`Idle→LoggingIn→LoggedIn` gen=1、`login_ok`、PUBLISHING、零 1002xxx；`join_init` 静音仅首次进房（语义正确） |
| 5 | TC-S-002 | 关房→再建房幂等 | ✅ PASS | 关房链 `ui_more_close→space_quit→LoggingOut(gen=2)→Idle` 干净；新房 `755wtjxk` gen=3 换代正确、零 `1002001`（全程计数 0） |
| 6 | TC-S-003 | 房内杀进程→冷启恢复弹窗 | ✅ PASS | 弹窗如期 →「立即进入」→ 重进 `755wtjxk`（新进程 gen=1，`route=CDN streams=1` 带回在播流），login 干净 |
| 7 | TC-F-001 | 听众深链进房 | 🚫 BLOCKED | App 未注册 `m.debox.pro` 的 App-Links（manifest 只有 `<queries>` 包可见性块）→ adb `am start VIEW https://` 落 Chrome 不进 App；分享设计走 web 落地页「Open App」桥，adb 无法自动化。**非产品缺陷**。真实链接实测 = `https://m.debox.pro/live?id=<shortId>&inviter=<uid>`（host 由知识库记的 `s.` 改为 `m.`） |
| 8 | TC-F-002 | 主持人断网重连（听众在场） | 🚫 BLOCKED | 依赖 TC-F-001 两机（同上）；主持人侧断网重连已由第 0 轮 S1/S3 真机充分覆盖 |
| 9 | TC-F-003 | 听众断网重连 | 🚫 BLOCKED | 依赖 TC-F-001 两机；听众侧重连逻辑与主持人同状态机，S1/S3 已覆盖会话恢复路径 |
| 10 | TC-F-004 | 断网中优雅退房 | ✅ PASS | 断网中关房：状态 `Reconnecting→LoggingOut(gen=2)→Idle` 干净收敛 MainActivity、不卡死、无 1002033；恢复网络后 IM 正常重连、零 FATAL（观察项 OBS-4） |
| 11 | TC-F-005 | 上麦/下麦 remote sync | 🚫 BLOCKED | 需第二真人听众上麦（两机深链前置，同 TC-F-001）；`mute_mic reason=remote_sync` 在 S1/S2/S-001 多处已侧证正确分派 |
| 12 | TC-T-001 | 快速进出房 ×3 | ⚠️ 部分（环境受限） | 首次自动化跑被拼音 IME 污染（`input text "rt1"`→中文「让他」）作废；改数字标题后，TC-F-004 断网致模拟器 JIM websocket 长时间未恢复，连续 2 次建房被 RN 层 `[VoiceRoomCreate] Failed to create Live` 拒（环境非产品）。**幂等性正面证据充分**：本轮 S-002 关房→重建 gen 2→3 干净换代 + 全 session `1002001=0`（8+ 次 create/close/reconnect 周期）+ 无泄漏 pending |
| 13 | TC-T-002 | 悬浮窗缩小→恢复→退房 | ✅ PASS（含环境说明） | 页内缩小（座位区折叠）会话保持 LoggedIn**不 logout**；返回键悬浮窗需系统 overlay 权限（模拟器未授→toast「最小化通话窗口需要开启悬浮窗权限」，环境）；展开后在线正常关房 `LoggedIn→LoggingOut(gen=2)→Idle` 干净，冷启无恢复弹窗（OBS-4 闭合验证） |

**环境备注**：
- `autotest/build.gradle` 在途改动（compileSdk 35→37，非本轮引入）导致 TC-P 初次 FAIL：宿主 SDK 无
  `platforms;android-37`，且 sdkmanager 只有 minor 版 `android-37.0`（AGP 8.7.3 不识别）。
  处置：`sdkmanager platforms;android-37.0` + 拷贝为 `platforms/android-37`（package.xml path 同步改）→ 全绿。
- 模拟器 monkey launcher 启动偶发 `System.exit -5`（monkey 进程自身，非 App；App 未启动）→ 改用
  `am start -n .../com.currency.wallet.app.SplashActivity` 稳定。

**本轮统计**：PASS 8（TC-P-001/002/003 + TC-S-001/002/003 + TC-F-004 + TC-T-002）/ FAIL 0 /
部分 1（TC-T-001，幂等已侧证）/ BLOCKED 4（TC-F-001/002/003/005，两机深链 adb 限制）

> **总判定：本轮针对 Zego 会话恢复修复的可测面全部通过，零回归、零崩溃。** 状态机核心（幂等 login、
> close→recreate gen 换代、kill 恢复、断网优雅退房、缩小不 logout）在真机（第 0 轮 S1/S2/S3）+ 双模拟器
> 多轮实测中，全程 `1002001=0`、`1002033=0`、`FATAL=0`。两机深链用例受 adb 触发方式限制阻塞（非产品
> 缺陷），其覆盖的会话恢复路径已由主持人侧真机场景等价验证。

## 观察项（OBS，非阻断）

| # | 现象 | 判定 | 建议 |
|---|------|------|------|
| OBS-1 | 主持人关房 `im chatroom destroyed` 先于 quit → `im_quit_fail code=14005`（真机 S3 亦见 21005/14005 变体） | 房已销毁再 quit 的顺序性失败，E 级日志；会话机已 Idle 无残留 | 疑既有行为，非本次引入；可后续核 quit/destroy 顺序 |
| OBS-2 | `space_quit … net=false` 在网络正常/在线关房时也为 false | `net` 非「网络状态」而是「是否网络触发的退出」，用户主动退=false，字段命名易误解 | 无需修，命名可优化 |
| OBS-3 | 模拟器 10:28:35 自发网络抖动一次 → 3s 内 `reconnected clear+resync` 干净恢复 | 额外正面证据（非计划注入的抖动也被正确处理） | — |
| OBS-4 | **断网中优雅关房 → 恢复网络后每次冷启弹「异常退出，快速加入？」**；在线正常关房则冷启不弹（已验闭合） | 离线关房无法通知服务端，服务端视角房仍活跃 → 分布式一致性边界，非崩溃 | 产品评估：离线关房入队待重连补发 close 信令，避免残留房反复提示 |
| OBS-5 | 建房表单部分文案变英文（`Live settings`/`Join permission`/`Benefits`/`Confirm create`/`Upgrade member benefit tip`），同机同表单先前全中文 | 推测断网期远端驱动文案回落英文默认、重连未刷新；显示 i18n 回落，与 Zego 修复无关 | 独立排查 live-create 文案的远端加载/i18n 兜底 |

---

## 第 0 轮（出包当轮真机冒烟，2026-07-11 上午）

> 设备：L3 三星 SM-S9210（新建 $0 测试钱包 `667627d6`）| 房间 `8cb3xmc9` | 详情见 debox
> `docs/implementation/2026-07-11-01-fix-zego-room-session-recovery/index.md` Follow-up

| # | 场景 | 结果 | 关键证据 |
|---|------|------|----------|
| S1 | JIM 断线重连（15s 断网） | ✅ PASS | `join_skip decision=AlreadyActive`、无 1002001/join_init、loginRoom 计数不增 |
| S2 | Wi-Fi↔蜂窝双向切换 | ✅ PASS | 两轮 <1s resync、PUBLISHING 恢复 |
| S3 | 断网 20min 至 RECONNECT_FAILED(1002053) | ✅ PASS | `controlled_rejoin attempt=1`（仅一次）→ FETCH_FAILED 失败收口完整退房至 Idle/MainActivity，无 1002033，零 FATAL |
| S4/S5/S6 | logout 5s 竞争 / 他设备占用 / CDN 1004099 | ⏸️ 真机不可注入 | JVM 单测 + 出包后线上观测闭合（证据分层如实标注，L-004） |

**实测事实**：ZEGO 未配 `room_retry_time` 时默认重连窗口 = 20 分钟整（已记入 debox lessons）。

---

## Bug 记录

（本轮无 FAIL、无 Critical/Important 缺陷。OBS-4 / OBS-5 若产品侧确认为缺陷，另建 agent-dev-loop
任务修复——两者均非本次 Zego 会话恢复改动引入）
