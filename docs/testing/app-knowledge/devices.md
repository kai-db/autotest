# 设备清单与能力矩阵（devices）

> 知识库文件：测试开始前先读，选设备按 `TEST_GUIDE.md` 第七节「设备策略（emulator-first）」路由。
> 探索中发现的设备新特性/新噪声，当轮回写本文件（谁探索谁回写）。

## 设备清单

| 层级 | 设备 | 标识 | root | 锁屏 | 环境 | 备注 |
|---|---|---|---|---|---|---|
| **L1** | AVD `Pixel_10_Pro_XL` | emulator-55xx | ❌ | 无（永不锁屏） | 按需装包 | 默认执行层：UI/功能黑盒、B 模式回归 |
| **L2** | AVD `debox_root` | emulator-55xx | ✅（userdebug，`adb root` uid=0） | 无（永不锁屏） | 测试钱包（本地新建） | 注入层：DNS/iptables 故障注入、双网切换。**2026-07-14 复核：含 GeeGuard v2.7.1.1 的 2.14.2 可正常冷启、采集和实跑，未复现 `System.exit -5`**；L2 可执行需 root 的 App 用例，但每轮仍先冷启验活（历史上的 root 早退结论已作废，详见下文） |
| **L3** | 小米 25067PYE3C | `402714f0` | ❌ | 安全锁（AOD 指纹，adb 不可绕） | **正式环境** | 复核层；日常被使用，可能被接管/掉线。⚠️ **正式环境=真实账号真实资产 → 只做只读观察（冷启+logcat），禁一切 UI/业务操作**。2026-07-14：装 2.14.2，GeeGuard 在此机**自然降级**（`status=-300 → geeToken.length=4772`，极验服务不可达），App 无 crash |
| **L3** | 三星 SM-S9210 (S25) | `RFCYA0F9SSZ` | ❌ | 安全锁（Bouncer，adb 不可绕） | 测试环境 `t.debox.pro` | 复核层。**最后核验（2026-07-14 17:17）：设备已被外部换成 non-debuggable release 包**，无法用 PRETTY_LOGGER/行号探针做 GeeGuard 观察；此前 14:47 的 HEAD debug 2.14.2 实测证据仍有效。原 2.15.0 包备份于 `debox-android/apks/debox-2.15.0-samsung-backup.apk`。测真机前必须重新核 `versionName` + debuggable + 运行时探针。账号「请输入昵称1」Lv.16（**与 emulator-5556 同账号** → 两者不能互相加入对方语音房/领对方红包） |

> AVD 镜像事实：`debox_root` = `android-15;google_apis;arm64-v8a` userdebug。模拟器序列号以 `adb devices` 实际输出为准（5554/5556 会因启动顺序变化）。

> ⚠️ **GeeGuard 目标测试基线（2026-07-14 19:30 起）= `origin/dev` tip `29fa710b29`**（非旧 HEAD `7d9ad73`）。基线包 `apks/debox-dev-baseline.apk`（行号探针 **148**，sha256 `94484888bd29645e`）。**最后核验的装机状态（2026-07-15）**：5554 仍为 dev 基线；5556 因 CodePush 专项临时装为 feat/gasless debug 构造包 2.14.2/21400099，做 GeeGuard 回归前需恢复 dev 基线。**GeeGuard 日志行号：dev=148 / 旧 HEAD=130**——每轮开测先核版本、debuggable、行号与 APK sha256。dev 相比旧 HEAD 就 GeeGuard 只多 `geetest_token_check` token 开关（`80804ae97c`）+ 2 个 `TokenVerifyManager`(DID 校验)修复（`d637`/`acb038`，后者是 autotest 反馈的 BUG-001）。

### 版本兼容矩阵专用 AVD（2026-07-14 第 7 轮自建，验 GeeGuard SO 跨 Android 版本兼容）

| AVD | 镜像 | API/Android | 用途 |
|---|---|---|---|
| `debox_api25` | `android-25;google_apis;arm64-v8a` | 25 / 7.1（**DeBox minSdk 边界**） | GeeGuard SO 加载 + 采集**下限**验证：✅ `submitReceipt ok, length=436`、零 crash、零 UnsatisfiedLinkError |
| `debox_api30` | `android-30;google_apis;arm64-v8a` | 30 / 11（中间主流版本） | 同上，中间版本佐证 |

- **自建 AVD 命令**：`sdkmanager "system-images;android-<N>;google_apis;arm64-v8a"` → `avdmanager create avd -n <name> -k "..." -d pixel` → `emulator -avd <name> -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect`。
- **关键点**：GeeGuard 采集在冷启动 StartupTask 阶段（先于登录页）→ **新 AVD 无需登录即可验 SO 加载/采集/crash**（版本兼容性核心）。业务注入需登录且新设备会被风控拦截，本类 AVD 不验业务侧。
- ⚠️ 宿主 Apple Silicon **只能跑 arm64 镜像**；x86_64（TC-F3）与 arm32（TC-F2）AVD 均无法在本机运行（arm32 更是被现代 emulator 彻底弃用）。

## 能力矩阵（选层判据速查）

| 能力 | L1 普通模拟器 | L2 root 模拟器 | L3 真机 |
|---|---|---|---|
| UI 流 / 导航 / 弹窗 / 表单黑盒 | ✅ 默认 | ✅ | 可但不必 |
| B 模式 instrumented 回归 | ✅ 默认 | ✅ | 可（需解锁） |
| DNS/hosts/iptables 故障注入 | ❌ | ✅（DNAT :53 → 10.0.2.2 方案已实证） | ❌（非 root） |
| WiFi↔蜂窝切换 | ❌ | ✅（双网卡 + `svc wifi disable`） | ✅（需真 SIM） |
| 真实网络路径 / DNS 解析目标结论 | ⚠️ 有 fake-IP 代理噪声 | ⚠️ 同左 | ✅ 权威 |
| 厂商 ROM / 推送 / 性能 / 传感器 | ❌ | ❌ | ✅ 唯一 |
| 全程无人值守（不锁屏/不被接管） | ✅ | ✅ | ❌ |

## ⚠️⚠️ 模拟器被服务端风控拦截登录（2026-07-14 15:05 实锤，用户确认）

- **现象**：测试环境 `t.debox.pro` 上，两台模拟器反复掉 DID 登录态；重登 `app_login` 返 **`-2051`**（伴生 `account_state -2023`（`token_valid:false`）、业务接口 `-2018`），文案统一「系统繁忙」**具有迷惑性**——曾被连续误判为「后端未开/DID 不稳定」。
- **根因**：GeeGuard 服务端风控按设备指纹**拦截模拟器**（`emulate_check:2` 策略生效）；被拦请求 body 中 `gee_token` 完好（非客户端问题）。同环境三星真机全程放行（`code:1`）。
- **鉴别法**：真机同环境请求成功 + 模拟器 `-2051` → 是风控拦截；真机也失败 → 才是后端故障。
- **解除**：极验后台按 geeID（root_id）加白，风险码 60113。实际 geeID 跨会话稳定，文档不留存；按 `screens/SettingAdminScreen.md` 复制后通过安全渠道交给运维。5554/5556 均已完成加白。
- ✅ **2026-07-14 15:32 两台模拟器已加白并实测放行**（`app_login ok channel=JC`），当前可正常登录/建房/进房。
- ⏳ **加白生效有延迟**：加白后 0–5 分钟内仍会返 `-2051`（平台缓存），**约 5–10 分钟后转绿**——加完白不要立刻判失败，等一轮再测。
- ⚠️ **副作用**：模拟器已加白 → **无法再复现拦截场景**（TC-C6/E3 需拦截才能验）。要复现须先移出白名单或换未加白设备。

## ⚠️⚠️⚠️ 包归因铁律：**绝不把「设备上 pull 出来的包」当作 HEAD 包**（2026-07-14 事故，教训第 2 次复现）

- **事故**：第 5 轮做 TC-G2 覆盖升级时，从 emulator-5554 `pull` 出 APK 当「HEAD 包」用。实际上 **5554 在 16:15 被装了 HEAD 之外的分支包**（含 commit `80804ae97c` GeeGuardTokenGate，**AI 未执行该安装，疑为用户手动装**）→ 差点用错包给出 P0 结论。
- **识破手段（行号探针，已验证有效）**：日志打出 `GeeGuardManager.kt:148`，而 **HEAD 源码 148 行是注释**（`grep -n` 可查，130 行才是 `submitReceipt ok`）→ 日志调用点不可能落在注释上 → 立刻识破。
  - **`GeeGuardManager.kt:130`** = HEAD（`7d9ad736`）
  - **`:148`** = 含 `GeeGuardTokenGate` 的更新分支（`80804ae97c`，非 HEAD 祖先）
- **铁律**：
  1. 需要 HEAD 包 → **现场构建**（§7.7），别从设备 pull、别用 `apks/` 里的历史文件；
  2. 装包后**必须做归因验证**：`versionName` + **运行时行号探针** + APK sha256，三者对齐才算「被测包 = 目标代码」；
  3. **设备上的包随时可能被任何人替换**（用户、其他会话、App 自更新）——每轮开测前重新核验，别信上一轮的记忆。
- 这是 06-18「拿旧包/异版本包测新逻辑 = 假阴性/假阳性头号来源」的**第二次复现**（第一次：三星 2.15.0 重号包）。

## 💡 「需专门出包 / 需特定设备」≠「测不了」——构造法解阻（2026-07-14 第 5 轮，一次挖出 P0）

阻塞项写着「需外部条件」时，先问**能不能用构建配置等效构造**（§7.7 允许 AI 自主构建，只改配置不改代码，构建后 `git checkout` 还原）：

| 原阻塞理由 | 构造法 | 结果 |
|---|---|---|
| TC-E1「需专门构建不配 AppID 的包」 | `local.properties` 置 `GEEGUARD_APP_ID=`（空）→ 构建 | ✅ PASS（gate 告警 + 零采集 + 后端 fail-open 放行） |
| TC-F3「宿主 Apple Silicon 跑不了 x86_64 AVD」 | **等效构造**：`packagingOptions` 排除 `lib/arm64-v8a/libgtcore.so` → arm64 设备上 SO 必然加载失败 = **x86_64 无对应 ABI SO 的同一条代码路径** | 🔴 **FAIL — 挖出 P0 崩溃**（`catch(Throwable)` 兜底无效，异常抛在 SDK 自建线程） |
| TC-G1/G2/G3「无线上老版本 APK」 | **新功能引入 commit 的父提交 = 天然老版本基线**（GeeGuard 引入 `0590bb3343` → 父 `bafce98b6c`），`git worktree` 检出即可构建 | ✅ 三条全 PASS（真降级 versionCode 21400001，`install -r -d` 保数据不卸载） |

**核心教训**：**TC-F3 此前被判「由单测 + `catch(Throwable)` 静态代验」而跳过实跑，静态代验给出了错误的安全结论**（单测 mock 掉真实 SDK，测不出 SDK 异步线程崩溃）。**第三方 SDK 的异步崩溃路径，静态代验永远覆盖不到，必须真包实跑。**

## ⚠️⚠️ release 包留在模拟器 = 下一轮全员假死（2026-07-15 教训）

- **现象**：feat/gasless **release** 包（非 debuggable）在模拟器冷启 ~4s **静默 `System.exit(0)`**——无弹窗无 Toast 无 FATAL，先正常进 MainActivity 再退出，极易误判为「新 P0 崩溃」或「网络故障」。
- **根因**：release 构建的**反模拟器安检 exitProcess**（debox-android `2026-07-14-01-fix-release-r8-codegen-duplicate-spec/review.md` V8 已记录）。debug 包无此拦截。
- **鉴别法**：`run-as <pkg>` 报 `package not debuggable` → 装的是 release 包；dex 归因（`strings classes*.dex | grep 特征类`）确认代码血统。
- **铁律**：release 包验证完**立即换回 debug 包**；每轮开测归因必查 debuggable（run-as 探一下）。
- **附**：`debox://rn/<route>` 深链可直达 RN 容器（`jumpReactNativeActivity`），未注册路由即触发 RouteForce——测 RN/CodePush 的最快入口，免 admin 页。

## 已知模拟器噪声（结论可信度必读）

- **Mac fake-IP 代理**：模拟器侧 DNS 可能被宿主代理统一解析成 `198.18.x`（`198.18.0.0/15`），掩盖真实连接目标——开测先做**网络噪声预检**（TEST_GUIDE 七 §7.3）。
- **DNS 重度缓存**：netd + JVM 双层缓存，注入对已建立连接/已缓存域名不即时生效（07-01 实录）。
- 命中上述噪声时，**网络层结论只能标【模拟器实测，待真机复核】**（TC-N-05 / BUG-002 教训）。

## 一次性配置备忘

- 模拟器锁屏：设置 → 安全 → 屏幕锁定 = None（新建 AVD 后配一次）。
- 屏幕保活：每轮开测 `adb -s <serial> shell svc power stayon true`（真机用 `stayon usb`）。
- 登录态快照：模拟器完成登录（含验证码）后打 AVD snapshot（命名 `logged-in-<env>`，如 `logged-in-testenv`）；恢复后必做健康基线（时间同步/网络可达/IM onOpen），不健康才重登。
- `debox_root` 注入复原：`iptables -t nat -F OUTPUT`、杀宿主 DNS 响应器、装回原始包、冷启验证 DNS 非回环（07-01 复原清单）。

## ⚠️⚠️ emulator 多账号风险（2026-07-11 事故教训，每轮开测必核身份）

- **debox_root（emulator-5554）App 内登录了 3 个账号**：测试号 `2309b9ea`（$0）+ **用户真实账号 Kai**（Lv.11/856粉丝/真实资产）+ **真实账号 赵长鸟**（Lv.5/416粉丝）。「我的」页顶部「切换账号」弹层可互切。
- **事故**：2026-07-11 UI 自动化坐标盲点误触切换，前台变成真实账号 Kai/赵长鸟，并误入「账号详情」页（导出助记词/私钥/移除账号俱在）。已安全切回，真实账号零操作。
- **铁律**：① 每轮开测先到「我的」页**核实名字=测试号**（5554=`2309b9ea`，5556=`10b92305`）；② **页面跳转后必须重新截图/取元素再点击，禁止用旧坐标盲点**；③ 「切换账号」弹层内行尾编辑按钮（ivEditWallet）= 账号详情危险页，勿碰。
- **GeeGuard 补充观察**：07-11 下午本轮 debox_root 上 App（16fa94f6 build）**全程正常运行**——07-10 记录的「root 必退 System.exit -5」非必现（疑与 adb root 附加状态相关），用前实测冷启为准。
- **✅ 2026-07-14 复核确认（GeeGuard 专项轮）**：debox_root（emulator-5554，2.14.2/21400002 含 GeeGuard v2.7.1.1）**冷启动全程正常，无 System.exit -5**，且 GeeGuard 正常初始化并采集到正常形态 token（`respondedGeeToken.length=392`）。**「root 必退」结论正式作废**——L2 可正常跑需 App 实跑的用例，不必再降级到 L1。

## ⚠️ 两机联测需同后端环境（2026-07-11 晚，语音房两机深链实证）

- **每个 App 安装实例按 `DomainManager` 持久化域名**（加密 SP/MMKV，adb 读不到明文，看实际 HTTP `current_host` 为准）。两机若不同环境**无法共处同一语音房**（跨环境查房 `liveroom/info` 返 `-2213 DB系统繁忙`）。
- **切环境入口**：SettingAdminActivity（⚠️ 2026-07-15 实测 `am start` shell 直起已不成立——`SecurityException: not exported`，须走 UI 路径：关于我们 → 连点 logo ×5 → 密码，见 SettingAdminScreen.md）→ 列表点「开发环境」(`t.debox.pro`)/「正式环境」(`debox.pro`)/「域名池:…」行 → 弹「App必须重启」→确定 relaunch。**只 setCacheHttp+resetUrl+relaunch，不清 token/钱包数据**（不走会清 token 的 switchChain 分支）；切后该账号在新环境是全新身份（无数据、走新用户引导页，跳过即可），**原环境数据保留、切回即恢复**。
- **当前设备环境状态（2026-07-11 晚）**：**5554 已切到测试环境 `t.debox.pro`**（为两机联测，2309b9ea 测试环境 Lv.1 空号；真实号 Kai/赵长鸟 数据在生产待切回恢复）；5556 本就在 `t.debox.pro`。两机现同环境，可两机联测。**测试完若要恢复 5554 真实号正常显示，需切回「域名池: dbxsocial.com」或「正式环境」**。
- **深链进房（两机 TC-F）**：`adb -s <听众机> shell am start -a android.intent.action.VIEW -d "debox://open/share?type=live\&id=<roomId>"` → 弹「现在加入」→ 进房（详见 LiveRoomScreen.md）。

## ⚠️⚠️ 真机 run-as 写限制（2026-07-11 事故教训，务必先验）

- **三星 SM-S9210 (Knox) 的 SELinux 禁止 `run-as` 写 app 数据**：`runas_app` 域可**读**（backup 成功）但**不可写**（restore 失败，连新建文件都 `Permission denied`，即使 uid+SELinux categories 匹配）。模拟器无此限制（restore 正常）。
- **铁律**：真机做任何**破坏性数据注入前**，必须先**实测 run-as 写能力**（`run-as pkg sh -c 'echo x > files/wtest'` 成功且可读回），确认 restore 真能落地，**再动破坏性步骤**。模拟器验证过 ≠ 真机能 restore（SELinux 策略不同）。
- **allowBackup**：钱包 App 通常 `allowBackup=false`，`adb backup/restore` 不可用兜底。
- **后果**：2026-07-11 在三星测试账号做 Realm key 破坏性注入(TC-R-003)后无法 restore，本地钱包 key 丢失→需用户 seed 重导恢复（链上资产/社交账号未损）。
- **现状（2026-07-11 晚，Zego 冒烟时）**：三星上已由 autotest **新建全新空测试钱包**（$0、新 DeBox 账号 `667627d6`，支付密码已口头交接用户、不落文档），装 2.14.2 debug 包（21400002）。原测试账号仍待用户 seed 重导（导入可与新钱包共存）。
