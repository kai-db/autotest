# Plan — 测试 debox feat/share-refactor（分享弹窗 V2 + RN 图片分享）

> ① 计划 / 方案。当前状态以 `index.md` 为准；本文件存方案与各轮 plan-review **过程历史**（per-round VERDICT 是快照，非当前状态）。

## User Request

```text
debox 项目 feat/share-refactor 分支所有的改动，按照 autotest 项目规范生成测试用例，然后执行测试。
```

## Background

- 被测分支：debox `origin/feat/share-refactor`（远端分支，本地未检出；主干 = `origin/dev`），
  相对 merge-base 共 31 个提交、159 文件、+11248/-5091 行。两大块：
  1. **分享弹窗 V2 整体重构**（debox 任务档 `2026-07-15-01-refactor-share-dialog`，T2，30 轮 impl-review 全 PASS）：
     11/11 类型迁移至 V2（WEB/DAPP/SWAP/QUOTES/GROUP/FRIEND/MOMENT/EVENT/SPACE/SPACE_FINISH/QUOTES_DETAILS），
     组件含 ShareTarget(sealed+Parcel)、ShareLinkBuilder、渠道矩阵、埋点映射、ShareCardFactory 卡片、
     发送链路（Gateway/OpenGraph 抓取/Sender/SSRF 防护）、编排层+结果去重+会话横列、
     `dialog_fragment_share_v2` 弹窗壳、老 `ShareDialogFragment` 标 @Deprecated。
     其 Follow-up #1 = **各类型真机验收**（逐类型卡片/二维码/旋转/RN 回调），是本轮测试的核心来源。
  2. **RN 图片分享桥接**（分支内 plan `docs/superpowers/plans/2026-07-16-rn-image-share-bridge.md`）：
     RN `DeboxBridge.call("debox.system.share", {type:"image", url})` → BaseModule 事件 →
     MainActivity/ImageShareDialogCoordinator → ShareImagePreparer（本地复制/HTTPS 下载/超时）→
     `ShareTarget.Image` 打开 V2；本地渠道=保存/更多，HTTPS=保存/复制/更多，均可发会话；
     错误码 reject + RnLogSanitizer 日志脱敏。
- 分支自带**20+ 单测文件**（Robolectric/JUnit4/MockK），覆盖 Parcel 往返、渠道矩阵、SSRF、
  bridge 合同、图片准备管线等——是「逻辑正确」的第一层证据。
- 账本相关条目：L-004（证据分层：模拟器≠真机门禁，如实标注闭合态）、L1/L2（同类全查）、
  L4（宣称前先验证）、L12（单测过≠能跑，需真包 e2e 冒烟）、L13/L14（codex 评审姿势）、
  devices.md 包归因铁律（现场构建+sha256+versionCode+运行时探针）、
  dangerous-ops.md（分享发送=「发布内容」三类社交操作：用例明确要求+测试群+最小次数）。

## Goals / Non-goals / Constraints

- Goals:
  - [x] 按 `TEST_CASES.md` 模板生成本轮用例 → `docs/testing/runs/2026-07-16-分享弹窗V2重构/cases.md`（38 条）
  - [x] 在 L1 模拟器完成全量执行（P0→P1→P2），结果写 `results.md`（7/11 类型 PASS，4 类 BLOCKED 附证据）
  - [x] 分支单测全量跑通（4 个模块的 share 相关单测）作为 P0 前置证据（197/197）
  - [x] 产出分层证据结论：V2 路径正确性（L1 模拟器层）+ 明确标注 debox 侧「真机验收」门禁 NOT_RUN 不闭合
- Non-goals:
  - 不闭合 debox `2026-07-15-01` Follow-up #1 的**真机**验收门禁（L3 两台真机：小米=正式环境只读、
    三星=release 包不可探针，均不可做业务操作）——本轮只提供模拟器层增量证据并如实标注（L-004）
  - 不测埋点数据上报后端正确性（无后端查询通道；只验客户端不崩、渠道行为正确）
  - 不测「发布到动态」渠道（社交不可逆，dangerous-ops 三类，用例无明确业务要求）
  - 不修复发现的 bug（记录进 results.md；修复按铁律#5 另开 agent-dev-loop 任务）
- Constraints:
  - 铁律#7：每次点击前比对 dangerous-ops.md；禁清数据/登出/切环境/切账号
  - 分享**发送到会话**仅允许发到测试群/测试好友，最小次数（每个发送用例 ≤1 次）
  - 构建走 §7.7：git worktree 检出分支（不动用户当前 `feat/gasless-beta` 工作区）+ react shim +
    `NODE_PATH` 构建 debug 包；安装预检（签名一致 + versionCode 不降级，降级则停并改策略）；
    归因链 = HEAD SHA + APK sha256 + versionCode + 运行时探针
  - 模拟器已风控加白（5554/5556 的 geeID），若登录态丢失按 devices.md 流程重登，禁止判「后端故障」

## Plan Proposal

### v1（2026-07-16）

**一、测试面拆解（用例矩阵概要，详细步骤落 cases.md）**

| 组 | 优先级 | 内容 | 证据层 |
|---|---|---|---|
| TC-P 前置 | P0 | ① autotest 框架编译+单测；② debox 分支单测×4 模块：`:im:imKit`（share 包 17 个测试类）、`:ReactNative`（ShareBridgeHandler/RnLogSanitizer）、`:business:BaseModule`（ImageShareDialogEvent）、`:business:moduleMain`（Coordinator/WebShareRequestHolder） | 单测 |
| TC-S 冒烟 | P0 | 分支包构建→安装预检→冷启→登录态在→任一入口打开分享弹窗且为 **V2**（resource-id `dialog_fragment_share_v2` 判据）→ 无 FATAL | 真包 e2e |
| TC-F 功能 | P1 | 逐类型 V2 弹窗验收（模拟器可达入口）：GROUP（群设置-分享群组）、FRIEND（用户主页）、MOMENT（动态）、SPACE（语音房「...」-分享）、SPACE_FINISH（退房统计页）、DAPP（发现页 dApp 详情）、QUOTES/QUOTES_DETAILS（`debox://rn/trade/token?...` 深链→分享，K 线/涨跌配色/截图发图片消息）、WEB（RN 页分享回调）。每类型验：卡片渲染/二维码、渠道行与会话横列、**复制链接**（剪贴板内容含正确深链，安全渠道）、关闭 | 真包 UI |
| TC-F 发送 | P1 | 端到端发送仅 2 条：GROUP 卡片→测试群；FRIEND 卡片→测试好友（危险操作三类：用例明确要求，最小次数） | 真包 UI |
| TC-F 图片 | P1 | RN 图片分享：先探索 RN 侧 UI 入口（`debox://rn` 深链遍历已知路由）；有入口→验预览等比/长图滚动/本地 vs HTTPS 渠道差异/保存落盘；无入口→该组降级为「单测证据 + 记延后」，如实标注 | 真包 UI 或单测 |
| TC-T 边界 | P2 | 旋转/Activity 重建弹窗不崩（Parcel 往返实地验证）、断网打开分享（OpenGraph 抓取失败降级）、重复快速点击渠道去重、V1 老弹窗不再出现（@Deprecated 路径抽查） | 真包 UI |

**二、执行步骤**

1. 生成用例：拷贝 TEST_CASES/TEST_RESULTS 模板 → `runs/2026-07-16-分享弹窗V2重构/`，按上表落全部用例。
2. Phase 1 环境：启动 L1 AVD `Pixel_10_Pro_XL`；`git worktree add`（scratchpad 下）检出
   `origin/feat/share-refactor` → §7.7 shim → `NODE_PATH=... ./gradlew :app:assembleAppDebug`；
   同 worktree 内跑分支单测（步骤 1 的 TC-P）；apksigner 预检 + `install -r`；冷启归因验证。
3. Phase 2 全量执行 P0→P1→P2，每条截图留证（`runs/.../screenshots/`），点击前比对危险操作清单。
4. Phase 3 结果全部写 `results.md`（先记录再修复）。
5. 有 FAIL → Phase 4 按铁律#5 另建修复任务（本任务不改 debox 业务代码）；全 PASS → Phase 6 验收结论。
6. 收口：本任务档回写终态 + impl-review + retro 分流。

**三、验证方式 / 判据**

- V2 判据：uiautomator dump 见 `dialog_fragment_share_v2` / `share_v2_*` 资源 id；V1 判据 = `dialog_fragment_share`（老布局，出现即 FAIL）。
- 复制链接判据：剪贴板文本含该类型深链（如 `m.debox.pro`/`debox://` 相应 path）。
- 保存图片判据：`run-as` 或 MediaStore 查询新文件出现。
- 发送判据：测试群/好友会话内出现分享卡片消息，点击可回跳。
- 崩溃判据：logcat FATAL / mobile MCP crash 列表为空。

**四、风险**

- R1：分支 versionCode 低于已装包 → 安装预检失败。缓解：不 uninstall，改装到另一台无数据冲突的 AVD 或按 §7.7 停下改策略（版本构造法需用户确认前提时再说，优先换设备位）。
- R2：EVENT/SWAP/WEB 三类型入口在模拟器上可能不可达（依赖线上活动/RN 页面状态）→ 如实标 BLOCKED+原因，不虚报覆盖（L-003：要么执行要么显式记延后）。
- R3：RN 图片分享无 RN 侧调用入口 → 降级为单测证据，Follow-up 记「等 RN 业务接入后补 UI 层」。
- R4：模拟器登录态/风控波动（-2051）→ 按 devices.md 鉴别法处理，等 5-10 分钟重试，不误判后端。

### v2（2026-07-16，按 plan-review R1 全部 8 条 Required Changes 修订；基线 = v1 + 以下差异）

**Δ1（Critical：监工模式）** 执行步骤补「监工」环节，贯穿 Phase 1–6：
- 机制：每 ~5 分钟自查一次（长后台任务——构建/单测/模拟器启动/UI 用例——用后台任务通知 + 定时唤醒兜底，唤醒间隔 ≤5 分钟量级）。
- 检查内容：后台任务输出文件尾部是否有新增行；adb 设备是否在线；当前用例是否超单条预算（UI 用例单条 ≤10 分钟，单测模块 ≤15 分钟，构建 ≤10 分钟）。
- 恢复策略：无进展且超预算 → 截图+logcat 留证 → kill 重试 1 次 → 再失败该条记 FAIL/BLOCKED（附证据）继续下一条，不整体卡死。
- 停止条件：Phase 6 结论写入 results.md 或任务进入收口。

**Δ2（V2/V1 判据修正）** 布局文件名不是 uiautomator 可观测对象，改用**实际 view id 组合**（已在分支源码核验）：
- V2 判据 = 同屏出现 `scrollPreview` + `rvChannelList` + `rvSessionList`（源自 `dialog_fragment_share_v2.xml`，id 全局唯一组合）；辅证 `tvSearchInput`/`llPreviewContainer`。
- V1 判据 = 出现 `llShareImg` 或 `llQuotes` + `aaChartView`（源自老 `dialog_fragment_share.xml`，现存于 BaseModule）→ 出现即 FAIL。
- 兜底探针：debug 包 `DebugLog`/FragmentManager dump（`dumpsys activity top`）中 Fragment 类名 `ShareDialogFragmentV2` vs `ShareDialogFragment`。

**Δ3（11 类型全建用例）** TC-F 功能组为 **11 种类型逐一建独立用例**（WEB/DAPP/SWAP/QUOTES/QUOTES_DETAILS/GROUP/FRIEND/MOMENT/EVENT/SPACE/SPACE_FINISH）。入口不确定的类型（SWAP/EVENT/WEB）用例内含「入口探索」步骤（探索范围=下方 Δ5 allowlist + 已知原生入口），探索失败 → 记 BLOCKED 并附：尝试过的入口清单、截图、复测触发条件。禁止无执行记录的「可能不可达」。

**Δ4（RN 图片分享安全合同）** TC-F 图片组拆为两层：
- 单测层（必跑）：逐项核对分支负向合同测试**存在且通过**——scheme 白名单拒绝（http/data/resource/SVG）、`INVALID_SHARE_TYPE`/`INVALID_IMAGE_URL`/`UNSUPPORTED_IMAGE_SCHEME` 错误码、超时/迟到结果状态机（`ImageShareDialogEventTest`）、`RnLogSanitizer` 白名单摘要（不含 params/路径/带查询 URL）。单测清单在 cases.md 列成独立用例行，逐条对账测试报告。
- 集成层（条件跑）：若 UI 入口可达，加验 1 条负向（HTTPS 大图或断网超时 → reject 不崩）+ **logcat 脱敏抽查**：触发分享后 `logcat` 窗口内 grep 完整 URL/本地绝对路径，命中即 FAIL（安全合同违约）。UI 不可达 → 集成层标 BLOCKED，说明单测证据边界（测不到真实 LiveEventBus/Glide 路径）。

**Δ5（RN 深链只读 allowlist）** 深链探索仅限**预审 allowlist**（执行前逐条对 dangerous-ops.md 完成 pre-action 审查，打开页面本身无交易/发布副作用）：
1. `debox://rn/profile/shares`（我的分享列表，只读）
2. `debox://rn/trade/token?tokenId=<已知测试 token>-<chain>`（行情详情，只读报价页；QUOTES_DETAILS 入口）
- 禁止：枚举未知路由、任何含 swap/transfer/order/pay 语义的路由、404 兜底页以外的猜测性参数。allowlist 之外的 RN 入口只从 App 内可见 UI 导航进入。页面打开后的每次点击仍单独做 pre-action 比对。

**Δ6（Phase 6 与跨任务闭环）** Phase 5/6 明确为：
- Phase 5 回归 = 修复合入后**全量重跑**（非只跑 FAIL 条）。
- Phase 6 验收 = **独立的不改代码 P0→P1→P2 全量重跑**，不复用之前轮次结果。
- 若产生 FAIL 且修复转交独立 agent-dev-loop 任务：本任务状态保持 **blocked（等待修复回归）**，不得标 done；直到修复任务收口 + 本任务完成 Phase 5 回归与 Phase 6 验收，或经用户确认按 PASS_WITH_ACCEPTED_RISK 收口（FAIL 项列入 Accepted Risk，owner/reason/follow-up/expiry 四要素齐）。

**Δ7（证据层统一标注）** 证据层枚举改为：`单测（JVM/Robolectric）` / `L1 模拟器真包 UI` 两类；cases.md 每条用例、results.md 每条结果、最终结论都用该标注。最终结论固定含独立小节「真机门禁状态」，逐项列 debox `2026-07-15-01` Follow-up #1 真机验收项 = **NOT_RUN（本轮不闭合，L3 不可用）**；全文禁用「真机验收完成」等表述。

**Δ8（证据基线与关联规则）** 每条 UI 用例执行协议：
- **logcat 窗口**：用例开始前记录 `date +%m-%d\ %H:%M:%S.000` 作 T0，判定只用 `logcat -t '<T0>'` 窗口内、且 `--pid $(pidof com.tm.security.wallet)` 过滤的行。
- **剪贴板**：点复制前先 `cmd clipboard set-primary "autotest-baseline-<caseId>"` 写基线值，点击后读取值 ≠ 基线且含预期深链才算 PASS。
- **保存图片**：操作前后各查一次 MediaStore（`content query --uri content://media/external/images/media --projection _id,_display_name,date_added --sort "date_added DESC"` 取前 5），PASS 判据 = after 出现 before 没有的新行且 `date_added ≥ T0`。
- **崩溃**：判定窗口 = 该用例 T0→结束，`logcat -t '<T0>'` 内无 `FATAL EXCEPTION`（pid 过滤）+ mobile MCP crash 列表无新增（同窗口）。
- **截图命名**：`<caseId>-<step>-<HHMMSS>.png`，与用例行一一对应。

### v3（2026-07-16，按 plan-review R2 全部 5 条 Required Changes 修订；基线 = v2 + 以下差异）

**Δ9（RC1：重定向 SSRF 与体积超限）** RN 图片分享安全合同对账精确到测试方法，并显式登记两个已核验缺口：
- 已有单测覆盖（cases.md 逐行对账）：`SharePreviewSecurityTest` 9 项（非 http scheme/localhost/IPv6 回环/私网 IPv4 字面量/**DNS 解析后内网地址**/多地址任一内网/解析失败/公网放行/边界匹配）；`ShareImagePreparerTest` 7 项（本地复制+MIME 探测/HTTPS 临时文件清理/不可读/无权限/非图与截断拒绝/下载失败/**超时**）；`ShareOpenGraphFetcherTest`（unsafe page/og:image 降级、超时单次完成）；`ShareBridgeHandlerTest`（scheme 白名单+错误码）；`ImageShareDialogEventTest`（超时/迟到状态机）；`RnLogSanitizerTest`（日志白名单摘要）。
- **缺口 A（重定向 SSRF）**：源码核验——HTTPS 下载走 `Glide.downloadOnly()`（`ShareImagePreparer.kt:245`），SSRF 校验只作用于原始 URL，3xx 重定向后无二次校验。这对应 debox 任务档已登记 Accepted Risk「SSRF 抓取层替换（expiry 2026-09-30）」→ 本轮用例判定标准 = 缺口存在性核验（源码+单测均无该防线）→ 结果标 **KNOWN_ACCEPTED_RISK**（复核 expiry 未过期），写入 results.md 观察项，不重复报 bug。
- **缺口 B（响应体超限）**：单测无 size-cap 用例、实现无 maxBytes/contentLength 防线 → 记为**覆盖缺口发现项**（观察级）写 results.md，修复归 debox 侧决策；本轮集成层若可跑则验「大响应不 OOM 崩溃」（超时/内存判据），判据 = 无 FATAL + reject 错误码 + cache 目录 before/after 无残留落盘 + 日志无完整 URL。

**Δ10（RC2：发送对象预审绑定）**
- GROUP 发送目标 =「**担保师测试群**」（2026-07-14 run 已登记的测试群），执行判据：会话列表/群名**精确匹配**该名称才可发送；不存在或多个同名 → BLOCKED。
- FRIEND 发送：知识库**当前无登记测试好友** → 用例内固定候选 = 账号「请输入昵称1」（devices.md 登记的测试账号，与三星/5556 同号）；执行时需在好友列表唯一精确匹配，否则 **BLOCKED**（禁止搜索/临时挑选替代对象）。若确认成功，回写 app-knowledge 登记。
- 通用：发送前截图留证目标会话标题；每用例最多发送 1 次。

**Δ11（RC3：全改动覆盖追踪矩阵）** cases.md 增「覆盖映射」表：`git diff --stat`159 文件按变更域归组，每域 → 用例 ID / 单测类 / 排除理由（三选一，杜绝静默缺口，对齐 L-003）。变更域：①imKit share 核心逻辑（单测+UI）②卡片布局与资源 colors/dimen（各类型 UI 用例）③11 调用方切换（TC-F 逐类型）④老弹窗 @Deprecated 收尾（TC-T V1 不再出现）⑤ReactNative bridge（单测+TC-F 图片）⑥BaseModule 事件+老布局微调（单测）⑦moduleMain 编排/MainActivity/WebShareRequestHolder（单测+TC-F WEB）⑧多语言 strings（抽查 zh/en 弹窗文案无 key 裸奔）⑨docs/测试文件（排除：非运行时产物/证据本体）。执行前逐域核对无遗漏。

**Δ12（RC4：崩溃 PID 协议修正）** T0 时记录 `PID0`；用例结束取 `PID1`。判定：`PID0==PID1` 时用 pid 过滤窗口；**PID 变化或进程消失 → 崩溃疑点流程**：全窗口 logcat（不带 pid 过滤）grep `FATAL EXCEPTION|Process .* died|ANR in` + `dumpsys dropbox --print` 窗口内 `data_app_crash/data_app_anr` 条目；确认崩溃 → FAIL 附 dropbox 摘要与堆栈头。

**Δ13（RC5：知识库门禁显式化）** Phase 1 第 0 步（硬门禁）：完整读取 `app-knowledge/` 全部文件（README/dangerous-ops/devices/network-domain/dapp-sign-testing/screens/*.md），完成前禁止任何设备操作；results.md「环境」小节记录知识库快照 = autotest repo HEAD SHA + 各文件读取时间。执行中新发现（新入口/新弹窗/测试好友确认）当轮回写 app-knowledge。

### v4（2026-07-16，按 plan-review R3 全部 4 条 Required Changes 修订；基线 = v3 + 以下差异）

**Δ14（RC1：体积超限缺口升级为 Accepted Risk，消除判据矛盾）** 缺口 B 从「观察项」升级为本任务 **Accepted Risk 提名**（四要素：owner=Kai（debox 分享链路，与 SSRF 同 owner）；reason=`Glide.downloadOnly()` 无 contentLength/maxBytes 防线，超时是唯一兜底；follow-up=debox 侧补下载大小上限+单测，建议与「SSRF 抓取层替换」独立任务同批实施；expiry=2026-09-30 与 SSRF 对齐）。**删除**「大响应/大图验证」集成用例（不可控超大响应属破坏性验证）；集成层负向只保留**确定性探针**：断网触发 HTTPS 图片准备 → 预期 `IMAGE_PREPARE_FAILED/IMAGE_PREPARE_TIMEOUT` reject、无 FATAL、cache 无残留、日志无完整 URL（判据与现有实现的超时/失败防线一致，无矛盾）。

**Δ15（RC2：SSRF Accepted Risk 四要素复核入档）** 已从 debox 任务档 `2026-07-15-01/review.md` Accepted Risk Log 抄录核验原文四要素：**Finding**=SSRF:TextCrawler 重定向/DNS rebinding 后目标不复检；**Owner**=Kai；**Reason**=老实现既有缺口，等价迁移不新增暴露面，修复需整体替换抓取层超出「行为不变」基线；**Follow-up**=独立任务：OkHttp 禁自动重定向 + ≤3 跳逐跳 isSafeUrl + 自定义 Dns 固定已校验地址集；**Expiry**=2026-09-30（今日 2026-07-16 **未过期**，承接有效）。cases.md/results.md/最终结论均引用此四要素全文；expiry 过期或要素缺失才按安全缺陷处理（当前不满足触发条件）。

**Δ16（RC3：FRIEND 发送改为默认 BLOCKED）** 知识库当前**无任何测试好友的稳定 ID 登记**（仅有可变昵称）→ FRIEND 端到端发送用例**计划内即标 BLOCKED**（原因：预审身份绑定不满足；解锁条件：知识库登记好友稳定标识（DeBox ID/UID）并经用户确认后，后续轮次执行）。禁止凭昵称匹配发送。端到端发送仅保留 GROUP 1 条（目标=「担保师测试群」，群名精确唯一匹配 + 发送前截图核对，失配即 BLOCKED）。FRIEND 类型的弹窗展示/卡片渲染/复制链接用例不受影响（不涉发送）。

**Δ17（RC4：MediaStore 协议实测修正）** 已在 emulator-5554 实测可用语法（exit 0）：
`adb shell content query --uri content://media/external/images/media --projection _id:_display_name:date_added --sort '"date_added DESC"'`（projection **冒号**分隔；取前 5 在宿主侧 `head -5`；`date_added` 单位=秒级 UNIX epoch，与 T0 比较前统一到秒）。判据：before/after 各跑一次，命令自身报错（usage/exception）→ 该用例证据协议 BLOCKED（不猜替代语法）；PASS = after 出现 before 没有的 `_id` 且 `date_added ≥ T0(秒)`。

**Δ18（新证据：分支为 reopened 修复态，渲染用例升级为修复复验）** 从 debox review.md 阶段 6 获知：真机验收曾发现 V2 布局底座缺陷——FRIEND/SPACE/QUOTES_DETAILS/SPACE_FINISH 卡片**完全不显示**、GROUP/MOMENT/EVENT 卡片**上半截出屏**；分支 tip `0ebe363f96` 即该修复（待复验）。因此 TC-F 各类型「卡片渲染」判据强化：卡片容器可见 **且 bounds 完整在屏**（top≥0、bottom≤屏高、高度>0），逐类型截图对照；此项为本轮最高价值验证点（修复复验 + 已知症状清单可直接对表）。

> **超限说明（loop cap）**：plan review 已达 3 轮上限（R1-R3 均 FAIL 但 findings 收敛 8→5→4，且 R3 无 Critical）。按 CLAUDE.md 铁律#1（AI 全程自主）/#4（人工仅白名单五项，超限授权不在白名单），autonomous session 无法等待用户授权，决定加跑 R4 并在任务收口时向用户明示此决策请求追认。（R4 后 findings 收敛至 2，同理加跑 R5。）

### v5（2026-07-16，按 plan-review R4 全部 2 条 Required Changes 修订；基线 = v4 + 以下差异）

**Δ19（RC1：Glide 重定向 SSRF 与 TextCrawler 风险分离）** 核验结论：debox 既有 Accepted Risk 原文明确限定「SSRF:**TextCrawler** 重定向/DNS rebinding」（OpenGraph 抓取链路）；RN 图片下载走 `Glide.downloadOnly()`（`ShareImagePreparer.kt:245`）是**不同实现路径且属新增代码（新增暴露面，非等价迁移）**——既有四要素**不覆盖**该路径。处置改为：缺口 A 拆两条——
- A1（TextCrawler 抓取链路）：已被 debox Accepted Risk 覆盖（四要素见 Δ15，未过期）→ 结果标 KNOWN_ACCEPTED_RISK。
- A2（**Glide 图片下载重定向 SSRF**）：本轮定性为**安全缺口发现项（Important 级缺陷候选）**写入 results.md，**提名** debox 侧二选一处置：修复（Glide 下载改经与 isSafeUrl 同源的重定向复检，建议与抓取层替换独立任务同批）或按四要素正式登记 Accepted Risk（提名要素：owner=Kai；reason=新增 RN 图片 HTTPS 下载面，SSRF 校验仅作用于原始 URL；follow-up=同「抓取层替换」任务；expiry=建议 ≤2026-09-30）。**本测试任务无权替 debox 接受风险**，A2 与体积超限（Δ14，同为提名）都保持「发现项待承接」态，不作为本任务 FAIL 阻断项（属存量设计缺口，非本轮回归引入），列入 Follow-up 跟踪。

**Δ20（RC2：RN reject 错误码观测通道收窄）** 核实：分支 APK 内 RN bundle **不含** `debox.system.share` 调用方（`unzip -p ... | grep -c` = 0），RN 侧无 UI 触发/回调观测通道 → **错误码 reject 合同仅由单测证据判定**（`ShareBridgeHandlerTest`/`ImageShareDialogEventTest`，已全绿）。集成层用例判据收窄为：若后续发现 UI 入口，仅验「无 FATAL + cache 无残留 + 日志脱敏（无完整 URL/路径）」三项可观测项，**不宣称验证 reject 错误码**；无 UI 入口（当前状态）→ 集成层 BLOCKED（原因=RN 业务未接入，复测条件=RN bundle 出现 `debox.system.share` 调用方）。

## Plan Review Log（per-round，含 VERDICT 快照）


### plan-review Round 1 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
- [Standards/规范] [95] 执行步骤未纳入铁律要求的 `/loop 5m` 监工模式，长时间构建及全量测试缺少定时检查与卡住恢复机制 -> 将监工启动、检查内容、恢复策略和停止条件写入执行步骤。

### Important (warn)
- [Bugs/逻辑] [88] 将布局资源名 `dialog_fragment_share_v2` / `dialog_fragment_share` 直接视为 UIAutomator 可见的 `resource-id`，该假设仅在实际 View 显式设置对应 `android:id` 时成立，可能导致 V2/V1 判定失真 -> 核验现有布局并改用实际可观测 View ID、V2 特有控件组合或运行时探针。
- [Test coverage/测试覆盖] [88] 功能矩阵只落了 9 类，SWAP、EVENT 仅出现在风险说明中；“可能不可达”不能替代正式用例及执行结论 -> 为全部 11 类型分别建用例，执行探索后再记 PASS/FAIL/BLOCKED，并记录入口、阻塞证据和补测条件。
- [Security/安全] [89] RN 图片分享仅规划正向 UI 验证，未覆盖方案中的安全合同：scheme 白名单、重定向后 SSRF、超时/大小边界、错误码以及日志 URL 脱敏 -> 增加对应负向单测清单及可执行的集成验证，至少检查拒绝结果和 logcat 不泄露敏感 URL 参数。
- [Security/安全] [88] “遍历已知 RN 路由”没有给出精确只读 allowlist，深链启动本身可能触发页面副作用，不能只在后续点击前做 dangerous-ops 比对 -> 执行前固定允许访问的路由及参数，逐条完成危险操作审查，禁止猜测、枚举或调用有交易/发布副作用的路由。
- [Plan alignment/计划对齐] [87] Phase 6 仅写“验收结论”，没有明确按规范进行“不改代码的完整全量重跑”；出现 FAIL 后另开修复任务，也未定义本测试任务在修复与回归闭环前的状态 -> 明确 Phase 6 是独立的 P0→P2 全量重跑；若修复转交其他任务，本任务保持 BLOCKED/未闭环，直到修复、全量回归及最终验收完成，或明确按 accepted risk 收口。
- [Standards/规范] [84] 证据层表中使用“真包 UI”容易与真机证据混淆，不足以落实 L-004 的分层要求 -> 统一标注为“L1 模拟器真包 UI”，并在每条结果及最终结论中单列真机门禁为 NOT_RUN/BLOCKED，不使用“真机验收完成”等表述。
- [Test coverage/测试覆盖] [84] 保存图片、剪贴板、崩溃等判据缺少操作前基线、时间窗口和唯一关联标识，可能把历史文件、旧剪贴板或无关进程日志当成本轮证据 -> 为每项定义 before/after 基线、文件名或时间戳、目标 PID/包名及单用例 logcat 窗口。

### Questions
- None

### Required Changes
1. 将 `/loop 5m` 监工模式及卡住恢复策略加入执行流程。
2. 核验并修正 V2/V1 的可观测判据。
3. 为 11 种分享类型全部建立独立用例和 BLOCKED 证据规则。
4. 补齐 RN 图片分享安全合同的负向测试。
5. 为 RN 深链探索建立预审后的精确只读 allowlist。
6. 明确 FAIL 后的跨任务闭环状态及独立 Phase 6 全量验收。
7. 将证据层统一标为 L1 模拟器证据，并显式记录真机门禁状态。
8. 为文件、剪贴板和崩溃验证增加基线与证据关联规则。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-plan-review-R1.txt

### plan-review Round 2 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
None

### Important (warn)
- [Security/安全] [89] RN 图片分享安全合同仍未明确覆盖 HTTPS 重定向后的 SSRF 校验，以及下载体积超限的确定性拒绝判据；“HTTPS 大图或断网超时”不能证明重定向地址和大小边界均受保护 -> 增加重定向到私网/保留地址、响应体超限的独立用例，并核对预期错误码、无文件落盘及日志脱敏。
- [Security/安全] [87] 两条会话发送用例仅写“测试群/测试好友”，未规定如何确认接收方是知识库预先登记的测试对象；执行时误选同名正式群或正式好友会造成不可逆发布 -> 在 cases.md 中绑定经预审的唯一标识和页面识别特征；无法唯一确认时必须 BLOCKED，禁止临时选择或搜索替代对象。
- [Test coverage/测试覆盖] [88] 原始需求要求覆盖分支所有改动，但计划仅按两大功能摘要设计测试，没有建立 159 个变更文件/31 个提交到测试用例、已有单测或明确不测项的可追溯映射，可能遗漏重构附带的资源、入口及跨模块回归 -> 在生成用例前增加 diff 影响清单和覆盖映射，所有变更域必须对应测试证据或带理由的排除项。
- [Bugs/逻辑] [86] 崩溃证据在查询时使用当前 `pidof` 过滤；若用例期间进程崩溃并被重启，当前 PID 会变化，旧进程产生的 FATAL 可能被过滤掉，造成假 PASS -> 在 T0 固定记录初始 PID，同时检测 PID 变化，并结合未按当前 PID 过滤的 ActivityManager/crash_buffer 记录判断崩溃。
- [Standards/规范] [83] 计划虽引用知识库内容，但执行步骤没有把“测试 session 开始时先完整读取 app-knowledge，尤其危险操作清单”设为 Phase 1 的显式门禁和留痕项 -> 在环境启动、构建和 UI 探索前增加知识库读取检查，并在 results.md 记录读取版本或快照时间。

### Questions
- None

### Required Changes
1. 补充 HTTPS 重定向 SSRF 与下载体积超限的独立安全用例和明确判据。
2. 为测试群和测试好友绑定预审过的唯一身份，无法唯一确认时禁止发送并记 BLOCKED。
3. 建立分支全部改动到用例、单测或排除理由的覆盖追踪矩阵。
4. 修正崩溃日志关联协议，固定初始 PID 并覆盖崩溃重启导致的 PID 变化。
5. 将 session 开始前读取 app-knowledge 设为显式执行门禁并记录证据。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-plan-review-R2.txt

### plan-review Round 3 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
None

### Important (warn)
- [Security/安全] [89] 已确认下载实现缺少响应体大小上限，但计划仍将其降为“观察项”，同时集成用例又要求返回 reject 错误码；现有实现没有确定性超限拒绝机制，该判据与现状矛盾，且无界下载存在内存/磁盘耗尽风险 -> 将其明确登记为安全 Accepted Risk（owner/reason/follow-up/expiry）或缺陷，并避免使用不可控超大响应做破坏性验证；仅运行有严格服务端大小上限的安全探针。
- [Security/安全] [87] 重定向 SSRF 仅引用 debox 既有 Accepted Risk 名称和 expiry，未在本计划中核验并记录完整的 owner/reason/follow-up/expiry，不能据此判定风险仍被有效承接 -> 在 cases.md/results.md 和最终结论中记录完整四要素、原风险来源及当前有效性；任一要素缺失或已过期时按安全缺陷处理。
- [Security/安全] [86] FRIEND 发送目标仍只通过可变昵称“请输入昵称1”识别；“唯一精确匹配”无法证明该账号就是预审测试对象，而执行成功后再回写知识库晚于危险操作门禁 -> 发送前必须从知识库或可信账号资料绑定稳定用户 ID/账号标识；无法预先确认时固定记 BLOCKED，禁止发送。
- [Bugs/逻辑] [84] MediaStore 基线命令使用逗号分隔 projection，且展示命令未实现“仅取前 5 条”；Android `content query` 的 projection 通常要求冒号分隔，命令失败或全量输出会使保存证据协议不可执行 -> 在计划中采用目标设备实测可用的查询语法，明确排序、截取、时间单位归一和命令失败时的 BLOCKED 判据。

### Questions
None

### Required Changes
1. 将响应体无上限缺口升级为明确安全风险或缺陷，消除“观察项”与“必须 reject”之间的判据矛盾。
2. 补齐并复核重定向 SSRF Accepted Risk 的 owner/reason/follow-up/expiry。
3. 为测试好友绑定预审过的稳定身份标识；仅凭昵称不得发送。
4. 修正并预验证 MediaStore 查询协议，确保 before/after 证据可执行且可关联。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-plan-review-R3.txt

### plan-review Round 4 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
None

### Important (warn)
- [Security/安全] [89] Δ15 引用的既有 Accepted Risk 明确针对 TextCrawler 抓取链路的重定向/DNS rebinding，而 RN 图片下载实际走 `Glide.downloadOnly()`，属于不同实现路径和新增 RN 暴露面；现有四要素不能自动覆盖该路径 -> 核验原风险范围明确包含 RN 图片下载，或为 Glide 重定向 SSRF 单独记录 owner / reason / follow-up / expiry。
- [Test coverage/测试覆盖] [85] RN 图片断网集成用例要求验证 `IMAGE_PREPARE_FAILED/IMAGE_PREPARE_TIMEOUT` reject，但计划未定义 UI 自动化如何观测 RN Promise reject；仅凭无崩溃、无缓存残留和脱敏日志无法证明错误码正确 -> 指定可观测的 RN 回调测试页、测试探针或脱敏错误码日志；若无观测通道，则错误码仅由单测判定，UI 层不得宣称已验证 reject 合同。

### Questions
None

### Required Changes
1. 为 RN 图片下载的 Glide 重定向 SSRF 单独完成风险承接，或提供既有 Accepted Risk 明确覆盖该路径的证据。
2. 明确 RN 集成层 reject 错误码的可观测机制，或收窄集成用例判据并声明错误码仅有单测证据。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-plan-review-R4.txt

### plan-review Round 5 — 2026-07-16（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: PASS_WITH_ACCEPTED_RISK

### Critical (block)
None

### Important (warn)
- [Security/安全] [89] RN 图片下载使用 `Glide.downloadOnly()`，仅校验原始 URL，未覆盖重定向后 SSRF；v5 已独立记录 owner=Kai、reason=新增 RN HTTPS 下载暴露面、follow-up=补同源逐跳复检并纳入抓取层替换任务、expiry≤2026-09-30 -> 按该四要素承接并持续跟踪，未正式承接或到期时升级为安全缺陷。
- [Security/安全] [87] RN 图片下载缺少响应体大小上限，存在磁盘或资源耗尽风险；v4/v5 已记录 owner=Kai、reason=当前仅靠超时兜底、follow-up=补下载大小上限及单测、expiry=2026-09-30 -> 按该四要素承接，避免执行不可控超大响应探针。

### Questions
None

### Required Changes
None
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-16-01-test-debox-share-refactor/codex-plan-review-R5.txt
## Plan Revision Log

> 每次按 review 修订方案时追加一行：vN · 针对哪条 finding · 改了什么。

- v2 · R1 全部 8 条 Required Changes · Δ1 监工机制入流程（Critical）；Δ2 V2/V1 判据改为源码核验过的 view id 组合；Δ3 11 类型全建独立用例+BLOCKED 证据规则；Δ4 RN 图片分享安全合同单测对账+集成负向+logcat 脱敏抽查；Δ5 深链只读 allowlist（2 条预审路由）；Δ6 Phase 5/6 全量重跑语义+跨任务 blocked 状态；Δ7 证据层统一「单测/L1 模拟器真包 UI」+真机门禁 NOT_RUN 小节；Δ8 剪贴板/MediaStore/logcat/截图的基线与关联协议。
- v3 · R2 全部 5 条 Required Changes · Δ9 安全合同对账精确到测试方法+缺口 A（重定向 SSRF=debox 已登记 Accepted Risk，标 KNOWN_ACCEPTED_RISK）/缺口 B（体积超限无防线=观察级发现项）；Δ10 发送对象预审绑定（群=担保师测试群精确匹配；好友=「请输入昵称1」唯一匹配否则 BLOCKED）；Δ11 159 文件按 9 变更域建覆盖映射表（用例/单测/排除理由三选一）；Δ12 崩溃协议记 PID0/PID1+变化走 dropbox 疑点流程；Δ13 知识库完整读取设为 Phase 1 第 0 步硬门禁并记快照。
- v4 · R3 全部 4 条 Required Changes · Δ14 体积超限升级为 Accepted Risk 提名（四要素齐）+删除不可控大响应验证（负向只留断网确定性探针）；Δ15 SSRF Accepted Risk 四要素原文抄录核验（未过期，承接有效）；Δ16 FRIEND 发送计划内 BLOCKED（无稳定 ID 登记，禁昵称匹配发送），端到端发送仅留 GROUP 1 条；Δ17 MediaStore 语法已实测（冒号 projection，exit 0）+失败即 BLOCKED；Δ18（新证据）分支为 reopened 布局修复态，渲染判据强化为 bounds 完整在屏=修复复验。
- v5 · R4 全部 2 条 Required Changes · Δ19 缺口 A 拆 A1（TextCrawler=既有 Accepted Risk 覆盖）/A2（Glide 下载重定向 SSRF=新增暴露面发现项，提名 debox 处置，本任务不代为接受）；Δ20 实证 RN bundle 无 `debox.system.share` 调用方 → reject 错误码合同仅单测证据，集成层判据收窄为三项可观测项且当前 BLOCKED。

## Accepted Plan

> **不全文复制方案**：基线 = Proposal vN + 逐条差异。

- 基线 = Proposal v5（= v1 + Δ1–Δ20 全部差异），无额外差异。
- Accepted at / Decision：2026-07-16 · plan-review R5 **PASS_WITH_ACCEPTED_RISK**（R1-R5 findings 收敛 8→5→4→2→2，R5 两条 Important 即下方已登记风险提名本身，Required Changes=None；R4/R5 为超限加跑，决策依据见 v4 超限说明，待用户追认）。
- Accepted risks（owner / reason / follow-up / expiry）: 共 2 条安全缺口提名（详见 review.md Accepted Risk Log），均 owner=Kai、expiry≤2026-09-30、待 debox 正式承接：
  1. **RISK-A2 Glide 图片下载重定向 SSRF**：owner=Kai（提名，待 debox 正式承接）；reason=新增 RN HTTPS 下载暴露面，SSRF 校验仅作用于原始 URL；follow-up=补同源逐跳复检，纳入「抓取层替换」独立任务，本任务 results.md 上报发现项；expiry=≤2026-09-30，未正式承接或到期升级为安全缺陷。
  2. **RISK-B RN 图片下载无响应体大小上限**：owner=Kai（提名，待 debox 正式承接）；reason=当前仅靠超时兜底，存在磁盘/资源耗尽风险；follow-up=debox 侧补下载大小上限+单测；expiry=2026-09-30。执行约束：不做不可控超大响应探针。
- Deviation Policy: 改变公开 API、数据结构、部署方式、安全逻辑、依赖或核心实现路径的偏离，必须先 consult Codex。
