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
