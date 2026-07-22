# 测试结果 — 登录单飞与实例释放回归

> 测试日期：2026-07-18  
> 被测代码：`e0650d2b1386d8a527893f5301fb431f98b55dbe` + `evidence/phase2/00-source-baseline.txt` 所列未提交改动  
> 被测 APK：`7da4fe2b…c186837d6`（2.15.0 / 21500000；AutoTest 1.8.2；L1/L2 保数据安装成功）
> 状态：Phase 1 环境确认完成；本文件从空结果建立

## 环境确认

| 项目 | 当前结果 | 证据/备注 |
|---|---|---|
| 源码范围与工作区指纹 | ✅ 已冻结 | `evidence/phase2/00-source-baseline.txt`；指纹 `723e8403…1cf5a2` |
| 设备映射与现装版本 | ✅ 已冻结 | `evidence/phase2/00-device-baseline.txt`；安装前仍须重查 |
| AutoTest 自检/发布 | ✅ PASS | Debug/Release 各 323，0 failure/0 error；mavenLocal 成功；见 `01-unit-gates.txt` |
| 测试账号/钱包/TP-WC 物料 | ✅ PASS | `evidence/phase2/04-phase1-environment.txt`；L1/L2 账号与本地助记词/BTC 物料已核验；TP/WC 缺失仅阻塞 F-008/F-009 |
| App/test APK 哈希、签名、安装 | ✅ PASS | 同签名、无降级、L1/L2 `install -r` 成功；见 `02-build-install.txt` |

## Phase 2 首轮全量测试

| # | 用例 | 结果 | 证据/问题 |
|---:|---|---|---|
| 1 | TC-P-001 AutoTest 自检 | ✅ PASS | `evidence/phase2/01-unit-gates.txt` |
| 2 | TC-P-002 环境与账号基线 | ✅ PASS | `evidence/phase2/04-phase1-environment.txt` |
| 3 | TC-P-003 被测 build 归因 | ✅ PASS | `evidence/phase2/02-build-install.txt` |
| 4 | TC-P-004 钱包/第三方物料 | ✅ PASS | 本地助记词与 BTC 三路径可测；TP/WC 缺失已按专用用例隔离 |
| 5 | TC-S-001 助记词钱包登录 | ⏳ NOT_RUN | |
| 6 | TC-S-002 私钥钱包登录 | ⏳ NOT_RUN | |
| 7 | TC-S-003 手动切钱包 | ❌ FAIL | L2 切换后出现多轮 LoginOut/app_login/IM episode，未满足单轮收口；见 `evidence/phase2/12-l2-wallet-switch-login-storm.txt` |
| 8 | TC-S-004 登出并重新登录 | 🚫 BLOCKED | 当前安全 UI 只有“移除并退出账号”组合入口，会触发钱包删除；无独立登出入口，按危险边界不点击；见 `evidence/phase2/07-wallet-switch-exploration.txt` |
| 9 | TC-F-001 同输入单飞零副作用 | ✅ PASS | 两机 harness 8 次输入、7 次 skip、单 app_login；JVM 矩阵已 PASS；见 `evidence/phase2/06-auth-harness.txt` |
| 10 | TC-F-002 dispose 后旧回调零提交 | ⏳ NOT_RUN | 外部 force-stop 会连同 instrumentation 一并终止，无法据此观察旧回调是否提交；仅证明冷启动恢复，见 `evidence/phase2/09-background-cold-start.txt` |
| 11 | TC-F-003 删除最后钱包作废 attempt | 🚫 BLOCKED | 安全边界固定：未授权删除钱包；不得执行或记 PASS |
| 12 | TC-F-004 同世代鉴权熔断 | ⏳ NOT_RUN | |
| 13 | TC-F-005 换代自动放行 | ⏳ NOT_RUN | |
| 14 | TC-F-006 后台签名与 UI 响应 | ⏳ NOT_RUN | |
| 15 | TC-F-007 BTC 落库与兼容 | ⏳ NOT_RUN | 设备兼容子集已通过；尚需按 case 完成已备份钱包登录、类型/数量与落库核对；见 `evidence/phase2/05-btc-device-suite.txt` |
| 16 | TC-F-008 TP/WC 缓存签名登录 | 🚫 BLOCKED | 两台设备均无 TP/WC 测试物料 |
| 17 | TC-F-009 TP/WC 新签名接管 | 🚫 BLOCKED | 两台设备均无 TP/WC 测试物料 |
| 18 | TC-T-001 十轮释放/登录交错 | ⏳ NOT_RUN | |
| 19 | TC-T-002 60s 租约兜底 | ⏳ NOT_RUN | |
| 20 | TC-T-003 refreshWallet 失败重试 | ⏳ NOT_RUN | |
| 21 | TC-T-004 BTC 单路径/桥能力边界 | ✅ PASS | JVM 终态门禁已 PASS；真实三组 androidTest 10 条/0 failure；regtest 假设 1 条按客观前置 BLOCKED；见 `evidence/phase2/05-btc-device-suite.txt` |
| 22 | TC-T-005 切钱包与同步交错 | ⏳ NOT_RUN | |

**本轮统计**：PASS 2 / FAIL 1 / BLOCKED 3 / NOT_RUN 16。

## Phase 3 缺陷与归因

尚未进入 Phase 3。Phase 2 出现 FAIL 后先登记证据和根因，再通过 `agent-dev-loop` 建立独立修复任务。

## Phase 5 全量回归

⏳ NOT_RUN。除固定 BLOCKED 的 TC-F-003 外，21 条全部从头重跑；不复用本 run Phase 2 或另一 run 的 PASS。

## Phase 6 最终验收

⏳ NOT_RUN。最终轮禁止边测边改；21 条可执行 case 全 PASS 后才可放行。

## 环境复原

| 项目 | 状态 | 备注 |
|---|---|---|
| L2 iptables nat/filter OUTPUT | ⏳ NOT_RUN | 前后快照，精确 `-D` |
| App 进程与 instrumentation | ⏳ NOT_RUN | 清理 test runner，正常冷启 |
| 前台账号/钱包 | ⏳ NOT_RUN | 保留已备份可恢复账号 |
| 敏感信息审计 | ⏳ NOT_RUN | 只留固定事件/计数/掩码 ID |
