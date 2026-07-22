# Review — 设计 debox account-lifecycle 分支全量回归用例

> ③ 过程 & 问题。当前状态以 `index.md` 为准；本文件存各轮 review 与修复**过程历史**（per-round VERDICT 是快照，非当前状态）。
> 每轮必有 `VERDICT`；正式问题按 Critical / Important + confidence ≥ 80。

## Plan Review Log（主会话 Codex，原样入档）

### Plan Review Round 1 — 2026-07-19

```text
VERDICT: FAIL
```

#### Critical (block)

1. **[PL-CALLGRAPH-96]** [confidence 96] §3/§4 的调用方矩阵仍以 R-1/R-2/R-6“补证”占位，且已确认漏掉真实入口：HEAD 中 WalletRepository.add 除创建/导入外还有 CreateWalletDialogFragment 与 AppCacheManager.saveConnectWallet；saveConnectWallet 又由 WalletConnectFragment:208 和 MainAccountCoordinator:363 调用，新增 onError 日志是净行为。setWallet 还由 SwitchChainDialogFragment、SwitchWalletDialogFragment、WalletAddressActivity、WalletConnectFragment、MainAccountCoordinator 等调用。接受计划前必须完成这些调用方逐项定性：行为会变则建 case，行为屏障则写证据；不得保留影响覆盖相关 TODO。
2. **[PL-KICK-CONTRACT-97]** [confidence 97] D11 漏了 16aafaf5 明确新增的两个用户契约：AccountTokenState(manual=true) 手动恢复绕过终态门禁、IMNetStateView 1500ms 连点去抖（快速多击只能触发一次恢复且出现 netstate_click_debounced）。同时未纳入该提交文档明示的 AR-A3/A4/A5 边界：业务错误早于 11011 的一次自动重签/无本地上界、旧连接迟到 11011 缺 connection identity、终态写盘失败 fail-open。必须各自给可执行注入/替代门禁或明确 verification gap，不能用笼统“双机风暴”覆盖。当前双机方案还假设“5556+新 L1 同账号”，现场并无该前提；应优先落到已知同账号的 5556+Samsung，并定义签名/版本不兼容时的单机注入 fallback 与证据强度。
3. **[PL-BTC-TRANSFER-95]** [confidence 95] BaseTransferActivity 的净改动已经明确：BTC 分支删除旧 sendTransaction 调用，改为 Toast “BTC transfer is not supported yet.”；BtcManager 同时删除 load/query/send transaction，SettingAdmin 去掉 WebBTC network update。v1 将其写成“改动定性后补”，会漏掉用户可见回归。必须新增 BTC 转账不支持的安全 UI case（到密码校验/提示边界，不签名不广播），并对被移除入口、管理员 BTC 切链（危险禁测）分别给静态/单测门禁或行为屏障。

#### Important (must revise)

4. **[PL-THIRD-PARTY-WALLET-92]** [confidence 92] D6 的“建/导钱包”预算没有覆盖 third_party_import 的新增失败可观测性以及 WalletConnect/TP/WC 导入态失败策略。应拆出第三方连接钱包子域或在 D4/D6 明列成功、异常回包保钱包、验证态失败恢复、导入态 timeout 删除临时钱包与非 timeout refresh 的契约；任何危险删除必须只对本轮临时测试钱包。
5. **[PL-BUILD-BASELINE-88]** [confidence 88] clean worktree 计划不可直接执行：新 worktree 默认没有未跟踪 local.properties 与 ReactNative/node_modules，但 v1 使用 NODE_PATH=/Users/xiaochengcheng/StudioProjects/autotest/ReactNative/node_modules；也未写 worktree 移除/临时 shim 恢复门禁。需给不打印 secret 的安全接线（只引用原仓 local.properties、显式 NODE_PATH 指向原仓 node_modules 或只读 symlink），验证 worktree status clean、构建后恢复 shim、最终 git worktree remove 明确路径；不得触碰原 4 个脏文件。
6. **[PL-BUDGET-87]** [confidence 87] §4 行预算实际合计 67 条，优先级合计 P0=37/P1=23/P2=7；文中的≈60、52功能+5、~32/23/7 相互矛盾，破坏产能/统计门禁。修订后从逐行表机械汇总，补上因上述 finding 新增的 case 数，统一所有数字。

#### Required Changes

- 完成 R-1/R-2/R-6，不得把关键覆盖证据推迟到 Accepted Plan 后；特别补证 harness=androidTest-only（已确认路径 app/src/androidTestAutotest 且 app.gradle gated）并落行为屏障。
- 逐项修正 D11、D8/D12、D6/D4 与设备/构建策略。
- 给 v2 精确、自洽的域数、case 数和 P0/P1/P2 统计。
- Plan Revision Log 逐条映射 PL-*；Accepted Plan 仍保持空白，等待 Round 2。

#### Round 1 处置表（Claude，均已在 plan.md v2 落实）

| Finding | 处置 | 补证证据（只读 git show/grep，@bd165ef3ee） |
| --- | --- | --- |
| PL-CALLGRAPH-96 | **修复**：§3 D6/D13 调用方逐项定性入矩阵，R-1/R-2/R-6 全部闭合，无覆盖类 TODO 残留 | `saveConnectWallet` 定义 AppCacheManager.kt:976，调用方 WalletConnectFragment.kt:208 + MainAccountCoordinator.kt:363；`setWallet(` 调用方 AccountUtils:124/SwitchChainDialogFragment:214/WalletRepository:104/WalletConnectFragment:197/SplashScreenActivity:167/MainAccountCoordinator:290,394/WalletAddressActivity:220/harness:54,62；WalletConnectDialogFragment:103,147 为 `WalletConnectV2Manager.setWallet`＝不同符号（屏障）；`SwitchWalletDialogFragment` 在 HEAD grep 未直接命中 `AppCacheManager.setWallet`——按保守原则并入 D13 切换面动态验证 |
| PL-KICK-CONTRACT-97 | **修复**：D11 增 manual=true 绕行、1500ms 去抖 2 条 case；AR-A3/A5 各给 L2/harness 注入 case，AR-A4 登记 verification gap+源码级替代门禁；双机改为 5556+Samsung，定义单机注入 fallback 与证据强度 | IMNetStateView.kt:57 `FLogger.w("Login","netstate_click_debounced")`、:108-110 `AccountTokenState(NEED_AGAIN_LOGIN, manual = true)`、:230 `CLICK_DEBOUNCE_MS = 1500L`；AR-A3/A4/A5 见 debox `docs/implementation/2026-07-19-01-fix-cross-device-kick-storm/{index,plan}.md`；AccountTokenState posters=IMNetStateView/RN WalletBridgeService:1689/AccountNativeCallHandler:93/MainBusinessModel:420/harness:98；SessionFragment.kt:573 注释证实不再发（b68357ce 修复点） |
| PL-BTC-TRANSFER-95 | **修复**：D8 增「BTC 转账不支持」安全 UI case（P0，到 Toast 边界）+ 移除入口静态门禁 case（P1）；SettingAdmin BTC 切链=危险禁测，改静态屏障 | BaseTransferActivity BTC 分支 diff：删 `BtcManager.sendTransaction(...)` 改 `ToastUtil.showMessage("BTC transfer is not supported yet.")`，注释证实旧路径 `mBtcBalance` 全仓无赋值恒早退；SettingAdminActivity diff 删 `WebBTCManager.updateNetworkData()`；`WebBTCManager` 残留仅注释/docs，`btc.html` 已不在 tree（ls-tree grep 空） |
| PL-THIRD-PARTY-WALLET-92 | **修复**：新增 D13 第三方连接/导入钱包域 5 条（成功落库、异常回包保钱包、third_party_import 失败可观测、timeout 删临时钱包（仅本轮临时测试钱包）、非 timeout refresh） | AppCacheManager.kt:1024 `FLogger.e("Wallet","event=wallet_add_failed scene=third_party_import")` |
| PL-BUILD-BASELINE-88 | **修复**：§1 构建接线重写（local.properties 只引用不打印、NODE_PATH 显式指原 debox 仓 node_modules、status clean 门禁、shim 还原、`git worktree remove` 明确路径、全程 `-C <worktree>` 不触原仓） | 见 plan.md §1（v2） |
| PL-BUDGET-87 | **修复**：逐行表机械汇总（awk 自校验通过）：14 行 78 条=P0 42/P1 29/P2 7，全文数字统一 | 见 plan.md §4（v2）+ 本文件 Verification |

## Consult Log

None（本会话按用户指令不调用 Codex；plan-review 由主会话 Codex 执行）。

## Accepted Risk Log

（Accepted Plan 未落，暂 None；AR-A4 类 verification gap 在 plan.md §3/§10 登记，待 Round 2 裁决后如需转 Accepted Risk 再入本表）

| Finding | Owner | Reason | Follow-up | Expiry |
| --- | --- | --- | --- | --- |

## Verification

- 命令与结果：R1 补证全部为只读命令（`git grep -n ... bd165ef3ee`、`git show`、`git diff b8fd28ddff^..bd165ef3ee -- <file>`、`git ls-tree -r`），退出码均 0（`WebBTCManager`/`btc.html` 残留检索按预期空命中）；plan.md v2 §4 预算表用 awk 逐行重新求和核对（78=42+29+7，与文字一致）。debox 仓库零写入，4 个脏文件未触碰（`git status --porcelain` 仍仅原 4 行）。
- Secret 扫描已完成、未记录命中行：是（本目录无 token/密钥/助记词/账号密码内容）。

## Final Review

（待 Plan Review Round 2 及后续 impl-review）
