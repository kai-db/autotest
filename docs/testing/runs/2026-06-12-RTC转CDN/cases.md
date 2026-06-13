# 语音房 RTC 转 CDN — 测试用例

> 被测分支：debox-android `feat/dev-cdn-rtc`（基线 `c749ccda`..`a03b23bcbe`，含 CDN 路由
> `c1dd3a5b11`..`994fa80c89` 与 **1V1 互斥 `46607b9c41`**）
> 用例依据：`debox-android/docs/语音房RTC转CDN-Android客户端改造方案.md` §8 测试卡口 + `46607b9c41` 提交说明
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 语音房 RTC 转 CDN 播放路由 + 1V1 语音通话互斥 |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | JVM 单测（A 层）+ 单机真机（B 层）+ 后端联调（C 层）+ **双机真机（D 层）** |
| 设备 | 三星 SM-S9210（`RFCYA0F9SSZ`）+ 小米 25067PYE3C（`402714f0`），均装分支 2.12.3 构建（小米 6-11 20:46 / 三星 6-12 13:59，均含全部 11 个分支提交） |

---

## A 层：JVM 单测（全自动，无需设备）

> 落点：`debox-android/im/imKit/src/test/.../ZegoManagerCdnRouteTest.kt` + `business/BaseBusiness/src/test/.../LiveStreamPlayableTest.kt`
> 对应 §8 卡口：乱序守卫、幂等键、敏感凭证、跨会话残留

| # | 用例 | 验证标准 | 卡口 | 优先级 |
|---|------|----------|------|--------|
| UT-01 | mergeLiveStreams 同 session 旧/等 sequence 拒绝 | 缓存不被旧 seq 覆盖 | 乱序 | P0 |
| UT-02 | mergeLiveStreams 旧 session 回流拒绝 | teardown 后迟到的旧 playing 不复活流 | 乱序 | P0 |
| UT-03 | mergeLiveStreams 新 session 接受并更新守卫 | 缓存切到新 session | 乱序 | P0 |
| UT-04 | 事件整表缺失 media 删除 / 接口回包缺失不删 | fromEvent=true 删，false 不删 | 乱序规则 4 | P0 |
| UT-05 | audio/video 双键独立守卫 | 一个 media 的 seq 不影响另一个 | 乱序规则 2 | P0 |
| UT-06 | cdnPlayStreamId：stream_id→session_id→兜底；非法字符替换；256 截断 | 三级回退 + sanitize 正确 | §4.4 | P1 |
| UT-07 | sanitizeStreamUrl 对含 URL 的 info 脱敏 | rtmp/http URL 替换为 [stream_url]，无 URL 原样 | 敏感凭证 §4.7 | P0 |
| UT-08 | shouldReenterCdnVideo 条件矩阵 | 仅 serverRoute=CDN 且非推流且 video 可播且未在 CDN video 时为 true | 降级恢复升回 | P1 |
| UT-09 | playable 扩展函数 | 仅 playing 且 URL 非空命中；按 media_type 过滤 | §3 | P1 |
| UT-10 | desiredRouteFromServer：L3→L3、RTC/空串/未知→RTC | 兜底映射正确 | §1.2 | P1 |
| UT-11 | startMixedCdn 四元组幂等键 | 全同跳过；URL/seq/session 任一变化先停旧再起新（单活不变量） | §4.4 停流清单 | P0 |
| UT-12 | applyInitialPlayRoute 重置缓存并注入路由 | 旧守卫/缓存清空，effective 按新 route | §5.1 | P1 |
| UT-13 | clearCdnRouteState 全清 | 退房后无策略残留带入下个房间 | 退房卡口 | P0 |
| UT-14 | snapshotPlayRouteState 快照 | rejoin 前快照 route+streams 完整 | 1002001 重进 | P2 |

## B 层：单机真机（AI 自主执行，mobile-mcp + adb logcat）

> CDN 路由场景依赖服务端给该账号下发 route=CDN；若测试环境未开 CDN 灰度，
> 标记 SKIP 并归入 C 层联调项。route=RTC 基线回归不受影响。

| # | 用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------|----------|--------|
| TC-S-001 | App 启动冒烟 | 安装分支 APK → 启动 → 进首页 | 无 crash，登录态正常 | P0 |
| TC-S-002 | 语音房基线回归（route=RTC 零变化） | 进入任一语音房（观众） | 房间可进、麦上声音正常、UI 正常 | P0 |
| TC-F-001 | join 路由注入时序 | 进房同时抓 logcat | `applyInitialPlayRoute -> route=.. effective=..` 早于首个 ADD 拉流日志 | P0 |
| TC-F-002 | 退房清理 | 进房 → 退房 ×3 | 退房后无声音残留；日志见 stopMixedCdn/clearCdnRouteState 收口；无叠加 | P0 |
| TC-F-003 | stream_url 不进日志 | 房内全程抓 logcat | 全量日志无 `rtmp://`/混流 URL（含 onApiCalledResult 通道） | P0 |
| TC-F-004 | CDN 路由下声浪关闭 | route=CDN 时观察麦位声浪 | 无"恒定超级主持人头像跳动"；RTC 下声浪正常 | P1 |
| TC-F-005 | 冷启动恢复 | 房内杀进程 → 重启 App | check_current_room 恢复进房，路由经 applyInitialPlayRoute 注入 | P1 |
| TC-F-006 | seat_limit=0 投屏入口 | 房间有投屏时缩小为悬浮窗 | 「查看投屏」入口可见（seat_limit=0 不再误判满座） | P1 |
| TC-T-001 | 前后台/锁屏 | 房内切后台、锁屏 30s 回前台 | 播放恢复正常，route 重新确认（check_current_room） | P2 |
| TC-T-002 | 断网重连 | 房内飞行模式 10s → 恢复 | 重连后出声恢复；CDN 时日志见强制 stop→start | P2 |

## D 层：双机真机（两台设备均由 AI 操作，无需后端配合）

> 设备角色：**主持机** = 小米 `402714f0`（已创建语音房，麦上身份）；
> **从机** = 三星 `RFCYA0F9SSZ`（观众/对端）。两台均可抓 logcat。
> 通用 logcat 锚点（tag 过滤 `ZegoManager|ChatRoomManager|JIMChatRoom`）：
> `applyInitialPlayRoute` / `forceStartPlayingStream` / `ADD` / `DELETE` /
> `logoutRoom` / `clearCdnRouteState` / `resubscribe`
> 危险操作红线：全程不登出、不清数据、不切环境；不触碰转账/签名入口。

### D1 组：双机语音房基础（RTC 基线，原 C 层中"只差第二台设备"的部分）

| # | 用例 | 前置 | 步骤 | 验证标准 | 优先级 |
|---|------|------|------|----------|--------|
| DV-01 | 双机同房互见 | 小米已建房 | 三星进入同一语音房（观众） | 双方成员列表互见；三星 logcat 见 `applyInitialPlayRoute` 早于首个 `ADD`，随后 `forceStartPlayingStream mode=AUDIO_RTC` 拉主持人流 | P0 |
| DV-02 | 远端说话声浪联动 | DV-01 在房 | 小米麦上（主持人）保持，观察三星端主持人麦位 | 三星端主持人头像声浪动画随说话出现（环境静音时允许只验证拉流 PLAYING 状态） | P1 |
| DV-03 | 对端上麦 ADD 链路 | DV-01 在房 | 三星申请上麦（主持人小米通过，如需审批） | 小米 logcat 出现新 `ADD` + `forceStartPlayingStream`（拉三星的流）；双方麦位 UI 同步 | P0 |
| DV-04 | 对端下麦 DELETE 链路 | DV-03 三星在麦上 | 三星下麦 | 小米端对三星流 `DELETE`/停止拉流，无残留拉流日志；麦位 UI 同步移除 | P0 |
| DV-05 | 对端退房成员同步 | DV-01 在房 | 三星退出房间 | 小米端成员列表/在线人数更新；三星 logcat `logoutRoom success` + `clearCdnRouteState` 收口，无拉流残留 | P0 |
| DV-06 | 合成流豁免雏形（RTC 面） | 双机在房 | 三星反复上麦/下麦 ×3 触发 SEAT_MEMBER reconcile | 小米端 reconcile 后拉流集合与实际麦位一致，无误停/重复拉流（CDN 面留 CO-10） | P1 |
| DV-07 | 主持人关房对端联动 | 双机在房 | 小米（超级主持人）关闭房间 | 三星收到关房事件，UI 退出房间，logcat 清理收口；**测完需小米重新建房**，放在 D1 组最后执行 | P1 |

### D2 组：1V1 语音通话与语音房互斥（提交 `46607b9c41`）

> **⏭ 本组按用户指示整组跳过（2026-06-12）：只测语音房 CDN 相关逻辑，1V1 互斥不在本轮范围。**
> 用例保留备后续轮次使用。前置：两台设备账号互为好友（可发起 1V1 语音通话）。
> 文案锚点：拦截 toast =「正在语音通话中，请先挂断再加入语音房」（`live_join_space_hangup_call_first`）；
> 拨打拦截 toast =「已加入语音房」（`already_join_space`）；
> 接听确认框 =「接听将退出当前语音房，是否继续？」（`live_accept_call_quit_space_confirm`）。

| # | 用例 | 前置 | 步骤 | 验证标准 | 优先级 |
|---|------|------|------|----------|--------|
| MX-01 | 语音房中拨打 1V1 被拦 | 三星在语音房中 | 三星打开与小米账号的私聊 → 发起语音通话 | toast「已加入语音房」，不发起呼叫（小米端无来电）；三星仍正常在房 | P0 |
| MX-02 | 来电接听-取消：留在语音房 | 三星在语音房中 | 小米拨打三星 → 三星出现来电界面 → 点「接听」→ 弹确认框 → 点「取消」 | 来电被挂断（小米端显示对方拒绝/通话结束）；三星仍在语音房，房间声音/拉流正常无中断 | P0 |
| MX-03 | 来电接听-确认：退房后接通 | 三星在语音房中 | 小米拨打三星 → 三星点「接听」→ 确认框点「确定」 | 三星先退出语音房（logcat `quit` 清理 + 音频焦点释放）后接通通话；通话双向有声；logcat 无语音房拉流残留 | P0 |
| MX-04 | 确认框旁路封死 | MX-02 过程中 | 确认框弹出时按返回键、点弹窗外蒙层 | 弹窗不消失，只能从确定/取消二选一退出 | P1 |
| MX-05 | 通话中加入语音房被拦 | 双机 1V1 通话中（小米先退出语音房再互拨，或用 MX-03 接通的通话） | 三星通话中尝试进入语音房 | toast「正在语音通话中，请先挂断再加入语音房」，不进房、房间状态不被污染；挂断后再进房成功 | P0 |
| MX-06 | join 失败复位不误拦（resetJoinSessionState） | 三星不在房、麦克风权限可弹窗 | `pm revoke` 三星 RECORD_AUDIO → 进语音房 → 权限弹窗点拒绝（join 失败）→ 小米向三星拨打 1V1 | 三星来电可正常接听**不弹**退房确认框（活跃态已复位）；测完恢复权限 | P1 |
| MX-07 | 通知栏接听路径走同一闸门 | 三星在语音房中且 App 退后台 | 小米拨打 → 三星从通知栏点接听（isAccept 路径） | 进入通话页后同样弹退房确认框，行为同 MX-02/03 | P2 |
| MX-08 | 加入中窗口期拨打被拦 | 三星不在房 | 三星点进房后立即（space_info 回包前）切到私聊拨打 | 仍被拦（isLiveSessionActive 覆盖窗口期）；时序难卡准则降级为 code-review 确认 | P2 |

### D3 组：CDN 实链路双机（**2026-06-12 实测确认测试环境后端已开 CDN 下发**，原 C 层可落地部分）

> 实测回包：join 返回 `route:"CDN"` + `live_streams[{media_type:audio, stream_id:cdn_audio_mix_<roomId>,
> session_id:mix_audio_<roomId>, sequence:1, stream_url:rtmp://play-ws-test...}]`。
> 角色：三星 = 主持人（麦上，RTC 推流）；小米 = 听众（route=CDN，混流播放）。
> 前置：**两台设备必须装含 CDN 提交的构建**（dex 含 `applyInitialPlayRoute` 字符串可验真，
> 见第 2 轮 BUG 记录——6-12 下午两台均被旧包覆盖过）。
> logcat 锚点：`applyInitialPlayRoute -> route=CDN effective=` / `startMixedCdn` / `stopMixedCdn` /
> `clearCdnRouteState` / `mergeLiveStreams` / `forceStartPlayingStream mode=`

| # | 用例 | 对应原用例 | 步骤 | 验证标准 | 优先级 |
|---|------|------------|------|----------|--------|
| CD-01 | 听众进房走 CDN 混流（ADD else 卡口） | TC-F-001 + CO-02 | 小米从活动卡片进房，全程抓 logcat | ① `applyInitialPlayRoute route=CDN` 早于 loginRoom/首个 ADD；② `startMixedCdn` 起播混流；③ 房内已有推流者的 ADD **不**触发 `forceStartPlayingStream`（不拉单流） | P0 |
| CD-02 | CDN audio 可听性 | CO-01 | 主持人三星开麦说话（或播声源），观察小米端 | 小米端混流播放器 PLAYING 无 error；主持人麦位声浪/soundLevel 表现正常（AI 以日志+UI 判断） | P0 |
| CD-03 | 上麦先停混流再推流 | CO-03 | 小米申请/直接上麦，抓 logcat 顺序 | `stopMixedCdn` 在 `startPublishingStream` **之前**；上麦后小米按 RTC 拉其他麦上单流；无自声回授 | P0 |
| CD-04 | 下麦按新 route 切回 CDN | CO-04 | 小米下麦 | 触发 check_current_room；route=CDN 重新生效，混流重启（新一轮 `startMixedCdn`）；停拉单流 | P0 |
| CD-05 | 退房清理（CDN 面） | TC-F-002 | 小米退房 ×2 | 每次退房 `stopMixedCdn` + `clearCdnRouteState` 收口；重进无旧 session 残留（mergeLiveStreams 守卫日志） | P0 |
| CD-06 | SEAT_MEMBER reconcile 混流豁免 | CO-10 | 三星主持人开/关麦 ×3，小米保持听众 | 小米端混流不被误停/重启（无 stop→start 翻转，幂等键日志跳过）；麦位 UI 正常刷新 | P0 |
| CD-07 | CDN 下声浪表现 | TC-F-004 | CDN 播放中观察小米端主持人麦位 | 无「恒定头像跳动」（CDN 无 per-stream soundLevel 时声浪应关闭或按设计降级） | P1 |
| CD-08 | 凭证脱敏（CDN 实链路） | TC-F-003 | CD-01~06 全程日志复查 | `ChatRoom-*` 与 Zego `onApiCalledResult` 通道 0 处 `rtmp://`（已知例外：debug http body 明文，见第 1 轮观察项 1） | P0 |
| CD-09 | 冷启动恢复 CDN 路由 | TC-F-005 | 小米房内杀进程 → 重启 App → 恢复进房 | check_current_room 回包 route=CDN 经 applyInitialPlayRoute 注入；恢复后走混流不拉单流 | P1 |
| CD-10 | 断网重连（CDN 强制重启流） | TC-T-002 | 小米房内飞行模式 10s → 恢复 | 重连后混流恢复出声；日志见强制 stop→start（reconnect 路径） | P2 |
| CD-11 | 麦上身份保持 RTC | §5.1 | 三星主持人端日志核查 | 主持人 `effective=RTC`（麦上不走 CDN），正常推流 + RTC 拉流 | P1 |

## C 层：需配合（双端 / 后端联调）

| # | 用例 | 需要的配合 | 卡口 | 优先级 |
|---|------|------------|------|--------|
| CO-01 | CDN audio 首帧与稳定性（demo 房实测，§7-⑤） | 后端提供有 audio mix 的 demo 房 + 给测试账号下发 route=CDN | 首帧/延迟阈值 | P0 |
| CO-02 | ADD else 卡口：route=CDN 下新 ADD 不拉单流 | 同上 + 第二设备上麦触发 ADD | ADD else（无编译保护） | P0 |
| CO-03 | 上麦顺序：先停混流后推流，无自声回授 | 测试账号可上麦的 CDN 房 | 上麦卡口 | P0 |
| CO-04 | 下麦后按新 route 切回（check_current_room） | 同上 | §5.3 | P0 |
| CO-05 | 双声道：CDN audio 播放中有人开投屏，投屏者 RTC 音频被抑制 | 第二设备投屏 | 双声道卡口 | P0 |
| CO-06 | 投屏 CDN video：进入观看走 video 混流，画布不入 playingCanvasMap，首帧不被重启为 L3 | 第二设备投屏 + video mix | 画布豁免 | P0 |
| CO-07 | 降级不混搭：video mix 不可用整体降 L3；恢复后升回（onCdnVideoRecovered） | 后端制造 video failed / 坏 URL | 降级规则 2 | P0 |
| CO-08 | 降级链 CDN→L3→RTC，每跳 route_change/cdn_fallback 埋点 + info 回补 | 后端制造拉流失败 | 降级链 | P1 |
| CO-09 | 回滚重订阅：服务端切 route=RTC/L3 后基于 knownRemoteStreams 重订阅，全房不静音 | 后端切线路（灰度回滚演练） | 回滚卡口 | P0 |
| CO-10 | 合成流豁免：CDN 播放期间 SEAT_MEMBER 等事件触发 reconcile 不误停混流 | CDN 房 + 第二设备上下麦 | 合成流豁免 | P0 |
| CO-11 | stream_type 双挂点：首推/投屏切换/重连重推后 extra_info 正确（联调首日验证 §7-②） | 后端核对 stream_created 回调 | §4.5 | P1 |
| CO-12 | 乱序实测：teardown 后迟到旧事件不复活流 | 后端配合重放事件 | 乱序 | P2 |
| CO-13 | L3 路由：AUDIO_L3 纯音频拉流 + soundLevel 回调（§7-⑤） | 后端下发 route=L3 | §4.3 | P1 |

---

## 统计

- A 层 14 条（全自动，第 1 轮已 19/19 PASS）
- B 层 10 条（AI 单机真机，第 1 轮 6 PASS / 3 SKIP）
- D 层 15 条（AI 双机真机：DV-01~07 + MX-01~08，**第 2 轮新增**）
- C 层 13 条（需后端配合：CDN route 下发/混流 demo 房/降级演练；其中 CO-02/03/04/05/10 的
  "第二设备"条件已具备，等后端开 CDN 后双机即可补测）
