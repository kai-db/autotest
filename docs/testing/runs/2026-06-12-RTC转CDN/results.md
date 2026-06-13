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

## Bug 记录

### BUG-001 — `:im:imKit` 单独构建在冷配置下失败（react-native-screens node 解析）

**关联用例**：A 层运行环境

**状态**：🟡 已绕过（workaround），未修复

**现象**：`./gradlew :im:imKit:testDebugUnitTest` 配置阶段失败：
`A problem occurred evaluating project ':react-native-screens' > Process 'command 'node'' finished with non-zero exit value 1`

**根因分析**：`gradle.properties` 开了 `org.gradle.configureondemand=true`。
单独构建 imKit 时 `:app` 不参与配置，react-native-screens 的
`safeAppExtGet("REACT_NATIVE_NODE_MODULES_DIR")` 取不到 app ext，回退到
`node --print "require.resolve('react-native/package.json')"`（workingDir=rootDir），
而 react-native 在 `ReactNative/node_modules` 下、根目录不可解析 → exit 1。
与本次 CDN 改动无关，是仓库既有构建配置问题。

**修复方案（建议）**：跑单模块任务时加 `-Dorg.gradle.configureondemand=false`
（本轮采用）；或在根 `build.gradle` 的 `allprojects.ext` 同步设置
`REACT_NATIVE_NODE_MODULES_DIR` 兜底。

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
