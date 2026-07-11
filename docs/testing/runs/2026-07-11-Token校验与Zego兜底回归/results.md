# 测试结果 — Token 校验误报退出修复 + Zego 兜底回归

> 被测包：2.14.2-debug（versionCode 21400002），构建 SHA `ed273d6cd0`（dev HEAD），
> APK sha256 `93b5f5c0…04da086`，签名 V2 `2ee89168…`，`install -r` 保数据安装 emulator-5554。
> dex 探针：`TokenVerifyRetryPolicy`/`fetchTimeout` 命中 85 处（改动确在包内）。
> 证据 = FLogger logcat（`[FLogger:Login]`/`LiveZego`/`ChatRoom`）+ PRETTY_LOGGER + HandlerUtils 轨迹 + crash buffer + 截图。

## 当前轮次（第 1 轮）

> 测试日期：2026-07-11 | 环境：L1 emulator-5554（登录态在）
> 触发原因：上轮已测包（`f09ca3f2`）之后 4 个新 commit 回归（Token 修复 `ed273d6c` / Z-1 `3a383cdb` / Z-2/Z-3 `559f87b8` / 字号收窄 `eaada993`）

| # | 用例 | 输入/操作 | 结果 | 备注 |
|---|------|-----------|------|------|
| 1 | TC-S-001 | §7.7 构建→预检→安装→冷启 | ✅ PASS | shim 构建成功已还原；versionCode 同号非降级；冷启进 MainActivity、零 FATAL、dex 探针命中 |
| 2 | TC-S-002 | 在线冷启基线 | ✅ PASS | `check_token ok channel=JC auto=false`；无「DID已退出登录」；主页正常 |
| 3 | TC-S-003 | 语音房建房冒烟 | ✅ PASS | 房 `mykxfsm7`：`Idle→LoggingIn→LoggedIn` gen=1、`login_ok`、PUBLISHING、零 1002001/1002033 |
| 4 | TC-F-001 | 断网冷启不误登出 | ✅ PASS（UI 面） | 断网冷启仍进 MainActivity、消息主页完好、不弹登出、不进登录页、零 FATAL；无 `login_state_change`（网络失败未持久化，P0-1 成立） |
| 5 | TC-F-002 | 断网退避重试轮次 | ❌ **FAIL → BUG-001** | 重试链第 2 次尝试后静默死亡；6 次/轮、`temp_unavailable`、episode、退避、netcb **全部未发生** |
| 6 | TC-F-003 | 网络恢复自动恢复 | ❌ **FAIL → BUG-001** | 恢复网络后 60s+：JIM 自行重连 `jim_status connected`，但**零** `check_token` 活动——netcb 从未注册，边沿唤醒不存在。用户可见面靠 JIM 自愈遮盖 |
| 7 | TC-F-004 | 关房→再建房幂等 | ✅ PASS（状态机）+ **BUG-002 偶发** | 两次关房链均干净 `LoggedIn→LoggingOut(gen=2)→Idle`、新房 login 干净、全程 1002001/1002033=0；但第 1 次关房 29ms 后进程 FATAL（见 BUG-002），第 2 次无 |
| 8 | TC-F-005 | 房内断网→恢复受控重进 | ✅ PASS | 断网 `LoggedIn→Reconnecting`；恢复 `reconnected clear+resync→LoggedIn`（gen 不变）；IM 重连后 `join_skip decision=AlreadyActive`（Z-1 幂等分派正确、无线程守卫违例）；FETCH 超时未误触发、无 ReconnectFailed 卡死；恢复后模拟器抖动 3 次均 3~9s 自愈 |
| 9 | TC-F-006 | 字号档位收窄 | ✅ PASS | seekBar max=4（5 档），最右档显示 **1.3X** 预览放大正常，无 1.4/1.5；未走保存重启链（chore 仅改 SCALE_LEVELS 数组，持久化链路上轮字号任务已覆盖） |
| 10 | TC-T-001 | 断网杀进程→断网冷启→恢复 | ➖ 并入 | 断网冷启（含 force-stop）已在 TC-F-001 执行；恢复段行为=TC-F-003 同根因 FAIL，不重复计 |
| 11 | TC-T-002 | 反复断网/恢复 ×3 | 🚫 BLOCKED（by BUG-001） | 重试链已死，轮次隔离/退避递增无从观察；待修复后回归 |

**本轮统计**：PASS 6 / FAIL 2（BUG-001 同根因）/ BLOCKED 1 / 并入 1 + 偶发崩溃 BUG-002

> **总判定**：Zego 三个防御性修复（Z-1/Z-2/Z-3）**零回归**，字号收窄生效；Token 修复
> **P0-1（不误登出/不污染持久态）达成**，但 **P0-2/P0-3（轮次重试+退避自动恢复）在最典型的
> 断网快速失败路径下完全失效（BUG-001）**；另发现关房偶发 FATAL（BUG-002，疑与长重连周期下
> FGS 生命周期竞态相关，与本轮 4 个 commit 无直接归因证据）。

---

## BUG-001（P0）：断网快速失败路径重试链死亡，自动恢复机制永不 arm

**现象**（emulator-5554，断网冷启，pid 30380）：

```
12:53:59.812 checkToken attempt0 → onFail -100 网络未连接
12:53:59.827 HandlerUtils: handler_check_token start          ← attempt1 调度（2s）
12:54:01.830 HandlerUtils: run → finishTask                   ← attempt1 触发
12:54:01.830 checkToken attempt1 → onFail -100（同步快速失败）
12:54:01.831 HandlerUtils: handler_check_token start          ← attempt2 在回调内同步 arm
12:54:01.832 HandlerUtils: destroy ---->handler_check_token   ← finishClear 把刚 arm 的 attempt2 杀掉
（此后 4 分钟+ 无任何 checkToken / FLogger:Login 输出，链死）
```

**根因**：`TokenVerifyManager` 默认 `delayScheduler` =
`HandlerUtils.get(tag).time(d).finishClear().onFinish{run()}.start()`。断网时 `DeBoxHttpRequest`
的 `-100 网络未连接` 是**同步回调**（与 checkToken 同毫秒），重试的重新 `start()` 发生在
`finishTask()` 的 `onLoadFinish()` **栈内**；`finishTask()` 随后执行 `if (finishClear) destroy()`
→ `handler.removeCallbacks(run)` 把刚 postDelayed 的下一次重试移除 + `map.remove(tag)`。
链死于 attempt 2，轮次永不耗尽 → `GET_VALIDA_FAILED` outcome 不达 →
episode 入口/`temp_unavailable` 文案/退避调度/`registerNetworkRecoveryCallback` 全部不执行。

**为何单测全绿**：31 个 JVM 测试通过 `delayScheduler` seam 注入 fake 调度器，未覆盖生产
HandlerUtils「同步回调内重 arm 会被 finishClear destroy」的语义。

**影响面**：
- ✅ P0-1（网络失败不污染持久登录态）仍成立——链早死反而没写任何状态，UI 不误登出；
- ❌ P0-2（独立轮次 6 次重试）断网路径失效（仅 2 次）；
- ❌ P0-3（退避自动恢复 + 网络 VALIDATED 边沿唤醒）断网路径**完全失效**（netcb 从未注册）；
- ⚠️ 异步失败路径（网络通但服务器超时等，OkHttp 线程回调）不受影响——重 arm 不在 finishTask 栈内。

**修复方向**（供 agent-dev-loop）：`delayScheduler` seam 不复用 HandlerUtils finishClear 语义
（如裸 `Handler(mainLooper).postDelayed` + token 管理），或 HandlerUtils.finishTask destroy 前
判断 handler 是否已被重新 start。注意 `HandlerUtils.setNull()` 会 `map.clear()` 全局清空所有
tag 实例（另一处坑，波及面待评估）。

## BUG-002（P1，偶发 1/2）：关闭语音房后 FGS 超时 FATAL，进程死亡重启

**现象**（13:09:42，房 `mykxfsm7` 关闭 29ms 后，pid 31148 → SIG 9 → 自动重启为 32739）：

```
FATAL EXCEPTION: main
android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
Context.startForegroundService() did not then call Service.startForeground():
ServiceRecord{... io.debox.call.ForegroundNotificationService}
```

**上下文**：该房关闭前经历了 96s 的长 Reconnecting 周期（13:08:06 断→13:09:42.286 恢复），
`reconnected` 后 245ms 即 `ui_more_close`。FGS 超时窗（约 10s）回推 startForegroundService
发生在重连期间（~13:09:32），服务在 `startForeground()` 前被关房清理
（`ChatRoomManager:1564 stopForegroundNotificationService`）停止 → 系统按约抛 FATAL。
第 2 次正常网络下关房（房 `oex9pyr9`）**不复现**，pid 存活。

**归因**：start 点在 `ZegoManager.kt:1791/1808/2581`，stop 点 `ChatRoomManager.kt:1564`；
`ForegroundNotificationService.java:109` 已有「压缩 startForegroundService→startForeground
窗口」的既有加固注释，说明该竞态已知但未收敛「已 start 未 foreground 即被 stop」的路径。
与本轮 4 个 commit 无直接归因证据（Z-1/Z-2/Z-3 未动 FGS），**疑既有缺陷**被长重连场景放大；
上轮同场景未观察到（上轮断网中优雅退房 TC-F-004 是断网状态下关房，FGS 路径不同）。

**用户影响**：关房瞬间 App 闪退重启（回到主页后可正常继续，登录态无损）。

---

## 第 2 轮（BUG-001 修复回归，2026-07-11 下午）

> 修复走 agent-dev-loop（debox `2026-07-11-02-fix-token-verify-false-logout` Round 2）：
> plan-review R5 PASS + impl-review R6 PASS（均零 findings）。改动仅 `TokenVerifyManager.kt`
> 生产 wiring（HandlerUtils → companion 裸 Handler `by lazy`）。修复包 sha256 `c6926d78…`，
> JVM 112 tests 0 failed（首版 eager Handler 曾炸 19 测，`by lazy` 后全绿）。

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| 1 | TC-F-002 断网退避重试轮次 | ✅ **PASS（修复后）** | 6 次/轮整齐（2s 间隔）→ `temp_unavailable code=-100 attempts=6 episode_entry=true next_delay_s=30` → toast 事件 → `token_retry_netcb registered=true`；第二次 episode `auto_retry_failed attempts=6 next_delay_s=60`（退避递增 ✓） |
| 2 | TC-F-003 网络恢复自动恢复 | ✅ **PASS（修复后）** | 60s 退避窗口内恢复网络 → `net_edge wakeup_in_ms=958` → `auto_retry reason=network_recovered` → `check_token ok channel=JC auto=true` → `auto_recovered`；timer 轮进行中恢复网络亦自动成功（第 1 次 episode） |
| 3 | TC-T-002 反复断网/恢复（简化 ×2 episode） | ✅ PASS | 两次独立 episode（冷启 ×2）链路均完整收敛、无弹窗堆积、零 FATAL、最终 MainActivity 在线 |

**BUG-001 状态：已修复并设备实证闭环**（P0-1/P0-2/P0-3 全达成）。
**BUG-002 状态：已修复并设备实证闭环**（debox `2026-07-11-05-fix-fgs-close-race`，plan R2 PASS + impl R1 PASS）。
真根因比初判更进一步：`ChatRoomManager.quit()` 先 stopService，`ZegoManager.cleanupAfterLogout()`
的 cleanupStep("foregroundService") 随后**无条件复活**已停服务——常态残留 LIVE 型 FGS+常驻通知
（实机证据：无房态下 `types=0x82` 服务在跑 + 服务端 `-2008 用户已在live房间中` 伴生，导致无法建新房），
竞态下义务未履约触发 FATAL。修复 = cleanup 增加 `restartForegroundService` 显式参数（受控重进
=true / 终局退房=false，编译期定值零 TOCTOU；Codex R1 否决过 `isServiceRunning()` gate 方案）。
修复包（sha256 `49de4398…`）回归：正常关房 + 长断网(≥60s)重连后立即关房 ×3 + 断网期间服务存续，
全部零 FATAL、进程零重启、每次关房出现 `skip foregroundService restart (terminal logout)` 日志、
无 LIVE 型残留（仅设计内 BACKGROUND 保活 `types=0x2`）。附带清理了服务端残留房 `g4tbxete`
（恢复弹窗进房→正常关闭），`-2008` 建房阻塞随之消失。

## 观察项（OBS，非阻断）

| # | 现象 | 判定 |
|---|------|------|
| OBS-1 | 第 2 次关房 `im_quit_fail code=21005` | 上轮 OBS-1 既有行为（房已销毁再 quit 的顺序性失败），非本轮引入 |
| OBS-2 | 上轮 OBS-5（建房表单文案回落英文）本轮**未复现**，表单全中文 | 支持上轮「断网期远端文案回落」推测 |
| OBS-3 | 建房表单「公开Live」默认**开启**，需手动关闭 | 测试规约风险点（误建公开房），已回写 LiveRoomScreen.md 注意事项候选 |
| OBS-4 | 恢复网络后模拟器 RTC 抖动 3 次（13:07:02/13:07:23/13:08:06）均 3~9s 自愈 | 模拟器网络噪声（devices.md 已知），非产品问题 |

## 环境备注

- 本轮为纯 App 黑盒（MCP 驱动），未跑 instrumented；TC-P 框架检查沿用上轮全绿。
- 断网注入：`svc wifi disable && svc data disable`（L1 可用，无需 root）。
- FontSizeActivity 可 `am start` 直接拉起（exported 或 debug 可达），无需走 UI 导航链。
