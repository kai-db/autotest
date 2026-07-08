# DApp / 签名 黑盒测试入口（Web3 / WebFragment）

> 测签名弹窗、Web3 provider、DApp 交互崩溃时先读本文件。核心：**用内置 `ProviderTestActivity` + 自建本地测试 dApp**，能黑盒触达真实 `WebFragment` 签名链路。
> 首次沉淀：2026-07-08（P-3 崩溃修复回归 TC-T-002 补测）。

## 关键纠正（易踩坑）

- ❌ 旧误判：「App 内 DApp 浏览器走 RN 容器，碰不到 `WebFragment`」。
- ✅ 实况：**`WebFragment`（`business/BaseModule/.../web/WebFragment.kt`）就是原生 Web3 签名链路所在**，可通过内置测试 Activity 黑盒拉起。签名弹窗、`handleSignMessage`、`onSign*` 回调全在这里。

## 入口：ProviderTestActivity（内置测试页）

- 类：`com.app.business.base.module.web3.ProviderTestActivity`（BaseModule，manifest 已声明）。
- 作用：接受任意 `extra_test_url` → 挂 `WebFragment.newInstance(url)` → 复用**真实** `setupWeb3()`（真 listener / 真弹窗 / 真回包）。
- 正常入口：`SettingAdminActivity`（我的-管理员设置）里的 Provider Test 按钮，默认 URL `https://provider-dapp.pages.dev/`、`https://demo.reown.com/` 等。
- **黑盒拉起（需 root，如 L2）**：
  ```bash
  adb -s <dev> shell "su 0 am start -n com.tm.security.wallet/com.app.business.base.module.web3.ProviderTestActivity --es extra_test_url '<URL>'"
  ```
  intent extra key = `extra_test_url`。

## 自建测试 dApp（本地页面）

- **provider 注入不卡 host**：`Web3View` 在 `onPageStarted` 对**任意 URL** 注入 provider 脚本（约 81048 字符），全局对象 = **`window.ethereum`**（EIP-1193 `request({method,params})`）。
- 原生桥对象 = **`window.DeBoxBlockChain`**（`addJavascriptInterface(signCallbackJSInterface,"DeBoxBlockChain")`）；`@JavascriptInterface` 方法：`signMessage / signPersonalMessage / signTypedMessage / requestAccounts / signTransaction / walletSwitchEthereumChain …`。可绕过 `window.ethereum` 包装直接调，用于构造畸形输入。
- **本地页面可达性**：`WebHttpAccessGate` 显式放行 `10.0.2.2 / localhost / 私网段`。Mac 上 `python3 -m http.server 8899`，模拟器用 `http://10.0.2.2:8899/<page>.html` 访问。
- 最小页面：`window.ethereum.request({method:'eth_requestAccounts'})` 连接 → 再发 `personal_sign` 等。（参考本轮 scratchpad 里的 `mal.html`，含 connect / 各类 mal / normal / raw-bridge 按钮。）

## 签名弹窗与红线

- 弹窗 = `DeBoxSignDialogFragment`（logcat tag `button3`，payload 打印 `{chainId,data,origin,title}`）。
- 取消 → `[bridge][native] respond id=… error reason=cancelled`，web 侧收到回调不卡死。
- **红线（见 [dangerous-ops](dangerous-ops.md)）**：只验「弹窗出现 + 可取消」，**绝不点「确定/签名/Approve」**；仅在 **L2 测试钱包（$0）** 做，真机真钱包只读。

## 崩溃点触达性备忘（2.14.1 崩溃修复回归实测）

- **P-3 `WebFragment.handleSignMessage` 空 `userMessage` NPE（`7c5850d5`）黑盒造不出**：`EthereumMessage` 构造函数 `message==null?"":message` —— personal_sign/eth_sign/signMessage 的 `userMessage` **结构上永不为 null**；typed 路径 `parseV3(null)` 不抛、走 V3 非 fallback。修复的 `!isV4 && userMessage==null` 是正常桥到不了的窄边界（Crashlytics 仅 1 次）→ 单测 `SignMessageAbortPolicyTest` 收口。**但「签名弹窗无回归」可黑盒坐实**（弹窗出现+可取消+多畸形输入不崩）。
- **P-7 `TextWithImageView.setIcons` 超长文本 OOM（`574292b7`）黑盒结构上不可达**：全仓仅 `SessionListFragment`（761/765）两处调用，且都先 `tvTitleIcon.text=""` → `originalText` 恒空，OOM 分支走不到；会话名实际由 `DiDView`(tvDiDTitle) 渲染。→ 单测 `SpanTextCapTest` 收口。
