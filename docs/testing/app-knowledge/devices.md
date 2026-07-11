# 设备清单与能力矩阵（devices）

> 知识库文件：测试开始前先读，选设备按 `TEST_GUIDE.md` 第七节「设备策略（emulator-first）」路由。
> 探索中发现的设备新特性/新噪声，当轮回写本文件（谁探索谁回写）。

## 设备清单

| 层级 | 设备 | 标识 | root | 锁屏 | 环境 | 备注 |
|---|---|---|---|---|---|---|
| **L1** | AVD `Pixel_10_Pro_XL` | emulator-55xx | ❌ | 无（永不锁屏） | 按需装包 | 默认执行层：UI/功能黑盒、B 模式回归 |
| **L2** | AVD `debox_root` | emulator-55xx | ✅（userdebug，`adb root` uid=0） | 无（永不锁屏） | 测试钱包（本地新建） | 注入层：DNS/iptables 故障注入、双网切换。⚠️ **2026-07-11 更新：GeeGuard（2026-07-06 替换图灵盾）检测 root/userdebug → App 冷启 `System.exit -5` 不进 Application**；含 GeeGuard 的 build 在 L2 跑不起来（旧「图灵盾不拦」G3 结论已失效）。需 App 实跑的用例（Realm 注入/IM/UI）改用 L1；L2 仅剩纯网络层 root 注入（若该路径不触发 GeeGuard 早退才可用，需先验） |
| **L3** | 小米 25067PYE3C | `402714f0` | ❌ | 安全锁（AOD 指纹，adb 不可绕） | **正式环境** | 复核层；日常被使用，可能被接管/掉线 |
| **L3** | 三星 SM-S9210 (S25) | `RFCYA0F9SSZ` | ❌ | 安全锁（Bouncer，adb 不可绕） | 测试环境 | 复核层 |

> AVD 镜像事实：`debox_root` = `android-15;google_apis;arm64-v8a` userdebug。模拟器序列号以 `adb devices` 实际输出为准（5554/5556 会因启动顺序变化）。

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

## 已知模拟器噪声（结论可信度必读）

- **Mac fake-IP 代理**：模拟器侧 DNS 可能被宿主代理统一解析成 `198.18.x`（`198.18.0.0/15`），掩盖真实连接目标——开测先做**网络噪声预检**（TEST_GUIDE 七 §7.3）。
- **DNS 重度缓存**：netd + JVM 双层缓存，注入对已建立连接/已缓存域名不即时生效（07-01 实录）。
- 命中上述噪声时，**网络层结论只能标【模拟器实测，待真机复核】**（TC-N-05 / BUG-002 教训）。

## 一次性配置备忘

- 模拟器锁屏：设置 → 安全 → 屏幕锁定 = None（新建 AVD 后配一次）。
- 屏幕保活：每轮开测 `adb -s <serial> shell svc power stayon true`（真机用 `stayon usb`）。
- 登录态快照：模拟器完成登录（含验证码）后打 AVD snapshot（命名 `logged-in-<env>`，如 `logged-in-testenv`）；恢复后必做健康基线（时间同步/网络可达/IM onOpen），不健康才重登。
- `debox_root` 注入复原：`iptables -t nat -F OUTPUT`、杀宿主 DNS 响应器、装回原始包、冷启验证 DNS 非回环（07-01 复原清单）。

## ⚠️⚠️ 真机 run-as 写限制（2026-07-11 事故教训，务必先验）

- **三星 SM-S9210 (Knox) 的 SELinux 禁止 `run-as` 写 app 数据**：`runas_app` 域可**读**（backup 成功）但**不可写**（restore 失败，连新建文件都 `Permission denied`，即使 uid+SELinux categories 匹配）。模拟器无此限制（restore 正常）。
- **铁律**：真机做任何**破坏性数据注入前**，必须先**实测 run-as 写能力**（`run-as pkg sh -c 'echo x > files/wtest'` 成功且可读回），确认 restore 真能落地，**再动破坏性步骤**。模拟器验证过 ≠ 真机能 restore（SELinux 策略不同）。
- **allowBackup**：钱包 App 通常 `allowBackup=false`，`adb backup/restore` 不可用兜底。
- **后果**：2026-07-11 在三星测试账号做 Realm key 破坏性注入(TC-R-003)后无法 restore，本地钱包 key 丢失→需用户 seed 重导恢复（链上资产/社交账号未损）。
- **现状（2026-07-11 晚，Zego 冒烟时）**：三星上已由 autotest **新建全新空测试钱包**（$0、新 DeBox 账号 `667627d6`，支付密码已口头交接用户、不落文档），装 2.14.2 debug 包（21400002）。原测试账号仍待用户 seed 重导（导入可与新钱包共存）。
