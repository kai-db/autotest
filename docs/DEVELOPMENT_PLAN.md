# Android AutoTest 完整开发计划

> 两条路线并行：autotest 框架用于 CI 自动化回归，Claude Code + mobile-mcp 用于交互式 AI 测试。

---

## 整体路线图

```
阶段1: 框架基础 + 稳定性     ← 已完成 ✅
阶段2: 代码质量优化          ← 已完成 ✅
阶段3: debox 落地（CI 回归）  ← 未开始
阶段4: Claude Code + mobile-mcp 交互式测试 ← 未开始
阶段5: CI 集成               ← 未开始
阶段6: 持续扩展              ← 长期迭代
```

---

## 阶段 1：框架基础 + 稳定性 ✅ 已完成

- [x] 1.1 项目初始化（Android Library + mavenLocal 发布）
- [x] 1.2 BaseUiTest / BaseActivityTest 测试基类
- [x] 1.3 Espresso 扩展（viewById/viewByText/click/typeText 等）
- [x] 1.4 UiAutomator 扩展（权限弹窗、滑动、等待、点击）
- [x] 1.5 WaitUtil + ScreenshotRule
- [x] 1.6 AppActions（Tab 切换、引导页跳过）+ AppAssertions（断言）
- [x] 1.7 TestConfig 分层配置（ConfigLoader + ConfigKeys）
- [x] 1.8 结构化 JSON 报告（RunReport + ReportWriter + ReportCollector）
- [x] 1.9 DSL 步骤层（scenario/step + 报告集成）
- [x] 1.10 RunnerInfo 设备信息 + DeviceSelector
- [x] 1.11 FlakyClassifier + RetryRunner + RetryPolicy + WaitPolicy
- [x] 1.12 单元测试覆盖

---

## 阶段 2：代码质量优化 ✅ 已完成

- [x] 2.1 删除 ai/ 和 risk/ 空壳模块（方向调整为 Claude Code + mobile-mcp）
- [x] 2.2 修复 ReportCollector 全局单例状态污染 → 每次测试新建实例
- [x] 2.3 修复 DeviceSelector.getSerial() 在 API 26+ 崩溃
- [x] 2.4 ScreenshotRule 接入 BaseUiTest 基类
- [x] 2.5 报告文件名加时间戳，防覆盖
- [x] 2.6 build.gradle 依赖拆分（api vs implementation）
- [x] 2.7 创建 CLAUDE.md（mobile-mcp 测试指南）

---

## 阶段 3：debox 落地（CI 回归测试）

### 3.1 接入框架
- [ ] 3.1.1 debox 根 build.gradle 添加 `mavenLocal()`
- [ ] 3.1.2 app/build.gradle 添加 `androidTestImplementation 'com.autotest:autotest:1.0.0'`
- [ ] 3.1.3 创建 test-config.properties（包名、超时、Tab、截图目录）

### 3.2 迁移现有测试
- [ ] 3.2.1 AppLaunchTest 从 Kaspresso → autotest
- [ ] 3.2.2 NavigationTest 从 Kaspresso → autotest
- [ ] 3.2.3 MainScreen / LaunchAppScenario → autotest DSL

### 3.3 DeBox 适配层
- [ ] 3.3.1 DeboxActions（登录流程、引导页/升级弹窗、ARouter 跳转）
- [ ] 3.3.2 DeboxAssertions（登录状态、钱包连接验证）

### 3.4 核心用例（12 个）
- [ ] 冷启动 / 权限弹窗 / Tab 切换
- [ ] 手机号登录 / 钱包登录 / 退出登录
- [ ] 首页信息流 / 社区帖子 / 钱包页面 / 设置导航
- [ ] 升级弹窗 / 网络异常恢复

### 3.5 验收
- [ ] 真机 12 用例全部通过，Flaky 率 < 5%

---

## 阶段 4：Claude Code + mobile-mcp 交互式测试

> 核心思路：你用自然语言下指令，Claude Code 通过 mobile-mcp 操控真机测试。

### 4.1 环境搭建
- [ ] 4.1.1 确认 mobile-mcp 连接真机/模拟器稳定
- [ ] 4.1.2 完善 CLAUDE.md 中 DeBox 页面结构描述（各页面元素 ID/文本）

### 4.2 测试场景模板
- [ ] 4.2.1 冒烟测试模板（启动 → Tab 遍历 → 截图验证）
- [ ] 4.2.2 登录流程模板（手机号 / 钱包登录）
- [ ] 4.2.3 探索性测试模板（AI 自主浏览页面，发现异常）

### 4.3 结果固化
- [ ] 4.3.1 AI 测试发现 bug → 自动生成 autotest 用例代码 → 加入 CI 回归
- [ ] 4.3.2 截图基线管理 — 保存正常截图，后续对比变化

---

## 阶段 5：CI 集成

- [ ] 5.1 CircleCI 自动化测试 Job（模拟器 + 执行 + 报告 + 飞书通知）
- [ ] 5.2 集成到 debox-android CI pipeline
- [ ] 5.3 报告推送（OSS 上传 + 飞书消息）
- [ ] 5.4 CI 模拟器验收

---

## 阶段 6：持续扩展（长期）

- [ ] 6.1 性能测试模块（启动时间、帧率、内存）
- [ ] 6.2 多设备并行执行（ParallelRunner）
- [ ] 6.3 accesscontrolterminal 项目接入
- [ ] 6.4 私有 Maven 仓库发布
- [ ] 6.5 HTML 报告生成器

---

## 两条测试路线对比

| | CI 自动化回归（autotest 框架） | AI 交互式测试（Claude Code + mobile-mcp） |
|---|---|---|
| 触发方式 | 每次发版自动运行 | 你随时对话触发 |
| 测试内容 | 固定的 12+ 核心用例 | 灵活探索，任意场景 |
| 执行速度 | 快（脚本直接跑） | 慢（截图分析 + 交互） |
| 适合场景 | 回归保护，防止已知功能破坏 | 新功能探索，发现未知 bug |
| AI 角色 | 无 | Claude Code 是大脑 |
| 闭环 | 失败 → 通知 → 人工修 | 发现 bug → 生成 autotest 用例 → 加入 CI |

## 建议执行顺序

```
阶段 3（debox CI 落地）→ 阶段 4（mobile-mcp 交互式）→ 阶段 5（CI 集成）

理由：
1. 先把 CI 回归跑通，保护核心路径
2. 再用 Claude Code 做探索性测试，发现更多问题
3. AI 发现的 bug 固化为 autotest 用例，持续壮大 CI 用例库
```
