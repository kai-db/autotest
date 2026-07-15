# 极验 GeeGuard 设备指纹接入 — 本轮测试用例（2026-07-14）

> 用例来源：`docs/testing/runs/极验GeeGuard设备指纹-测试用例.md`（业务侧测试用例文档，2026-07-13）
> 本文件按 `TEST_CASES.md` 模板 + `TEST_GUIDE.md` 第七节设备阶梯，把该文档落地为**本环境可执行**的用例集：
> 保留原用例编号（TC-A1…TC-I4）以便追溯，新增「设备」列与「执行方式」列（AI 自主 / ⏸️ 外部依赖 / 静态代验）。
> 优先级：P0 = 必测阻塞项 / P1 = 重要 / P2 = 一般。设备：L1 = 普通模拟器 / L2 = root 模拟器 / L3 = 真机 / S = 静态（代码/构建核验，不跑 App）。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | DeBox 极验 GeeGuard 设备指纹 SDK 接入（替换腾讯图灵盾 RCE）：初始化 / token 采集缓存 / 5 接口 body 注入 / fail-open 降级 / 日志安全 / 运维支撑 |
| App 包名 | `com.tm.security.wallet`（所有 variant 同包名，无 `.debug` 后缀） |
| 被测包 | 2.14.2（versionCode 21400002，emulator-5554 / 5556 均已装，含 GeeGuard v2.7.1.1 AAR） |
| 仓库路径 | `/Users/xiaochengcheng/StudioProjects/debox-android` |
| 测试方式 | Claude Code + mobile-mcp / adb logcat（`PRETTY_LOGGER-GeeGuard` + `http_log_interceptor`）+ 静态代码核验 |
| 设备 | L1 = emulator-5556（Pixel，测试号 `10b92305`）/ L2 = emulator-5554（debox_root，测试号 `2309b9ea`）/ L3 = 三星 SM-S9210（测试环境，需人工解锁）。⚠️ 小米 25067PYE3C 为**正式环境**，本轮不使用 |
| 前置条件 | 两模拟器已登录测试号、同处测试环境 `t.debox.pro`、网络在线；开测核实「我的」页账号 = 测试号 |

---

## 代码事实基线（探索结论，用例判定依据）

来自 debox-android 代码探索（2026-07-14）+ 模拟器实测：

1. **初始化**：`GeeGuardInitTask`（MediumPriorityTasks.kt:74，MEDIUM 子线程）→ `PrivacyConsent.isAgreed()` gate → `GeeGuardManager.init(app)`（GeeGuardManager.kt:55，幂等）→ `GeeGuard.register` + `refreshTokenAsync()` 预热。
2. **token 缓存**：`@Volatile cachedToken`（空串默认）；`getDeviceToken()` = O(1) 读缓存（行 79），永不阻塞/不抛异常。重试 `PREWARM_MAX_RETRY=2`、间隔 2000ms（1+2 次）；`decideToken` 决策：status=200 存 respondedGeeToken / -300·-500·-501 存降级 geeToken / -200 不缓存不重试；**非空才覆盖缓存**（行 127）。
3. **5 接口注入**（统一模式 `getDeviceToken()` + `refreshToken()` + `if (isNotEmpty) 注入`，空 token **不加字段** = fail-open）：
   - `app_login` → AppCacheManager.kt:463-469（JSON body）
   - `liveroom/join` → JoinSpaceDialogFragment.kt:381-388（join 弹窗）**及** :123-129（房主/进行中直接进房分支）
   - `lucky_box/receive_lucky_box` + `lucky_box/onchain_box/receive` → LuckyBoxDialogFragment.kt:205-213（robReq）
   - `liveroom/lucky_box/rob` → LuckyBoxDialogFragment.kt:170-177（liveReq）
4. **日志**：tag=`GeeGuard`（经 LogUtils pretty logger，**logcat 实际匹配 `PRETTY_LOGGER-GeeGuard`，`logcat -s GeeGuard` 匹配不到**）。全部日志语句只打长度/状态码（GeeGuardManager.kt:61/72/111/130/132/135/137/140/142），无 token 内容。
5. **配置**：`BuildConfig.GEEGUARD_APP_ID` ← local.properties → env → 空串（BaseBusiness/build.gradle:64-66）；空串时 `"GEEGUARD_APP_ID not configured, skip register"`（行 61）。
6. **ABI**：app/build.gradle:74-78 仅 `arm64-v8a` + `armeabi-v7a`（x86/x86_64 注释关闭）。
7. **兜底**：register/submitReceipt 均 `catch (Throwable)`（含 UnsatisfiedLinkError，行 64-73/110-113）。
8. **管理员页**：`SettingAdminActivity`（shell 可直起）`btnCopyGeeId`（行 112-120），未就绪 toast=「设备指纹未就绪，稍后再试」，复制 label=`geeID`。
9. **⚠️ 实测口径修正**：模拟器实测 `submitReceipt ok, respondedGeeToken.length=392`——正常形态长度 **~400 字符**，与用例文档「~1K」不符；降级形态待实测。**判定以日志行语义为准**（`submitReceipt ok`=正常 / `degrade to geeToken`=降级），长度只做记录与形态区分参考。
10. 后端配置开关实测已下发：`geetest_token_check:true`、`emulate_check:2`（http_log_interceptor 实录）。

---

## 0. 静态核验（S，替代模板框架前置检查——本轮不改 autotest 框架代码）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-S1 | 5 接口注入代码模式一致 | 核对 5 处注入点源码（见基线 §3） | 均为「缓存读取 + 非空才注入 + 字段名精确 `gee_token`」模式；`app_login` 与其余 4 处模式一致（支撑 B 组静态代验） | P0 | S | AI 自主 |
| TC-S2 | 字段名拼写核验 | grep 全仓 `gee_token` 硬编码点 | 5 处（AppCacheManager:469 / JoinSpace:388,129 / LuckyBox:213,177）拼写精确一致，无驼峰混用 | P0 | S | AI 自主 |
| TC-S3 | ABI 打包核验 | 查 app/build.gradle abiFilters | 仅 arm64-v8a + armeabi-v7a；x86/x86_64 关闭（TC-F3 前提成立） | P1 | S | AI 自主 |
| TC-S4 | 单测回归 | `./gradlew :business:BaseBusiness:testAppDebugUnitTest --tests "*GeeGuard*"` | GeeGuardManagerTest 全绿（decideToken 状态矩阵/空 token 不注入/未 init no-op） | P0 | S | AI 自主 |
| TC-S5 | 日志语句静态审计 | 通览 GeeGuardManager.kt 全部日志语句 | 只打长度/状态码/服务端错误原文，无 token/geeID 内容输出（支撑 TC-I3） | P0 | S | AI 自主 |

## A 组：SDK 初始化与 token 采集

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-A1 | 配置确认 | 冷启动 → 停首页 → `logcat` grep `PRETTY_LOGGER-GeeGuard` | 无 `not configured` 告警；数秒内 `submitReceipt ok, respondedGeeToken.length=<n>`（正常形态） | P0 | L1+L2 | AI 自主 |
| TC-A2 | 初始化不拖慢启动 | 冷启动，观察 StartupTaskManager 日志中 GeeGuard 任务耗时与首页展示 | 任务在子线程（`main=false`）、耗时 ms 级；首页正常展示无卡顿 | P0 | L1 | AI 自主 |
| TC-A3 | 断网启动降级 | 飞行模式 → 冷启动 → 停 30s → 看日志 | App 可用不 crash；`submitReceipt failed/crashed` + 重试记录，最多 1+2 次、间隔 2s 后停止；无 token 泄露 | P0 | L1 | AI 自主 |
| TC-A4 | 断网恢复后刷新 | 接 A3：恢复网络 → 触发风控场景（进语音房）两次 | 第 1 次可能不带/带降级 token；`refreshToken()` 触发刷新后第 2 次携带正常形态 token | P1 | L1 | AI 自主 |
| TC-A5 | 弱网采集 | emulator console 限速（gsm/edge）→ 冷启动 → 触发风控场景 | 业务操作不因采集变慢（取缓存 O(1)）；不 ANR | P1 | L1 | AI 自主（console 可用时） |
| TC-A6 | 杀进程重采集 | 完成 A1 → force-stop → 再冷启动 → 看日志 + 触发场景 | 每次冷启动重新走 register/submitReceipt（token 不跨进程持久化）；采集完成后请求带 token | P2 | L1 | AI 自主 |

## B 组：登录/注册（app_login）——危险清单命中，静态代验

> ⚠️ `app_login` 仅由「创建/导入钱包、登出重登」触发，全部命中 `dangerous-ops.md` 一类（丢登录态/新账号脏数据/助记词暴露），**本轮不实跑**（与 06-18 图灵盾轮同口径）。注入代码模式与 C/D 组完全一致（TC-S1），由 C/D 组实跑 + TC-S1/S2/S4 静态代验推广。

| # | 用例 | 处置 | 优先级 | 设备 | 执行方式 |
|---|------|------|--------|------|---------|
| TC-B1~B5 | 创建/导入/秒登/断网登录/多链登录 | ⏸️ 危险规避 → 由 TC-S1/S2/S4（静态+单测）+ TC-C/D 组同模式实跑代验；实跑留待人工窗口或后端联调轮 | P0→S | S | 静态代验 |

## C 组：语音房（liveroom/join）——本轮 body 注入核心实跑面 ★

> 取证：`logcat -s PRETTY_LOGGER-http_log_interceptor` 看 `liveroom/join` 请求 body 是否含 `gee_token` 及其长度（无需代理证书）。两机均在 `t.debox.pro`，可两机联测：5554 开房/房主进房，5556 深链加入（`debox://open/share?type=live&id=<roomId>`，LiveRoomScreen.md 已实证）。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-C1 | 普通用户加入 | 5554 开测试房 → 5556 深链弹「现在加入」→ 确认 → 抓 `liveroom/join` body | body 含 `gee_token`（非空，形态记录）；进房成功 | P0 | L1+L2 | AI 自主 |
| TC-C2 | 密码房加入 | 5554 建密码房 → 5556 输密码加入 → 抓包 | `password` 与 `gee_token` 同时携带；进房成功 | P1 | L1+L2 | AI 自主（可建密码房时） |
| TC-C4 | 房主直接进房 | 5554 房主从入口点自己的进行中语音房直接进房 → 抓包 | 直接进房分支（JoinSpace:123-129）同样携带 `gee_token` | P0 | L2 | AI 自主 |
| TC-C5 | token 未就绪加入 | 5556 force-stop → 冷启动后**立即**（<3s）深链加入 | 若缓存未就绪：body **不含** `gee_token` 字段（非空串）仍正常进房（fail-open）；就绪则带 token（记录实际） | P1 | L1 | AI 自主 |
| TC-C3 | 付费房加入 | —— | ⏸️ 资金操作 + 需 USD 余额，测试号 $0，留待有测试资金时 | P2 | — | ⏸️ 数据阻塞 |
| TC-C6 | 风控拦截表现 | —— | ⏸️ 需后端对测试设备下发拦截策略 | P1 | — | ⏸️ 外部依赖（后端） |

## D 组：红包（3 个领取接口）

> 危险操作口径（dangerous-ops §二）：发红包 = 资金操作，**用例明确要求 + 测试环境 + 测试号间小额闭环** 三条件满足后允许（同 06-18 轮先例）；仅发最小金额、最少必要次数。若测试号余额不足以发红包 → 标 ⏸️ 数据阻塞，不注资、不动真实账号。

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-D1 | 群聊普通红包 | 测试群内 5554 发最小额红包 → 5556 点开 →「开」→ 抓 `lucky_box/receive_lucky_box` | 参数含 `gee_token`；领取成功显示金额 | P0 | L1+L2 | AI 自主（余额允许时） |
| TC-D3 | 语音房红包 | 测试房内发红包 → 领取 → 抓 `liveroom/lucky_box/rob` | 参数含 `gee_token`；领取成功 | P0 | L1+L2 | AI 自主（余额允许时） |
| TC-D5 | 秒抢性能 | 红包出现立刻点「开」 | 点击响应无可感知延迟（token 取缓存不现场采集）；结果正常 | P1 | L1 | AI 自主（随 D1/D3） |
| TC-D6 | 领取失败恢复 | 点已抢完的红包「开」 | 按钮状态恢复可再点/显示「已抢完」态；不 crash | P1 | L1 | AI 自主（随 D1） |
| TC-D7 | token 未就绪领取 | 冷启动后立即进群抢红包 | 不带 `gee_token` 也正常领取（fail-open） | P1 | L1 | AI 自主（随 D1） |
| TC-D2 | 链上宝箱 | —— | ⏸️ 需链上资产发宝箱，测试号无链上资产 | P0→⏸️ | — | ⏸️ 数据阻塞 |
| TC-D4 | 专属红包 | —— | ⏸️ 需第三账号配合指定接收人（避免动真实账号） | P2 | — | ⏸️ 数据阻塞 |

## E 组：异常与降级（fail-open 核心）

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-E2 | 极验服务端故障模拟 | L2 root 注入：屏蔽 `*.geetest.com`（解析 IP 后 iptables 阻断，或 DNAT :53 劫持）→ 冷启动 → 触发场景 → 抓包 | 日志 `degrade to geeToken.length=<n>`（记录降级形态实际长度）；请求带降级 token 或不带；业务全部正常；测完按 devices.md 复原清单复原 | P1 | L2 | AI 自主 |
| TC-E4 | 全程无 crash | 全轮测试期间持续观察 logcat FATAL / mobile_get_crash | 无 GeeGuard/geetest/gtcore/UnsatisfiedLinkError 相关 crash（本地 logcat 口径；Firebase 平台不可及） | P0 | 全部 | AI 自主 |
| TC-E1 | AppID 未配置包 | —— | ⏸️ 需专门构建不配 AppID 的包并覆盖安装，风险大于收益；gate 逻辑（行 58-63）由 TC-S4 单测代验 | P1 | — | 静态代验 + ⏸️ |
| TC-E3 | 后端风控真拦截 | —— | ⏸️ 需后端下发高风险拦截策略 | P0 | — | ⏸️ 外部依赖（后端） |

## F 组：设备兼容

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-F4 | arm64 模拟器（风控识别） | 本轮 L1/L2 全程即 arm64 AVD：记录 token 形态 + 各场景实际放行/拦截表现 | SO 正常加载、token 正常携带；记录实际表现供后端比对（`emulate_check:2` 已下发，拦/放由后端拍板，不预设） | P1 | L1 | AI 自主（后端判定部分 ⏸️） |
| TC-F5 | root 设备 | 5554（debox_root，userdebug root）跑 A1+C4 | token 正常携带；记录风控判定表现；顺带复核 devices.md「root 必退 System.exit -5」是否复现 | P2 | L2 | AI 自主 |
| TC-F1 | 主流真机矩阵 | 三星 SM-S9210（测试环境）跑 A1+C1 | token 正常形态；⚠️ 小米为正式环境不参与 | P0 | L3 | ⏸️ 待真机人工解锁（白名单 §7.6.2），不阻塞其余用例 |
| TC-F2 | 32 位老机型 | —— | ⏸️ 无 armeabi-v7a 设备 | P1 | — | ⏸️ 设备缺失 |
| TC-F3 | x86_64 模拟器兜底 | —— | ⏸️ 宿主为 Apple Silicon，无法运行 x86_64 AVD；UnsatisfiedLinkError 兜底由 TC-S4 单测 + catch(Throwable) 静态代验（行 64-73） | P0→S | — | 静态代验 + ⏸️（需 Intel 机器） |

## G 组：新老版本兼容与升级

| # | 用例 | 处置 | 优先级 | 执行方式 |
|---|------|------|--------|---------|
| TC-G1/G2/G3 | 老版本回归 / 覆盖升级 / 新老共存 | ⏸️ 无线上老版本 APK 在手；且覆盖安装有登录态风险（§7.7 安装预检不满足则禁装）。留待发版前专项 | P0 | ⏸️ 外部依赖（老包） |
| TC-G4 | 后端双形态解析确认 | 客户端侧本轮产出两种形态证据（TC-A1 正常 + TC-E2 降级，含长度记录）→ 移交后端核对风控日志；后端确认部分 ⏸️ | P0 | 部分 AI 自主 + ⏸️ 后端 |

## H 组：性能

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-H1 | 冷启动耗时 | L1 冷启动 5 次取均值（`am start -W` TotalTime） | 无旧版基线 → 记录绝对值 + GeeGuard 任务耗时占比（实测 4ms 级）作为基线留档 | P1 | L1 | AI 自主（口径降级：记录制） |
| TC-H2 | 主线程无卡顿 | 启动/进房/领红包全流程观察 | 无 GeeGuard 相关主线程告警、无 ANR 对话框 | P2 | L1 | AI 自主 |
| TC-H3 | 后台流量 | App 挂后台 ≥10min 观察 logcat | 无 GeeGuard 周期性网络请求（仅业务触发刷新）；1h 口径留待长时窗口 | P2 | L1 | AI 自主（缩短口径） |

## I 组：运维支撑与日志安全

| # | 用例 | 步骤 | 验证标准 | 优先级 | 设备 | 执行方式 |
|---|------|------|----------|--------|------|---------|
| TC-I1 | 复制设备指纹 | `am start SettingAdminActivity` → 精准点 `btnCopyGeeId` → 读剪贴板 | 剪贴板得到非空 geeID。⚠️ admin 页含**切环境行**（危险清单）——只点复制按钮，先取元素再点，禁用旧坐标 | P1 | L1 | AI 自主 |
| TC-I2 | 指纹未就绪提示 | 断网冷启动 → 立即进 admin 页点复制 | toast「设备指纹未就绪，稍后再试」；不 crash、剪贴板不写空串 | P2 | L1 | AI 自主 |
| TC-I3 | 日志不泄露 token | 全轮导出 logcat：① `PRETTY_LOGGER-GeeGuard` 只有长度/状态码；② 检查 `http_log_interceptor` 打印的请求 body 中 `gee_token` 值是否脱敏（记录实际，若明文全量输出 → 记 finding 由开发定级） | P0 | L1+L2 | AI 自主 |
| TC-I4 | 白名单生效 | —— | ⏸️ 需先有拦截（TC-E3）+ 运维极验后台配合 | P2 | — | ⏸️ 外部依赖 |

---

## 危险操作专项标注（执行前逐条比对 `app-knowledge/dangerous-ops.md`）

1. **禁登出/清数据/卸载/切环境**（一类红线）：B 组因此全组静态代验；全轮「重置环境」仅 terminate → launch。
2. **多账号切换红线**（07-11 事故）：开测先核「我的」页 = 测试号（5554=`2309b9ea`，5556=`10b92305`）；页面跳转后重新取元素再点击，禁旧坐标盲点。
3. **SettingAdminActivity（TC-I1/I2）**：页内含「开发环境/正式环境/域名池」切环境行——只点 `btnCopyGeeId`，不碰环境行；误触弹「App必须重启」一律取消。
4. **发红包（TC-D1/D3）**：测试环境 + 测试号间最小额闭环，最少必要次数；红包弹窗内不点「提现」等资金出口；余额不足即停，不注资。
5. **语音房（TC-C 组）**：进房/开房允许；房内不点「发布到动态」「举报」「移除房间」。
6. **L2 注入（TC-E2）**：测完按 devices.md 复原清单复原（iptables -t nat -F OUTPUT 等），冷启验证恢复。

## 统计

| 分类 | 用例数 | AI 自主实跑 | 静态代验 | ⏸️（外部/设备/数据） |
|------|--------|------------|----------|---------------------|
| 0 静态核验 S | 5 | 5 | — | 0 |
| A 初始化采集 | 6 | 6 | 0 | 0 |
| B 登录注册 | 5 | 0 | 5（并 1 条记） | 0 |
| C 语音房 | 6 | 4 | 0 | 2 |
| D 红包 | 7 | 5（依赖余额） | 0 | 2 |
| E 异常降级 | 4 | 2 | 1 | 1 |
| F 设备兼容 | 5 | 2 | 1 | 2 |
| G 版本兼容 | 4 | 1（部分） | 0 | 3 |
| H 性能 | 3 | 3（口径降级） | 0 | 0 |
| I 运维日志 | 4 | 3 | 0 | 1 |
| **合计** | **49** | **31** | **7** | **11** |

## 执行顺序

1. **TC-S 静态组**（单测 + grep 核验）——任一 P0 失败阻断实跑。
2. **Phase 1 环境**：账号身份核验（两机「我的」页）→ 噪声预检 → 保活。
3. **A 组**（L1 为主，A3/A4 断网系列连跑）→ **C 组两机联测 ★**（C4 → C1 → C5 → C2）→ **D 组**（余额探明后决定实跑/⏸️）。
4. **E2 注入**（L2，测完复原）→ **I 组**（admin 页谨慎操作）→ **H 组** → 收尾 E4 全程 crash 扫描 + I3 日志审计。
5. 结果全部写入 results.md；⏸️ 项逐条注明阻塞原因与移交对象（后端/运维/真机窗口）。

---

## 📋 需测但未测项汇总（2026-07-15 复核，实事求是）

> 说明：以下为**需要测试但截至目前尚未测试**的用例。状态含义——⏸️ 完全没测（前置/外部条件不满足，无法执行）；⚠️ 只测了一侧（另一侧未覆盖）。**未测 = 未验证的风险，既不算通过也不算失败。** 能判「通过」的只有 results.md 里标 ✅ PASS 的项。

| 用例 | 要测什么 | 状态 | 为什么没测 / 解除条件 |
|---|---|---|---|
| TC-D1/D2/D3 | 红包 3 个领取接口注入 | ⏸️ 完全没测 | 需链上 BNB gas（用户已指示跳过红包） |
| TC-D5/D6/D7 | 红包秒抢 / 领取失败恢复 / 未就绪领取 | ⏸️ 完全没测 | 同上，随红包 |
| TC-D4 | 专属红包（指定接收人） | ⏸️ 完全没测 | 需第三个测试账号 |
| TC-C3 | 付费房加入注入 | ⏸️ 完全没测 | 需测试号有 USD 余额（测试号 $0），按危险红线不注资 |
| TC-C6 | 风控拦截时的客户端 UI 表现 | ⏸️ 完全没测 | 需后端对 `liveroom/join` 下发高风险拦截策略 |
| TC-F2 | 32 位 armeabi-v7a 兼容 | ⏸️ 完全没测 | 现代 emulator 不支持 arm32，需真 32 位 ARM 设备 |
| TG-4（token 开关关闭态） | 开关关时请求不带 gee_token 的真机端到端 | ⏸️ 完全没测 | 需后端下发 `geetest_token_check=false`；目前仅单测覆盖逻辑 |
| TC-B2/B3/B4/B5 | 创建钱包 / 秒登 / 断网登录 / 多链登录 的注入 | ⚠️ 仅静态代验 | 命中危险清单（登出/新账号），只做代码核对未真机实跑（B1 的 app_login 第 1 轮意外抓到过） |
| TC-I2 | 指纹未就绪提示 | ⚠️ 无法构造 | 未就绪窗口 <2s，用户实际触达不到（非缺陷，但没真验到） |
| TC-G4 | 后端能否解析正常/降级两种 token 形态 | ⚠️ 只做了客户端侧 | 客户端已产出两形态证据，后端核对未完成，待后端 |

> 另需知悉：本轮**唯一 FAIL = TC-F3（SO 加载失败必崩，FINDING-002，P0，未修）**；FINDING-001（gee_token 明文进 debug 日志）为部分通过遗留项。
