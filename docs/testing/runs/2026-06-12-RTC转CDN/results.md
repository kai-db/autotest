# 语音房 RTC 转 CDN — 测试结果

> 用例见同目录 `cases.md`；被测分支 debox-android `feat/dev-cdn-rtc`

---

## 第 1 轮（首次测试）

> 测试日期：2026-06-12 | 测试环境：三星 SM-S9210（RFCYA0F9SSZ）+ t.debox.pro 测试环境
> 被测包：分支 debug 构建（2.12.3，10:55 安装，含全部 CDN 提交）
> 触发原因：首次测试
> 环境限制：**测试环境后端未开 CDN 下发**——join / check_current_room 回包均无
> `route` / `live_streams` 字段（route 注入日志显示 `route=` 空串）。CDN 实链路
> 场景全部 SKIP 归入 C 层联调项；本轮真机覆盖「RTC 基线回归 + 路由注入时序 +
> 清理/脱敏卡口」。

### A 层 JVM 单测（19/19 PASS）

| # | 用例 | 结果 | 备注 |
|---|------|------|------|
| UT-01~05 | mergeLiveStreams 双键 sequence 守卫（旧 seq/旧 session 拒绝、事件删接口不删、双 media 独立） | ✅ PASS | `ZegoManagerCdnRouteTest`，15 条断言用例 |
| UT-06 | cdnPlayStreamId 三级回退 + sanitize + 256 截断 | ✅ PASS | |
| UT-07 | sanitizeStreamUrl 脱敏（rtmp/rtmps/http/https） | ✅ PASS | |
| UT-08 | shouldReenterCdnVideo 条件矩阵 | ✅ PASS | |
| UT-10 | desiredRouteFromServer 映射 + CDN 不可播降 L3 | ✅ PASS | |
| UT-11/11b | startMixedCdn 四元组幂等 + 单活不变量 + stopMixedCdn 幂等 | ✅ PASS | mockk 模拟 ZegoExpressEngine |
| UT-12 | applyInitialPlayRoute 重置守卫并注入 | ✅ PASS | |
| UT-13 | clearCdnRouteState 全清（防跨会话残留） | ✅ PASS | |
| UT-14 | snapshotPlayRouteState 快照 | ✅ PASS | |
| UT-09 | playable 扩展函数 | ✅ PASS | `LiveStreamPlayableTest`（BaseBusiness），4 条 |

运行命令（注意要关 configure-on-demand，见 BUG-001）：

```bash
./gradlew -Dorg.gradle.configureondemand=false :im:imKit:testDebugUnitTest \
  --tests "io.rong.debox.ZegoManagerCdnRouteTest"
./gradlew -Dorg.gradle.configureondemand=false :business:BaseBusiness:testDebugUnitTest \
  --tests "com.app.base.business.network.entity.space.LiveStreamPlayableTest"
```

### B 层 单机真机

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| TC-S-001 | App 启动冒烟 | ✅ PASS | 冷启动正常，登录态保持，无 crash |
| TC-S-002 | 语音房基线回归（route 空 → RTC 零变化） | ✅ PASS | 观众进房 676464646，远端流 312163 `PLAYING`，主持人声浪动画正常 |
| TC-F-001 | join 路由注入时序 | ✅ PASS | `applyInitialPlayRoute -> route= effective=RTC`（10:07:22.043）早于 `loginRoom`（22.110）和首个 `ADD`（22.304）；ADD 后 `forceStartPlayingStream mode=AUDIO_RTC` |
| TC-F-002 | 退房清理 | ✅ PASS | `logoutRoom success`，退房后 0 条拉流/CDN 活动日志，重复进退无叠加 |
| TC-F-003 | stream_url 不进日志 | ✅ PASS* | 全程 0 处 `rtmp://`；`onApiCalledResult` 脱敏通道实证生效（SDK 错误 info 中 URL 被替换为 `[stream_url]`）。*环境无 CDN 流，约束面未完全覆盖，联调时复测 |
| TC-F-004 | CDN 路由下声浪关闭 | ⏭ SKIP | 环境无 CDN route，归入 C 层；RTC 声浪正常（基线） |
| TC-F-005 | 冷启动恢复 | ✅ PASS | 房内杀进程 → 冷启动 `check_current_room`（回包无 route 字段，旧后端口径）→ 恢复弹窗 → 重进同样先 `applyInitialPlayRoute` 再 loginRoom/ADD |
| TC-F-006 | seat_limit=0 投屏入口 | ⏭ SKIP | 单机无投屏场景，归入 C 层（A 层无对应纯逻辑可测——判定内联在 FloatLiveWindowView） |
| TC-T-001 | 前后台/锁屏 | ⏭ SKIP | RTC 基线下价值有限，与 CDN 场景一起联调时测 |
| TC-T-002 | 断网重连 | ✅ PASS | 飞行模式 12s：`RECONNECTING(1002051)` → `RECONNECTED` → `reconcile[refreshSpaceMembers] resubscribe (AUDIO_RTC)` 重订阅恢复；后续 JIM 重进触发的 `loginRoom 1002001`（重复登录）无副作用，房间 UI/拉流正常 |

**本轮统计**：A 层 PASS 19 / B 层 PASS 6、SKIP 3（环境限制）/ FAIL 0

### C 层（待联调，见 cases.md CO-01~13）

全部依赖：① 后端给测试账号下发 `route=CDN` + audio/video mix demo 房（§7-⑤）；
② 第二设备/账号（上麦、投屏、双声道场景）。本轮未执行。

---

## 第 2 轮（双机 + CDN 实链路）

> 测试日期：2026-06-12 下午 | 设备：三星 SM-S9210（主持人，麦上）+ 小米 25067PYE3C（听众）
> 触发原因：双机条件具备 + **实测确认后端已开 CDN 下发**（join 回包
> `route:"CDN"` + live_streams audio mix，见 cases.md D3 组前言）
> 范围：按用户指示只测语音房 CDN 逻辑（D1/D3 组 + B 层 CDN 复测），D2 互斥组跳过

### 环境问题（先于用例）

**ENV-001 — 两台设备被旧构建覆盖，route=CDN 被旧代码无视**

15:44 小米以旧包进房（房 `jj7mxfwc`/676767667）：join 回包带 `route=CDN` +
live_streams，但 logcat 无 `applyInitialPlayRoute`/`startMixedCdn`，直接
`forceStartPlayingStream → 100009` 拉 RTC 单流——dex 字符串验证（
`strings classes*.dex | grep applyInitialPlayRoute` = 0 hits）证实两台装的
都是**不含 CDN 提交的构建**（三星 6-12 13:59 被覆盖，疑为固化桥轮次装回旧包；
小米 6-11 20:46 本就是旧构建）。**结论：该行为不是代码 bug，是包错了。**
处置：重新构建分支 `assembleAppDebug` 并 `adb install -r` 两台（同签名覆盖，
保登录态）。后续轮次教训：**装包后必须先做 dex 字符串验真再开测**。

### 用例结果

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| CD-01 | 听众进房走 CDN 混流（ADD else 卡口） | ✅ PASS* | `applyInitialPlayRoute -> route=CDN effective=CDN`（15:54:37.340）早于 loginRoom（37.455）；ADD → `playRemoteStreamFromAdd suppressed by CDN mix: 100009`（37.614）；`startMixedCdn seq=3`（37.633）。*但见 BUG-002 瞬时单流 |
| CD-02 | CDN audio 可听性 | ✅ PASS（复测） | 首测 FAIL（BUG-003 DNS 无记录，永驻 PLAY_REQUESTING）；后端修复 DNS 后 17:18（房 jj7mxggy）/ 17:34（房 uq367e75）两次进房 `cdn_audio_mix_* PLAYING`，出声正常 |
| CD-03 | 上麦先停混流再推流 | ✅ PASS | 17:24:22 小米上麦：`stopMixedCdn`（.322）早于 `PUBLISH_REQUESTING`（.397），先停混流后推流 ✅ |
| 投屏降级 | enterVideo 时 video mix 不可用整体降 L3（CO-07 降级分支） | ✅ PASS | 17:35:09 进入投屏观看：服务端 live_streams 仅 audio 无 video 条目 → `applyPlayRoute CDN -> L3 (cdnFallback:enterVideo:videoUnavailable)` → `stopMixedCdn` + `resubscribe(AUDIO_RTC)` → 单流视频首帧 17:35:10。**不混搭规则正确执行**。⚠️ 投屏走 CDN video 需后端下发 media_type=video 混流条目，当前后端未开 |
| CD-06 | SEAT_MEMBER/MIC_ON reconcile 混流豁免 | ✅ PASS（初验） | 主持人开关麦事件到达后 `stopMixedCdn` 0 次，混流未被误停；完整版待上下麦场景复测 |
| CD-08 | 凭证脱敏（CDN 实链路） | ✅ PASS | `ChatRoom-*`/`onApiCalledResult` 通道 0 处 `rtmp://`；仅 debug http body 6 处（第 1 轮已知观察项 1，release 不存在） |
| CD-11 | 麦上身份保持 RTC | ✅ PASS | 三星主持人 `applyInitialPlayRoute -> route=RTC effective=RTC streams=1`，正常 `PUBLISHING` |
| UT 守卫实测 | mergeLiveStreams 乱序守卫线上验证 | ✅ PASS | 等 seq 重复事件被拒：`mergeLiveStreams drop stale: media=audio session=mix_audio_jj7mxfwc seq=3` ×2 |
| 投屏恢复 | 投屏结束后听众升回 CDN（CO-07 恢复分支） | ❌ FAIL | 投屏结束仅切 VIDEO_L3→AUDIO_L3，46 分钟未升回 CDN，见 BUG-004（恢复路径缺口） |
| CD-07 | CDN 下声浪表现 | ✅ PASS（初验） | 18:54 截图：CDN 播放中主持人麦位无声浪动画、无"恒定头像跳动"；代码层 `shouldSuppressSoundLevelUi`=CDN 显式关闭（§4.6）。完整验证需主持人开麦说话场景 |
| CD-04/05/09/10 | 下麦切回/退房清理/冷启动/断网 | ⏸ 待测 | BUG-003 已修复（DNS 已通）；18:53 小米已切回测试环境重新进房（房 uq367e75，seq=5，CDN PLAYING），随时可回归 |

### 第 2 轮新增 Bug

**BUG-002 — CDN 路由下进房瞬间有一次绕过日志路径的单流拉起（自愈，低危）**

- 现象：听众进房 loginRoom 成功瞬间（15:54:37.613），SDK 收到
  `StartPlayingStream2(100009)`，单流短暂 PLAYING（37.989）后被
  `reconcile[refreshSpaceMembers] stop for CDN mix`（38.000）停掉，38.067 NO_PLAY。
  瞬时窗口约 0.4s，最终态正确（仅混流在拉）。
- 排查：ADD 路径已正确 suppress（37.614 有日志）；ZegoManager 所有拉流入口均带日志，
  该次调用无任何 app 层日志 → 入口在日志路径之外。静态排查未定位到调用方
  （`startPlayingStream(streamId, view)` canvas 变体无 CDN 抑制也无日志，嫌疑最大，
  但未实证）。
- 影响：流量/时序毛刺，defense-in-depth（reconcile）已兜住。建议开发在 canvas
  变体入口补 CDN 抑制 + 日志后复测。置信度 85（现象确证，归因待定）。
- **复现确认（18:53:41 二次进房）**：同模式再现——100009 PLAY_REQUESTING(41.518)
  → PLAYING(41.837) → `reconcile[refreshSpaceMembers] stop for CDN mix`(41.860) →
  NO_PLAY(41.908)。**CDN 进房必现**，非偶发。

**BUG-004 — 投屏结束后听众不升回 CDN，永久停留 L3 单流（Important 80-85，恢复路径缺口）**

> **状态（第 5 轮更新）：🟢 已修复（按建议方案落地，触发路径已验证）**——
> `updateScreenCaptureInfo` 投屏开→关且非自投屏时 `reapplyServerPlayRoute("screenCaptureEnd")`。
> CDN 升回分支因本轮服务端把听众路由到 L3 未现场演示，待 route=CDN 窗口补演示（详见第 5 轮）。

- 现象：17:35:09 听众因 `enterVideo:videoUnavailable` 降级 L3 后，17:38:03 投屏结束
  仅触发 `reconcile[updateScreenCaptureInfo] VIDEO_L3 -> AUDIO_L3`（资源模式切换），
  **无 `applyPlayRoute L3 -> CDN`、无 `startMixedCdn`**。直到 18:24 进程结束（46 分钟）
  听众一直走 L3 单流。音频仍正常出声（用户无感），但偏离 CDN 目标线路，规模化收益丢失。
- 代码面（`git show feat/dev-cdn-rtc:.../ZegoManager.kt`）：升回仅有 4 个触发点——
  ① `applyServerPlayRoute`（liveroom/info / check_current_room 回包）；
  ② `onLiveStreamsUpdated`（LIVE_STREAM_STATE 事件，**且 mergeLiveStreams changed 才重算**）；
  ③ `onLiveStreamsFromApi`（screen-capture 接口回包，同样 changed 前置）；
  ④ `tryEnterCdnVideo`（再次进投屏观看，按 serverRoute 准入升回——本地降级态不阻断，设计正确）。
  投屏结束事件（PUSH_SCREEN_CAPTURE/updateScreenCaptureInfo）不在其中；且"本地降级 +
  服务端流表无变化（audio mix 恒 seq=1 → changed=false 短路）"组合下**不存在自然恢复路径**，
  只能等下次前后台 check_current_room / 冷启动 / 重进房。
- 建议修复：投屏结束处理（updateScreenCaptureInfo 检测 screen_capture_on=false）补一次
  `applyPlayRoute(desiredRouteFromServer(), "screenCaptureEnd")`（幂等，RTC/L3 路由下无副作用）。
- 备注："前后台切换可升回"的实测未完成——18:24 小米被人工接管（切正式环境使用），
  设备退出测试房间。待回归窗口补测。

**BUG-003 — 测试环境 CDN 域名无 DNS 解析，拉流永挂 PLAY_REQUESTING（环境/后端，P0 阻塞）**

- 现象：听众端无声。混流播放器自 15:54:37 起停在 `PLAY_REQUESTING`，无 error 回调。
- 根因链：后端下发 `stream_url=rtmp://play-ws-test.cipxnn.com/...` →
  两台手机 `ping: unknown host`；DoH 权威查询（阿里 223.5.5.5 / Cloudflare 1.1.1.1）
  确认 `cipxnn.com` 域存在（NS=julydns.com）但 `play-ws-test.cipxnn.com` **无 A 记录**
  （NODATA）→ 手机 DNS 解析失败 → SDK 无法建连，无到 1935 端口的 TCP 连接
  （`ss -tn` 证实）。**判定：CDN 流侧/环境问题，非本地、非客户端代码问题。**
  （注意：开发机本机代理 fake-IP 会把该域名"解析"到 198.18.0.98，Mac 上验证会误判，
  必须以手机侧/DoH 为准。）
- 客户端配套观察（设计内但值得评估）：降级链只在 `NO_PLAY + errorCode != 0` 触发
  （ZegoManager.kt:611-628 注释明确：避免与 SDK 20min 自动重连打架）。DNS 这类
  **永久性失败**下用户将长时间静音且无提示、不降级。建议与方案 §7-① 对齐：
  后端保证 URL 可用性之外，客户端可考虑"首帧超时看护"（如 N 秒未 PLAYING 触发
  fallbackFromCdn）。
- 修复动作：后端/运维给 `play-ws-test.cipxnn.com` 配置 DNS A 记录（或换已解析域名），
  修复后回归 CD-02/03/04/05/07/09/10。
- **状态更新（同日 17:18）：已修复**。后续进房 audio mix 正常 PLAYING（CD-02 复测 PASS）。

---

## 第 3 轮（双机 CDN 全链路回归 — 补完第 2 轮待测项）

> 测试日期：2026-06-15 | 设备：小米 25067PYE3C（402714f0，**听众/route=CDN**）+ 三星 SM-S9210（RFCYA0F9SSZ，**主持人/RTC 推流**）
> 触发原因：用户切到 `feat/dev-cdn-rtc` 重新构建，两台 `adb install -r` 同签名覆盖重装后从头回归
> 范围：CD-01~10（D3 组 CDN 实链路），补完第 2 轮「待测/初验」项；重点拿下 CD-04/05/09/10
> 房间：krdr49do（房名 ,85666666，主持人为创建者 user_id 100009）

### 环境/验包（开测前）

- **重装+验包闭环**：`:app:assembleAppDebug` 构建产物 `apks/debox-debug.apk`（229MB），
  dex 字符串验真通过（applyInitialPlayRoute×2 / startMixedCdn×4 / clearCdnRouteState×1 /
  mergeLiveStreams×9 / applyServerPlayRoute×1）；`install -r` 两台 Success（保登录态）。
  教训落实：装包后先 dex 验真再开测。
- **PRE-001（重装前现场，corroborates BUG-004）**：重装前旧状态下，听众前台 check_current_room
  回包 `route:CDN`，但实际只拉 RTC 单流 100009、无 startMixedCdn/applyServerPlayRoute，
  停在单流不升回 CDN。重装后该现象不再（全新 join 走正常路径）。说明 BUG-004 恢复缺口在
  「服务端流表无变化（changed=false 短路）+ 本地降级」组合下确实无自然恢复路径。

### 用例结果

| # | 用例 | 结果 | 证据（房 krdr49do，听众 pid 27062→29345） |
|---|------|------|------|
| CD-01 | 听众进房走 CDN 混流（ADD else 卡口） | ✅ PASS* | 11:49:13 `applyInitialPlayRoute route=CDN effective=CDN`(13.072) 早于 loginRoom(13.218)；`playRemoteStreamFromAdd suppressed by CDN mix:100009`(13.389)；`startMixedCdn seq=3`(13.403)；`cdn_audio_mix PLAYING`(13.788)+首帧(14.017)。*BUG-002 复现 |
| CD-02 | CDN audio 可听性 | ✅ PASS | 混流首帧 14.017 + 持续 akbps 48–113；基线后纯播 cdn_audio_mix（质量回调 61 条 / 单流 0 条） |
| CD-03 | 上麦先停混流再推流 | ✅ PASS | 11:55:28 `stopMixedCdn`(28.474) 早于 `startPublishingStream`(28.516)；自己流 312163 PUBLISHING；无自声回授（0 次拉 312163）；拉主持人 100009 |
| CD-04 | 下麦按新 route 切回 CDN | ✅ PASS*（功能达成，引入 BUG-005） | 12:00:49 `stopPublishingStream`+`StopPlayingStream 100009`+`applyPlayRoute L3->CDN`(49.310)+`startMixedCdn seq=4`(49.311)+`cdn_audio_mix PLAYING`(49.385)。*下麦后遗留单流双拉，见 BUG-005 |
| CD-05 | 退房清理（CDN 面）×2 | ✅ PASS | 退房#1(12:08:29)/#2(12:15:13)均 `stopMixedCdn`+`StopPlayingStream cdn`+`logoutRoom success`，退房后 0 条 play 回调；clearCdnRouteState 源码确认在 logoutRoom 内调用（line 2046，纯内存重置无日志）；重进(12:13:32)`applyInitialPlayRoute`重置守卫+`mergeLiveStreams drop stale seq=5`去重，无旧 session 残留 |
| CD-06 | SEAT_MEMBER/MIC reconcile 混流豁免 | ✅ PASS（完整版） | 主持人开/关麦×3（6 次 MIC_ON 事件 11:51:37~52:11），听众 stopMixedCdn=0 / startMixedCdn=0 / 混流 NO_PLAY=0，混流持续 PLAYING |
| CD-07 | CDN 下声浪表现 | ✅ PASS | 听众端 0 条 soundLevel 回调（CDN 混流无 per-stream soundLevel）→ 主持人头像无声浪动画；对照：上麦转 RTC 后两麦位声浪恢复 |
| CD-09 | 冷启动恢复 CDN 路由 | ✅ PASS | 杀进程(pid 27062)→重启(29345)→恢复弹窗「立即进入」→ 12:06:46 check_current_room route=CDN → `applyInitialPlayRoute route=CDN`(46.297)早于 loginRoom → `startMixedCdn seq=5` → `reconcile stop for CDN mix:100009`(46.959) 收口 → 纯混流无双拉 |
| CD-10 | 断网重连（CDN 强制重启流） | ✅ PASS | 飞行 12:02:04→15：`PLAY_REQUESTING 1004020`→`RECONNECTING 1002051`→`RECONNECTED`(17.408)→`stopMixedCdn→startMixedCdn seq=4/seq=5`(强制stop→start)→`cdn_audio_mix PLAYING`+首帧(17.785)。混流恢复出声。双拉(BUG-005)在重连后仍保留 |
| CD-11 | 麦上身份保持 RTC | ✅ PASS | 主持人三星全程 `publish OnPublishQualityUpdate stream:100009`，正常 RTC 推流 akbps 57 |

**第 3 轮统计**：CD-01~11 全部 PASS（其中 CD-01 PASS*=BUG-002 自愈瞬时；CD-04 PASS*=功能达成但引入 BUG-005）/ FAIL 0 / 新增 BUG-005

### 第 3 轮新增/复现 Bug

**BUG-002 复现 + 根因坐实**：CD-01 进房 11:49:13，ADD 已 `suppressed`(13.389)，但仍有
`StartPlayingStream2 stream:100009`(13.388) 经 **canvas/view 变体**拉起（紧随
`SetViewMode/SetViewRotation streamid:100009`——正是第 2 轮怀疑的无 CDN 抑制的 canvas 入口），
首帧(13.551)后被 `reconcile[refreshSpaceInfo] stop for CDN mix:100009`(13.791) 停掉，
瞬时窗 ~0.4s，最终态正确。**进房必现**，建议在 canvas 变体入口补 CDN 抑制 + 日志。

---

## 第 4 轮（BUG-005 修复 + 屏幕共享 CDN 定责/复测）

> 测试日期：2026-06-15 下午 | 设备同第 3 轮（小米=听众/CDN，三星=主持人）
> 触发：用户要求 ① 修复 BUG-005 ② 测试屏幕共享是否走 CDN
> 代码改动见文末「第 4 轮代码改动」；多次 rebuild + `install -r` + dex 验真

### 一、BUG-005 修复 + 回归验证 ✅ 已修复

**修复点**：`ZegoManager.onPlayerStateUpdate`（PLAYING 分支）加单活不变量兜底——
CDN 路由（`currentAudioRoute()==CDN`）下任何非混流单流变 PLAYING 即停（异步上主线程 +
二次确认路由；`playingCanvasMap` 中的投屏视频单流豁免，避免误停投屏导致黑屏）。
**一处同时兜住 BUG-005（下麦双拉）与 BUG-002（进房 canvas 变体瞬时单流）**——
在 SDK 确认 PLAYING 的权威时点收口，不依赖上游 reconcile 的时序。

**回归（装含修复的构建后）**：
- 进房 CD-01：100009 瞬时 PLAYING 后 `stop single stream under CDN mix:100009` 触发，稳态纯 cdn_audio_mix / 0 单流 ✅
- **下麦 CD-04**：12:32:16 下麦 → 100009 被 reconcile 按 L3 重拉(16.221) → PLAYING(16.409) →
  **`stop single stream under CDN mix:100009`(16.409) 立即停(16.410)** → 稳态最近 10 条全 cdn_audio_mix、**0 双拉** ✅
→ **CD-03/04 回归 PASS，BUG-005 修复有效。**（细化记录：首版兜底误停了投屏视频单流，加 `playingCanvasMap` 豁免后修正。）

### 二、屏幕共享是否走 CDN — client/server 定责

**结论（服务端修复前）：服务端问题，客户端无责。** 证据链（房 hbzfu3rx）：

| 环节 | 责任 | 实测 | 结论 |
|---|---|---|---|
| ① 显式通知后端投屏 | client | `POST liveroom/v1/<room>/screen-capture/start` | ✅ 已做 |
| ② 推流打 video 标记（双挂点） | client | `applyStreamTypeExtraInfo[prePublish]/[publishing] -> {"stream_type":"video"}` 字面值实证 | ✅ 已做 |
| ③ 停推→重推刷新 stream_created | client | stopPublishingStream→startScreenCapture→startPublishingStream | ✅ 已做 |
| ④ 后端感知投屏 | server | 回包 `screen_capture_on:true` | ✅ 后端知道 |
| ⑤ **后端产出 CDN video 混流** | **server** | live_streams 只有 media_type:audio，**0 条 cdn_video_mix** | ❌ 后端没做 |
| ⑥ 客户端消费 video 混流 | client | `tryEnterCdnVideo→startMixedCdn(video)` 就绪；无 video 时正确降级 L3「不混搭」 | ✅ 就绪 |

客户端两条通道都正确发了 video、后端也确认知道，但后端始终不产出 video 混流 → 投屏只能降级 L3。

### 三、服务端修复后复测：屏幕共享已走 CDN ✅

后端修复后复测（房 g4taqzse，16:34:49）：
```
startMixedCdn media=video seq=3 cdn_video_mix_g4taqzse video=true   ← CDN 视频混流起播
cdn_video_mix PLAYING(49.631) → onPlayerRecvVideoFirstFrame(49.800)
fps 15-16 / delay ~2000ms 持续
```
→ **屏幕共享现已走 CDN video 混流，服务端缺口已修复。**（第 2/3 轮"后端未开 video 混流"已不再成立。）

### 四、新观察 / 验证

- **stream_type 双挂点（CO-11）验证 PASS**：音频态 `{"stream_type":"audio"}`、投屏态 `{"stream_type":"video"}`，
  prePublish + publishing 两挂点均正确（字面值实证）。停推→重推形态正确（§4.5）。
- **OBS-投屏白屏（待定性，疑非客户端）**：CDN video 混流在播（有首帧、fps 15-16），但听众看「投屏中间内容是白的」。
  管道正常出帧 → 白屏来自采集源本身：最可能 ① 主持人共享的是 DeBox 语音房自身界面（麦位下方本就是大片空白），
  无真实内容；② DeBox 钱包 App 对自身窗口 `FLAG_SECURE`，被录屏涂白防泄漏。定性需主持人共享「整个屏幕」
  并切到非 DeBox 内容（桌面/浏览器）复验。**状态：✅ 已解决（2026-06-16 用户确认修复）。**
- **BUG-004（投屏结束不升回 CDN）仍未修复**：第 3/4 轮再次复现（投屏结束/前台刷新 route=CDN 但停留 L3 单流）。状态见下。

### 第 4 轮代码改动（debox-android `feat/dev-cdn-rtc`，未提交）
1. **BUG-005 修复**（生产代码）：`ZegoManager.onPlayerStateUpdate` PLAYING 分支 CDN 单流兜底（含 `playingCanvasMap` 投屏豁免）。
2. **诊断日志**（定责用，可保留/回退）：`applyStreamTypeExtraInfo` 打印 stream_type 字面值；新增 `onRoomStreamExtraInfoUpdate` 重写打印听众收到的 extraInfo；顺手修正原 `onRoomExtraInfoUpdate` 的错标注释。

---

## 第 5 轮（BUG-004 修复 + 触发验证）

> 测试日期：2026-06-16 | 设备同前（小米=听众 / 三星=主持人）| 触发：用户要求修复 BUG-004
> 代码改动：`ChatRoomManager.updateScreenCaptureInfo` 投屏由开→关且本端非投屏者时
> 调 `ZegoManager.reapplyServerPlayRoute("screenCaptureEnd")`（新增公开方法，按当前 serverRoute
> 幂等重收敛）。诊断：reapplyServerPlayRoute 加无条件日志打印 effective/serverRoute/desired。

### 验证结果（部分 ✅ / CDN 升回分支未现场演示）

- ✅ **修复触发路径可达**：投屏结束 15:39:13 `reapplyServerPlayRoute(screenCaptureEnd)` 确实触发。
- ✅ **正确按服务端线路收敛**：日志 `effective=L3 serverRoute=L3 -> desired=L3`，收敛 L3 不乱升（幂等、无副作用）。
- ⚠️ **CDN 升回分支未能现场演示**：本轮服务端把该听众进房 0.8s 后即路由到 **L3**
  （`15:36:46 applyPlayRoute CDN -> L3 (serverRoute=L3)`，早于任何投屏），全程合法在 L3、
  走 L3 单流而非 CDN video 混流——无 CDN 目标可升回。**服务端路由在 CDN↔L3 间变动**
  （第 4 轮同房还是 CDN video 混流，本轮变 L3），非客户端问题。
- **代码逻辑**：原始 BUG-004（serverRoute=CDN + 本地 enterVideo:videoUnavailable 降级 L3）下，
  `reapplyServerPlayRoute → applyPlayRoute(desiredRouteFromServer()=CDN)` 会 L3→CDN 升回；
  触发路径已验证，CDN 分支逻辑直白。待服务端稳定下发 route=CDN 的窗口补演示。

### 构建踩坑（留痕）

- `apks/debox-debug.apk` 用相对路径 `outputFileName`，增量打包偶发产出**残缺 APK**
  （4.8MB，正常 ~220MB）→ `INSTALL_PARSE_FAILED ... AndroidManifest.xml`；
  或 `packageAppDebug` 报 `Zip file already contains entry ... cannot overwrite`。
  **处置**：`rm -f apks/debox-debug.apk` + 必要时 `:app:clean` 后重打包；装机前先核对 APK 大小。

---

## 第 6 轮（已修复构建 + 服务端 video 混流 上线后 — CD-01~11 全量回归）

> 测试日期：2026-06-16 | 设备：小米(听众/CDN) + 三星(主持人) | 房 eofyg47w(房名 5556666)
> 触发：用户要求「后面的都测试」→ 全量重跑 D3 组 CDN 用例
> 构建：含 BUG-005 修复 + BUG-004 修复 + 诊断日志；服务端已上线 audio+video CDN 混流
> 抓取：logs-round3/r10~r11-{xiaomi,samsung}.log

| # | 用例 | 结果 | 证据 |
|---|------|------|------|
| CD-01 | 听众进房走 CDN 混流(ADD else) | ✅ PASS | 冷启动恢复 18:30:25 `applyInitialPlayRoute route=CDN` 早于 loginRoom → `suppressed by CDN mix:100009` → `startMixedCdn seq=6` → 稳态纯混流(**本轮无 BUG-002 瞬时单流**) |
| CD-02 | CDN audio 可听性 | ✅ PASS | 纯 `cdn_audio_mix_*` PLAYING、0 单流 |
| CD-03 | 上麦先停混流再推流 | ✅ PASS | 18:21:30 `stopMixedCdn`(.923) 早于 `startPublishingStream`(.952)；无自声回授(0 拉 312163) |
| CD-04 | 下麦切回 CDN | ✅ PASS | 18:22:58 `applyPlayRoute L3→CDN`+`startMixedCdn seq=2`；**BUG-005 复验：100009 被 `stop single stream under CDN mix` 即停、稳态 0 双拉** |
| CD-05 | 退房清理 ×2 | ✅ PASS | 退房#1(18:31:41)/#2(18:35:27) 均 `stopMixedCdn`+`StopPlayingStream cdn`+`logoutRoom success`、退房后 0 回调；重进(18:34:36) `applyInitialPlayRoute` 重置+`mergeLiveStreams drop stale` 去重、双收口、无残留 |
| CD-06 | reconcile 混流豁免 | ✅ PASS | 主持人开关麦×6 事件，听众 stopMixedCdn=0/startMixedCdn=0/NO_PLAY=0/兜底无误触，混流不断 |
| CD-07 | CDN 下声浪 | ✅ PASS | 听众 0 条 soundLevel 回调 |
| CD-08 | 凭证脱敏 | ✅ PASS | ChatRoom/onApiCalledResult 通道 0 处 rtmp://；`[stream_url]` 脱敏占位生效；仅 debug http body 有明文(已知 release 无) |
| CD-09 | 冷启动恢复 CDN | ✅ PASS | 杀进程→立即进入→check_current_room route=CDN→applyInitialPlayRoute→startMixedCdn→纯混流 |
| CD-10 | 断网重连强制重启流 | ✅ PASS | 飞行 18:29:04→`RECONNECTING 1002051`→`RECONNECTED`(16.885)→`stopMixedCdn→startMixedCdn seq=5/6`(强制stop→start)→PLAYING+首帧；稳态纯混流无双拉 |
| CD-11 | 麦上身份保持 RTC | ✅ PASS | 主持人 publish 100009 RTC |
| 屏幕共享走 CDN | ✅ PASS（服务端已修复） | 18:25:33 `startMixedCdn media=video seq=1 cdn_video_mix_eofyg47w video=true`→PLAYING→`OnRenderRemoteVideoFirstFrame`，听众走 CDN video 混流 |
| 投屏结束升回(BUG-004) | ✅ PASS | 18:28:20 投屏结束→`stopMixedCdn video`+`startMixedCdn audio seq=5`+**`reapplyServerPlayRoute(screenCaptureEnd) serverRoute=CDN`(修复触发)**→升回 cdn_audio_mix |

**第 6 轮统计**：CD-01~11 + 屏幕共享 CDN video + BUG-004/005 修复复验 **全 PASS / FAIL 0**。
（说明：BUG-004 的 L3→CDN 升回分支因本轮 serverRoute 全程 CDN、有 video 混流故无 L3 降级触发，
修复以幂等收口形态触发；触发可达 + applyPlayRoute L3→CDN 由 CD-04 实证，组合成立。）

### 环境踩坑（留痕）

- **设备空闲自动锁屏**（安全锁，adb `wm dismiss-keyguard` 解不开）打断回归——需人工解锁。
  已把两台 `screen_off_timeout` 调到 600000ms(10min) 防测试中再锁；**测完需恢复原值**。
- **「您的IP不在服务范围」弹窗**反复出现（地域/网络检查），偶尔盖住进房 sheet 致重进失败，重试即可。

## Bug 记录

### BUG-005 — 下麦从 CDN 房切回后遗留 RTC/L3 单流双拉（Important，恢复路径缺口）

**关联用例**：CD-04（下麦切回 CDN）

**状态**：✅ 已修复（第 3 轮发现，**第 4 轮修复并回归 PASS**，置信度 90）
**修复**：`ZegoManager.onPlayerStateUpdate` PLAYING 分支加 CDN 单活兜底（详见第 4 轮一节）；
回归实测下麦后单流被即时停掉、0 双拉。

**现象**：听众上麦(CD-03)后下麦(CD-04, 12:00:49)，混流重启的同时主持人单流 100009 被重新拉起
且**再未停掉**，形成 `cdn_audio_mix` + `100009` 持续双拉（12:00:52 起每 3s 各 1 条质量回调，
3+ 分钟未自愈；冷启动/重进房才清除）。

**竞态序列**：
- 12:00:49.217 `reconcile[refreshSpaceMembers] switch AUDIO_RTC->AUDIO_L3` + `forceStartPlayingStream 100009 mode=AUDIO_L3`（此刻 route 仍 L3，按 L3 听众逻辑重拉主持人单流）
- 12:00:49.310 `applyPlayRoute L3->CDN` + 49.311 `startMixedCdn seq=4`（起混流，**未停刚被重拉的单流**）
- 49.385 `cdn_audio_mix PLAYING` / 49.406 `100009 PLAYING` → 双拉

**影响**：主持人音频被听众同时经 CDN 混流(delay~147ms) 与 L3 单流(delay~483ms) 拉取 →
~336ms 回声/双声 + 上行带宽翻倍（违背 CDN 单流规模化收益）。用户可感（回声）。

**根因**：进房路径有 `reconcile[...] stop for CDN mix:100009` 收口（CD-01/09/重进均见，正确清除瞬时单流）；
但「下麦→CDN」(`applyPlayRoute L3->CDN`/`startMixedCdn`) 路径无对应「停所有非混流单流」收口，
与 reconcile 重拉单流形成竞态后无人善后。与 BUG-004 同源：**路由切到 CDN 的各入口对"清残留单流"处理不一致**。

**建议修复**：`applyPlayRoute` 切到 CDN（`startMixedCdn`）时，统一 stop 所有非混流单流拉取
（复用 join 路径的 stop-for-CDN-mix 逻辑），保证 CDN 单活不变量在所有进入 CDN 的路径上成立。

### BUG-001 — `:im:imKit` 单独构建在冷配置下失败（react-native-screens node 解析）

**关联用例**：A 层运行环境

**状态**：🟢 已修复（2026-06-16，关 configureondemand；dry-run 验证 BUILD SUCCESSFUL）

**现象**：`./gradlew :im:imKit:testDebugUnitTest` 配置阶段失败：
`A problem occurred evaluating project ':react-native-screens' > Process 'command 'node'' finished with non-zero exit value 1`

**根因分析**：`gradle.properties` 开了 `org.gradle.configureondemand=true`。
单独构建 imKit 时 `:app` 不参与配置，react-native-screens 的
`safeAppExtGet("REACT_NATIVE_NODE_MODULES_DIR")` 取不到 app ext，回退到
`node --print "require.resolve('react-native/package.json')"`（workingDir=rootDir），
而 react-native 在 `ReactNative/node_modules` 下、根目录不可解析 → exit 1。
与本次 CDN 改动无关，是仓库既有构建配置问题。

**修复（2026-06-16 落地）**：关闭 `org.gradle.configureondemand`（`gradle.properties:23`
true→false）。验证：不加 `-Dconfigureondemand=false` 跑
`:im:imKit:testDebugUnitTest --dry-run` → BUILD SUCCESSFUL（配置阶段不再触发 node 解析，实测 2s）。

> 真因纠正：原建议的「`allprojects.ext` 兜底 `REACT_NATIVE_NODE_MODULES_DIR`」其实**早已存在**
> （根 `build.gradle:77`）却**无效**——`react-native-screens` 的 `safeAppExtGet`
> （`android/build.gradle:100`）只从带 `com.android.application` 插件的 `:app` 项目读 ext，
> configureondemand 下 `:app` 未被配置时 `find{ hasPlugin('com.android.application') }` 返回 null，
> 根/allprojects 的 ext 它根本不读 → 回退 `node require.resolve` 失败。故唯一干净的本源修复是
> 关 configureondemand，而非补 ext。代价仅配置阶段略慢（多配置 :app 等模块），实测无感。

---

## 观察项（非 Bug，留痕）

1. **HTTP debug 日志会明文打印回包 body**：`PRETTY_LOGGER-http_log_interceptor`
   打印了 `check_current_room` 完整回包（含 ZEGO token）。分支新增的
   `BaseLogInterceptor` 脱敏只覆盖 header（auth/cookie/key 等），不覆盖 body。
   后端开 CDN 后，debug 包抓 logcat 将看到 `live_streams[].stream_url` 明文。
   该通道受 `BuildConfig.DEBUG` 门控、release 不存在，方案 §4.7 是否要求覆盖
   debug HTTP body 由你拍板；如要收口可在 http log 通道加 body 字段级脱敏。
2. **`sanitizeStreamUrl` 会把 SDK 错误提示里的文档 URL 也替换**（如
   `Please refer to [stream_url] for details`）。过度匹配无害（宁脱勿漏），仅排障时
   注意该占位不一定是真实流地址。
3. 测试环境 `check_current_room` 冷启动连续调用了 2 次（50ms 内，
   Application.checkSpace 与页面入口各一次）——既有行为，与本分支无关。

---

## 待办 / 未闭环清单（截至第 6 轮，2026-06-16 收口）

> 把散落各轮的未闭环项收口到一处。BUG-003/004/005 主体已闭环，剩余为「本源未修 /
> 已修但未现场验证 / 覆盖缺口 / 需后端配合」四类。

### A. 代码侧未落地（建议项，debox-android `feat/dev-cdn-rtc`）

| 项 | 现状 | 建议 | 优先级 |
|---|---|---|---|
| **CDN 首帧超时看护** | 降级只在 `onPlayerStateUpdate` CDN 流 `NO_PLAY && errorCode!=0` 触发；DNS/建连**永久失败**会停在 `PLAY_REQUESTING`、永不进 error 态 → 现有降级覆盖不到 → 用户永久静音且无提示（BUG-003 暴露的客户端缺口） | 见下「落地方案」，设计已就绪，待 owner 拍阈值后落地 | 高（真实致哑） |
| BUG-002 本源 | canvas 变体 `startPlayingStream(streamId, view)`（`ZegoManager.kt:1074`）入口**未过 `resolvePlayMode`** 直接拉流；外部 `ZegoPlayerViewDelegate` 在 CDN 下对麦上音频流绑 view 即触发；现靠 `onPlayerStateUpdate` PLAYING 兜底（第 6 轮无毛刺） | ⚠️ **不能简单按 `IGNORE_FOR_CDN_MIX` 抑制**：CDN 下 `resolvePlayMode` 对投屏视频也返回该值（CDN 短路在投屏判断之前，`:131`），会误停「CDN audio mix + 投屏 L3 video」合法形态→黑屏（BUG-005 修复同款坑）。安全修复须先豁免当前投屏视频流（`screen_capture_on && view_on && id==casting`）再抑制，且需投屏+CDN 双机回归确认不黑屏 | 低（已兜住，暂不盲改） |
| BUG-001 本源 | ✅ **已修复（2026-06-16）**：关 `configureondemand`（`gradle.properties:23`），dry-run 验证 BUILD SUCCESSFUL，单模块测试不再需手动 flag。真因见 Bug 记录区（原 `allprojects.ext` 建议对 `safeAppExtGet` 无效） | — | — |

**首帧看护落地方案**（基于 `ZegoManager.kt` 现状；只看护「起播→首帧」一段，PLAYING 即撤表，**不与 SDK 20min 自愈打架**）：
- 字段：`cdnFirstFrameHandler = Handler(Looper.getMainLooper())`（同 `publishRetryHandler` 风格）+ `@Volatile cdnFirstFramePending` + watchedId；常量 `CDN_FIRST_FRAME_TIMEOUT_MS`（建议 8–12s，默认 10s；正常首帧 audio ~0.4s / video ~0.8s，留 ~15× 余量避免弱网误降）。
- 起表：`startMixedCdn`（`:1940`）末尾 `engine.startPlayingStream(...)` 之后 arm（记 `cdnPlayStreamId(stream)` 为 watchedId，pending=true，`postDelayed`）。
- 撤表（任一即取消）：① `onPlayerStateUpdate` CDN 流分支收到 `PLAYING` 且 == 当前 cdnId（首帧到）；② `stopMixedCdn`；③ `logoutRoom`/`clearCdnRouteState`（退房）。
- 失火：主线程二次确认 `pending && effectiveRoute==CDN && cdnPlayStreamId(cdnPlayState)==watchedId` → `fallbackFromCdn("firstFrameTimeout")`（内部走 `applyPlayRoute(L3)`，复用既有原子切换，不直接 `stopMixedCdn`）。
- 回归：需后端造**建连永久失败**场景（坏 URL / 无 A 记录域名，复刻 BUG-003），验「N 秒未首帧 → 自动降 L3 出声」。

### B. 已修但未现场验证
- **BUG-004 L3→CDN 升回分支**：修复已合、触发路径已验，但 serverRoute=CDN 持续窗口未遇，**L3→CDN 升回从未端到端演示**（第 5/6 轮只验到幂等收口形态）。下一步：抓 serverRoute=CDN 稳定窗口 + 本地 `enterVideo:videoUnavailable` 降 L3 后投屏结束，实测升回。

### C. 覆盖缺口（用例未跑 / 未留痕）
- **D1 组 DV-01~07**（双机 RTC 基线）：results 三轮无独立执行记录，链路被 D3（上麦 CD-03 / 下麦 CD-04 / 退房 CD-05）隐含覆盖。处置二选一：cases.md 标注「由 D3 等价覆盖」或补跑一轮留痕。
- **B 层** `TC-F-006`（seat_limit=0 投屏入口可见）、`TC-T-001`（前后台/锁屏 route 重确认）：三轮均未补，CDN 场景下尤其应验。
- **C 层（需后端造场景，至今 0 证据）**：`CO-09` 回滚重订阅（**P0 灰度安全卡口，上线前必演练**）、`CO-05` 双声道抑制、`CO-08` 降级链 CDN→L3→RTC 每跳埋点、`CO-12` 乱序重放、`CO-13` L3 `soundLevel` 回调。
- `CO-06` CDN video 画布豁免（画布不入 `playingCanvasMap`、首帧不被重启 L3）：第 6 轮屏幕共享走 CDN video 整体 PASS，但该具体卡口未单独验。

### D. 观察项处置
- **OBS-投屏白屏**：✅ 已解决（2026-06-16 用户确认）。
- **观察项 1**（debug HTTP body 明文 `stream_url`）：release 无此问题（`BuildConfig.DEBUG` 门控）；是否给 debug body 加字段级脱敏待产品拍板。

### 建议优先级
1. `CO-09` 回滚重订阅演练（P0 上线卡口，零证据）
2. CDN 首帧超时看护落地（唯一会致用户无声且无感的真实风险）
3. BUG-004 L3→CDN 升回分支补演示
4. 其余收尾（D1 留痕 / B 层 SKIP 回归 / BUG-002·001 本源）
