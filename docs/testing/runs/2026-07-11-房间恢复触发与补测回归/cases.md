# 用例 — 房间恢复触发修复回归 + 历史缺口补测

> 被测：dev HEAD `16fa94f6`「修复重启后房间恢复检查未触发」（Application.kt 生命周期绑定重构 +
> RoomRecoveryCheckTrigger + InstanceRegistry/ActivityUtils 引用身份管理）。
> 补测缺口：F-1 `71a464b2` onWarnClick 定向用例、交易卡 UI 走查（`c7c9e137`/`2d82686e`）。
> 设备：L1 emulator-5556（Pixel_10_Pro_XL）+ emulator-5554（debox_root，本 build 实测可跑）。

## 组 B：归因链与被测包

| # | 用例 | 步骤 | 预期 |
|---|------|------|------|
| TC-B-001 | 归因链 + dex 探针 + 冷启校验 | §7.7 构建→I-89 预检→install -r→dex 探针→双机冷启 | 签名一致、vc 非降级、RoomRecoveryCheckTrigger/InstanceRegistry 在 dex、进 MainActivity 零 FATAL |
| TC-B-002 | restore_check 正常路径门禁日志 | 冷启登录后观察 FLogger ChatRoom | `scheduled reason=im_login → request → skipped reason=response_not_restorable`（无残留房时） |

## 组 R：房间恢复触发（16fa94f6 核心）

| # | 用例 | 步骤 | 预期 |
|---|------|------|------|
| TC-R-001 | 房内杀进程→冷启→恢复弹窗（回归 TC-S-003） | 建房（公开关、数字标题）→ force-stop → 冷启 | `restore_check dialog_show` + 弹窗「异常退出」可见 |
| TC-R-002 | 同进程 JIM 重连再触发（修复核心路径） | 残留房在服务端 + 同进程断网→恢复→JIM 重连 | 同 pid 下再次 `scheduled→request→dialog_show`，宿主解析为当前 MainActivity，不静默跳过 |
| TC-R-003 | 宿主不可用时受控跳过（新门禁语义） | 触发 scheduled 后使 MainActivity 不可用（后台销毁） | `skipped reason=missing_target/inactive_target`，无 crash 无静默 |
| TC-R-004 | 恢复弹窗「立即进入」重进房→正常关房 | 弹窗确认→重进→关房 | 重进成功、关房链干净、无 -2008 残留、零 FATAL |
| TC-R-005 | ActivityUtils 波及面冒烟 | 四 Tab 遍历 + 全程 logcat | 驻 MainActivity、零 FATAL/ANR |
| TC-P-001 | 新增单测独立验证 | `RoomRecoveryCheckTriggerTest` + `InstanceRegistryTest` | 全绿 |

## 组 W：F-1 onWarnClick 补测（71a464b2）

| # | 用例 | 步骤 | 预期 |
|---|------|------|------|
| TC-W-001 | 失败消息警告图标重发 | DM 会话断网发「[autotest]…」→失败出警告图标→恢复网→点警告 | 消息重发成功、主线程无 ANR |
| TC-W-002 | 连点防重（I-88 guard） | 对同一失败消息快速连点警告 ×5 | 最多一次重发，不崩 |

## 组 U：交易卡 UI 走查（c7c9e137/2d82686e）

| # | 用例 | 步骤 | 预期 |
|---|------|------|------|
| TC-U-001 | 房内交易卡布局/副标题 | 建房观察聊天区资产卡消息 | 行为文案完整、副标题=代币对（BOX · USDT 式）；不可达则 BLOCKED 如实记录 |
