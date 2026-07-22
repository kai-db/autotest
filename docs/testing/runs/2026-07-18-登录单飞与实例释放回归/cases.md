# 测试用例 — 登录单飞、后台签名、鉴权熔断与实例释放回归

> 优先级：P0 = 核心登录链必须通过；P1 = 第三方钱包重要路径；P2 = 并发/边界。  
> 设备：L1=`emulator-5554` / Pixel_10_Pro_XL；L2=`emulator-5556` / debox_root；L3=真机确认。执行前以系统属性重新核验映射。  
> 本 run 必须独立执行并记录；不得复用 7 月 17 日 run 的 PASS，只可共享同一经哈希确认的 APK。

## 项目信息

| 属性 | 值 |
|---|---|
| 测试目标 | walletLogin 单飞、签名派生移出主线程、鉴权熔断、BTC 桥/派生/签名/PSBT 兼容、实例 dispose、切钱包、登出重登、后台清理 |
| App 包名 | `com.tm.security.wallet` |
| 代码范围 | `b8fd28ddffca966c4cd9ff3b97e6dd1c1107e51f`（含）至冻结 HEAD `e0650d2b1386d8a527893f5301fb431f98b55dbe`，以及 `evidence/phase2/00-source-baseline.txt` 所列全部未提交改动 |
| 关键归因 | G3/G4/G5、C1/C2/C3 与登录/实例修复已在 `c27bf78f54`；未提交范围主要是 BaseModule BTC JS↔Native androidTest 与构建依赖，不再误写为“G3/G4/G5 未提交” |
| 测试方式 | AutoTest 规范 + JVM/androidTest + gated instrumentation harness + adb/UIAutomator/logcat |
| 全局验收 | 无新增 FATAL/ANR；旧 attempt 不污染新认证态；敏感数据不进入证据 |

## 全局执行契约与红线

1. 按 P0→P1→P2 执行。除验证在途/前后台的 case 外，每条先 force-stop 冷启；每次点击前重新获取 layout。
2. 用户已授权切账号/钱包、登出、创建账号和被踢。账号无需零资产，但必须是已备份、可恢复且允许测试的账号；正式环境真机只读。
3. 禁止 `pm clear`、卸载、切环境/测试链、删除钱包/账号、读取/导出/截图助记词或私钥、确认 DApp 签名、Approve、支付或转账。TC-F-003 因需要删除最后钱包继续保持 BLOCKED。
4. harness 只能位于 `androidTestAutotest`，日志只能输出固定事件/阶段/计数；不读取或打印 token、AuthInputs、签名、地址或密钥。
5. 网络规则只在 L2 按 UID 精确加/删；iptables 只能制造网络阶段失败，不能被写成 `-2007/-2018/-2023` 的业务码来源。

## 0. 前置检查（P0，任一阻断则停止设备执行）

| # | 用例 / 来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-P-001 | AutoTest 框架自检 | 执行 `:autotest:compileReleaseKotlin`、`:autotest:test`、`:autotest:publishToMavenLocal` | 全部 exit 0，测试 0 failure，AAR 发布成功 | P0 / 主机 | 无设备副作用 |
| TC-P-002 | 环境与账号基线 | 核验 L1/L2 测试环境、网络、设备时间；选择已备份且可恢复的测试账号/钱包；记录掩码 ID 与钱包类型 | 环境可写且账号可恢复；不要求零资产；正式环境设备不参与写操作 | P0 / L1+L2 | 不查看密钥；不因有资产而擅自做资金操作 |
| TC-P-003 | 被测 build 归因 | 冻结 HEAD/status/diff 指纹；构建 app+test APK；记录哈希、版本、签名；比对现装签名/versionCode 后 `install -r`；核验 debuggable/harness | APK 与本 run results 哈希一致；不降级/同签名；run-as 与 instrumentation 可用；正常产物不包含 harness | P0 / L1+L2 | 签名不一致 BLOCKED，禁止卸载绕过 |
| TC-P-004 | 钱包与第三方物料 | 盘点已备份助记词/BTC钱包、私钥钱包、第二本地钱包、TP/WC 测试物料；只记类型/数量/掩码 ID | 本地两类钱包可测；TP/WC 缺失只阻塞 F-008/F-009；BTC 类型基线可追踪 | P0 / L1+L2 | 不进入导出/删除；不记录完整地址 |

## 1. 冒烟测试（P0）

| # | 用例 / 来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-S-001 | 助记词钱包正常登录 / 4c220c、c27bf78 | 选择已备份助记词钱包；清日志后冷启，等待 ≤60s | 恰一次 `app_login ok channel=`；首页账号正确；无 abort/stale 提交/FATAL/ANR | P0 / L1 | 不打开助记词页 |
| TC-S-002 | 私钥钱包正常登录 / c27bf78 | 切到已备份私钥钱包，force-stop 后冷启 | 恰一次登录成功；无 LoginOut/重试风暴、abort、FATAL/ANR | P0 / L1 | 不打开私钥详情 |
| TC-S-003 | 手动切钱包 / c27bf78 | 从钱包 A 切到已备份钱包 B；等待登录和同步收口 | B 新世代恰一次成功，UI/持久化均为 B；A 的迟到回包无状态提交 | P0 / L1 | 用户已授权；不选未知/不可恢复钱包 |
| TC-S-004 | 登出并重新登录 / c27bf78 | 在已备份账号执行 UI 登出；确认登出态并观察 10s；再用同账号登录 | 登出后旧 attempt 不再成功；重登恰一次成功；首页/IM/同步恢复；无 FATAL/ANR | P0 / L1 | 用户已授权；禁止用清数据代替登出 |

## 2. 专项功能测试

| # | 用例 / 来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-F-001 | C1：同输入单飞且零副作用 / c27bf78 | 使用 `AccountAuthInjectionHarness#repeatSameWallet` 在登录在途连续调用同钱包 8 次；另执行 `LoginAttemptCoordinatorTest` 同 key 并发矩阵 | harness `start/posted` 各 1；`setWallet skip: same attempt in flight`≥1；窗口 `app_login ok`≤1；合并请求不换认证世代、不广播额外 LoginOut；JVM 矩阵全过 | P0 / L2+主机 | harness 不输出钱包/AuthInputs；case 自身不做中间 reset |
| TC-F-002 | C2：实例释放后旧回调零提交 / c27bf78 | 使用 `AccountAuthInjectionHarness#releaseInstanceDuringLogin`；同时抓 PID/日志；随后从 launcher 冷启；执行协调器 dispose JVM 矩阵 | dispose 后旧 epoch 的 `canPublishEvents/tryDispatchLogin/canPersistLate` 均关；旧回调不得 SUCCESS/FAIL/写认证态；新实例可正常成功；无 FATAL/ANR | P0 / L2+主机 | 只释放实例/杀进程，不清数据；若 harness 影响 runner 进程，以日志和退出码如实判定 |
| TC-F-003 | C3：删除最后钱包作废在途 attempt / c27bf78 | **BLOCKED，不执行**：可靠设备触发需删除最后一个钱包/账号 | 不得记 PASS；只接受代码审查 + `invalidateCurrent()` 邻近 JVM 证据为补偿控制 | P0 / — | 用户未授权删除；不可逆且可能销毁私钥，禁止操作 |
| TC-F-004 | G5：同世代鉴权熔断 / c27bf78 | 主机运行 `AuthSyncBreakerTest` 与 `CacheServiceBatchKeyTest` 全矩阵；设备端仅在真实服务端失效/被踢窗口触发批量用户/收藏同步 | JVM 三业务码均首次 break、重复不再打点、stale/非鉴权不拉闸、分页停止；设备真实码出现时一次 `sync_auth_break`，随后 skip 且请求停止增长 | P0 / 主机+L1/L2 | 网络 DROP/DNAT 不得宣称制造鉴权业务码；设备无真实码则该层 BLOCKED |
| TC-F-005 | G5：换代自动放行 / c27bf78 | 承接真实被踢/失效；恢复并重新登录形成新 generation；触发相同同步；并执行 breaker 换代 JVM case | JVM `shouldSkip=false`；设备新世代无旧 block，用户/收藏同步可返回；旧分页不跨代落库 | P0 / 主机+L1/L2 | 不改 host/环境；只用真实登录换代 |
| TC-F-006 | G4：后台签名与 UI 响应 / c27bf78 | L1 助记词钱包冷启登录；以 250ms 周期采样前台 activity/PID，连续执行安全 Tab 切换；抓 `ANR in`、Input dispatch timeout、主线程长任务；L3仅确认 | 登录最终成功；采样期间 UI 指令持续响应；无 ANR/Input timeout/主线程签名长耗时；L1 是主判据，L3不具备条件时不阻塞 | P0 / L1(+L3) | 不用用户交互确认签名；L3 正式环境只读冷启 |
| TC-F-007 | BTC 派生落库与 JS↔Native 兼容 / c27bf78 + 未提交 BTC androidTest | 在 L2 独立执行 `BtcDerivationCompatTest`、`BtcMessageSignCompatTest`、`BtcPsbtCapabilityTest`；再用已备份助记词/BTC 钱包登录并读取类型/数量 | androidTest 实际执行数>0且 0 failure；公开向量的派生/消息签名验证一致；PSBT 支持边界按 oracle 明确；App BTC addressType 齐全、计数无重复；无 persist/post rejected | P0 / L2 | 不记录公开向量以外的地址/签名；绝不读取真实助记词 |
| TC-F-008 | TP/WC 缓存签名登录 / 023d031、c27bf78 | 仅在真实 TP/WC 测试物料存在时连接并登录；若弹窗出现只验证可取消 | 登录成功且缓存签名分支收口；无 abort/重复 LoginOut；弹窗可取消 | P1 / L1 | 缺物料记 BLOCKED；不以本地钱包替代；不点击确认/授权 |
| TC-F-009 | TP/WC 新签名接管 / 023d031、c27bf78 | TP/WC 登录后触发重验；取消一次，再触发拿到新的签名输入（不确认授权型交易） | 新 AuthInputs 必须新建 attempt 而非同输入合并；新登录可收口；旧 attempt 无状态提交 | P1 / L1 | 缺物料或无法在不确认签名下取新输入则 BLOCKED |

## 3. 稳定性与边界（P2）

| # | 用例 / 来源 | 前置与动作 | 机器判据 | 优先级 / 设备 | 复原与危险边界 |
|---|---|---|---|---|---|
| TC-T-001 | 释放与登录高频交错 / c27bf78 | “冷启→登录在途 force-stop→立即冷启→稳定”×10，每轮记录 PID/开始结束时间 | 10/10 无 FATAL/ANR；每轮最终成功或可恢复登出态；无旧 epoch SUCCESS/FAIL、SUCCESS→FAIL 反转 | P2 / L1 | 不清数据；首轮异常即保存证据停止 |
| TC-T-002 | 60s 租约兜底 / c27bf78 | 以 `LoginAttemptCoordinatorTest` 假单调时钟确定性推进 >60s；设备端可用精确网络 DROP 做补充，但不作为唯一证据 | JVM：同 key 在租约内合并、过期后新 epoch 接管、旧 finish 不影响新 attempt；设备补充若执行则恢复后能登录 | P2 / 主机(+L2) | 不真实等待替代确定性 JVM oracle；网络规则精确复原 |
| TC-T-003 | refreshWallet 失败重试链 / 023d031、c27bf78 | 使用可归因的网络失败让 app_login 失败后恢复；观察 refreshWallet 重试 | 重试次数符合上限（≤4）；无重复 LoginOut 清 session；同 attempt 可出现 `walletLogin skip`；恢复后能收口 | P2 / L2 | 只制造网络失败，不伪造业务鉴权码；规则精确复原 |
| TC-T-004 | BTC 单路径失败与桥能力边界 / c27bf78 + 当前 BTC tests | 运行 `BtcDerivationAggregatorTest` 的 success/partial/failure/并发/重复 resolve；运行三组 BTC androidTest 的真实 JS/Native oracle；仅在存在真实可控 bridge fixture 时做设备单路径注入 | JVM 终态恰一次，单路径失败为 PARTIAL、其余结果保留；androidTest 非 fake fixture 且实际执行；无真实 bridge fixture 时设备注入层 BLOCKED，不影响确定性层结果 | P2 / 主机+L2 | 禁止为造失败修改真实钱包数据或密钥；不把 mock-only 结果写成端到端 PASS |
| TC-T-005 | 切钱包与同步交错 / c27bf78 | 用户/收藏分页同步在途时切到已备份另一钱包 | 旧 generation 分页停止且不落库；新账号首次同步不被旧批次 key 去重；无跨账号串写 | P2 / L1 | 用户已授权切换；不进入删除页 |

## BLOCKED 固定项：TC-F-003

TC-F-003 继续固定为 BLOCKED。用户授权切换、登出、创建账号和被踢，并未授权删除钱包；删除最后钱包可能不可逆销毁私钥。补偿证据只包括 `LoginAttemptCoordinator.invalidateCurrent()` 语义测试、dispose 邻近路径设备验证及接线审查，均不得把该 case 改记 PASS。解除条件是用户另行提供并明确授权可删除的一次性钱包环境。

## 统计与放行

| 分类 | 用例数 | P0 | P1 | P2 | 固定 BLOCKED |
|---|---:|---:|---:|---:|---:|
| 前置检查 | 4 | 4 | 0 | 0 | 0 |
| 冒烟 | 4 | 4 | 0 | 0 | 0 |
| 专项功能 | 9 | 7 | 2 | 0 | 1 |
| 稳定性/边界 | 5 | 0 | 0 | 5 | 0 |
| **登记合计** | **22** | **15** | **2** | **5** | **1** |

放行要求：除固定 BLOCKED 的 TC-F-003 及有充分客观前置证据的专项 BLOCKED 外，Phase 6 所有 case PASS；尤其 F-001/F-002 必须有 harness + 生产日志正向证据，F-004/F-005 不得用 iptables 假造鉴权码，F-007/T-004 必须记录 BTC androidTest 的真实执行数。
