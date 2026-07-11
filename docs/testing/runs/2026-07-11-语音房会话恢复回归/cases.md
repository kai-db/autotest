# 测试用例 — 语音房会话恢复回归（Zego 状态机 + 受控重进）

> 来源：debox `docs/implementation/2026-07-11-01-fix-zego-room-session-recovery/`（Accepted Plan v6）
> + 设计文档 `docs/superpowers/specs/2026-07-10-zego-room-recovery-design.md`「手工验证」节。
> 真机网络三场景（S1/S2/S3）已在出包当轮完成（见 results.md 第 0 轮），本轮补功能面 + 双机 + 边界。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 语音房（ZEGO 会话状态机 / 幂等 join / 受控重进 / logout 收敛） |
| App 包名 | `com.tm.security.wallet`（2.14.2-debug，versionCode 21400002，含 f09ca3f2 修复） |
| 测试方式 | Claude Code + adb 黑盒驱动，FLogger logcat 取证 |
| 前置条件 | L1 emulator-5554（听众/单机位）+ L3 三星 RFCYA0F9SSZ（主持人位，已建 $0 测试钱包 667627d6） |

---

## 0. 框架前置检查

| # | 检查项 | 步骤 | 验证标准 | 优先级 |
|---|--------|------|----------|--------|
| TC-P-001 | 框架编译 | `./gradlew :autotest:compileReleaseKotlin` | BUILD SUCCESSFUL | P0 |
| TC-P-002 | 单元测试 | `./gradlew :autotest:test` | 0 failures | P0 |
| TC-P-003 | 发布到 mavenLocal | `./gradlew :autotest:publishToMavenLocal` | aar 生成成功 | P0 |

---

## 1. 冒烟测试（P0，单机 L1）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-S-001 | 冷启建房 ZEGO 登录 | 冷启 → +「发起直播」→ 建房（关公开Live） | 进房 UI 正常；log `session_state Idle->…->LoggedIn`、publish PUBLISHING、无 1002xxx 错误 | P0 | L1 |
| TC-S-002 | 优雅退房→再进房幂等 | 「...」→离开语音房→确定 → 再建房 | 退房 log `logout→Idle` 干净；重进无 `1002001`、无重复 loginRoom；引擎配置标志不重复设置 | P0 | L1 |
| TC-S-003 | 杀进程→冷启恢复弹窗 | 房内 `am force-stop` → 冷启 | 弹「异常退出…快速加入？」→ 立即进入 → 会话恢复 LoggedIn，无错误码 | P0 | L1 |

## 2. 功能测试（P1，双机 = L3 主持人 + L1 听众）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-F-001 | 听众深链进房 | L3 建房 → L1 `am start` 深链 `s.debox.pro/live?id=<roomId>` | L1 进房为听众、双端人数=2、麦位互见 | P1 | L1+L3 |
| TC-F-002 | 主持人断网重连（听众在场） | L3 断网15s→恢复 | L3 `join_skip AlreadyActive` 无 1002001；L1 端主持人麦位不消失、房间不散 | P1 | L1+L3 |
| TC-F-003 | 听众断网重连 | L1 airplane on 15s→off | L1 resync 恢复、播放路由按恢复策略 stop/start、无 1002033；房间人数恢复 2 | P1 | L1+L3 |
| TC-F-004 | 断网中优雅退房 | L1 airplane on → 立即「离开语音房」 | UI 正常退出不卡死（logout 无网收敛）；恢复网络后无残留会话/无错误弹窗 | P1 | L1 |
| TC-F-005 | 上麦/下麦 remote sync | L1 申请上麦 → L3 通过 → L3 将其切回收听 | L1 变发言人（默认闭麦）→ 变回听众；`mute_mic reason=remote_sync` 正确、无误静音 | P1 | L1+L3 |

## 3. 稳定性/边界测试（P2）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 |
|---|------|------|----------|--------|------|
| TC-T-001 | 快速进出房 ×3 | 连续 建房→退房 ×3（间隔≤10s） | 每轮 login/logout 状态机干净收敛；无错房回调销毁合法 pending、无泄漏迹象 | P2 | L1 |
| TC-T-002 | 悬浮窗缩小→恢复→退房 | 房内返回键缩小 → 点悬浮窗恢复 → 优雅退房 | 缩小不退房（会话保持 LoggedIn）；恢复 UI 正常；退房干净 | P2 | L1 |

> 真机不可注入项（logout 5s 回调竞争 / 他设备占用不抢占 / CDN 1004099 降级）：JVM 单测 +
> 出包后线上观测闭合，本轮不重复列入（见 debox 任务档案 Follow-up）。
