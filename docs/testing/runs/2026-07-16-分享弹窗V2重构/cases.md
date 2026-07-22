# 测试用例 — debox feat/share-refactor（分享弹窗 V2 重构 + RN 图片分享）

> 依据：`docs/implementation/2026-07-16-01-test-debox-share-refactor/plan.md` Accepted Plan（Proposal v5）。
> 优先级：P0 = 核心流程必须通过 / P1 = 重要但非阻断 / P2 = 边界/探索场景
> 证据层（Δ7）：**单测（JVM/Robolectric）** / **L1 模拟器真包 UI** 两类；真机门禁本轮一律 NOT_RUN。
> 背景（Δ18）：分支处于 reopened 布局修复复验态——真机曾发现 FRIEND/SPACE/QUOTES_DETAILS/SPACE_FINISH 卡片完全不显示、
> GROUP/MOMENT/EVENT 卡片上半截出屏（tip `0ebe363f96` 为修复提交）→ 各类型「卡片渲染」判据 = 容器可见 **且 bounds 完整在屏**。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 分享弹窗 V2（11 类型）+ RN 图片分享桥接 |
| App 包名 | `com.tm.security.wallet` |
| 被测版本 | `origin/feat/share-refactor` tip `0ebe363f96`（debug 构造包 2.15.0/21500000，sha256 前缀 `f5e768abd404d9ad`） |
| 测试方式 | Claude Code + mobile MCP / adb（AI 驱动黑盒）+ 分支单测 |
| 设备 | L1 = AVD `Pixel_10_Pro_XL`（emulator-5554，已风控加白） |
| 前置条件 | 知识库 9 文件已完整读取（Δ13 门禁）；登录态在（消息主页可见「全部」分类条） |

## 通用执行协议（Δ8/Δ12，每条 UI 用例适用）

1. T0：记录宿主时间（秒级）+ `PID0=$(adb shell pidof com.tm.security.wallet)`。
2. 判定窗口：`logcat -t '<T0>'`；`PID0==PID1` 时按 pid 过滤，PID 变化/进程消失 → 崩溃疑点流程（全窗口无 pid 过滤 grep `FATAL EXCEPTION|Process .* died|ANR in` + `dumpsys dropbox --print` 窗口内 `data_app_crash/anr`）。
3. 剪贴板用例：点复制前 `cmd clipboard set-primary "autotest-baseline-<caseId>"`，复制后读取值 ≠ 基线且含预期链接才 PASS。
4. MediaStore 用例：前后各跑 `content query --uri content://media/external/images/media --projection _id:_display_name:date_added --sort '"date_added DESC"'`（宿主 head -5），PASS = 新 `_id` 且 `date_added ≥ T0`；命令报错 → 该证据协议 BLOCKED。
5. 截图 `<caseId>-<step>-<HHMMSS>.png` 存 `screenshots/`。
6. **每次点击前比对 `app-knowledge/dangerous-ops.md`**；V2 判据 = 同屏 `scrollPreview`+`rvChannelList`+`rvSessionList`（辅证 `tvSearchInput`/`llPreviewContainer`；兜底 `dumpsys activity top` 见 `ShareDialogFragmentV2`）；V1 判据 = `llShareImg` 或 `llQuotes`+`aaChartView` → 出现即 FAIL。
7. 监工（Δ1）：≤5 分钟自查后台任务/设备在线/单条预算（UI ≤10min、单测模块 ≤15min、构建 ≤10min）；超预算留证 → 重试 1 次 → 记 FAIL/BLOCKED 继续。

---

## 0. 框架与分支前置检查（P0）

| # | 检查项 | 步骤 | 验证标准 | 优先级 | 证据层 |
|---|--------|------|----------|--------|--------|
| TC-P-001 | autotest 框架编译 | `./gradlew :autotest:compileReleaseKotlin` | BUILD SUCCESSFUL | P0 | 单测 |
| TC-P-002 | autotest 单元测试 | `./gradlew :autotest:test` | 0 failures | P0 | 单测 |
| TC-P-101 | 分支单测 :im:imKit share 包 | worktree `--offline` `:im:imKit:testDebugUnitTest --tests "io.rong.debox.share.*"` | 全部通过 | P0 | 单测 |
| TC-P-102 | 分支单测 :ReactNative bridge | `--tests "com.debox.module.reactnative.*"`（ShareBridgeHandlerTest/RnLogSanitizerTest） | 全部通过 | P0 | 单测 |
| TC-P-103 | 分支单测 :business:BaseModule | `--tests "com.app.business.base.module.livebus.*"`（ImageShareDialogEventTest） | 全部通过 | P0 | 单测 |
| TC-P-104 | 分支单测 :business:moduleMain | `--tests "com.currency.wallet.main.*"`（Coordinator/WebShareRequestHolder） | 全部通过 | P0 | 单测 |

### RN 图片分享安全合同单测对账（Δ4/Δ9，逐项对 XML 报告）

| # | 合同项 | 对应测试 | 验证标准 | 优先级 |
|---|--------|----------|----------|--------|
| TC-P-201 | scheme 白名单拒绝（http/data/malformed） | `ShareBridgeHandlerTest.parser_rejects_invalid_contract` | 存在且通过 | P0 |
| TC-P-202 | 错误码 INVALID_SHARE_TYPE/INVALID_IMAGE_URL/UNSUPPORTED_IMAGE_SCHEME | 同上 | 存在且通过 | P0 |
| TC-P-203 | 超时/迟到结果状态机（单次完成） | `ImageShareDialogEventTest` ×2 | 存在且通过 | P0 |
| TC-P-204 | 日志脱敏白名单摘要（无 params/路径/带查询 URL） | `RnLogSanitizerTest.bridge_call_summary_contains_method_only` 等 | 存在且通过 | P0 |
| TC-P-205 | SSRF：私网 IPv4 字面量/localhost/IPv6 回环/DNS 解析后内网/多地址任一内网/解析失败拒绝 | `SharePreviewSecurityTest` 9 项 | 存在且通过 | P0 |
| TC-P-206 | 图片管线：非图/截断拒绝、下载失败、准备超时、HTTPS 临时文件清理 | `ShareImagePreparerTest` 7 项 | 存在且通过 | P0 |
| TC-P-207 | OpenGraph：unsafe page/og:image 降级、超时单次完成 | `ShareOpenGraphFetcherTest` | 存在且通过 | P0 |
| TC-P-208 | **缺口 A2 存在性核验**：Glide 下载重定向后无二次 SSRF 校验（新增暴露面） | 源码核验 `ShareImagePreparer.kt:245` + 无对应单测 | 缺口如实登记 results.md（Accepted Risk 提名，不阻断） | P0 |
| TC-P-209 | **缺口 B 存在性核验**：下载无响应体大小上限 | 源码核验 + 无 size-cap 单测 | 同上（提名，不阻断；禁超大响应探针） | P0 |

---

## 1. 冒烟测试（P0）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 证据层 |
|---|------|------|----------|--------|--------|
| TC-S-001 | 分支包安装预检+安装 | apksigner 证书比对（新包 vs 已装）→ versionCode 21500000≥21400099 → `install -r` | 签名一致、无降级、安装成功 | P0 | L1 真包 |
| TC-S-002 | 冷启归因验证 | force-stop → 冷启 → 核 `dumpsys package` versionName=2.15.0/21500000 + 登录态在（消息页「全部」可见）+ 窗口内无 FATAL | 三项全过=被测包就绪 | P0 | L1 真包 UI |
| TC-S-003 | 首个 V2 弹窗冒烟 | 「我的」→（比对危险清单后）进入个人主页 → 分享按钮 | V2 判据命中、无 V1 id、无 FATAL | P0 | L1 真包 UI |

---

## 2. 功能测试（P1）— 11 类型逐一验收（Δ3/Δ18）

> 每条通用验证点：①弹窗为 V2；②卡片渲染完整在屏（top≥0/bottom≤屏高/高>0，对照 Δ18 已知症状）；③渠道行 `rvChannelList` 与会话横列 `rvSessionList` 存在；④复制链接（如该类型有此渠道）剪贴板协议 PASS；⑤关闭弹窗回原页；⑥窗口内无 FATAL。
> 入口不确定类型含「入口探索」步骤：探索范围 = 已知原生入口 + Δ5 allowlist 深链；失败记 BLOCKED 附尝试清单+截图+复测条件。

| # | 类型 | 入口（已核验/待探索） | 类型特有验证点 | 优先级 | 证据层 |
|---|------|----------------------|----------------|--------|--------|
| TC-F-001 | GROUP | 消息页群会话 → 群设置 → 分享群组（`GroupShareActivity`） | 群卡片+二维码渲染；Δ18 症状「上半截出屏」复验 | P1 | L1 真包 UI |
| TC-F-002 | FRIEND | 「我的」个人主页（`UserCenterActivity`）分享 | 名片卡渲染；Δ18 症状「完全不显示」复验 | P1 | L1 真包 UI |
| TC-F-003 | MOMENT | 发现页动态卡片 → 详情 → 转发/分享 | 动态卡渲染；Δ18 症状「上半截出屏」复验 | P1 | L1 真包 UI |
| TC-F-004 | EVENT | 发现页「活动」二级 Tab → 活动详情 → 分享（**入口探索**） | 活动卡渲染；Δ18 症状「上半截出屏」复验 | P1 | L1 真包 UI |
| TC-F-005 | SPACE | 建语音房（消息「+」→发起直播，关公开 Live）→「...」→ 分享 | 房间卡渲染；Δ18 症状「完全不显示」复验 | P1 | L1 真包 UI |
| TC-F-006 | SPACE_FINISH | 语音房「...」→ 离开语音房（优雅退出）→ 结束统计页（`SpaceFinishActivity`）→ 分享 | 统计卡渲染；Δ18 症状「完全不显示」复验 | P1 | L1 真包 UI |
| TC-F-007 | QUOTES | 发现页「行情」Tab → token 列表项分享入口（**入口探索**） | 行情卡渲染 | P1 | L1 真包 UI |
| TC-F-008 | QUOTES_DETAILS | 行情 Tab → token 详情（或 allowlist 深链 `debox://rn/trade/token?...`，tokenId 从行情页实际 token 取）→ 分享 | K 线卡+涨跌配色渲染；Δ18 症状「完全不显示」复验；截图发图片消息入口存在性 | P1 | L1 真包 UI |
| TC-F-009 | DAPP | 发现页「DApp」Tab → dApp 详情（`FeatureDetailsActivity`）→ 分享 | dApp 卡渲染 | P1 | L1 真包 UI |
| TC-F-010 | WEB | 管理员页 `btnRN`「打开RN页面」（=`/profile/shares` RN 名片分享）→ 页内分享触发 | RN 回调链路（`WebShareRequestHolder`）：弹窗打开+关闭后 RN 页不卡死 | P1 | L1 真包 UI |
| TC-F-011 | SWAP | 「我的」实验室「闪兑」为资金类入口**默认不进**；探索限只读路径（**入口探索**，无只读入口则 BLOCKED） | swap 卡渲染（若可达） | P1 | L1 真包 UI |

### 端到端发送（Δ10/Δ16，危险操作三类——本用例即「明确要求」，最小次数）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 证据层 |
|---|------|------|----------|--------|--------|
| TC-F-101 | GROUP 卡片发送到测试群 | TC-F-001 弹窗 → 会话横列/搜索选「**担保师测试群**」（群名精确唯一匹配，发送前截图核对；失配→BLOCKED）→ 发送 ≤1 次 | 群会话出现分享卡片消息，点击回跳正确 | P1 | L1 真包 UI |
| TC-F-102 | FRIEND 卡片发送到测试好友 | **计划内 BLOCKED**（知识库无测试好友稳定 ID 登记，禁昵称匹配发送；解锁条件=登记稳定 DeBox ID/UID 并经用户确认） | 记录 BLOCKED 原因与解锁条件 | P1 | — |

### RN 图片分享集成层（Δ4/Δ20）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 证据层 |
|---|------|------|----------|--------|--------|
| TC-F-201 | RN 侧 UI 触发入口核验 | 已实证分支 APK RN bundle 无 `debox.system.share` 调用方（grep=0）；运行时在 `/profile/shares` 等 allowlist RN 页复核无图片分享入口 | 无入口 → **BLOCKED**（原因=RN 业务未接入；复测条件=bundle 出现调用方）；错误码合同仅由 TC-P-201~203 单测判定 | P1 | L1 真包 UI |
| TC-F-202 | 集成负向（条件跑，仅当 201 发现入口） | 断网触发 HTTPS 图片分享 → 观察三项：无 FATAL、cache 无残留、日志无完整 URL/本地路径（**不宣称验证 reject 错误码**） | 三项全过 | P1 | L1 真包 UI |

---

## 3. 稳定性/边界测试（P2）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 证据层 |
|---|------|------|----------|--------|--------|
| TC-T-001 | 旋转/重建不崩（Parcel 实地验证） | 任一 V2 弹窗打开态 → 旋转横屏→竖屏 | 弹窗保留或按设计重建、无 FATAL、无 PID 变化 | P2 | L1 真包 UI |
| TC-T-002 | 断网打开分享（OpenGraph 降级） | `svc wifi disable && svc data disable` → 打开 WEB/GROUP 分享弹窗 → **测完立即恢复网络** | 弹窗可打开、卡片降级不崩、无 FATAL | P2 | L1 真包 UI |
| TC-T-003 | 渠道快速重复点击去重 | V2 弹窗内快速双击「复制链接」渠道 ×2 轮 | 无重复动作副作用（toast/剪贴板单次语义）、无崩溃 | P2 | L1 真包 UI |
| TC-T-004 | V1 老弹窗不再出现 | 汇总 TC-F-001~011 全部弹窗的 uiautomator dump | 全程 0 次命中 V1 判据 id | P2 | L1 真包 UI |
| TC-T-005 | 多语言文案抽查 | V2 弹窗（zh 环境）渠道/按钮文案 | 无 key 裸奔（如 `share_xxx`）、无英文占位 | P2 | L1 真包 UI |

---

## 覆盖映射（Δ11：159 变更文件 → 9 变更域 → 证据/排除）

| 域 | 文件（代表） | 证据 |
|---|---|---|
| ① imKit share 核心逻辑（23 文件） | ShareTarget/LinkBuilder/ChannelMatrix/CardFactory/Gateway/Sender/OpenGraphFetcher/ImagePreparer 等 | TC-P-101 单测 + TC-F 全组 UI |
| ② 卡片布局与资源（11 layout + colors/dimen） | share_card_*.xml、dialog_fragment_share_v2.xml、share_v2_*.xml | TC-F-001~011 渲染判据（Δ18 强化） |
| ③ 11 调用方切换（14 文件） | GroupShareActivity/UserCenterActivity/SpaceFinishActivity/ChatLiveView/FeatureDetailsActivity/MainActivity 等 | TC-F-001~011 逐类型 |
| ④ 老弹窗 @Deprecated 收尾 | ShareDialogFragment.kt、GroupShareUtils、死 import 清理 | TC-T-004 + TC-P-101（编译即证） |
| ⑤ ReactNative bridge（6 文件） | ShareBridgeHandler/BridgeDispatcher/BridgeMethods/RnLogSanitizer | TC-P-102/201~204 + TC-F-201 |
| ⑥ BaseModule 事件 + 老布局微调 | ImageShareDialogEvent、dialog_fragment_share.xml(+2) | TC-P-103/203；老布局微调随 TC-T-004 覆盖 |
| ⑦ moduleMain 编排（4 文件） | ImageShareDialogCoordinator/WebShareRequestHolder/MainActivity | TC-P-104 + TC-F-010 |
| ⑧ 多语言 strings（5 文件 en/zh/ja/ko/vi + colors） | resource/values-* | TC-T-005 抽查 zh；其余语言=排除（同 key 结构，翻译质量非本轮范围） |
| ⑨ docs/测试文件（19 测试文件 + docs 增删） | *Test.kt、docs/implementation/*、docs/superpowers/plans/* | 排除：测试文件=证据本体（TC-P 运行）；docs=非运行时产物 |

## 统计

| 分类 | 用例数 | P0 | P1 | P2 |
|------|--------|----|----|----|
| 框架与分支前置（含安全合同对账） | 15 | 15 | 0 | 0 |
| 冒烟测试 | 3 | 3 | 0 | 0 |
| 功能测试（11 类型+发送+图片） | 15 | 0 | 15 | 0 |
| 稳定性/边界 | 5 | 0 | 0 | 5 |
| **合计** | **38** | **18** | **15** | **5** |

## 真机门禁状态（Δ7，固定小节）

debox `2026-07-15-01` Follow-up #1 各类型**真机**验收项（WEB RN 回调/旋转、GROUP/FRIEND/MOMENT/EVENT 卡片与二维码、SPACE 三态/入场条件/推动态、SPACE_FINISH 统计卡、QUOTES_DETAILS K 线/配色/截图发图）＝ 本轮 **NOT_RUN**（L3 两台真机均不可做业务操作：小米=正式环境只读、三星=non-debuggable release 包）。本轮全部结论为 L1 模拟器层增量证据，**不闭合真机门禁**。
