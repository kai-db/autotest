# Plan — 测试 debox dev 账号重验门禁 + 网络/IM 可观测性改动

> ① 计划 / 方案。当前状态以 `index.md` 为准；本文件存方案与各轮 plan-review **过程历史**（per-round VERDICT 是快照，非当前状态）。

## User Request

```text
debox-android项目下dev分支做了改动，你深度分析下，然后给我实现case然后测试
```

## Background

- **被测对象**：debox-android `dev` 分支**工作区未提交改动**（8 改 4 增 + 4 测试文件），对应 debox 侧设计文档
  `docs/superpowers/specs/2026-07-17-account-revalidation-network-observability-design.md`。
  该实现已过 debox 侧深度 review（`debox docs/implementation/2026-07-17-01-review-account-revalidation-observability/`，
  6 轮 Codex，终态 PASS_WITH_ACCEPTED_RISK，JVM 单测 15+2 全绿）。**本任务是 autotest 侧的设备级测试**：
  代码 review 与 JVM 单测已覆盖逻辑正确性，本任务补「真包真跑」的端到端证据（L12：单测过 ≠ 能跑）。
- 三块改动：
  1. **账号重验门禁** `AccountRevalidationGate` + `MainAccountCoordinator`：同一失效周期内并发
     `-2018/-2023/TOKEN_OVERDUE` 只触发一次 `revalidate_episode_start` 日志 + 一次 LoginOut 副作用；
     恢复入口三处（SwitchWallet SUCCESS+loginState==DEFAULT / TokenVerifyState.DEFAULT / 主动切钱包）。
  2. **HTTP 链路追踪** `NetworkCallTrace` + `NetEventListener` + `DeBoxHttpRequest/SolHttpRequest/DeBoxService`：
     每次真实网络尝试独立 `call_id` 经 Retrofit `@Tag` 贯通；失败时 `call_failed`（phase + dns/connect/tls/
     response_headers 耗时）与业务 `http request fail` 带同一 `call_id`；异常 message 结构化白名单脱敏
     （`类名:原因码`，17 个原因码枚举，禁落 message 原文）。
  3. **IM 连接周期观测** `ImConnectionEpisodeTracker`：`jim_connect_start`/`jim_status` 带
     `episode_id`+`elapsed_ms`；`jim_navi_config hosts=` 仅 host 不含 path/token。
- **dev 已提交增量**（上轮 2026-07-16-01 测试基线之后合入，未经设备级验证）：
  `955ced8f80` 分享卡片二维码放大、`5be1f1acc3` Web3 交易链路脱敏埋点（危险域，见 Non-goals）。
- **验证通道**：`FLogger.i/w/e` 双写 logcat + 文件（已核源码 `FLogger.java:47-59`）→ debug 包 logcat grep 即可断言。
- **注入缝**（TC-A 系列关键）：`DeBoxHttpRequest.kt:463` 业务错误时 `LiveEventBus.post(AccountTokenState(code))`
  → MainActivity:512 observer → 协调器。同 UID instrumented harness 可在真实 App 进程内精确复放该事件
  （debox `2026-07-10-06` Realm 注入 harness 先例；**禁 adb 广播钩子**，L-004 配套）。
- **mitm 路线已排除**：`network_security_config*.xml` 无 user CA trust-anchor、无 debug-overrides → 无法用
  mitmproxy 构造服务端 -2018 响应；服务端真实失效不可按需触发。
- 账本相关条目（两层已读，见 index）：L1/L2（同类全查）、L4（宣称前验证）、L12（单测过≠能跑）、
  L13/L14/L16（codex 驱动）、L17（包归因）、L18（NODE_PATH）、L-002（守卫下沉）、L-004（证据分层/
  快照兜底/同 UID harness）、L-005（BLOCKED 需探索证据，否则 NOT_RUN）；devices.md 全部⚠️条目
  （包归因铁律 / release 包假死 / 多账号风险 / 模拟器风控加白 / fake-IP 噪声）。

## Goals / Non-goals / Constraints

- Goals:
  - [ ] 为三块改动 + 分享二维码回归生成设备级用例（`docs/testing/runs/2026-07-17-账号重验与网络可观测性/cases.md`）。
  - [ ] 现场构建含工作区改动的 debug APK，完成包归因（versionName + sha256 + 新日志探针），按 P0→P2 执行。
  - [ ] 结果如实分层落 `results.md`（PASS 附日志/截图证据；不可达标 BLOCKED 附探索证据；未探索标 NOT_RUN + owner + 复测条件）。
- Non-goals:
  - **Web3 交易链路脱敏埋点（`5be1f1acc3`）实跑**：需真实转账/签名 = 资金类危险操作（dangerous-ops §二），
    且该提交属 debox 侧独立变更；本任务仅在 A3 阴性观察中顺带确认其不产生异常日志，不做交易触发。**显式延后**
    （L-003：owner=debox，触发条件=下次交易链路专项测试）。
  - 服务端真实 -2018 风暴复现（不可按需构造，门禁并发语义由 JVM 单测 + harness 注入代证）。
  - L3 真机复核（铁律#8：真机不在线不阻塞；两台真机分别为正式环境只读/被外部改装 release 包）。
  - CodePush / GeeGuard / 分享弹窗 V2 全量回归（上轮已收口，本轮仅二维码增量）。
- Constraints:
  - 铁律#7/#8 + dangerous-ops 全清单；**每轮开测核实前台账号=测试号**（5554=`2309b9ea`，5556=`10b92305`）。
  - TC-A 系列会触发 LoginOut 副作用（登出类，dangerous-ops §一）——仅允许在 **L2 测试账号 + AVD 整盘快照
    兜底 + TOKEN_OVERDUE 自动重登回收** 三条件同时满足下执行（详见「风险」）；三条件任一不满足 → 记 NOT_RUN。
  - 测试产物（harness 文件）只加不改 debox 业务代码；构建配置若临时改动，构建后 `git checkout` 还原（§7.7）。

## Plan Proposal

### v1（2026-07-17）

#### 1. 用例设计（12 条，P0×4 / P1×5 / P2×3）

**A. 账号重验门禁（harness 注入，L2 `debox_root`）**

| ID | P | 用例 | 预期 |
|---|---|---|---|
| TC-A1 | P0 | harness 并发 post `AccountTokenState(TOKEN_OVERDUE)` ×8（多线程）| logcat 恰 **1 条** `revalidate_episode_start code=-2023`；LoginOut 副作用恰 1 次（IM logout 序列 1 组）；无日志风暴 |
| TC-A2 | P0 | A1 后等待自动重登完成（`SwitchWallet SUCCESS` + loginState 恢复 DEFAULT）→ 再次注入 ×1 | 第 **2** 条 `revalidate_episode_start` 出现（门禁已重开，验 debox F1 修复真实接线——该接线正是 debox 侧 Accepted Risk #1 未被 JVM 单测覆盖的缺口） |
| TC-A3 | P1 | 正常使用 10 分钟（浏览/群聊/刷新）阴性观察 | **0 条** `revalidate_episode_start`；无 `call_failed` 风暴；成功请求无逐条阶段日志（仅受管域名 dns_end/connect_*） |

- harness 形态：debox `androidTest` 最小注入类（启动 MainActivity → 等登录态就绪 → 并发 post 事件），
  走 `am instrument` 同 UID 进程内注入，复用 `2026-07-10-06` 基建模式；测试后该文件保留在 debox 工作区
  （与被测改动同批，由 debox 侧决定去留）。
- 执行序约束：A 系列排在全部无损用例之后（最后执行）；注入前打 AVD snapshot `pre-revalidation-inject`，
  异常不可恢复即回滚。

**B. HTTP 链路追踪（L1 `Pixel_10_Pro_XL` 为主）**

| ID | P | 用例 | 预期 |
|---|---|---|---|
| TC-B1 | P0 | 断网（`svc wifi disable` + data off）→ App 内触发刷新 | `call_failed` 含 `call_id=http-…` + `phase∈{dns,connect,call_start}` + 耗时字段；同一失败的业务 `http request fail` 带**同一** `call_id`（一一关联） |
| TC-B2 | P0 | B1 日志脱敏断言 | `err=` 形如 `UnknownHostException:dns_unresolved`（白名单枚举）；全量新增日志 **0** 次出现 URL path/query/token/message 原文 |
| TC-B3 | P1 | 恢复网络后正常请求 + 一次业务错误（如访问需登录态的接口） | 网络成功路径无 `call_failed`；业务失败 `http request fail` 的 `network_phase∈{response_body_complete,complete}`（与网络失败可区分） |
| TC-B4 | P1 | L2 DNS 黑洞主域名（DNAT :53 方案，devices.md 已实证）触发域名自愈重发 | 重发尝试产生**新** `call_id`；最终 `http request fail` 的 `call_id` = 最后一次尝试；`domain_retried=true` |

**C. IM 连接周期（L1）**

| ID | P | 用例 | 预期 |
|---|---|---|---|
| TC-C1 | P0 | 冷启动 App | `jim_navi_config hosts=`仅 host 列表（无 path/token）；`jim_connect_start episode_id=im-…`；后续 `jim_status connected episode_id=<同值> elapsed_ms=<≥0>` |
| TC-C2 | P1 | 断网 ≥30s → 恢复 → IM 重连 | 新 `jim_status` 序列 episode_id 与冷启动不同（若 SDK 重新 connectWithToken）或同周期 elapsed 递增；disconnected/connecting/connected 均带 episode 字段 |

**D. 回归增量（L1）**

| ID | P | 用例 | 预期 |
|---|---|---|---|
| TC-D1 | P2 | 群聊发起分享 → 查看分享卡片二维码 | 二维码明显大于旧版基线（对照上轮 run 截图）、白边收窄、可正常渲染；分享流程无回归 |
| TC-D2 | P2 | 冷启动 + 首页浏览冒烟（版本号 `5845b982` 后包） | 无 crash / ANR / 新增 ERROR 风暴 |
| TC-D3 | P2 | B1 断网场景下 Sol 链路（钱包页刷新资产触发 Sol RPC） | Sol `http request fail` 也带 `call_id`/`network_phase`（SolHttpRequest 接线验证） |

#### 2. 执行步骤

1. **Phase 1 环境**：`adb devices` 确认两台 AVD 在线（不在线则启动）；身份核验（「我的」页 = 测试号）；
   屏幕保活；网络噪声预检（fake-IP 检查，§7.3）。
2. **构建与归因**：debox 工作区现场构建 debug APK（`NODE_PATH=$PWD/ReactNative/node_modules ./gradlew :app:assembleDebug`，
   L18）；装两台 AVD；归因三件套 = `versionName` + APK sha256 + **运行时探针 = logcat `jim_connect_start episode_id=`
   字段存在**（旧包无 episode_id 字段，天然区分探针）+ `run-as` 确认 debuggable。
3. **执行序**：L1 无损用例（C1→B1→B2→B3→D1→D2→D3→C2→A3）→ L2 注入用例（B4 → 快照 → A1→A2）。
   每条：重置到干净前台态 → 执行 → logcat/截图取证 → 记录。
4. **判定纪律**：PASS 附日志行/截图路径；入口不可达需探索证据才记 BLOCKED，未探索记 NOT_RUN（L-005）；
   模拟器网络层结论标【模拟器实测，待真机复核】（devices.md 噪声条目）。
5. **结果回写**：`runs/2026-07-17-账号重验与网络可观测性/{cases.md,results.md}`；FAIL 项先记录后修
   （修复另起 agent-dev-loop 任务，铁律#5）。

#### 3. 验证方式（本任务自身的完成判定）

- cases.md 覆盖上表全部用例且含前置/步骤/预期/危险操作标注；
- results.md 每条用例有终态（PASS/FAIL/BLOCKED/NOT_RUN）+ 证据路径；
- 归因三件套记录在 results.md 环境节；
- L2 注入后复原确认（iptables 清空、快照策略执行记录）。

#### 4. 风险与缓解

| 风险 | 缓解 |
|---|---|
| TC-A1/A2 触发 LoginOut（dangerous-ops §一「登出」） | ①仅 L2 测试账号（5554 前台核验=`2309b9ea`）；②注入前 AVD 整盘快照，失败即回滚（L-004 事务式兜底先例）；③选 TOKEN_OVERDUE 码 → 本地钱包自动 setWallet→login() 重登回收，服务端 token 本身未失效，登录必然可恢复；④排全部用例最后。三条件任一不成立 → NOT_RUN（owner=Kai，复测条件=条件补齐） |
| 5554 存在真实账号 Kai/赵长鸟（2026-07-11 事故） | 开测/注入前后各核验一次前台=测试号；不进「切换账号」弹层 |
| 模拟器风控（-2051）干扰登录类用例 | 两台已加白（07-14）；若复现 → 按 devices.md 鉴别法区分风控 vs 后端故障，不误判 |
| harness 并发 post 与主线程 observer 时序（LiveEventBus 粘性/主线程投递可能天然串行化，测不出「并发」） | 门禁在 post 前于**调用线程**同步 `tryEnter()`（代码已证：`if (!revalidationGate.tryEnter()) return` 在 runOnUiThread 之前）→ 多线程直接调 `handleAccountTokenState` 即可真并发；harness 直接持有 coordinator 不可行时退化为 LiveEventBus 高频连发 ×8，仍能验「一周期一次」语义 |
| 工作区未提交代码被并发会话改动 | 开工记录 `git diff --stat` 快照哈希；执行中不改 debox 业务代码 |
| L1 fake-IP 代理噪声影响 B 系列 DNS 结论 | 预检；DNS 语义类断言（dns_unresolved 等）在 L2 注入环境复核 |

#### 5. 需要用户确认

- 无阻塞项。TC-A1/A2 的登出类操作已按「用例明确要求 + 测试账号 + 快照兜底」三条件设计，
  由 plan-review 审核该安排是否充分；若 Codex 判需人工危险操作确认，则 A1/A2 转 NOT_RUN 并在收口时向用户申请。

### v2（2026-07-17，按 plan-review R1 修订；只写与 v1 的差异）

**Δ1（Critical-99）A1/A2 执行门改为「用户危险操作确认」，本轮默认 NOT_RUN**：
- dangerous-ops §一无例外流程 → **A1/A2 不自动执行**。本轮产出 = harness 代码 + 用例文档「就绪态」；
  执行门 = 用户（规则 owner）显式确认（TEST_GUIDE §7.6 白名单第 3 项「危险操作确认」正是此类人工介入）。
  未确认 → results.md 记 **NOT_RUN**（owner=用户 Kai；复测条件=①用户确认授权 ②androidTest APK 构建通道可用，
  见 Δ8）。收口汇报时向用户申请确认，确认后另轮执行。
- 连带：执行步骤 3 的执行序去掉 A1/A2（保留 B4 的 L2 注入，不涉登录态）。

**Δ2（Important-89）A1 语义更名 + 注入路径落实**：
- 更名 TC-A1 =「**失效周期高频重复抑制**」：harness 在**后台线程串行** post `AccountTokenState(TOKEN_OVERDUE)` ×8
  （间隔 <50ms），预期恰 1 条 `revalidate_episode_start` + 1 组 LoginOut 副作用。**不宣称覆盖并发互斥**——
  并发 CAS 语义已由 debox JVM 单测（AccountRevalidationGateTest 多线程用例）覆盖，设备级验证的是
  「事件路径贯通 + 一周期一次副作用」。
- 注入路径固定为 LiveEventBus（`LiveEventBus.get(AccountTokenState::class.simpleName, AccountTokenState::class.java).post(...)`，
  与 DeBoxHttpRequest.kt:463 发布方完全同键同类型）；**不反射获取 coordinator**（私有字段脆性高）。

**Δ3（Important-88 恢复入口）三处 reset 覆盖矩阵**：
- `SwitchWallet SUCCESS+DEFAULT` 收口 → TC-A2（保留，oracle 见 Δ7）。
- `TokenVerifyState.DEFAULT` → **新增 TC-A4**（P1，harness post `TokenVerifyState(DEFAULT)` → 门禁重开 →
  再注入出现第 2 条 episode；同属执行门 Δ1 管控）。
- 主动切钱包 reset → **Accepted Risk 提名**：owner=Kai；reason=「切换账号」属 dangerous-ops §一
  （5554 存真实账号，自动化切换风险不可接受）；follow-up=用户人工手测一次（切到测试号自身或另一测试号）；
  expiry=下次触碰 MainAccountCoordinator 账号分支时复审。
- TC-A 系列编号语义在 cases.md 保持一致：A1 抑制 / A2 SUCCESS 收口重开 / A3 阴性 / A4 TokenVerify 重开。

**Δ4（Important-88 B3 入口）确定性业务错误触发**：
- 主入口 = **语音房 info 查询失败**：进入一个已关闭/不存在的语音房（devices.md 已实证该接口对无效房间返
  `-2213 DB系统繁忙`——07-11 跨环境联测记录）。触发法：房间列表进入后立即由房主关闭的房间/历史房间入口，
  或深链失效房间 id。预期：`http request fail` 带 `code=-2213`（或该接口实测业务码）+ `call_id≠空` +
  `network_phase∈{response_body_complete,complete}` + **同窗口无该 call_id 的 `call_failed`**。
- 探索纪律（L-005）：若该入口 15 分钟内探索不可达 → 退而求其次用**任一实测返回非 0 业务码的只读接口**
  （记录实际入口+码）；两者均失败 → BLOCKED 附探索证据。

**Δ5（Important-86 B2 oracle）脱敏审计窗口与正则**：
- 采集窗口：B1 执行前 `logcat -c` 清缓冲，断网触发后 dump 至恢复网络前 → 该窗口内**全部**新增日志行为审计集。
- 审计正则（对审计集全行扫描，0 命中为 PASS）：
  - 禁止模式：`https?://[^ ]*/[^ ]|[?&][a-zA-Z_]+=|token=|Authorization|Bearer |/api/|/v[12]/`
  - `err=` 字段白名单：每条 `call_failed`/`connect_failed` 的 err 必须整体匹配 `err=[A-Za-z.]+:[a-z_]+$`
    且原因码 ∈ REASON_RULES 17 枚举 ∪ {other}。
- 局限声明：无法注入 canary（不改业务代码）→ 本用例证明「实测窗口无泄漏」，泄漏完备性由 debox 侧
  NetEventListenerSanitizeTest 6 项枚举回归代证；两层证据分层记录（L-004）。

**Δ6（Important-86 C2 oracle）以 `jim_connect_start` 出现与否分叉的唯一判据**：
- 恢复网络后观察 120s：
  - 若窗口内**出现**新 `jim_connect_start`（App 层重新 connectWithToken）→ 其后全部 `jim_status` 的
    episode_id **必须等于**最新 jim_connect_start 的 episode_id（且 ≠ 冷启动值）。
  - 若**未出现**（SDK 内部重连）→ 全部 `jim_status` 的 episode_id **必须等于**冷启动 episode_id，
    且 elapsed_ms 单调不减。
  - 两分支各自唯一，无宽松备选；断网后 120s 内无任何 `jim_status` 变化 → BLOCKED（附 logcat 证据），
    不判 PASS/FAIL。

**Δ7（Important-85 A2 恢复探针）**（随 Δ1 同门控）：
- harness 内探针：注入后轮询 `AppCacheManager.get().getWallet()?.loginState`（同进程可读），
  **最大等待 60s**；恢复 `DEFAULT` → 第二次注入并断言第 2 条 episode；超时未恢复 → 用例判
  **BLOCKED（环境/登录问题）**而非 FAIL，且**不执行**第二次注入；同时以 logcat `jim_connect_start`
  （重登触发 IM 重连）为旁证。

**Δ8（Important-88 还原策略）零构建配置改动 + local.properties 精确恢复**：
- 主包 `:app:assembleDebug` 不改任何配置。
- harness 编入需 `local.properties` `autotest.enabled=true`（untracked 文件，git 不管理）：改前记录
  该行原值（或不存在），改后仅追加/修改该单行；恢复 = 按记录写回原值并 `grep` 核验；**全程禁用
  `git checkout`/`git restore`**。
- 新增风险：`assembleAppDebugAndroidTest` CLI 曾被 RN bundle 任务图阻断（debox `2026-07-10-06` 移交用户 AS）
  → 构建失败即列入 A 系列 NOT_RUN 复测条件②，不在本轮强攻。

### v3（2026-07-17，按 plan-review R2 修订；只写与 v2 的差异）

**Δ9（Critical-99）A 系列注入用例（A1/A2/A4）本任务固定 NOT_RUN，规则变更是唯一解锁前置**：
- dangerous-ops §一现行文本无任何例外条款 → 本任务**不执行、不构建 harness、不改 debox 工作区**。
  cases.md 中 A1/A2/A4 保留完整设计（含 oracle），状态固定 NOT_RUN：owner=Kai（规则 owner）；
  复测条件=①规则 owner 正式修订 `dangerous-ops.md`（增加「受控登出测试例外流程」条款并落文档：
  限定测试账号+AVD 快照兜底+逐次显式授权）②androidTest 构建通道可用；expiry=规则修订后 30 天内执行或作废。
- 收口汇报向用户**提议**规则修订（提供例外条款草案），但修订与否由用户决定；未修订前任何轮次都不执行。
- v2 Δ7/Δ8 的 harness 探针与 local.properties 操作降级为「未来执行轮的设计存档」，本轮零 debox 工作区写入。

**Δ10（Important-88×2）A2/A4 归因唯一性 → 登记完整 Accepted Risk + debox 侧改进建议**：
- 设备级黑盒无法唯一归因门禁 reset 来源（SUCCESS 收口 / TokenVerify DEFAULT / 切钱包三入口共享同一
  可观测结果「下一周期可进入」）→ 即便未来执行 A2/A4，也只能证明「恢复后门禁重开」总体语义。
- **Accepted Risk（随 NOT_RUN 一并登记）**：`SwitchWallet.SUCCESS→reset` 与 `TokenVerifyState.DEFAULT→reset`
  接线的**分路归因**无设备级证据。owner=Kai；reason=reset 无来源日志，黑盒不可分路归因（debox 侧
  Accepted Risk #1 同源）；follow-up=建议 debox 在三个 reset 入口各加一行脱敏日志
  `FLogger.i("Login","revalidation_gate_reset source=<switch_wallet_success|token_verify_default|manual_switch>")`
  （纯枚举无敏感字段），落地后 A2/A4 可按 source 分路断言；expiry=下次触碰 MainAccountCoordinator 账号分支或 2026-09-30。
- 主动切钱包 reset 的 Accepted Risk（v2 Δ3）并入本条（同 owner/expiry；follow-up=规则例外落地后用户人工手测一次）。

**Δ11（Important-84）A1 LoginOut 副作用计数规则（未来执行轮用，写入 cases.md）**：
- 主计数：审计窗口内 `revalidate_episode_start` 行数 **==1**（门禁入口一次仅一行，规则确定）。
- 副作用组计数：以 `jim_connect_start`（episode_id 变化）为组边界——注入后窗口内
  「`jim_status disconnected` → 后续首个 `jim_connect_start`」序列组数 **==1**；SDK 自身重试产生的多条
  `connecting/disconnected` 状态行**不开新组**（无新 `jim_connect_start` 即同组）。
- 旁证：`WalletValidCloseDialog`/`LoginOut` 无独立日志 → 不作计数依据，仅以 IM 组数为唯一副作用口径并在
  结果中声明该口径边界。

**Δ12（Important-82）D3 前置条件 + 请求发起探针 + BLOCKED 分流**：
- 前置：断网前在钱包页确认测试钱包存在 **SOL 资产行**（截图为证；无 SOL 账户 → 直接 BLOCKED，
  证据=钱包页截图，复测条件=测试号补 Sol 账户）。
- 判定探针：断网窗口 logcat 出现带 `method=` 字段的 `http request fail`（Sol 日志独有 method 字段，
  与 DeBox 主链路 `path=` 区分）→ 才进入断言（`call_id≠空` + `network_phase` 有值）。
- 分流：窗口内无任何 `method=` 失败日志（Sol 请求未发起，缓存/页面状态原因）→ **BLOCKED** 附
  logcat 空结果 + 操作序列截图，不判 FAIL。

### v4（2026-07-17，按 plan-review R3 修订；cap 末轮，5 条 Important 修法逐字采纳）

**Δ13（88）A1 副作用 oracle 收缩 + Accepted Risk**：
- A1 主 oracle 只保留可确定断言：审计窗口 `revalidate_episode_start` 行数 **==1**。
- 「LoginOut 调用恰一次」**登记 Accepted Risk**：现有生产日志无唯一对应 LoginOut 调用的事件，IM 序列只能作旁证
  （在结果中标注为旁证，不作判定依据）。owner=Kai；reason=无直接计数信号；follow-up=并入 Δ10 的 debox 侧
  脱敏日志建议（增加 `login_out_published` 一行，与 reset 来源日志同批）；expiry=同 Δ10。

**Δ14（87）HTTP 失败阶段覆盖矩阵**：
- 新增 **TC-B5**（P1，L2）：iptables 构造 connect 阶段失败（仅改模拟器内路由，复原=`iptables -t nat -F`）：
  - `REJECT --reject-with tcp-reset`（或 DNAT 到 10.0.2.2 未监听端口）→ 预期 `phase=connect` + `err=*:connect_refused`；
  - `DROP` → 预期 `phase=connect` + `err=*:timeout`，`connect_ms` 无值、`elapsed_ms`≈超时配置。
- 覆盖矩阵终态：dns（B1/B4）/ connect refused / connect timeout（B5）设备级覆盖；
  **TLS 与 response_headers/response_body 阶段失败不可安全构造**（需伪造 TLS 端点或服务端配合半途断流）
  → 登记 Accepted Risk：owner=Kai；reason=设备黑盒无安全构造手段，phase 推进逻辑已由 debox JVM 单测
  （NetworkCallTraceTest 4 项）覆盖；follow-up=下次若搭建本地 mock TLS 服务专项轮补齐；expiry=2026-09-30。

**Δ15（85）B3 夹具固化**：
- 主夹具 = **固定虚构房间 ID** 的语音房 info 查询（不依赖实时房间状态的只读夹具）。执行时探索一次入口
  （深链 `debox://` 系或历史房间列表），把**实测可用的触发方式 + 实测业务错误码**回写 cases.md 固化为长期夹具；
  错误码集合以实测为准登记（预期为「房间不存在/已关闭」类非 0 码，-2213 只作参考不作硬预期）。
- 探索失败（30 分钟内无任何稳定入口）→ 登记环境依赖 Accepted Risk：owner=Kai；reason=App 无稳定只读业务错误
  夹具；follow-up=请 debox 提供测试夹具（固定返回业务错误的只读入口）；expiry=2026-09-30。B3 记 BLOCKED 附探索证据。

**Δ16（83）B2 审计集与字段提取算法**：
- 审计集构造：`adb logcat -c` 于 T0 → 操作 → T1 `adb logcat -d -v epoch` 全量导出；过滤 ①PID=App 进程
  （`pidof com.tm.security.wallet`）②tag 前缀 ∈ 实测的 FLogger tag 形态（执行时以 `dns_end`/`call_failed`/
  `http request fail` 实际行确认 tag 集，先验证后过滤，L4）。
- err 断言算法：对审计集中每条 `call_failed`/`connect_failed` 行，先以 `err=([^ ]+)` **提取字段值**，
  再对字段值做**完整匹配** `^[A-Za-z0-9.$]+:[a-z_]+$` 且冒号后原因码 ∈ REASON_RULES 17 枚举 ∪ {other}；
  不假定 err 在行尾。
- 禁止模式扫描：对审计集全部行（含字段值内部）跑 Δ5 禁止正则，0 命中 PASS；命中先人工核对是否存量格式
  （如 dns_end 的 ips 数组）再判。

**Δ17（82）D1 量化 oracle**：
- 从 `955ced8f80` 代码 diff 读出二维码尺寸参数变化（执行时 `git show` 提取具体 dp/px 值）→ 换算预期比例。
- 判定：截图测量二维码外接矩形宽度 / 卡片内容宽度比值，与代码预期一致（±10%）；白边宽度/二维码边长比值
  与代码预期一致（±10%）；上轮 run 同类型（GROUP 卡片）截图存在时补基线对比（比值需严格增大），
  不存在则以代码换算值为唯一 oracle 并在结果中记录测量数据。

**cap 超限决策记录**：plan-review 达 3 轮上限仍 FAIL，但末轮 **0 Critical**、5 条 Important 均附确定修法且
本方无异议 → 按先例（debox `2026-07-17-01` Round 2、autotest `2026-07-16-01` v4）逐字采纳后直接进入实施，
由 impl-review 独立终审；autonomous session 无法即时等待用户，**收口汇报向用户如实披露本决策**。

## Plan Review Log（per-round，含 VERDICT 快照）


### plan-review Round 1 — 2026-07-17（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
- [Standards/规范] [99] TC-A1/A2 明确触发 `LoginOut`，违反 dangerous-ops.md §一“任何情况都禁止”；测试账号、快照和自动重登只能降低损失，不能构成项目硬规则的例外，且方案将危险操作确认后置给 reviewer -> 将 A1/A2 标记为 NOT_RUN；只有规则 owner 正式修改危险操作政策后才能执行

### Important (warn)
- [Design/架构] [89] A1 的既定 harness 通过 LiveEventBus 注入，但 observer 很可能在主线程串行调用 coordinator，无法证明 `tryEnter()` 的并发互斥；“直接持有 coordinator”仅作为未落实的条件分支 -> 明确可编译的同进程调用入口及 coordinator 获取方式，否则将用例改称“高频串行重复抑制”，不得宣称覆盖并发
- [Test coverage/测试覆盖] [88] 门禁有三处恢复入口，方案只验证 `SwitchWallet SUCCESS + DEFAULT`，未覆盖 `TokenVerifyState.DEFAULT` 和主动切钱包后的 reset -> 补充设备用例，或分别记录 owner/reason/follow-up/expiry 后作为 Accepted Risk
- [Test coverage/测试覆盖] [88] TC-B3 的“如访问需登录态的接口”没有确定入口、错误码或触发方法，在当前有效登录态下未必产生业务错误，因此判定不可重复执行 -> 指定稳定接口、确定触发条件和预期错误，或设计不改变业务代码的确定性注入
- [Code quality/代码质量] [86] TC-B2 要求“全量新增日志 0 次出现 URL path/query/token/message 原文”，但没有限定采集窗口、日志来源、敏感模式及如何识别未知原文，容易误报或假通过 -> 定义日志 tag/前缀、起止时间、禁止字段正则，并使用可识别的 canary path/query/token 验证泄漏
- [Design/架构] [86] TC-C2 同时允许“新 episode”或“沿用旧 episode”，使 episode 生命周期实现错误也可能 PASS -> 根据现有 `connectWithToken` 调用路径，为每种实际发生的重连路径规定唯一 oracle；未发生目标调用时应 BLOCKED，而非使用宽松备选预期
- [Regression risk/回归] [88] 临时构建配置通过 `git checkout` 还原，在明确存在未提交改动且可能有并发会话的工作区中可能覆盖用户改动；执行前的 diff 哈希不能防止还原误伤 -> 禁止使用整文件 checkout，要求构建不改配置，或仅精确反向撤销本任务自己记录的补丁并核验前后内容
- [Bugs/逻辑/语法] [85] TC-A2 以“服务端 token 未失效，所以登录必然恢复”为前提，但方案未定义自动重登成功的唯一信号、超时和失败分流；重登未完成时再次注入会把环境问题误判为 gate reset 失败 -> 指定登录恢复探针、最大等待时间及 BLOCKED/FAIL 分界，并在第二次注入前验证 `loginState==DEFAULT`

### Questions
- dangerous-ops.md §一是否存在由规则 owner 批准并落文档的例外流程？若不存在，A1/A2 必须保持 NOT_RUN。
- `MainAccountCoordinator.handleAccountTokenState` 是否能从 androidTest 可靠取得实例并直接调用？若不能，A1 只能验证串行重复抑制。
- TC-B3 是否已有稳定、无副作用且能确定返回业务错误的接口和操作入口？

### Required Changes
1. 移除 A1/A2 的自动执行安排，按 NOT_RUN 记录 owner 和复测条件；不得以 plan-review 代替危险操作授权。
2. 明确 harness 的进程、线程、实例获取和调用路径，并使 A1 名称与实际可验证语义一致。
3. 补齐另外两处 gate reset 的覆盖或登记完整 Accepted Risk 元数据。
4. 为 B2、B3、C2 和 A2 写出确定、可重复且不会宽松放行的 oracle。
5. 删除 `git checkout` 还原策略，改为不会覆盖既有或并发工作区改动的精确恢复方案。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/.adl/review-artifacts/2026-07-17-01-test-debox-account-revalidation-observability/codex-plan-review-R1.txt

### plan-review Round 2 — 2026-07-17（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
- [Standards/规范] [99] v2 仍将用户确认视为执行 TC-A1/A2/A4 的授权入口，但 dangerous-ops.md §一规定登出“任何情况都禁止”，现有规则并未提供人工确认例外；用户确认不能替代规则变更 -> 本方案内保持 NOT_RUN，只有规则 owner 先正式修改并落文档例外流程后才能执行

### Important (warn)
- [Test coverage/测试覆盖] [88] TC-A2 仅观察 `loginState==DEFAULT`、IM 重连及第二条 episode，不能证明门禁确由 `SwitchWallet.SUCCESS` 分支重开；期间 `TokenVerifyState.DEFAULT` 等其他 reset 路径也可能执行，无法补证 Accepted Risk #1 -> 增加可唯一证明 SUCCESS observer 已执行的探针，或将该接线继续登记为 Accepted Risk
- [Test coverage/测试覆盖] [88] TC-A4 在自动重登流程之后 post `TokenVerifyState.DEFAULT`，门禁可能已被 `SwitchWallet.SUCCESS` 提前 reset，第二次 episode 无法归因于 TokenVerify 分支 -> 设计隔离其他 reset 入口的确定性前置状态与 oracle，否则登记完整 Accepted Risk
- [Design/架构] [84] TC-A1 以“一组 IM logout 序列”作为 LoginOut 恰一次的 oracle，但未定义序列起止标志、唯一计数事件及 SDK 自身重试如何排除，判定可能把一次副作用的多条状态回调误认为多次登出 -> 指定稳定的一次性 LoginOut 调用旁证或明确可执行的日志序列计数规则
- [Test coverage/测试覆盖] [82] TC-D3 的“钱包页刷新资产触发 Sol RPC”未保证一定发起 Sol 请求，缓存、钱包类型或页面状态均可能令目标链路未执行，且未规定未观察到 Sol 请求时的分流 -> 固定支持 Sol 的测试钱包、清晰入口和请求已发起探针；目标请求未发生时按探索证据记 BLOCKED

### Questions
- dangerous-ops.md §一的规则 owner 是否愿意先修改规则，明确允许在测试账号、快照和显式确认条件下执行登出类测试？
- 现有日志或测试探针中，是否存在能够唯一确认 `SwitchWallet.SUCCESS` observer、`TokenVerifyState.DEFAULT` observer 和 LoginOut 调用次数的信号？

### Required Changes
1. 删除“用户确认即可执行”这一门槛；在危险操作规则正式变更前，将 A1/A2/A4 固定为 NOT_RUN。
2. 为 A2、A4 分别设计可隔离其他 reset 入口的唯一 oracle；无法隔离的接线缺口登记完整 owner/reason/follow-up/expiry。
3. 明确 A1 LoginOut 副作用的可执行计数规则。
4. 为 D3 补充确定的 Sol 请求前置条件、请求发起探针及 BLOCKED 分流。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/.adl/review-artifacts/2026-07-17-01-test-debox-account-revalidation-observability/codex-plan-review-R2.txt

### plan-review Round 3 — 2026-07-17（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
None.

### Important (warn)
- [Design/架构] [88] TC-A1 仍以 IM 重连序列组数推断 `LoginOut` 副作用次数，但 `jim_connect_start` 既不是 LoginOut 调用信号，也可能受 SDK 重连和登录恢复路径影响，无法证明 LoginOut“恰一次” -> 将副作用次数明确登记为 Accepted Risk，或增加能直接观测 LoginOut 调用次数的脱敏日志/测试探针
- [Test coverage/测试覆盖] [87] HTTP 失败测试集中在断网/DNS 阶段，没有覆盖 connect、TLS、response_headers 等主要失败阶段及对应耗时字段，无法设备级验证 `NetEventListener` 的完整阶段归因面 -> 补充可控的连接拒绝、连接超时等安全场景；不可构造的 TLS/响应头失败应记录 owner/reason/follow-up/expiry
- [Test coverage/测试覆盖] [85] TC-B3 依赖“房主恰好关闭房间”、历史入口或临时探索任意错误接口，入口和业务码仍不稳定，可能随服务端数据消失，不能作为可重复用例 -> 固定长期保留的无效房间 ID/只读测试夹具及允许的错误码集合，或把该项按完整元数据登记为环境依赖风险
- [Code quality/代码质量] [83] TC-B2 没有精确定义“新增日志行为审计集”的筛选方式，且 `err=...$` 假定 err 位于整行末尾；若日志后面还有字段会误判，混入历史或其他模块日志也会产生噪声 -> 明确 tag、消息前缀、PID和时间窗口，并先提取 `err` 字段值再做完整匹配
- [Test coverage/测试覆盖] [82] TC-D1 使用“明显大于”“白边收窄”作为视觉 oracle，无法稳定复核或支持 PASS/FAIL 判定 -> 给出基线截图、相同设备与页面状态，并定义二维码外接矩形尺寸及白边像素比例的量化阈值

### Questions
- 现有生产日志中是否有能直接且唯一对应一次 `LoginOut` 调用的事件？若没有，是否接受该次数断言只能作为旁证？
- L2 是否可以通过路由到拒绝端口或不可达地址安全构造 connect refused/timeout，而不修改服务端或业务代码？
- 是否存在长期稳定的只读测试夹具，可确定返回非零业务码并避免依赖实时房间状态？

### Required Changes
1. 修正 TC-A1 的副作用 oracle；无法直接计数 LoginOut 时登记完整 Accepted Risk。
2. 补充 HTTP connect/TLS/response_headers 阶段覆盖矩阵，无法设备构造的阶段分别登记完整 Accepted Risk。
3. 为 TC-B3 固定可重复的只读业务错误夹具，或登记环境依赖风险。
4. 明确 TC-B2 的日志审计集筛选和字段提取算法。
5. 将 TC-D1 的视觉预期改为可量化、可复核的判定标准。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/.adl/review-artifacts/2026-07-17-01-test-debox-account-revalidation-observability/codex-plan-review-R3.txt
## Plan Revision Log

> 每次按 review 修订方案时追加一行：vN · 针对哪条 finding · 改了什么。

- v1 · 初版。
- v4 · 针对 R3 全部 findings（cap 末轮逐字采纳）：88 → A1 oracle 收缩为 episode 行数、LoginOut 次数记 Accepted Risk；87 → 新增 TC-B5 connect refused/timeout（L2 iptables）+ TLS/响应头阶段 Accepted Risk；85 → B3 固定虚构房间 ID 夹具+实测回写固化+探索失败分流；83 → B2 审计集 PID/tag 过滤+err 字段先提取后完整匹配；82 → D1 代码尺寸换算量化 oracle（±10%）。
- v3 · 针对 R2 全部 findings：Critical-99 → A1/A2/A4 固定 NOT_RUN（规则修订为唯一前置，本轮零 debox 写入，收口时仅提议规则修订草案）；88×2 → A2/A4 分路归因不可行登记完整 Accepted Risk + debox 加 reset 来源日志建议；84 → A1 副作用计数规则（episode 行数 + jim_connect_start 组边界）；82 → D3 SOL 资产行前置 + method= 探针 + BLOCKED 分流。
- v2 · 针对 R1 全部 findings：Critical-99 → A1/A2 执行门=用户危险操作确认（本轮 NOT_RUN）；89 → A1 更名高频重复抑制+固定 LiveEventBus 注入；88(恢复入口) → 新增 TC-A4 + 切钱包 reset 记 Accepted Risk 提名；88(B3) → 固定 -2213 语音房错误入口+探索纪律；86(B2) → 审计窗口+禁止/白名单正则+canary 局限声明；86(C2) → jim_connect_start 分叉唯一 oracle；88(还原) → 零配置改动+local.properties 单行精确恢复、禁 git checkout；85(A2) → loginState 探针+60s 超时+BLOCKED 分流。

## Accepted Plan

- 基线 = **Proposal v1 + Δ1~Δ17（v2/v3/v4 全部差异）**。要点速查：
  - 执行集：L1 → C1、B1、B2、B3、D1、D2、D3、C2、A3；L2 → B4、B5。
  - 固定 NOT_RUN：A1/A2/A4（dangerous-ops §一规则未修订；规则修订草案随收口汇报提交用户）。
  - Accepted Risks（详见 Δ10/Δ13/Δ14/Δ15，owner/reason/follow-up/expiry 四要素齐）：①gate reset 分路归因
    ②LoginOut 调用次数无直接信号 ③TLS/响应头/响应体阶段失败不可构造 ④B3 夹具环境依赖（仅探索失败时激活）。
- Accepted at / Decision: 2026-07-17 · **cap 末轮无 Critical，5 Important 修法逐字采纳后进入实施**
  （超限决策见 v4 末节；非正式 PASS，由 impl-review 终审把关，收口向用户披露）。
- Deviation Policy: 改变执行集范围、危险操作边界、Accepted Risk 内容的偏离，必须先 consult Codex；
  用例内探索性微调（入口路径、tag 实测形态）按 Δ15/Δ16 授权就地回写 cases.md。
