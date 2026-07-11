# Plan — debox dev 07ae7655..HEAD 深度分析 + 深度回归测试

> ① 计划 / 方案。当前状态以 `index.md` 为准；本文件存方案与各轮 plan-review **过程历史**（per-round VERDICT 是快照，非当前状态）。

## User Request

```text
debox-android 项目 dev 分支这次 07ae7655c681556ec050f6280d8ab37b1402a18a 提交的包含之后的深度分析下，
我需要结合现在 autotest 项目深度测试下，保证没有问题
```

## Background

- **目标提交范围**（`07ae7655~1..dev` = 8960bb29，5 提交，3 个代码提交）：
  1. `07ae7655` fix(im)：治理主线程同步 JetIM DB 读的四个 ANR 入口 + 消息上传空路径 NPE（P1' clearUnreadCount diskIO 化、P2' LocalAttrCache 缓存双轨+generation/epoch/suspend 闸门、P3' searchRecentContacts 下沉 IO、P4' 置顶/免打扰状态经 DbAsyncBridge、P5' 上传空路径显式失败；新增 ImSessionEpoch）
  2. `fc77707a` feat(base)：PictureSelector 相册 ANR 观测埋点（FLogger tag=PicSel 三段计时）
  3. `75d44b39` fix(wallet)：Realm 加密库多候选 key 自动恢复（PRIMARY_RETRY/PLAIN_BACKUP 双候选、probe 直发布、回填+明文备份清理、失败地板 fail-closed）
- **debox 侧挂账的设备验证缺口**（本任务的核心闭合对象）：
  - Realm 恢复真机注入验证 = **2.14.3 放量硬门禁**（debox `2026-07-10-03` 档案 v5-3 清单，未执行不得放量）
  - ANR 修复「无真机端到端冒烟（CLI 会话）」Accepted Risk（debox `2026-07-10-02` Round 3）
  - PicSel 埋点 `:app` 无编译证据 + 行为未验证（debox `2026-07-10-04`）
- **设备现状**（2026-07-10 实测）：L2 `debox_root`(emulator-5554, root, 装 2.14.1/21400001)、L1 `Pixel_10_Pro_XL`(emulator-5556, 2.14.1)、L3 三星 S25（测试环境, 2.14.2/21400002 装于 15:15）、L3 小米（正式环境, 2.14.2 装于 14:21）。**真机 2.14.2 安装时间早于目标提交时间（21:41），大概率不含本批修复**。
- 账本相关条目（两层已读）：L4（声称前先验证）、L12（mock 过≠能跑，真依赖要 e2e）、L13/L14/L16（codex 驱动姿势）、L17（版本归因，崩栈对不上=旧包）、L18（debox CLI 编译 NODE_PATH）；项目 L-001（修复必须走 agent-dev-loop）、L-002（安全不变量下沉代码守卫）、L-003（提案项要么实施要么显式延后）。
- 独立核码已完成部分：Realm 4 文件 diff、PicSel 2 文件 diff 已亲核（与档案一致；备份 key 常量 `leek_box_name_lt_backup` 与 put fallback 写入一致已验证）；IM 大 diff 由只读 agent 独立核查（结论见 implementation.md「独立核码」节）。

## Goals / Non-goals / Constraints

- Goals【v3 诚实修正：真机 L3 全部锁屏+真资产+装 2.14.2 旧包 → 本任务不在真机做注入/装新包，故不「关闭」debox 真机门禁，只贡献模拟器层证据】:
  - [ ] G1 对 3 个代码提交产出**独立核码分析**（不信档案、验代码；发现代码级问题按 Critical/Important 记录）
  - [ ] G2 **Realm 恢复逻辑在 L2 模拟器测试钱包上的注入验证**（正确候选→自动恢复；错误候选→受控退出+旧库字节不变）= **debox 2.14.3 真机硬门禁的模拟器层增量证据，不替代、不闭合该真机门禁**（C-97：模拟器≠真机；回贴 debox 档案时明确标注「模拟器已验证恢复逻辑，真机硬门禁仍未闭合」）
  - [ ] G3 **ANR 修复面在 L1/L2 模拟器的端到端功能回归 + 冒烟**（列表滚动/清未读/搜索/置顶免打扰/发图 + 健康冷启动无卡无崩）= 功能无回归证据；**debox「无真机端到端冒烟」缺口是否关闭 = 取决于真机能否装含修复 debug 包（见 Q）；不能装则明确保留未闭合**（C-95）
  - [ ] G4 **PicSel 埋点行为验证**（面包屑序列完整性；兼作被测包含本批提交的探针）
  - [ ] G5 全程无新 FATAL/ANR；结果写 `docs/testing/runs/`，FAIL 项按铁律#5 在 debox 仓另立修复任务
  - **门禁闭合边界（C-97/C-95 收口原则）**：模拟器证据证明「恢复逻辑/功能路径正确」，是必要非充分；线上归零与真机放量门禁仍分别靠 ① Crashlytics 出包后观测 ② debox 档案登记的真机注入清单——本任务**如实标注各门禁的闭合/未闭合态，绝不虚假宣称闭合**（L4）
- Non-goals:
  - 不修产品代码（发现 bug → 另立 debox 任务）；不做 Crashlytics 线上归零验证（需出包+观测期，属 debox 侧 follow-up）；不测 SmartRefreshLayout 7066d9e1（已 wontfix+监控）；不动小米正式环境真机上的任何写路径。
- Constraints:
  - 铁律#7/#8：危险操作 pre-action 比对；模拟器优先；**登出/清数据/切环境属一类禁令**——切号（epoch）用例默认不执行黑盒版（见 R-风险 3）
  - 真机真资产只读；Realm 注入仅限 L2 模拟器测试钱包，改前备份、改后必复原
  - 修复类改动（含测试设施钩子）走 agent-dev-loop 子任务，不直接改 debox

## Plan Proposal（v1）

### Phase A · 深度分析（部分已完成）
1. Realm/PicSel diff 亲核 ✅；IM diff 独立核查（agent 报告合入 implementation.md）。
2. 分析产出物：逐提交「声称 vs 实码」对照 + 行为回归风险清单（直接转化为测试观察点）。
3. 若分析发现 Critical 代码问题：先报告用户，测试计划相应调整（先修后测 or 带病标注）。

### Phase B · 前提解除（P0，有两项外部依赖）【v2 修订】
0. **知识库前置（C-90）**：执行 session 第 0 步 = 通读 `app-knowledge/` 全部文件（README/元素表 screens/弹窗/dangerous-ops/devices/network-domain），版本与已读文件清单记入 results.md；全程每次点击做 dangerous-ops pre-action 比对；探索新发现当轮回写知识库。
1. **F1 被测包 + 归因链 + 白名单归类（C-94/I-85）**：需含 `8960bb29`（dev HEAD）的 **debug 包**。
   - **自动构建路径已穷尽（I-85 坐实，2026-07-10 本轮当场复验）**：`:app:compileAppDebugKotlin`（带 `NODE_PATH=ReactNative/node_modules`，L18 姿势）失败于 `Task 'createBundleAppDebugJsAndAssets' not found`（RN bundle 任务图缺口，需 AS 的 RN 工具链）；与 debox Round 1 / PicSel 任务档案同款环境缺口，非本席可解。
   - **白名单归类**：CLI 无法产出含 `:app` 的 APK = 属铁律#4 人工白名单第④项「外部系统修复」（RN 构建工具链仅在 AS 下完整，AI 侧不可自主完成）。**人工最小边界 = 仅「AS 点 Run 打 debug 包 + `assembleAppDebugAndroidTest` + 回报打包工作区 `git rev-parse HEAD`」，不含任何测试决策/取证/判定**（那些全部 AI 自主）。
   - **归因链证据 + 诚实边界（C-94）**：无 build-embedded git SHA 时，provenance **无法在密码学意义上把 APK 绑定到 8960bb29**——最终仍部分依赖用户构建声明。为把「用户声明」收窄到最小并做**多族运行时探针**（不止 PicSel）：① 用户回报 HEAD SHA（须 = 8960bb29 或后代）+ AS 构建（干净树，无本地改动）→ ② APK sha256 + versionCode + 签名摘要 + 安装后 `dumpsys package` 快照 → ③ **三族运行时探针各自证一族改动在包内**：PicSel 面包屑（fc77707a）/ 触发 Realm 恢复路径见 `RealmKey recover*` 日志（75d44b39）/ IM 列表滚动见 cached-only 异步回填日志或 LocalAttrCache 行为（07ae7655）。**三族探针全绿 = 三组改动确在被测包**（比单一 PicSel 探针强）；仍标注「provenance 建立在用户声明 + 运行时行为证据上，非 build SHA 绑定」。**best practice follow-up**：建议 debox 在 BuildConfig 注入 `GIT_SHA`（另立轻任务），使未来 provenance 可自证——登记为 AR-6。
2. **F1 安装预检（I-89）**：安装前核对 applicationId / 签名摘要（与现装包一致）/ versionCode（新 ≥ 现装 21400001）→ `adb install -r`；任一不兼容 → **停止并报告**（绝不卸载/降级/清数据）；备选 = 新建专用 AVD 导入无资产测试钱包，现有实例不动。
3. **F2 Realm 注入设施（C-95 修订：撤销广播钩子，改同 UID instrumented helper）**：
   - **ST-1'（debox 仓子任务，T1）**：`:app` androidTest 源集新增 test-only 注入 helper 类（零 production 代码、零 manifest 变更）：`copyMainKeyToBackup()`（进程内把主 SP 真 key 复制到明文 backup 位——TC-R-002 唯一必需路径，key 值不出进程/不落日志）、`removeMainKey()`、`plantBackup(value)`、`assertKeyState()`（仅布尔）。触发方式 = `adb shell am instrument -e class ...#方法`；instrumentation 要求同签名 debug 安装 + 显式调用，**无任何外部应用可触发面**。随 F1 一起请用户 AS `assembleDebugAndroidTest` 产出 test APK。走完整 plan/impl review。
   - **降级路径**（helper 构建失败时仍可测负向场景）：TC-R-003/004 的错值植入用 `run-as` 直写**明文** backup XML（不碰加密 SP）；仅 TC-R-002 依赖 helper。
4. **F3 注入安全协议（C-95/I-86/I-87/I-89 修订）**：
   - **敏感备份隔离（I-89）**：备份**不落 device 端宿主拉取到仓库/git/runs/artifact 任何位置**；统一存 session 隔离 scratchpad 子目录 `.../scratchpad/realm-backup/`（`chmod 700`，session 结束即随 scratchpad 销毁），任务收尾主动 `rm -rf` 并验证删除。测试产物（results.md）**只准记文件名 + size + md5 摘要，禁止拷贝 XML/realm 内容本体**（backup XML 可能含明文恢复 key）。宿主备份 owner=本席（session 内），最晚销毁=任务收尾。
   - **事务式恢复 = AVD 整盘快照兜底（C-92 修订：单文件 rename 不解决跨文件混合快照）**：L2 `debox_root` 是模拟器 → 注入前 `adb emu avd snapshot save realm-pretest` 打**整盘快照**（磁盘级原子，天然覆盖 SP+keyset+realm 主/辅所有文件的一致集）。任何注入/恢复异常 or 恢复后冷启验证失败 → `adb emu avd snapshot load realm-pretest` 整盘回滚到注入前一致态（journal 由快照提供，非逐文件拼凑）。文件级 `run-as` 备份仍做，但只作**取证/对比**用途（算 md5 判字节变化），**不作恢复主手段**。
   - **Realm 运行期文件契约（I-85 回归项）**：恢复只需持久文件（`db_tm_dp_w_c.realm` 主文件 + SP + keyset）；`.lock`/`.management/`/`.note` 是运行期可重建文件——注入/恢复前确保 `am force-stop` 进程退出（`.lock` 释放），恢复后让 Realm 自行重建，**不强行恢复陈旧锁态**（避免引入无关启动失败）。场景矩阵标注「持久=必恢复 / 运行期=停进程后由 Realm 重建」。
   - **进程隔离（I-88）**：instrumentation 若启动目标 Application 可能提前初始化 Realm，使「进程启动前 key 已丢失」失真。对策：helper 注入完成后 `am force-stop com.tm.security.wallet` + 轮询确认 PID 消失，再触发被测冷启动（`am start`）——注入与被测启动是两个独立进程生命周期。开工时先验证 test runner 启动不触发 Realm init（读 Application.onCreate 链），不满足则改独立同 UID 测试进程。
   - **五步主流程**：① AVD 快照 + `am force-stop` → ② 全量文件级备份（取证用，拉 scratchpad）→ ③ 注入 + force-stop 确认 PID 消失 → ④ 冷启取证 → ⑤ AVD 快照回滚 + 冷启验证进 App + 钱包数据在。任何步骤异常 = 立即快照回滚再报告；全程禁记 key 值/长度。
   - 逐场景「允许/禁止变化文件矩阵」在 cases.md（错误 key/全败：realm 主文件字节禁变；正确恢复：realm 允许 migration/compaction 变化，断言=开库成功+数据在+主 SP 回填+备份位清除）。

### Phase C · 测试设计与执行（cases.md → `docs/testing/runs/2026-07-10-ANR治理与Realm恢复回归/`）【v2 修订】

**流程与判据总则（C-93/I-88/I-85）**：
- cases.md 从 `TEST_CASES.md` 模板生成（含框架前置检查节），每条用例标 P0/P1/P2，**按 P0→P1→P2 顺序全量执行**（⏸️ 不算跑完）；出现 FAIL → Phase 3 先记录 → Phase 4 修复走 agent-dev-loop（debox 仓另立任务）→ Phase 5 全量重跑本轮 cases → Phase 6 不改代码最终验收全 PASS 收口。
- **客观判据与归因**：每条用例起止打 logcat marker（`log -t autotest "TC-x START|END"`）+ 记录 app PID；ANR 判定 = 窗口内 logcat 无 `ANR in com.tm.security.wallet`、无 `Input dispatching timed out`、（L2）`/data/anr/` 无新 trace、无系统 ANR 对话框；崩溃判定 = `mobile_list_crashes` 与 DropBox 基线快照对比仅计窗口内新增、PID 变化需归因（受控 force-stop 除外）。开测前采集基线快照。
- 操作参数固定可重复：滚动 = 固定坐标 fling ×20；清未读循环 ×5 会话；冷启动 = `force-stop → am start` ×10；等待上限逐条写在 cases.md。
- **点击统一走守卫（I-83）**：本轮为黑盒 MCP 驱动（`mobile_*` 直接坐标点击，不经框架 SelfHealingLocator，故 `DangerousOpsGuard` 代码网关不在链路上）——因此**每次点击前强制人工 dangerous-ops 比对并留证**：点击前记录目标文本/坐标 + 比对结论（allow/命中→stop）写入 cases.md 执行日志；命中危险词表（转账/签名/登出/删除钱包等）一律 stop+截图+核对用例。此为黑盒链路对铁律#7 的等效落地（框架内 B 模式回放才走 DangerousOpsGuard 代码网关）。

**组 R · Realm 恢复（L2 debox_root · debug 包 · 测试钱包 · 硬门禁）【v2：注入=ST-1' instrumented helper + 五步安全协议，见 Phase B-3/B-4】**
- TC-R-000 前置：debug 包登测试钱包 → 停 App → 五步协议之②全量文件备份 + 取证旧库 md5/size/mtime
- TC-R-001 健康冷启动 10/10 无回归（恢复代码不误伤健康路径；L1+L2 各跑）
- TC-R-002 key 丢失+备份正确：helper `copyMainKeyToBackup()` + `removeMainKey()` → 冷启 → 自动恢复进 App（logcat `RealmKey recovered=true source=PLAIN_BACKUP backfilled=true`）+ 钱包数据完好 + 备份位被清除 + 再次冷启正常（回填生效）
- TC-R-003 key 丢失+备份错误（I-86 精化）：植入的错值必须是**格式合法**的候选——与真 key 同长度/同编码（base key 经 `expandRealmKey` 得 64 字节的合法输入）**但值不同**（进程内 helper 生成随机合法 key，或 `run-as` 写一个已知格式合法的假 base key）。目的 = 触发 **probe 阶段 native 解密失败**（`recover candidate miss`），而非候选构建期 `expandRealmKey` 的解析失败（那走 `candidate invalid` 不同分支）。判据分轨记录：logcat 出现 `recover candidate miss source=PLAIN_BACKUP`（证走到 native probe 且失败）→ 最终 `recovered=false` 受控退出（无 boot-loop、无 mint）+ **旧库字节不变（md5/size/mtime）** → 文件级恢复。若只见 `candidate invalid`（解析失败）则该用例无效需重造错值。
- TC-R-004 key 丢失+无备份：仅 `removeMainKey()` → 受控退出 + 旧库字节不变 → 文件级恢复
- 每条走五步协议，恢复后冷启验证进 App、钱包数据在；逐场景文件变化矩阵见 cases.md
- ⚠️ 分轨断言口径（debox v5 修订 1）：**仅错误 key/全败场景断言字节不变**；正确恢复场景允许 migration/compaction 变化，断言=开库成功+数据在+回填+备份清除

**组 I · IM ANR 治理回归（L1 为主；三星测试机复核内容丰富面，若用户装包）**
- TC-I-001 消息列表滚动：进入含较多消息会话快速滚动 ×N，头像/昵称/消息扩展显示正确、无白块久留（cached-only miss 首帧降级可接受、不应永久旧值）、无 ANR
- TC-I-002 进出会话清未读：造未读（对端 bot 发消息/adb 灌入）→ 进会话 → 退出 → 徽标清除正确（异步化后的时序回归）
- TC-I-003 本地搜索：搜索最近联系人关键词 → 结果正确返回、输入过程主线程不卡
- TC-I-004 会话置顶/免打扰：开关切换 → 退出重进 → 状态读取正确（DbAsyncBridge 异步读时序）
- TC-I-005 发送图片消息：正常发图成功（P5' 回归面正向）
- TC-I-008 空路径上传显式失败（I-87，P5' 目标分支）：若 ST-1' helper 落地，进程内构造 localPath=null 的 JImageMessage 调 `OrderedMessageUploadProvider.startUpload` → 断言不崩、消息置失败态、队列继续推进；helper 不可行 → 登记 Accepted Risk（与 debox 档案「JVM 单测性价比低、复用线上已验证 onFail 路径」同口径）
- TC-I-006 切号 epoch 边界：**默认不执行黑盒版**（登出=一类禁令）；已有 30 例 JVM 单测覆盖；若用户显式授权且 AVD 有 logged-in snapshot 兜底，才执行（登出→登另一测试号→检未读/会话状态不串号→snapshot 恢复）
- TC-I-007 弱网/断网观察（F-2 靶点）：断网启动 App（connect 长挂 connecting）→ 消息列表 bind 用 extra 降级可用、不崩；恢复网络 CONNECTED 后缓存恢复
- 全程 `mobile_get_crash` + logcat ANR/FATAL 监测；逐条判据挂接 implementation.md A-4 的 R1–R10 观察点
- ⚠️ L1 测试账号 IM 内容近空（上轮实录）——内容依赖用例在 L1 造数（bot 私聊 + adb 灌文本/图），不足以覆盖的面标注【待三星复核】

**组 P · PicSel 埋点（L1）**
- TC-P-001 首次打开相册全序列（I-84 精化）：入口点「相册」→ 授权 → **等待 ≤3s 出现 `page_resumed`** → **主动执行返回/关闭相册动作**（系统返回键）→ 等待 ≤2s 出现 `page_paused`。断言事件**顺序**为 `open_gallery → perm_agree wait_ms → launch config_ms → page_resumed create_to_resume_ms → page_paused resumed_for_ms`，每段数值非负、量级合理；缺 `page_paused` = 未执行离场动作，补做而非判 FAIL。
- TC-P-002 权限拒绝分支：revoke 权限后拒绝 → `perm_reject` 终止事件；取消 → `perm_cancel`

**组 S · 整体冒烟（L1/L2）**
- TC-S-001 冷启动→主页→四 Tab 遍历→收发消息→退后台回前台，无新 FATAL/ANR/新弹窗异常

### Phase D · 结果回写
- results.md（runs 目录）+ 本任务 index.md 收口；Realm 硬门禁三证据回贴 debox `2026-07-10-03` 档案「真机验证清单」节；ANR「无真机冒烟」Accepted Risk 在 debox `2026-07-10-02` 补充「autotest 已冒烟」注记。
- FAIL → 先记录（Phase 3 纪律）→ debox 仓另立修复任务（铁律#5）。

### 风险 / 需要用户确认
1. **需用户 AS 打 debug 包 + androidTest APK**（外部依赖，白名单类：CLI 无法产出 :app），并回报打包工作区 HEAD SHA——阻塞项，plan 通过后立即请求。
2. ST-1' instrumented helper 属 debox 仓 androidTest 源集新增（零 production 代码），需用户知情同意。
3. 切号用例默认降级为单测覆盖（黑盒违一类禁令），用户可显式授权升级。
4. ANR 为低频统计现象：黑盒 PASS 证明「功能无回归 + 健康路径不卡」，**线上归零仍靠 Crashlytics 观测**（与 debox 档案验证口径一致，不替代）。
5. 模拟器 fake-IP 网络噪声：本批无网络层结论，影响忽略。

## Plan Review Log（per-round，含 VERDICT 快照）

（待 R1）


### plan-review Round 1 — 2026-07-10（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
- [Security/安全] [95] ST-1 必须允许 `adb shell` 触发，却未定义调用方认证；若 receiver 可导出，任意应用均可删除或篡改钱包 Realm key，debug 钱包资产同样面临风险 -> 改用同 UID 的 instrumented test；不得新增可由外部应用触发的密钥修改入口。
- [Bugs/逻辑] [95] TC-R-003/004 删除主 key 后声称“钩子回写正确 key”，但 ST-1 没有保存或恢复正确 key 的动作，且 `dump_key_state` 明确不返回值；测试失败或进程异常时可能无法复原钱包 -> 变更前停 App 并完整备份目标 SP、Realm 及关联文件，定义可验证的原子恢复流程和失败兜底，禁止导出或记录 key。
- [Plan alignment/计划对齐] [94] PicSel 日志只能证明 APK 含 `fc77707a`，不能证明 Realm 与 IM 两项目标提交及最终 `8960bb29` 均在被测包内，硬门禁证据可能建立在错误 APK 上 -> 构建并核验可追溯的 Git SHA，记录 APK hash、签名、versionCode 和安装后的 SHA 证据。
- [Standards/规范] [93] 方案只执行目标回归组和冒烟，没有按 `TEST_CASES.md` 的 P0→P1→P2 全量测试，也缺少“修复后全量回归”和“不改代码的最终全量验收”，违反项目测试流程 -> 从唯一用例模板生成本次 cases/results，并补齐 Phase 2、Phase 5、Phase 6。
- [Standards/规范] [90] 计划未把测试 session 开始前完整读取 `app-knowledge/` 元素表、弹窗和危险操作清单列为前置步骤 -> 增加知识库读取、版本记录及逐次危险操作 pre-action 守卫步骤。

### Important (warn)
- [Regression risk/回归] [89] 未验证现有 2.14.1/2.14.2 与 debug APK 的 applicationId、签名和 versionCode 兼容性；安装失败可能诱导卸载、降级或清数据，直接触犯禁令 -> 安装前核验四项信息；不兼容时创建新的 L1/L2 测试环境，不处理现有钱包数据。
- [Test coverage/测试覆盖] [88] ANR 用例使用“×N”“不卡”“无白块久留”“数值合理”等主观判据，缺少次数、持续时间、响应阈值、主线程证据及逐例日志窗口 -> 为每条用例定义固定循环次数、超时阈值、起止 marker、目标 PID，并采集 ANR traces/Dropbox/主线程阻塞证据。
- [Test coverage/测试覆盖] [87] P5' 仅回归正常发图，没有验证本次修复针对的空路径分支是否显式失败且不崩溃 -> 增加可控的空路径测试，断言失败结果被调用方处理、无 NPE、无错误上传；黑盒不可达时使用同 UID 测试。
- [Bugs/逻辑] [86] Realm 用例只笼统记录“旧库 md5”，没有定义 Realm 主文件、辅助文件、key SP 和备份位的前后状态矩阵；错误候选可能产生新文件或意外 key 写入而未被发现 -> 明确每个场景的文件清单、允许变化项、禁止变化项及恢复后校验。
- [Test coverage/测试覆盖] [85] `mobile_get_crash + logcat` 未说明清理旧事件、按 PID/时间窗归因及 App 进程被受控结束时的区分规则，容易把旧崩溃算入或漏掉新进程异常 -> 每例设置日志 marker，记录 PID 变化，并分别检查 logcat、ANR traces 与 Dropbox。

### Questions
- 是否允许以同 UID instrumented test 替代 ST-1 外部广播钩子？
- 若现有安装包与 debug APK 签名不兼容，是否允许新建专用 L1/L2 模拟器并导入无资产测试钱包？
- “保证没有问题”是否确认按项目规范执行完整 P0→P2、修复后全量回归及最终全量验收？

### Required Changes
1. 移除外部可触发的密钥修改 receiver，改为同 UID、仅测试构建可用的注入方式。
2. 补全 Realm 变更前备份、异常中断恢复、逐场景状态矩阵和恢复验证。
3. 建立目标 Git SHA 到 APK、安装实例和测试证据的完整归因链。
4. 从 `TEST_CASES.md` 生成本轮用例，并加入完整 Phase 2、Phase 5、Phase 6。
5. 将 `app-knowledge/` 阅读和危险操作守卫纳入强制前置步骤。
6. 把 ANR、PicSel、Realm 与空路径测试改为客观、可重复、可归因的判据。
7. 增加安装兼容性预检，明确禁止卸载、清数据、切环境和处理现有真资产钱包。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-10-01-test-debox-anr-realm-regression/codex-plan-review-R1.txt

#### R1 findings 处置

| # | 等级 | 处置 |
|---|------|------|
| C-95 广播钩子无认证 | Critical | ✅ 全采纳：撤销广播钩子，改 ST-1' 同 UID instrumented helper（androidTest 源集、零 production 代码、同签名+显式 am instrument 触发、无外部面）——Phase B-3 |
| C-95(2) 无恢复动作/失败兜底 | Critical | ✅ 全采纳：五步注入安全协议（停 App→全量文件级备份→注入→取证→原子恢复+冷启验证），异常即恢复；禁记 key 值——Phase B-4 |
| C-94 归因链断裂 | Critical | ✅ 全采纳：用户回报打包 HEAD SHA + APK sha256/versionCode/签名摘要 + dumpsys 快照 + PicSel 探针（降级为 sanity）——Phase B-1 |
| C-93 未按模板 P0→P2 全量 | Critical | ✅ 按正确语义采纳：`TEST_CASES.md` 实为空白格式模板（已核，69 行无具体用例）——cases.md 从模板生成、全用例 P0→P2 分级全跑、FAIL 走 Phase 4 修复→Phase 5 全量重跑→Phase 6 验收闭环——Phase C 总则 |
| C-90 知识库前置缺失 | Critical | ✅ 全采纳：session 第 0 步通读 app-knowledge + 逐点击 pre-action 比对 + 回写——Phase B-0 |
| I-89 安装兼容性预检 | Important | ✅ 全采纳：appId/签名/versionCode 三核对，不兼容停+报告，绝不卸载；备选新建 AVD——Phase B-2 |
| I-88 主观判据 | Important | ✅ 全采纳：固定循环次数/坐标/等待上限 + logcat marker + PID 归因 + ANR 四判据——Phase C 总则 |
| I-87 P5' 空路径分支未测 | Important | ✅ 采纳：新增 TC-I-008（helper 进程内构造），helper 不可行则显式 Accepted Risk |
| I-86 文件状态矩阵 | Important | ✅ 全采纳：并入五步协议 + cases.md 逐场景允许/禁止变化矩阵 |
| I-85 崩溃归因窗口 | Important | ✅ 全采纳：基线快照 + 窗口内新增判定 + PID 变化归因——Phase C 总则 |
| Q1 同 UID instrumented 替代 | Question | 是（即 v2 方案） |
| Q2 不兼容时新建 AVD | Question | 是，仅在不兼容时新建、现有实例零触碰 |
| Q3 是否完整 P0→P2+Phase5/6 | Question | 是，按 C-93 处置口径（模板生成的本轮全集，非全 App 历史用例堆叠） |


### plan-review Round 2 — 2026-07-10 ⚠️ 作废（假 PASS：codex models-manager 超时挂死，快照为 prompt 模板回显，无真实 VERDICT——L14 族事故，同 debox Round 3 impl-R1 首跑先例；见下方 Round 2-retry）

```text
## Review Findings

VERDICT: PASS | FAIL | PASS_WITH_ACCEPTED_RISK

### Critical (block, must fix)
- [维度] [文件:行] [置信度] 描述 -> 建议修复

### Important (warn, should fix or record)
- [维度] [文件:行] [置信度] 描述 -> 建议处理

### Accepted Risk Candidates
- [维度] [文件:行] [置信度] 描述 -> 接受该风险需要的 owner / reason / follow-up / expiry

### Verification Gaps
- ...

（任何一节为空写 None。除规定结构外不要输出其它内容。）

2026-07-10T14:10:46.233816Z ERROR codex_models_manager::manager: failed to refresh available models: timeout waiting for child process to exit
2026-07-10T14:13:51.238059Z ERROR codex_models_manager::manager: failed to refresh available models: timeout waiting for child process to exit
2026-07-10T14:16:56.243333Z ERROR codex_models_manager::manager: failed to refresh available models: timeout waiting for child process to exit
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-10-01-test-debox-anr-realm-regression/codex-plan-review-R2.txt

### plan-review Round 3（R2-retry）→ 后续 R4 快照见文件；v4 已全部采纳（处置见 Revision Log v4；cap 收口见 Accepted Plan）

### plan-review Round 3 — 2026-07-10（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
- [Plan alignment/计划对齐] [97] G2 要求闭合“Realm 恢复真机注入验证”放量硬门禁，但方案明确限定 Realm 注入仅在 L2 模拟器执行；模拟器证据不能替代真机硬门禁 -> 增加受控测试真机上的完整 R 组验证，或将硬门禁明确保留为未闭合并停止宣称可放量。
- [Plan alignment/计划对齐] [95] G3 要求关闭“ANR 修复无真机端到端冒烟”缺口，但 I/S 组以 L1/L2 模拟器为主，三星真机复核取决于用户装包且不是必跑门禁 -> 将包含目标提交的测试真机安装、IM 回归和整体冒烟设为必完成前提；无法执行时不得关闭该缺口。

### Important (warn)
- [Security/安全] [89] 五步协议会把钱包 Realm、SharedPreferences、keyset 和 backup XML 拉到宿主机，但未规定隔离目录权限、保留期限、销毁方式及禁止进入 runs/git/artifact；backup XML 还可能包含明文恢复 key -> 使用仓库外权限受限的临时目录，测试结束验证销毁，并明确禁止复制其内容、文件名或摘要之外的信息到测试产物。
- [Bugs/逻辑] [87] “文件级原子恢复”没有可执行定义；Realm 主文件、`.management`、`.note`、SP 与 keyset 跨多个文件，逐文件覆盖中断可能形成混合快照，恢复验证失败时也没有第二兜底 -> 定义同文件系统暂存目录、完整性校验、目录级切换或可回滚替换顺序，并保留只读原始副本直至最终冷启验收通过。
- [Test coverage/测试覆盖] [86] TC-R-003 的“错值 backup”未限定为格式合法但密码错误的 Realm key；任意字符串可能只覆盖 Base64/长度解析失败，不能证明候选 probe 失败时旧库保持不变 -> 进程内生成合法长度、合法编码且与真实 key 不同的随机候选，并分别记录解析失败与 probe 失败的判据。
- [Standards/规范] [85] F1 将常规打包和 HEAD 回报交给用户，但项目规则仅允许五类人工白名单介入，且现有描述没有证明该操作属于“外部系统修复” -> 先穷尽并记录自动构建恢复路径；确需人工时，明确白名单归类、阻塞原因和人工操作的最小边界。
- [Plan alignment/计划对齐] [88] TC-I-008 不可执行时直接登记 Accepted Risk，但缺少 owner、具体 follow-up 和 expiry；线上 ANR 归零验证也只有笼统 follow-up -> 为每项风险补齐 owner、reason、可验证 follow-up、截止版本或日期，否则保持 FAIL/未完成状态。

### Questions
- 哪台非正式环境真机被授权执行 Realm 文件注入与恢复，并如何保证其只含可丢弃测试钱包？
- 若三星测试机无法安装目标 debug 包，是否接受本任务不能关闭 Realm 真机硬门禁和 ANR 真机冒烟缺口？
- 宿主机敏感备份由谁负责保管和销毁，最晚销毁时间是什么？

### Required Changes
1. 将 Realm 真机注入验证和 ANR 真机冒烟改为明确的必跑门禁，或修正目标与最终结论，保留未闭合缺口。
2. 补充敏感备份的仓库外隔离、权限、禁止落盘范围、销毁和审计规则。
3. 把跨文件恢复细化为可中断回滚、可校验的恢复算法。
4. 明确 TC-R-003 使用格式合法但密码错误的候选 key，并区分解析失败与 probe miss。
5. 解决人工 AS 构建与最小人工介入白名单的冲突。
6. 为所有 Accepted Risk 补齐 owner、reason、follow-up 和 expiry。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-10-01-test-debox-anr-realm-regression/codex-plan-review-R3.txt

### plan-review Round 4 — 2026-07-10（adl-review 自动快照；findings 处置人工补写）

```text
## Plan Review

VERDICT: FAIL

### Critical (block)
- [Plan alignment/计划对齐] [94] 归因链仍未把目标 Git SHA 与 APK 内容可靠绑定：用户回报构建目录 HEAD、APK hash、安装快照及 PicSel 探针只能证明“某个 APK 被安装且包含 PicSel 改动”，不能证明该 APK 必然由所报 HEAD 构建并包含 Realm、IM 两组修复 -> 在构建产物中嵌入 Git SHA，或保存由受控构建流程生成且绑定 HEAD、APK hash 的可验证 provenance；否则不得以该 APK 的测试结果为目标提交背书。
- [Bugs/逻辑] [92] 所谓“原子恢复算法”仅保证单文件 `rename` 原子，SP、keyset、Realm 主文件及辅助目录仍会逐项替换；中途失败依然可能形成跨文件混合快照，未解决上一轮指出的恢复一致性问题 -> 设计目录级快照切换或带 journal/回滚清单的事务式恢复；每一步失败均须能恢复至同一份完整快照，验证失败时不得启动钱包继续使用。
- [Plan alignment/计划对齐] [91] AR-1 把“线上 Crashlytics 对应问题归零”列为 Realm 真机注入硬门禁的替代完成条件，与计划中“模拟器/线上观测不替代真机硬门禁”的核心边界冲突 -> 删除该替代条件；真机注入清单未执行时，2.14.3 放量门禁必须始终保持未闭合。

### Important (warn)
- [Bugs/逻辑] [88] ST-1' instrumentation 会启动目标 Application；若 Application 初始化阶段已读取 key 或打开 Realm，helper 随后删除 key 不能准确模拟“进程启动前 key 已丢失”，且计划未规定 helper 结束后再次 `force-stop`、确认目标进程退出再冷启 -> 证明测试 runner 启动不会触发 Realm 初始化，或改用独立同 UID 测试进程；注入完成后强制停止并确认 PID 消失，再开始被测冷启动。
- [Regression risk/回归] [85] 恢复集合包含 Realm `.lock` 等运行期文件，恢复陈旧锁状态可能引入与真实钱包数据无关的启动失败；计划未依据 Realm 版本和实际文件语义区分持久数据与可重建运行文件 -> 核验 Realm 文件契约，明确哪些文件必须恢复、哪些应在进程停止后删除并由 Realm 重建，同时把该规则写入场景矩阵。
- [Test coverage/测试覆盖] [84] PicSel 全序列断言要求出现 `page_paused`，但 TC-P-001 只描述“首次打开相册”，未定义离开相册的动作及事件等待窗口；用例可能无法稳定产生完整序列 -> 增加明确的返回/关闭相册步骤、事件顺序约束和每段超时判据。
- [Standards/规范] [83] 计划要求每次点击执行 dangerous-ops 比对，但没有明确所有 UI 操作必须经现有 `DangerousOpsGuard` fail-closed 网关；人工逐次比对难以审计，也可能被直接 UiAutomator/Espresso 点击绕过 -> 在 cases 执行规范中强制所有点击统一走守卫入口，并记录每次 allow/block 证据。
- [Plan alignment/计划对齐] [82] AR-3 的 expiry 为“本任务收口”，但 helper 不落地时 follow-up 只是维持既有代码核验，没有在到期前补齐目标分支的运行时验证；这不是可关闭的风险处置 -> 将 expiry 调整到明确版本/日期，并指定可执行的 instrumented 验证任务；到期仍未验证时保持任务未完成或明确延续风险。

### Questions
- debox 构建是否已有可从安装包读取的 Git SHA/BuildConfig 字段，可直接用于绑定 `8960bb29` 与 APK？
- 目标 Application 启动时是否会在 instrumentation 测试方法执行前初始化或打开 Realm？
- 当前 Realm Java 版本对 `.lock`、`.management/`、`.note` 的备份恢复契约是什么？

### Required Changes
1. 建立 Git SHA 与 APK 内容之间可验证、不可仅靠人工陈述的构建归因链。
2. 将跨文件恢复改为真正可回滚的一致性事务，并明确 Realm 运行期文件处理规则。
3. 删除 Crashlytics 归零可替代 Realm 真机注入硬门禁的表述。
4. 解决 instrumentation 启动 Application 对 key 丢失场景真实性和进程隔离的影响。
5. 补齐 PicSel 离场动作、事件顺序与等待窗口。
6. 强制 UI 点击统一经过 `DangerousOpsGuard` 并留存守卫证据。
7. 为 TC-I-008 风险设置真实可执行的 follow-up 和明确到期处理。
```
- 全量输出: /Users/xiaochengcheng/StudioProjects/autotest/.adl/review-artifacts/2026-07-10-01-test-debox-anr-realm-regression/codex-plan-review-R4.txt
## Plan Revision Log

> 每次按 review 修订方案时追加一行：vN · 针对哪条 finding · 改了什么。

- v2 · C-95/C-95(2) · 撤销 ST-1 广播钩子 → ST-1' instrumented helper + 五步注入安全协议（Phase B-3/B-4，组 R 重写）
- v2 · C-94/C-90/I-89 · Phase B 增 B-0 知识库前置、B-1 归因链、B-2 安装预检
- v2 · C-93/I-88/I-85 · Phase C 增「流程与判据总则」（模板生成/P0→P2 全跑/Phase4-6 闭环/客观判据/归因窗口）
- v2 · I-87 · 新增 TC-I-008 空路径上传显式失败
- v2 · I-86 · 文件变化矩阵并入五步协议与 cases.md
- v3 · C-97/C-95 · G2/G3 诚实修正：模拟器证据不替代/不闭合真机门禁，如实标注闭合态；补门禁闭合边界原则
- v3 · I-89 · F3 备份隔离（scratchpad chmod700+收尾销毁、产物只记摘要禁本体）
- v3 · I-87 · F3 原子恢复算法（同 FS 暂存+校验+单文件 rename+只读原始副本留至验收）
- v3 · I-86 · TC-R-003 错值须格式合法值不同，分轨 `candidate miss`(probe失败) vs `candidate invalid`(解析失败)
- v3 · I-85 · F1 白名单归类（自动构建穷尽已复验坐实，人工最小边界=仅打包+回报SHA）
- v3 · I-88 · 补 Accepted Risks 表（owner/reason/follow-up/expiry）
- v4 · C-91 · AR-1 删除「Crashlytics 归零」替代条件，真机门禁唯一闭合=真机注入清单
- v4 · C-92 · 事务式恢复改 AVD 整盘快照兜底（磁盘级原子），文件备份降为取证用途
- v4 · C-94 · provenance 诚实边界（非 SHA 绑定）+ 三族运行时探针增强 + AR-6 建议 BuildConfig 注 GIT_SHA
- v4 · I-88(proc) · instrumentation 注入后 force-stop 确认 PID 消失再冷启（进程隔离）
- v4 · I-85(realm) · Realm 运行期文件契约（.lock/.management/.note 停进程后重建，不恢复陈旧锁态）
- v4 · I-84 · TC-P-001 补离场动作 + 事件顺序 + 分段超时
- v4 · I-83 · 黑盒点击强制 dangerous-ops 比对留证（框架外链路等效落地铁律#7）
- v4 · I-82 · AR-3 expiry 改 2.14.3 发布前 + 指定 debox 补单测任务

## Accepted Risks（I-88，全部补齐四字段）

| # | 风险 | owner | reason | follow-up（可验证） | expiry |
|---|------|-------|--------|--------------------|--------|
| AR-1 | debox 2.14.3 Realm **真机**放量硬门禁 | Kai | ~~L3 真机无法注入~~ | ✅ **2026-07-11 实质闭合**：三星真实硬件验证 TC-R-002(正确恢复)+TC-R-003(错误 key fail-closed 且旧库零写入)通过;TC-R-004(无备份)真机未跑(TC-R-003 子集,不再注入)。**事故**:Knox 禁 run-as 写致 restore 失败,真账号 key 丢失→seed 重导已恢复。教训入 devices.md | **已闭合(核心路径真机验证)**;残留 TC-R-004 子集 |
| AR-2 | debox ANR「真机端到端冒烟」缺口是否闭合取决于真机能否装含修复包 | Kai | 同 AR-1 真机约束；能装则组 I/S 在真机复跑关闭，不能装则保留未闭合 | 装包成功→真机复跑组 I/S；否则出包后 Crashlytics ANR 族量级压降验证 | 2.14.3 发布后 2 周 |
| AR-3 | TC-I-008 空路径上传若 ST-1' helper 不落地则无黑盒运行时证据 | Kai | 黑盒无法自然构造 localPath=null 的 JImageMessage | helper 落地→instrumented 触发断言（不崩+失败态+队列续）；不落地→**在 debox 立 T1 任务补 `OrderedMessageUploadProviderTest` JVM/instrumented 单测**，非仅维持代码核验 | 2.14.3 发布前（未验证则该项保持未完成、风险延续） |
| AR-6 | provenance 非 build-SHA 绑定（依赖用户构建声明 + 运行时行为探针） | Kai | 当前 APK 无 embedded git SHA，无法密码学自证由 8960bb29 构建 | 三族运行时探针全绿作增强证据；建议 debox BuildConfig 注入 `GIT_SHA`（另立轻任务）使未来可自证 | debox 下次构建配置评审 |
| AR-4 | ANR 为低频统计现象，模拟器黑盒 PASS 不等于线上归零 | Kai | 低频边界竞态 happy-path 难触发（与 debox 档案同口径） | 出包后观测 b0f8f926/ee20dd36/16a70ab1 等 issue 于 ≥新 build 量级压降 | 2.14.3 发布后 2 周 |
| AR-5 | 模拟器 fake-IP 网络噪声 | Kai | 本批无网络层结论，Realm/IM/UI 崩溃回归模拟器即终局 | 无（不涉网络层判定） | 长期 |

## Accepted Plan

> 基线 = Proposal v1 + v2/v3 全部差异（见 Revision Log）。当前状态以 index.md 为准。

- **基线 = Proposal v1 + Revision Log v2/v3 全部条目**。
- **核心边界**：本任务产出 = ① 3 提交独立核码分析（已完成，见 implementation.md）② L2 模拟器 Realm 恢复逻辑注入验证 ③ L1/L2 模拟器 IM ANR 功能回归 + PicSel 埋点验证 + 整体冒烟；**不宣称闭合任何真机门禁/线上归零**（如实标注，见 AR-1/AR-2/AR-4）。
- **两项外部依赖（阻塞，待用户）**：F1 用户 AS 打 debug 包 + androidTest APK + 回报 HEAD SHA（白名单④外部系统修复，已坐实 CLI 不可自产）；ST-1' debox 仓 androidTest helper 需用户知情同意（零 production 代码）。
- Accepted at / Decision: **2026-07-10 收口 = PASS_WITH_ACCEPTED_RISK**。依据：plan-review 3 轮有效（R1 FAIL / R2-retry FAIL / R3 FAIL；R2 假 PASS 已作废）到 cap，findings **单调收窄、全部采纳修订至 v4、零分歧**（C-95 广播→instrumented / C-92 原子恢复→AVD 整盘快照 / C-94 provenance 诚实降级+三族探针 / C-91 删除门禁替代条件 …）；核心边界（真机门禁不闭合、Realm 注入范围）经**用户 2026-07-10 拍板**：①AS 打 debug+test 包 ②L2 模拟器全套注入验证 ③F-1/F-2 各立 debox T1。超 cap 收口同 debox 仓 ANR 清扫先例，全程留痕可审计。
- Accepted risks: 见上表 AR-1~AR-6。
- Deviation Policy: 改变注入方式、备份/恢复算法、安全逻辑、门禁闭合口径的偏离，先 consult Codex。
- **用户决策落地**（2026-07-10）：
  - 打包 = 用户 AS 出 debug APK + `assembleAppDebugAndroidTest` test APK（含 ST-1' helper），回报 HEAD SHA。
  - Realm = 组 R 全套 L2 注入（TC-R-001~004），ST-1' helper 落地（debox 子任务 `2026-07-11-01-add-realm-test-injection-helper` 或本仓记录移交）。
  - F-1/F-2 = debox 各立 T1 修复任务（**不进本次被测包**，避免污染回归对象——本轮测的是纯 8960bb29）。
