# 测试结果 — 账号重验与网络可观测性

> 测试日期：2026-07-18  
> 被测代码：`e0650d2b1386d8a527893f5301fb431f98b55dbe` + `evidence/phase2/00-source-baseline.txt` 所列未提交改动  
> 被测 APK：`7da4fe2b…c186837d6`（2.15.0 / 21500000；AutoTest 1.8.2；L1/L2 保数据安装成功）
> 状态：Phase 1 环境确认完成；2026-07-17 的旧结果全部作废，本文件从空结果重建

## 环境确认

| 项目 | 当前结果 | 证据/备注 |
|---|---|---|
| 源码范围与工作区指纹 | ✅ 已冻结 | `evidence/phase2/00-source-baseline.txt`；指纹 `723e8403…1cf5a2` |
| 设备映射与现装版本 | ✅ 已冻结 | `evidence/phase2/00-device-baseline.txt`；安装前仍须重查 |
| AutoTest 自检/发布 | ✅ PASS | Debug/Release 各 323，0 failure/0 error；mavenLocal 成功；见 `01-unit-gates.txt` |
| DeBox JVM/编译/BTC test APK | ✅ PASS | selected 92，0 failure/0 error；模块编译与 BaseModule test APK 构建成功 |
| App/test APK 哈希、签名、保数据安装 | ✅ PASS | 同签名、无降级、L1/L2 `install -r` 成功；见 `02-build-install.txt` |
| 测试环境、账号与钱包物料 | ✅ PASS | `evidence/phase2/04-phase1-environment.txt`；L1/L2 测试环境、测试账号、5/3 个本地助记词钱包与 BTC 三路径已核验；TP/WC 缺失只影响对应专用 case |

## Phase 2 首轮全量测试

| # | 用例 | 结果 | 证据/问题 |
|---:|---|---|---|
| 1 | TC-P-001 AutoTest 自检与发布 | ✅ PASS | `evidence/phase2/01-unit-gates.txt` |
| 2 | TC-P-002 DeBox JVM/编译门禁 | ✅ PASS | selected 92/92；BaseModule androidTest APK 已生成 |
| 3 | TC-P-003 APK 构建/签名/归因 | ✅ PASS | `evidence/phase2/02-build-install.txt` |
| 4 | TC-P-004 环境/账号/钱包物料 | ✅ PASS | `evidence/phase2/04-phase1-environment.txt` |
| 5 | TC-A-001 -2007 自动重签重登 | ✅ PASS | `evidence/phase2/06-auth-harness.txt`；两机各 1 episode/auto_resign/app_login |
| 6 | TC-A-002 -2018 自动重签重登 | ✅ PASS | `evidence/phase2/06-auth-harness.txt` |
| 7 | TC-A-003 -2023 自动重签重登 | ✅ PASS | `evidence/phase2/06-auth-harness.txt` |
| 8 | TC-A-004 高频失效同周期抑制 | ✅ PASS | 两机 burst=8 均压为单 episode、单自动重签、单 app_login |
| 9 | TC-A-005 SUCCESS 后门禁重开 | ⏳ NOT_RUN | |
| 10 | TC-A-006 旧认证世代回包抑制 | ⏳ NOT_RUN | |
| 11 | TC-A-007 TP/WC 重验失败重弹 | 🚫 BLOCKED | 两台设备均无 TP/WC 测试物料，不能用本地钱包替代 |
| 12 | TC-A-008 CacheService 鉴权拉闸 | 🚫 BLOCKED | JVM 矩阵已 PASS，但设备侧没有真实鉴权失效/被踢业务码，网络夹具不能伪造该前置 |
| 13 | TC-A-009 换代后同步恢复 | ⏳ NOT_RUN | |
| 14 | TC-A-010 正常使用阴性观察 | ⏳ NOT_RUN | |
| 15 | TC-B-001 connect-refused call_id | ⏳ NOT_RUN | |
| 16 | TC-B-002 失败日志脱敏 | ⏳ NOT_RUN | |
| 17 | TC-B-003 connect-timeout | ⏳ NOT_RUN | |
| 18 | TC-B-004 业务/网络错误分离 | ⏳ NOT_RUN | |
| 19 | TC-B-005 Sol call_id | 🚫 BLOCKED | 当前测试钱包无可用 SOL 请求入口，无法形成真实链路 |
| 20 | TC-C-001 JIM 冷启动 episode | ❌ FAIL | 单次冷启动出现 3 个 `jim_connect_start`，最终仅 `-4` connected；见 `evidence/phase2/12-cold-login-im-episode.txt` |
| 21 | TC-C-002 断网恢复 episode | ⏳ NOT_RUN | |
| 22 | TC-C-003 同账号多端被踢 | ⏳ NOT_RUN | |
| 23 | TC-C-004 被踢后重登与 IM 恢复 | ⏳ NOT_RUN | |
| 24 | TC-D-001 助记词/BTC 登录与兼容 | ⏳ NOT_RUN | |
| 25 | TC-D-002 私钥钱包登录 | ⏳ NOT_RUN | |
| 26 | TC-D-003 同钱包重复登录单飞 | ✅ PASS | 两机 harness count=8；各 7 次 same-attempt skip；最终 app_login=1；见 `06-auth-harness.txt` |
| 27 | TC-D-004 登录中切钱包 | ⏳ NOT_RUN | 已做普通切钱包探索；尚未制造登录在途延迟窗口，且 L2 目标钱包观察窗未成功，不能替代本 case；见 `evidence/phase2/07-wallet-switch-exploration.txt` |
| 28 | TC-D-005 创建账号与重启保持 | ❌ FAIL | 创建界面成功且出现 `Wallet-1`，但后续删除/重启复测导致 L1 物料从 5 个降为 1 个、BTC 物料消失；见 `evidence/phase2/14-create-wallet-regression.txt` |
| 29 | TC-D-006 登出后重新登录 | 🚫 BLOCKED | 当前安全 UI 未提供独立登出；唯一相关入口与移除钱包绑定，不能以删除钱包代替登出；见 `evidence/phase2/07-wallet-switch-exploration.txt` |
| 30 | TC-E-001 后台清理/冷启实例释放 | ✅ PASS | L1 在 `releaseInstanceDuringLogin` 在途窗口 force-stop 后冷启动成功，无 FATAL/ANR；见 `evidence/phase2/09-background-cold-start.txt` |
| 31 | TC-E-002 十轮释放与登录交错 | ⏳ NOT_RUN | |
| 32 | TC-E-003 四 Tab 业务冒烟 | ✅ PASS | L1 稳定账号依次访问首页/消息/发现/我的并冷启动复核，无 FATAL/ANR；见 `evidence/phase2/11-four-tab-smoke.txt` |

**本轮统计**：PASS 5 / FAIL 2 / BLOCKED 3 / NOT_RUN 22。

## Phase 3 缺陷与归因

### BUG-001：冷启动 IM 重复创建连接 episode

见 `evidence/phase2/12-cold-login-im-episode.txt`。

### BUG-002：创建/删除 disposable 钱包导致 L1 其他钱包物料消失

现象、基线对比及 `AccountUtils.delete()` 定位见 `evidence/phase2/14-create-wallet-regression.txt`。在恢复备份前不继续执行删除操作。

## Phase 5 全量回归

⏳ NOT_RUN。无论 Phase 2 是否发现缺陷，进入本阶段时按全部 32 条重新执行，不复用 Phase 2 的 PASS。

## Phase 6 最终验收

⏳ NOT_RUN。此阶段禁止边测边改，按全部 32 条再执行一轮；只有可执行项全 PASS 才能结束。

## 环境复原

| 项目 | 状态 | 备注 |
|---|---|---|
| L2 iptables nat/filter OUTPUT | ⏳ NOT_RUN | 每条网络 case 前后快照并精确 `-D` |
| 设备网络与屏幕保活 | ⏳ NOT_RUN | |
| App 前台账号/钱包 | ⏳ NOT_RUN | 最终保留已备份、可恢复账号 |
| 敏感信息审计 | ⏳ NOT_RUN | 证据不得包含 token/签名/密钥/完整地址 |
