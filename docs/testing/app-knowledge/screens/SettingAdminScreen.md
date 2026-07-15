# SettingAdminScreen（管理员页）⚠️ 危险操作页

> 进入路径（**2026-07-14 实证打通**）：「我的」→ 右上角最右图标（系统设置）→「关于我们」→
> **连点 DeBox logo ≥5 次** → 弹「请输入」密码框 → 从安全渠道取得调试口令并输入 → 确定 → 进入「管理员」页
> 实现类型：原生 XML（`SettingAdminActivity`）
> 最后核实：2026-07-14（极验 GeeGuard 设备指纹轮）

## ⚠️ 关键约束

- **`am start` 直起会被拒**：该 Activity **未 exported**，shell 拉起报
  `SecurityException: Permission Denial: ... not exported from uid`。
  （devices.md 旧记载「debug 包 shell 可直起」在 2.14.2 上**已不成立**，必须走上述 UI 路径。）
- **仅 debug 包可达**：入口代码 `AboutUsActivity.kt:155-164` 有 `logoTapCount >= 5 && BuildConfig.DEBUG` 双重条件，release 走不到。
- **「关于我们」页的「开发者模式」开关不是本页入口**（实测打开无效，已复原）——别被它误导，入口是**连点 logo**。

## 元素表

| 元素名 | 首选定位 | 备注 |
|---|---|---|
| 复制设备指纹 | `resource-id=btnCopyGeeId`（文本「复制设备指纹」） | ✅ **安全**。GeeGuard geeID 复制入口；成功 toast「复制成功」，未就绪 toast「设备指纹未就绪，稍后再试」 |
| 正式环境 / 开发环境 / 域名池 行 | text 含 `https://debox.pro/debox/` 等 | ⚠️⚠️ **危险：切换环境**（dangerous-ops 一类红线）。当前打勾行 = 当前环境（可用作**环境核验证据**，只看不点） |
| 打开日志开关 / 显示日志 | text 同名 | 翻转 `LogUtils.open`；本轮未动 |
| 打开RN页面 | `resource-id=btnRN`（文本「打开RN页面」，bounds≈[678,1449][960,1569]） | ✅ **安全**。走 `RouterRN.shares` 进 `ReactNativeContainerActivity`（RN 名片分享 `/profile/shares`）——**测 CodePush/RN 容器/bundle 加载的最快入口**，`printBundleInfo` 全量打 bundle 路径与 CodePush 目录树 |
| 清理CodePush缓存 | `resource-id=btnClearCodePush`（bounds≈[996,1449][1344,1569]） | ⚠️ 半危险。`RNPreloadManager.clearCodePushCache()` 删 `filesDir/CodePush` + 清 CodePush SP；不影响登录/钱包，但会重置热更状态。测 bundle 矩阵时可用 |
| EVM/ETH/BTC 测试链切换 | text 含「测试链」「切测试环境」 | ⚠️ **危险：切链**（会走清 token 分支），禁点 |
| 输入链接 + 打开链接 | 页面下部输入框 | 可作**剪贴板粘贴验证**的临时载体（用完清空）；**不点「打开链接」** |

## 已知弹窗

| 弹窗 | 触发时机 | 处理方式 |
|---|---|---|
| 「请输入」密码框 | 连点 logo ≥5 次 | 从安全渠道取得调试口令并输入 → 确定 |
| 「App必须重启」 | **误点环境行后**弹出 | ⚠️ 立即点「取消」——点确定会切环境并 relaunch |

## 注意事项

- **本页是 dangerous-ops 命中密集区**：环境切换行在页面**上部**（y≈490–830 显示坐标），
  「复制设备指纹」在**中部**（y≈1108）。点击前**必须重新取元素/截图确认坐标**，禁用旧坐标盲点。
- **环境核验用途**：打勾的环境行是判定「当前是否测试环境」最直接的证据
  （2026-07-14：5556 显示「开发环境 `https://t.debox.pro/debox/`」✓，与请求 header `appEnv=dev` 互证）。
- **geeID 形态**（2026-07-14 实测）：`G02-<10位数字>-<64位hex>-<8位随机串>`。
  文档不留存实际值；复制后通过安全渠道交给运维，按 root_id 加白（风险码 60113）。
- **断网降级态下 geeID 仍可用**（实测 toast 仍是「复制成功」）——设备指纹不因极验服务不可达而丢失。
