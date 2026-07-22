# 测试用例 — 账号重验、网络/IM 可观测性与账号生命周期

> 测试日期：2026-07-18（原 2026-07-17 run 全量重测）  
> 优先级：P0 = 阻断放行；P1 = 重要链路；P2 = 稳定性/扩展链路。  
> 设备：L1=`emulator-5554` / Pixel_10_Pro_XL；L2=`emulator-5556` / debox_root；L3=真机确认。设备映射执行前必须重新读取，不能只认序列号。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | token 失效重登、重验门禁、HTTP call_id、IM episode、鉴权熔断、切钱包/切账号、BTC/私钥/第三方钱包登录、账号被踢、创建账号、登出重登、后台清理与实例释放 |
| App 包名 | `com.tm.security.wallet` |
| 代码范围 | `b8fd28ddffca966c4cd9ff3b97e6dd1c1107e51f`（含）至冻结 HEAD `e0650d2b1386d8a527893f5301fb431f98b55dbe`，再加 `evidence/phase2/00-source-baseline.txt` 所列全部未提交改动 |
| 测试方式 | AutoTest 规范 + DeBox JVM/androidTest + adb/UIAutomator + logcat/进程/网络证据 |
| 结果原则 | 旧 `results.md` 结论全部作废；本文件是本 run 唯一用例源，Phase 2、5、6 分别独立执行和记录 |

## 全局执行契约

1. 按 P0 → P1 → P2 执行。除明确验证在途/前后台状态的 case 外，每条开始前执行 `am force-stop com.tm.security.wallet`，重新启动并等待首页稳定；先获取新鲜 layout，再操作。
2. 全程抓取带时间戳 logcat、FATAL/ANR、当前 PID 和开始/结束时间；日志证据只保存事件名、错误码、阶段、计数、布尔值和掩码 ID，不保存 token、签名、完整地址或任何密钥材料。
3. 用户已授权：切账号、切钱包、登出、创建账号、同账号多端登录并触发被踢。仍禁止：`pm clear`、卸载、切环境/测试链、删除钱包/账号、导出或截图助记词/私钥、签名/Approve/支付/转账。
4. L2 网络规则必须先保存 `iptables -t nat -S OUTPUT` 与 `iptables -S OUTPUT`；只用与 `-A` 完全同参的 `-D` 删除本轮规则，禁止 `-F`。APP_UID 每次安装后现场读取。
5. 预期物料缺失、后端无法制造指定业务码或安全边界不允许时记 `BLOCKED`，不得用相似路径冒充 PASS。任何 FAIL 先写 results，再进入 Phase 4。

## 0. 前置门禁（P0）

| # | 用例 / 变更来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-P-001 | AutoTest 自检与发布 / 项目铁律 | 在 autotest 依次执行 `:autotest:compileReleaseKotlin`、`:autotest:test`、`:autotest:publishToMavenLocal` | 三命令均 exit 0；测试 0 failure；本地 AAR 可解析 | P0 / 主机 | 无设备副作用 |
| TC-P-002 | DeBox JVM、编译与 BTC 测试门禁 / b8fd…e0650 + 工作区 BTC tests | 设置 `NODE_PATH=$PWD/ReactNative/node_modules`；运行 NetworkCallTrace/NetEventListener、AuthTokenStore/StaleAuth、AccountRevalidationGate/RevalidationReshow、LoginAttemptCoordinator/AppLoginFailurePolicy、AuthSyncBreaker/CacheServiceBatchKey、BtcDerivationAggregator 单测；编译 BaseBusiness/BaseModule/moduleMain；构建 BaseModule androidTest | 所列测试 0 failure；编译与 `assembleDebugAndroidTest` exit 0；实际执行数写 results，不能只写 BUILD SUCCESSFUL | P0 / 主机 | BTC 设备兼容测试在 TC-D-001 独立执行；不改生产代码 |
| TC-P-003 | 现场 APK 构建、签名、哈希与归因 / 全范围 | 记录构建前 HEAD、status、diff 指纹；构建 app debug 与 test APK；保存 SHA-256、versionCode、签名证书摘要；安装前与设备现装包比签名/versionCode；仅允许 `adb install -r` | 构建/安装 exit 0；签名一致且不降级；`run-as` 可用；APK 内含 `c27bf78f54` 行为及本轮 gated harness；哈希与安装时间写 results | P0 / L1+L2 | 签名不一致即 BLOCKED；禁止卸载绕过 |
| TC-P-004 | 设备、环境、账号与钱包物料基线 / 全范围 | 重新读取 AVD 名、设备型号、包版本、UID、网络；UI 核验 L1/L2 为允许写操作的测试环境；盘点已备份本地助记词钱包、私钥钱包、BTC 地址类型、可切换账号、第二登录端、TP/WC 物料 | 设备映射与 `00-device-baseline.txt` 一致或已更新；每类物料仅记录“有/无”和掩码 ID；正式环境真机只读 | P0 / L1+L2(+L3) | 不打开/读取/截图助记词或私钥；缺物料的对应 case 记 BLOCKED，不阻塞无关 case |

## 1. 账号失效、重验门禁与鉴权熔断

| # | 用例 / 变更来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-A-001 | `-2007` 本地钱包自动重签重登 / 4c220c、023d031、c27bf78 | 已登录且本地钱包可自动签名；清日志；用 gated harness 单次 post `AccountTokenState(-2007)`；等待 ≤60s | 恰 1 个 `revalidate_episode_start code=-2007`；出现 `revalidate_auto_resign code=-2007`；最终恰 1 个 `app_login ok` 且回到可用首页；无 LoginOut 风暴/FATAL/ANR | P0 / L2 | 自动签名属内部登录链；若出现用户签名/支付弹窗立即取消并 FAIL |
| TC-A-002 | `-2018` 本地钱包自动重签重登 / 同上 | 按 A-001 独立重置，注入 `-2018` | 与 A-001 同构，错误码必须为 `-2018`，最终登录成功且 episode 不重复 | P0 / L2 | 同 A-001 |
| TC-A-003 | `-2023` 本地钱包自动重签重登 / 同上 | 按 A-001 独立重置，注入 `-2023` | 与 A-001 同构，错误码必须为 `-2023`，最终登录成功且 episode 不重复 | P0 / L2 | 同 A-001 |
| TC-A-004 | 高频失效同周期抑制 / 7eca437、4c220c、c27bf78 | 登录稳定；harness 在 <400ms 内从后台线程 post 同一失效码 8 次 | 注入确认计数=8；`revalidate_episode_start`=1；`revalidate_auto_resign`≤1；`app_login ok`≤1；无 8 轮 LoginOut/IM 重连 | P0 / L2 | case 自身验证在途，不做中间 force-stop |
| TC-A-005 | SUCCESS 后门禁重开 / 7eca437、023d031 | 完成 A-004 并确认 loginState 收口；再独立注入一次同码 | 第二周期新增且仅新增 1 个 episode；第二周期可正常自动重登；总 episode=2 | P0 / L2 | 超过 60s 未收口记 FAIL/BLOCKED 并留证，不盲目继续注入 |
| TC-A-006 | 旧认证世代回包抑制 / 4c220c、c27bf78 | 先跑 `StaleAuthGuardTest`/`StaleAuthFlowTest`；设备端制造登录在途后立即切到另一已备份钱包/账号，继续观察旧请求回包 | JVM 矩阵全过；设备端新账号最终成功；旧世代不得发布 SUCCESS/FAIL、不得覆盖 token/uid、不得触发旧身份后续同步；允许固定 `stale` 诊断 | P0 / L1 | 用户已授权切换；只选已备份目标；不进入账号删除 |
| TC-A-007 | TP/WC 重验失败重弹 / 023d031、c27bf78 | 仅在真实 TP/WC 测试物料存在时：制造一次重验，取消第一次签名，然后再次触发 | 第一次取消后门禁可释放；第二次能重新出现签名入口/弹窗，不陷入永久抑制；确认按钮不点击 | P1 / L1 | 无 TP/WC 物料记 BLOCKED；只取消，不签名/授权 |
| TC-A-008 | CacheService 鉴权拉闸 / c27bf78 | 先跑 `AuthSyncBreakerTest`/`CacheServiceBatchKeyTest` 的三错误码与分页世代矩阵；设备端仅承接真实后端失效（优先复用 C-003 被踢窗口）触发批量用户/收藏同步 | JVM：每码同世代仅首次 break，stale/非鉴权码不 break，分页停止；设备：真实失效时出现一次 `sync_auth_break entry=… code=…`，随后同世代入口 skip 且请求数停止增长 | P0 / 主机+L1/L2 | iptables 不能伪造业务错误码；拿不到真实码时设备部分 BLOCKED，JVM 结果不得冒充设备 PASS |
| TC-A-009 | 换代后同步恢复 / c27bf78 | 承接 A-008/C-003；完成重新登录形成新世代，再触发用户信息与收藏刷新 | 新世代不再 `skip: auth blocked`；请求正常返回/界面刷新；旧世代分页无跨账号落库 | P0 / L1/L2 | 完成后保持当前已备份账号，不切环境 |
| TC-A-010 | 正常使用阴性观察 / 7eca437、b8fd28、c7b17a | 无注入，冷启后四 Tab、消息/列表只读浏览并前后台切换，持续 10 分钟 | `revalidate_episode_start`=0；无 `sync_auth_break`；`call_failed` 最佳为 0，若非 0 每条须可归因；NetEventListener 裸事件非空且只含设计白名单；无 FATAL/ANR | P1 / L1 | 只读操作；不点签名、支付或创建入口 |

## 2. HTTP 网络链路与脱敏

| # | 用例 / 变更来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-B-001 | connect-refused call_id 关联 / b8fd28 | L2 现场查 UID并保存规则；精确 DNAT 该 UID 的 TCP/443 到 `10.0.2.2:65534`；从首页触发只读请求；取日志后精确 `-D` | 同一请求的 `call_failed` 与 `http request fail` 有同一非空 `call_id`；`phase/network_phase=connect`；`err=*:connect_refused`；elapsed 有值 | P0 / L2 | finally 必须删同参规则并保存前后 diff；禁止 `-F` |
| TC-B-002 | 失败日志脱敏 / b8fd28、c7b17a | 审计 B-001 的 App PID/FLogger 窗口，并运行 NetEventListenerSanitizeTest | `err` 完整匹配类名+枚举原因；全行不得命中 URL 路径/查询参数/token/Authorization/Bearer/API path/签名/完整地址；单测全过 | P0 / L2+主机 | 发现敏感值立即停止复制日志，results 只写命中类型与脱敏位置 |
| TC-B-003 | connect-timeout 原因归类 / b8fd28 | L2 保存规则；精确为当前 UID 添加 TCP/443 OUTPUT DROP；触发只读请求并等到配置超时；精确 `-D` | `call_failed phase=connect`、`err=*:timeout`；`connect_ms=-1`/无值；elapsed 接近配置超时；业务失败携同 call_id | P1 / L2 | 最长只等待一个配置超时周期；finally 恢复规则 |
| TC-B-004 | 业务错误与网络错误分离 / b8fd28 | 正常网络下打开固定虚构只读 deeplink `debox://open/share?type=live&id=autotest404` | 业务失败 code 可复现且 `call_id` 非空；`network_phase∈{response_body_complete,complete}`；该 call_id 无 `call_failed` | P1 / L1 | 不创建直播/SPACE，不进入付费入口；若后端行为已变，记录实际响应并 BLOCKED 而非改 oracle |
| TC-B-005 | Sol 链路 call_id / b8fd28 | 先只读确认当前钱包存在 SOL 资产/请求入口；存在时用 B-001 夹具制造一次刷新失败 | `http request fail` 含 Sol 特有 `method=`、非空 call_id 与 network_phase，且可关联网络失败 | P2 / L2 | 无 SOL 请求入口记 BLOCKED；规则按 B-001 复原 |

## 3. IM 连接周期与账号被踢

| # | 用例 / 变更来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-C-001 | JIM 冷启动 episode / c7b17a | force-stop、清日志、冷启，等待 IM connected | `jim_navi_config hosts=` 仅含脱敏 host；有 `jim_connect_start episode_id=im-*`；其后 connected 的 episode_id 相同且 elapsed≥0 | P0 / L1 | 证据不得含 navi token、URL path/query |
| TC-C-002 | 断网恢复 episode / c7b17a | L2 对当前 UID 精确 DROP 网络 30s 后恢复，观察 120s | 若出现新 connect_start，后续状态绑定最新且不同旧 episode；若未新建，状态保持旧 episode 且 elapsed 单调；120s 无状态记 BLOCKED/FAIL 并留日志 | P1 / L2 | 保存/精确恢复规则；不关闭整台设备网络影响其他任务 |
| TC-C-003 | 同账号多端 KICKED_OFFLINE / 7eca437、c27bf78 | L1 登录已备份测试账号并稳定；L2 使用同一账号完成登录以触发服务端互踢；两端同步抓日志/UI | 被踢端出现真实 KICKED_OFFLINE/认证失效证据并进入可恢复登录态；同一周期仅一个重验 episode；无循环弹窗、请求/IM 风暴、FATAL/ANR | P0 / L1+L2 | 用户已明确授权；不展示/记录账号秘密；若服务端允许多端而不踢，记 BLOCKED 并说明后端策略 |
| TC-C-004 | 被踢后重新登录与 IM 恢复 / 7eca437、c7b17a、c27bf78 | 承接 C-003，在被踢端使用已备份账号重新登录 | 恰一轮登录成功；首页账号正确；出现新的 IM connect episode 并 connected；旧 episode/旧世代不得继续提交 | P0 / 被踢端 | 完成后保留一个稳定登录端；不通过删数据恢复 |

## 4. 钱包、账号与登录形态

| # | 用例 / 变更来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-D-001 | 助记词/BTC 钱包登录与 JS↔Native 兼容 / 4c220c、c27bf78 + 当前 BTC androidTest | 先在 L2 执行 BaseModule `BtcDerivationCompatTest`、`BtcMessageSignCompatTest`、`BtcPsbtCapabilityTest`；再选择已备份助记词/BTC 钱包冷启登录 | androidTest 每条实际执行且 0 failure；派生/签名/PSBT 的支持与不支持矩阵符合测试 oracle；App 恰一次登录成功；BTC 类型/数量完整、无重复；无 persist/post rejected | P0 / L2 | 只记录 addressType 与计数，不记录地址/消息签名/助记词；测试向量必须是公开固定向量 |
| TC-D-002 | 私钥钱包登录 / 4c220c、c27bf78 | 切到已备份私钥钱包，独立冷启等待登录 | 恰一个 `app_login ok`；账号/链信息正确；无重验死环、stale 状态提交、FATAL/ANR | P0 / L1 | 不打开私钥详情或导出页 |
| TC-D-003 | 同钱包重复登录单飞 / c27bf78 | 使用 harness 在首轮 setWallet 在途时重复提交同 AuthInputs；另以 UI 快速重复选择作补充 | harness 确认触发≥2；`setWallet skip: same attempt in flight`≥1；窗口 `app_login ok`≤1；合并请求不得换世代/广播 LoginOut | P0 / L2 | 固定事件不含 AuthInputs 内容；UI 重复点击以新鲜 layout 定位 |
| TC-D-004 | 登录中切钱包，旧回包抑制 / c27bf78 | 用网络延迟让钱包 A 登录在途，立即切已备份钱包 B，恢复网络 | B 最终成功且 UI/持久化均为 B；A 的迟到回包无 SUCCESS/FAIL/认证写入；无 SUCCESS→FAIL 反转 | P0 / L2 | 精确恢复网络；不选择未备份/未知钱包 |
| TC-D-005 | 创建账号首次登录与重启保持 / c27bf78 | 从允许的测试环境进入创建账号流程；仅在流程不要求自动化读取/记录助记词时继续；创建后完成首次登录，force-stop 冷启 | 首次恰一轮成功；新账号以掩码 ID 显示；冷启仍为该账号且能登录/同步；无旧账号数据串写 | P1 / L1 | 用户已授权创建；若页面展示密钥且必须读取/截图才能继续，立即 BLOCKED；不删除新账号，不执行资金操作 |
| TC-D-006 | 登出后重新登录 / c27bf78 | 选择已备份账号；清日志；UI 明确执行登出，确认登出态；等待 10s；使用同账号重新登录 | 登出后旧 attempt 不得再 `app_login ok`；重新登录仅一轮成功；账号/IM/同步恢复，无 FATAL/ANR | P0 / L1 | 用户已授权登出；执行前再次确认恢复物料存在；禁止清数据代替登出 |

## 5. 后台清理、重启与业务冒烟

| # | 用例 / 变更来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-E-001 | 后台清理/冷启动实例释放 / c27bf78 | 登录在途时 Home 退后台；记录 PID；`am force-stop`（不清数据），立即冷启并等待收口 | 新 PID；旧 attempt 无 SUCCESS/FAIL/认证写入；允许 stale 诊断；新实例最终登录成功；无 FATAL/ANR | P0 / L1 | 只杀进程，不清数据/卸载；本 case 不做开头额外 reset |
| TC-E-002 | 十轮释放与登录交错 / c27bf78 | 执行“冷启→登录在途 force-stop→立即冷启→等待稳定”10 轮，每轮记录 PID/结果 | 10/10 无 FATAL/ANR；每轮最终成功或可恢复登出态；无旧回调污染、SUCCESS→FAIL、进程残留风暴 | P2 / L1 | 任一轮异常立即保存证据后停止，不用清数据自愈 |
| TC-E-003 | 四 Tab 与登录后业务冒烟 / 全范围 | 稳定登录后依次首页、消息、发现、我的；每页等待加载并只读刷新一次；前后台切换 | 四页可达且账号一致；网络/IM 日志可归因；无无限 loading、错误弹窗风暴、跨账号数据、FATAL/ANR | P1 / L1 | 不进入转账、签名、购买、SPACE 创建或账号删除 |

## 统计与放行

| 分类 | 用例数 | P0 | P1 | P2 |
|---|---:|---:|---:|---:|
| 前置门禁 | 4 | 4 | 0 | 0 |
| 账号失效/熔断 | 10 | 8 | 2 | 0 |
| HTTP | 5 | 2 | 2 | 1 |
| IM/被踢 | 4 | 3 | 1 | 0 |
| 钱包/账号 | 6 | 5 | 1 | 0 |
| 清理/冒烟 | 3 | 1 | 1 | 1 |
| **合计** | **32** | **23** | **7** | **2** |

放行要求：Phase 6 所有可执行 P0/P1/P2 为 PASS；仅有前置客观缺失且证据充分的 case 可为 BLOCKED。`SKIP`、旧结果、另一 run 的 PASS 或“代码看起来正确”均不能替代本 run 的独立执行结果。
