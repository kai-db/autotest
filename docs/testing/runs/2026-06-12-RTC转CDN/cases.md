# 语音房 RTC 转 CDN — 测试用例

> 被测分支：debox-android `feat/dev-cdn-rtc`（CDN 相关提交 `c1dd3a5b11`..`994fa80c89`）
> 用例依据：`debox-android/docs/语音房RTC转CDN-Android客户端改造方案.md` §8 测试卡口
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 语音房 RTC 转 CDN 播放路由 |
| App 包名 | `com.tm.security.wallet` |
| 测试方式 | JVM 单测（A 层）+ Claude Code + MCP 真机（B 层）+ 双端/后端联调（C 层） |
| 前置条件 | 真机 RFCYA0F9SSZ 在线；分支 debug APK 已安装（已装 2.12.3 为 6-10 构建，**不含** 6-11 的 CDN 提交，须重装） |

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

- A 层 14 条（全自动）
- B 层 10 条（AI 自主真机）
- C 层 13 条（需双端/后端配合）
