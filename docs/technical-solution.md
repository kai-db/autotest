# 技术方案：Claude Code + mobile-mcp + autotest

> 通过 Claude Code 连接真机，用自然语言驱动 Android App 自动化测试。

## 1. 方案概述

### 目标

开发者/测试人员用中文对 Claude Code 说"帮我测一下 DeBox 的登录流程"，Claude Code 自动：
1. 连接真机
2. 启动 DeBox App
3. 执行登录操作（输入账号、点击按钮）
4. 截图验证每一步结果
5. 输出测试结论

同时，发现的 bug 可以固化为 autotest 框架的 JUnit 用例，加入 CI 自动回归。

### 核心思路

**不在 Android 端内置 AI**，而是让 Claude Code 作为外部 AI 大脑，通过 mobile-mcp 远程操控手机。

```
你说话 → Claude Code 想 → mobile-mcp 做 → 手机动 → Claude Code 看截图 → 判断对错
```

## 2. 技术选型

### 为什么用 mobile-mcp 而不是在框架里调 AI API？

| 方案 | 优点 | 缺点 |
|---|---|---|
| **框架内调 AI API**（已废弃） | 端到端自包含 | 需要 Android 端发 HTTP、AI 看不到屏幕、无法灵活交互 |
| **Claude Code + mobile-mcp**（采用） | AI 能看截图、灵活对话、无需改 App 代码 | 依赖 MCP 连接稳定性、执行速度较慢 |

### mobile-mcp 能力清单

| 工具 | 能力 | 用途 |
|---|---|---|
| `mobile_list_available_devices` | 列出连接的设备 | 确认设备就绪 |
| `mobile_launch_app` | 启动 App | 启动 DeBox |
| `mobile_terminate_app` | 关闭 App | 清理环境 |
| `mobile_take_screenshot` | 截图 | 每步验证 |
| `mobile_list_elements_on_screen` | 获取页面元素树 | 找到按钮/输入框 |
| `mobile_click_on_screen_at_coordinates` | 坐标点击 | 点击按钮 |
| `mobile_type_keys` | 输入文本 | 填写账号密码 |
| `mobile_swipe_on_screen` | 滑动 | 翻页、下拉刷新 |
| `mobile_press_button` | 按键（返回/Home） | 导航 |
| `mobile_get_screen_size` | 获取屏幕尺寸 | 计算滑动坐标 |
| `mobile_install_app` / `mobile_uninstall_app` | 安装/卸载 | 环境准备 |

## 3. 详细实现方案

### 3.1 环境准备

```
前置条件：
1. Android 手机通过 USB 连接 Mac，adb 可用
2. mobile-mcp server 已配置并运行
3. DeBox App 已安装在手机上
4. Claude Code 已配置 mobile-mcp 工具
```

验证命令：
```bash
adb devices                    # 确认设备连接
claude                         # 启动 Claude Code
# 在 Claude Code 中：
# "列出可用的移动设备"  → 调用 mobile_list_available_devices
```

### 3.2 AI 交互式测试流程

#### 单步执行模式

用户每说一句，Claude Code 执行一步：

```
用户：启动 DeBox
Claude：[调用 mobile_launch_app] → [截图确认] → "已启动，当前在首页"

用户：点击底部的"我的"Tab
Claude：[list_elements 找到坐标] → [click] → [截图] → "已切换到我的页面"

用户：检查下登录状态
Claude：[截图] → [分析截图] → "当前未登录，显示登录按钮"
```

#### 自动执行模式

用户描述完整场景，Claude Code 自动规划并执行：

```
用户：测试 DeBox 的手机号登录流程，用 13800000001 登录

Claude：
1. [launch_app] 启动 DeBox → [截图] ✓ 在首页
2. [click] 点击"我的" → [截图] ✓ 我的页面
3. [click] 点击"登录" → [截图] ✓ 登录页
4. [list_elements] 找到手机号输入框
5. [type_keys] 输入 13800000001 → [截图] ✓ 已输入
6. [click] 点击"获取验证码" → [截图] ✓ 已发送
7. 等待用户提供验证码...
8. [type_keys] 输入验证码
9. [click] 点击"登录"
10. [截图] 验证登录成功 → 显示用户昵称 ✓

测试结果：登录流程正常，耗时约 15 秒
```

### 3.3 截图驱动的验证

关键设计：**每步操作后必须截图**，Claude Code 通过视觉理解判断：
- 页面是否正确跳转
- 是否出现错误提示/弹窗
- 文本内容是否符合预期
- 布局是否异常

```
操作 → 等待 1-2 秒 → 截图 → AI 分析截图 → 判定 → 下一步
```

### 3.4 bug 固化为 CI 用例

当 AI 测试发现 bug 时，可以将操作步骤转化为 autotest 框架的 JUnit 用例：

```
AI 发现：点击"社区" Tab 后页面白屏

生成 autotest 用例：
@Test
fun testCommunityTab_shouldLoadContent() {
    val s = scenario("社区Tab加载") {
        step("启动App") { launchAppAndDismissDialogs() }
        step("点击社区Tab") { device.clickText("社区") }
        step("等待内容加载") { device.waitForText("推荐", 5000) }
        step("验证内容可见") {
            AppAssertions.assertTextVisible(device, "推荐")
        }
    }
    s.run()
}

加入 CI → 每次发版自动回归，防止问题复现
```

### 3.5 与 autotest 框架的分工

```
┌──────────────────────┐     ┌──────────────────────┐
│  Claude Code          │     │  autotest 框架        │
│  + mobile-mcp         │     │  + Gradle + CI        │
│                       │     │                       │
│  探索性测试            │     │  回归测试              │
│  新功能验证            │  →  │  核心路径保护           │
│  bug 复现             │ 固化 │  发版前自动检查         │
│  UI 视觉检查          │     │  Flaky 自动重试        │
│  灵活、慢、智能        │     │  固定、快、稳定         │
└──────────────────────┘     └──────────────────────┘
```

## 4. DeBox App 关键信息

### 基本信息

| 项目 | 值 |
|---|---|
| 包名 | `com.tm.security.wallet` |
| 最小 SDK | 25 (Android 7.0) |
| 目标 SDK | 34 (Android 14) |
| 架构 | 多模块 + ARouter 路由 |
| UI 框架 | XML Layout + ViewBinding |

### 主要页面结构

```
SplashActivity（闪屏）
  ↓ 自动跳转
首页（Bottom Tab 0）
  ├── 信息流列表
  └── 搜索入口

社区（Bottom Tab 1）
  ├── 推荐/关注 Tab
  └── 帖子列表

发现（Bottom Tab 2）
  └── 功能入口列表

我的（Bottom Tab 3）
  ├── 登录/用户信息
  ├── 钱包入口
  └── 设置入口
      └── 退出登录
```

### 常见弹窗

- 系统权限弹窗（存储、通知等）
- App 升级弹窗
- 引导页（首次安装）

## 5. 预期效果

### 日常开发场景

```
开发者：我改了登录模块的代码，帮我快速验证一下

Claude Code：
好的，我来测试登录流程。
1. 启动 DeBox... ✓
2. 进入我的页面... ✓
3. 点击登录... ✓
4. 输入手机号 → 获取验证码... ✓
5. 登录成功，显示昵称"测试用户" ✓

登录流程正常，未发现异常。
```

### 回归测试场景

```
CI 流水线：
1. git push 触发
2. ./gradlew connectedAndroidTest
3. autotest 执行 12 个核心用例
4. 全部通过 → 飞书通知"回归通过"
5. 有失败 → 飞书通知"回归失败" + 截图 + JSON 报告
```
