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
| 7 | TC-F-001 | 听众深链进房 | （执行中） | |
| 8 | TC-F-002 | 主持人断网重连（听众在场） | | |
| 9 | TC-F-003 | 听众断网重连 | | |
| 10 | TC-F-004 | 断网中优雅退房 | | |
| 11 | TC-F-005 | 上麦/下麦 remote sync | | |
| 12 | TC-T-001 | 快速进出房 ×3 | | |
| 13 | TC-T-002 | 悬浮窗缩小→恢复→退房 | | |

**环境备注**：
- `autotest/build.gradle` 在途改动（compileSdk 35→37，非本轮引入）导致 TC-P 初次 FAIL：宿主 SDK 无
  `platforms;android-37`，且 sdkmanager 只有 minor 版 `android-37.0`（AGP 8.7.3 不识别）。
  处置：`sdkmanager platforms;android-37.0` + 拷贝为 `platforms/android-37`（package.xml path 同步改）→ 全绿。
- 模拟器 monkey launcher 启动偶发 `System.exit -5`（monkey 进程自身，非 App；App 未启动）→ 改用
  `am start -n .../com.currency.wallet.app.SplashActivity` 稳定。

**观察项（非阻断，待后续核）**：
- OBS-1：主持人关房时 `im chatroom destroyed` 先于 quit → `im_quit_fail code=14005`（房已销毁再 quit 的
  顺序性失败，E 级日志；会话机已 Idle 无残留。疑似既有行为，非本次回归引入）。
- OBS-2：`space_quit … net=false` 的 `net` 字段在网络正常时也为 false，字段语义与直觉不符（待读源码确认含义）。
- OBS-3：模拟器 10:28:35 自发网络抖动一次 → 3s 内 `reconnected clear+resync` 干净恢复（额外正面证据）。

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

（暂无 FAIL；OBS-1/OBS-2 为观察项，若核实为缺陷再升级为 BUG 走 agent-dev-loop）
