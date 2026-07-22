# 测试结果 — debox feat/share-refactor（分享弹窗 V2 重构 + RN 图片分享）

> 记录原则：只记是否通过、问题描述、根因分析、修复方案。每轮独立一节，最新在最前。
> 证据层：**单测（JVM/Robolectric）** / **L1 模拟器真包 UI** 两类。真机门禁本轮一律 NOT_RUN。

---

## 第 1 轮（2026-07-16，首次测试）

> 测试环境：L1 AVD `Pixel_10_Pro_XL`（emulator-5554，已风控加白）
> 被测包：`origin/feat/share-refactor` tip `0ebe363f96` → debug 构造包 **2.15.0 / 21500000**，APK sha256 前缀 `f5e768abd404d9ad`
> 归因验证：`dumpsys package` 版本对齐 + 冷启进 MainActivity + 登录态在 + 运行时 Fragment 探针 `ShareDialogFragmentV2`（见下）
> 触发原因：首次测试（debox `2026-07-15-01-refactor-share-dialog` Follow-up #1 各类型验收的模拟器层增量证据）

### 环境快照（Δ13 知识库门禁）

- 开测前完整读取 `app-knowledge/` 全 9 文件（README/dangerous-ops/devices/network-domain/dapp-sign-testing + screens/{Discover,Friends,LiveRoom,MessageHome,Mine,SettingAdmin}），autotest repo HEAD = `bd040cc`，读取时间 2026-07-16 ~18:50。
- 安装预检（TC-S-001）：新包 vs 已装包证书 SHA-256 **一致**（`2ee891687f73...c7f4c8`）；versionCode 21500000 ≥ 已装 21400099（**无降级**）；`install -r` 成功（保留登录/钱包数据）。

### 结果汇总表

| # | 用例 | 结果 | 证据层 | 备注 |
|---|------|------|--------|------|
| TC-P-001 | autotest 框架编译 | ✅ PASS | 单测 | `:autotest:compileReleaseKotlin` BUILD SUCCESSFUL |
| TC-P-002 | autotest 单元测试 | ✅ PASS | 单测 | `:autotest:test` 0 failures |
| TC-P-101~104 | debox 分支 4 模块 share 单测 | ✅ PASS | 单测 | offline 模式全量：**197 tests / 0 failures / 0 errors**（25 测试类，含 imKit share 17 类 + ReactNative bridge + BaseModule 事件 + moduleMain 编排） |
| TC-P-201~207 | RN 图片分享安全合同对账 | ✅ PASS | 单测 | scheme 白名单/错误码（ShareBridgeHandlerTest）、超时状态机（ImageShareDialogEventTest）、日志脱敏（RnLogSanitizerTest）、SSRF 9 项（SharePreviewSecurityTest）、图片管线 7 项（ShareImagePreparerTest）、OpenGraph 降级（ShareOpenGraphFetcherTest）——均存在且通过 |
| TC-P-208 | 缺口 A2：Glide 下载重定向 SSRF | ⚠️ 观察项 | 单测/源码 | 源码核验 `ShareImagePreparer.kt:245` = `Glide.downloadOnly()`，SSRF 仅校验原始 URL，3xx 重定向无二次校验 → **Accepted Risk 提名**（见下方风险登记，不阻断） |
| TC-P-209 | 缺口 B：下载无响应体大小上限 | ⚠️ 观察项 | 单测/源码 | 无 maxBytes/contentLength 防线，仅超时兜底 → **Accepted Risk 提名**（不阻断） |
| TC-S-001 | 分支包安装预检+安装 | ✅ PASS | L1 真包 | 证书 SHA-256 一致 + versionCode 无降级 + `install -r` 成功（见上「环境快照」） |
| TC-S-002 | 冷启归因验证 | ✅ PASS | L1 真包 UI | 版本对齐、消息主页「全部」可见（登录态在）、T0 窗口 pid 过滤 0 FATAL |
| TC-S-003 | 首个 V2 弹窗冒烟 | ✅ PASS | L1 真包 UI | 见 TC-F-002 |
| **TC-F-001** | **GROUP** 群设置分享 | ✅ **PASS** | L1 真包 UI | 群设置右上分享图标 → V2 弹窗；群卡片+二维码+成员数完整在屏；`ShareDialogFragmentV2` 探针命中 |
| **TC-F-002** | **FRIEND** 个人主页分享 | ✅ **PASS** | L1 真包 UI | ivMore →「推荐给好友」→ V2；名片卡+二维码完整在屏（**Δ18 症状「完全不显示」未复现 = 修复复验通过**） |
| **TC-F-003** | **MOMENT** 动态分享 | ✅ **PASS** | L1 真包 UI | 个人主页动态 → 更多菜单「分享」→ V2；动态卡完整在屏（**Δ18 症状「上半截出屏」未复现**）；复制链接 toast「复制成功」+ 剪贴板气泡 `https://s.debox.pro/moment?id=...&invite_code=...` |
| **TC-F-007** | **QUOTES** 行情分享 | ✅ **PASS** | L1 真包 UI | 行情 Tab → XRP 详情页 → 分享图标 → V2（Fragment args 确认 `share_target=Quotes(url=https://deswap.pro/market/detail/?quotesId=0x1d2...-bsc...)`）；复制链接 toast + 气泡 `https://deswap.pro/market/detail/?quotesId=0...` |
| **TC-F-008** | **QUOTES_DETAILS** K 线卡片 | ✅ **PASS** | L1 真包 UI | 同页「分享图片」→ QuotesDetail 卡片（含 K 线走势/涨跌配色/市值/流动性/持币地址/二维码）完整在屏（**Δ18 症状「完全不显示」未复现**） |
| TC-F-101 | GROUP 发送到测试群 | ✅ PASS | L1 真包 UI | 会话横列「担保师测试群」（群名精确唯一匹配）→ 二次确认「确定分享到 担保师测试群 吗?」→ 确定 → 群会话出现「[群组分享]」邀请卡片消息；点卡片回跳 GroupDaoInfoActivity 正确 |
| TC-F-102 | FRIEND 发送到测试好友 | ⛔ BLOCKED | — | 计划内 BLOCKED（Δ16）：知识库无测试好友稳定 ID 登记，禁昵称匹配发送。**保存图片证据在此用例采集**：FRIEND 弹窗点保存图片 → 权限授权 → MediaStore 新增 `_id=39 date_added=1784201604`（after > before 空），保存落盘 PASS |
| TC-F-004 | EVENT 活动分享 | ⛔ BLOCKED | L1 真包 UI | **已探索**：发现页「活动」二级 Tab（精选/抽奖/空投）**均空态「什么都没有」**（证据 `screenshots/TC-F-004-event-empty-200300.png`）→ 无线上活动数据，活动详情分享入口不可达。复测条件=有活动数据时从活动详情分享 |
| **TC-F-005** | **SPACE** 语音房分享 | ✅ **PASS** | L1 真包 UI | **已建语音房**（消息「+」→发起直播→主题「autotesttest」+关公开 Live→确认创建）→ 房间「...」→「分享」→ V2；语音房卡片（房间名/创建者/加入条件/收听人数/二维码）**完整在屏**（Δ18 症状「完全不显示」未复现）；探针命中（`TC-F-005-space-201200.png`） |
| **TC-F-006** | **SPACE_FINISH** 结束统计卡 | ✅ **PASS** | L1 真包 UI | 房间「...」→关闭语音房→二次确认→结束统计页（`SpaceFinishActivity`）→ 右上分享箭头 → V2；统计卡（派对时长/参与人数/红包额/打赏额/交易额+二维码）**完整在屏**（Δ18 症状「完全不显示」未复现）（`TC-F-006-space-finish-201500.png`） |
| TC-F-009 | DAPP dApp 详情分享 | ⛔ BLOCKED | L1 真包 UI | **已探索**：发现页 DApp Tab → debox.space 详情底部弹层（社区/启动按钮）**无分享入口**（证据 `screenshots/TC-F-009-dapp-nodetail-200500.png`）；DAPP 分享疑在「启动」进 dApp WebView 内部（涉 Web3 交互，本轮不深入）。复测条件=确认 DAPP 分享入口位置 |
| TC-F-011 | SWAP 闪兑分享 | ⛔ BLOCKED | — | 主动规避：「我的」实验室「闪兑」为资金类入口（dangerous-ops 二类），无只读分享路径 → 按计划不进资金入口。复测条件=确认存在只读 swap 分享入口 |
| TC-F-010 | WEB RN 页分享回调 | ⛔ BLOCKED | L1 真包 UI | **已实际探索**：RN Shares 页（`debox://rn/profile/shares`）可达（证据 `screenshots/TC-F-010-rnpage-194800.png`）；页内「分享」按钮点击 ×2 **均未触发原生 V2 弹窗**（`dumpsys` ShareDialogFragmentV2 探针=0）。根因待定：该按钮可能走 RN 内部分享而非本次重构的 `ShareDialogEvent` 原生链路（RN bundle 已实证无 `debox.system.share` 调用方，grep=0）→ 记 BLOCKED，非确证 bug。复测条件=确认该按钮预期触发原生 V2 或 RN 侧接入 ShareDialogEvent |
| TC-F-201 | RN 图片分享 UI 入口 | ⛔ BLOCKED | L1 真包 UI | 实证分支 APK RN bundle 无 `debox.system.share` 调用方（grep=0）→ RN 业务未接入 → 图片分享 UI 层不可达；错误码 reject 合同仅由 TC-P 单测判定（已 PASS）。复测条件=bundle 出现调用方 |
| TC-F-202 | RN 图片集成负向 | ⛔ BLOCKED | — | 依赖 TC-F-201 入口，同 BLOCKED |
| **TC-T-001** | 旋转/重建不崩 | ✅ **PASS** | L1 真包 UI | QUOTES 弹窗打开态 → 横屏（弹窗自适应完整渲染）→ 竖屏（弹窗保留）；全程 `ShareDialogFragmentV2` 探针命中、PID 不变（20628）、0 FATAL |
| **TC-T-002** | 断网打开分享（降级） | ✅ **PASS** | L1 真包 UI | `svc wifi/data disable`（顶栏「连接中...」证断网）→ 群设置分享 → GROUP 卡片+二维码完整渲染、无崩溃、T0 窗口 0 FATAL；测后已恢复网络+清 http_proxy |
| **TC-T-003** | 渠道快速重复点击去重 | ✅ **PASS** | L1 真包 UI | MOMENT 弹窗对「复制链接」渠道快速双击 ×2 轮（共 4 击）→ 首击复制后弹窗关闭、仅单个「复制成功」toast、无重复发送/保存副作用、无崩溃、PID 不变（20628）、T0 窗口 0 FATAL（`TC-T-003-dedup-200721.png`） |
| **TC-T-004** | V1 老弹窗不再出现 | ✅ **PASS（限已执行 7 类范围）** | L1 真包 UI | 已执行的 7 类型 UI 用例（TC-F-001 GROUP/002 FRIEND/003 MOMENT/005 SPACE/006 SPACE_FINISH/007 QUOTES/008 QUOTES_DETAILS + TC-T-001/002/003）弹窗探针**全部命中 `ShareDialogFragmentV2`，0 次命中 V1（ShareDialogFragment / llShareImg / llQuotes+aaChartView）**。**未覆盖 EVENT/DAPP/SWAP/WEB 4 类**（BLOCKED，V1/V2 路径未闭合，不宣称 11 类老弹窗已全退役） |
| TC-T-005 | 多语言文案抽查 | ✅ PASS | L1 真包 UI | zh 环境弹窗渠道文案「保存图片/复制链接/更多/搜索/取消」「分享图片」均正常中文，无 key 裸奔、无英文占位 |

**本轮统计（按 38 用例展开口径，TC-P-101~104=4 条、TC-P-201~209=9 条）**：
**PASS 29 / 观察项 2（TC-P-208/209）/ BLOCKED 7（TC-F-004/009/010/011/102/201/202）/ NOT_RUN 0**。合计 38。
**0 FAIL、0 崩溃、0 ANR。**

### 关键结论

1. **分享弹窗 V2 核心路径在 L1 模拟器验证通过（7/11 类型）**：GROUP/FRIEND/MOMENT/SPACE/SPACE_FINISH/QUOTES/QUOTES_DETAILS 弹窗均为 V2、卡片渲染完整在屏。各能力分别在已注明类型验证通过——**复制链接**：MOMENT、QUOTES；**保存图片**：FRIEND（MediaStore 落盘）；**发送到会话**：GROUP（担保师测试群，含二次确认+回跳）；**二维码渲染**：GROUP/FRIEND/QUOTES_DETAILS/SPACE/SPACE_FINISH；**关闭回原页**：全 7 类；**去重（快速双击）**：MOMENT。未逐类型做全能力笛卡尔覆盖。
2. **Δ18 布局修复复验通过（6 类）**：debox 阶段 6 真机曾报 FRIEND/SPACE/QUOTES_DETAILS/SPACE_FINISH 卡片「完全不显示」、GROUP/MOMENT「上半截出屏」——本轮 L1 上这 6 类卡片**均完整在屏、症状全部未复现**，为 tip `0ebe363f96` 修复提供模拟器层正向证据。
3. **稳定性通过**：旋转重建（Parcel 往返实地，QUOTES 弹窗）、断网降级（OpenGraph 抓取失败，GROUP 弹窗）、渠道快速双击去重（MOMENT）均不崩、进程不重启。
4. **单测 197/197 全绿**，含 RN 图片分享安全合同（SSRF/scheme 白名单/错误码/日志脱敏/超时状态机）。
5. **覆盖边界（如实）**：11 类型中 4 类（EVENT/DAPP/SWAP/WEB）本轮未取得 V2 弹窗 UI 证据——EVENT/DAPP 已探索但入口不可达（无数据/无入口，有截图）、SWAP 主动规避资金入口、WEB 分享按钮未触发原生弹窗；这 4 类 V1/V2 路径**未闭合**，不宣称老弹窗已全退役。

### 未闭合项（Follow-up）

- **BLOCKED（均已实际探索/主动规避，附证据）**：
  - TC-F-004 EVENT——活动 Tab 空态无数据（owner=数据/后端，有活动时复测）。
  - TC-F-009 DAPP——dApp 详情弹层无分享入口（owner=debox 确认入口位置）。
  - TC-F-011 SWAP——资金入口主动规避（owner=确认只读 swap 分享入口）。
  - TC-F-010 WEB——RN 分享按钮未触发原生弹窗（owner=debox 确认预期行为）。
  - TC-F-102 FRIEND 发送——无测试好友稳定 ID（owner=用户登记后解锁）。
  - TC-F-201/202 RN 图片——待 RN 业务接入 `debox.system.share`（owner=debox）。
- 对齐 L-003（要么执行要么显式记延后）与 L-004（证据分层不虚假闭合）。本轮所有 BLOCKED 均有实际探索截图或主动规避理由，无 NOT_RUN 静默缺口。

### 真机门禁状态（Δ7，固定小节）

debox `2026-07-15-01` Follow-up #1 各类型**真机**验收项 = 本轮 **NOT_RUN**（L3 两台真机均不可做业务操作：小米=正式环境只读、三星=non-debuggable release 包）。本轮全部结论为 **L1 模拟器层增量证据，不闭合真机门禁**（对齐 L-004）。

---

## Bug 记录

本轮**无 FAIL、无崩溃**。以下为 Accepted Risk 提名（存量设计缺口，非本轮回归引入，不作为 FAIL）：

### RISK-A2 — Glide 图片下载重定向 SSRF（提名 debox 承接）

- **性质**：RN 图片分享 HTTPS 下载走 `ShareImagePreparer.kt:245` `Glide.downloadOnly()`，SSRF 校验（`SharePreviewSecurity`）只作用于原始 URL，3xx 重定向到私网/保留地址后无二次校验。属**新增 RN 图片下载暴露面**（与 debox 既有 Accepted Risk「SSRF:TextCrawler 抓取链路」是不同实现路径，既有四要素不覆盖）。
- **四要素（提名）**：owner=Kai；reason=新增 RN HTTPS 下载面，SSRF 仅校验原始 URL；follow-up=Glide 下载改经同源逐跳复检，建议与「抓取层替换」独立任务同批；expiry≤2026-09-30。
- **处置**：本测试任务无权替 debox 接受风险 → 上报发现项，由 debox 侧二选一（修复 / 正式登记 Accepted Risk）。

### RISK-B — RN 图片下载无响应体大小上限（提名 debox 承接）

- **性质**：`Glide.downloadOnly()` 无 contentLength/maxBytes 防线，仅超时兜底 → 无界下载存在磁盘/内存耗尽风险。单测无 size-cap 用例。
- **四要素（提名）**：owner=Kai；reason=当前仅超时兜底；follow-up=补下载大小上限+单测；expiry=2026-09-30。
- **处置**：同上，上报 debox 侧决策。执行约束：本轮不做不可控超大响应破坏性探针。

### 既有 Accepted Risk 承接核验（RISK-A1，来自 debox `2026-07-15-01`）

- **SSRF:TextCrawler 重定向/DNS rebinding 后目标不复检**：owner=Kai / reason=老实现既有缺口，等价迁移不新增暴露面 / follow-up=OkHttp 禁自动重定向+≤3 跳逐跳 isSafeUrl+自定义 Dns / expiry=**2026-09-30**（今日 2026-07-16 **未过期，承接有效**）。→ 标 KNOWN_ACCEPTED_RISK，不重复报。
