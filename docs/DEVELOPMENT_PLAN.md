# Android AutoTest 完整开发计划

> 从零到落地的全流程，最终目标：在 debox-android 项目中稳定运行 AI 辅助的自动化测试。

---

## 整体路线图

```
阶段1: 框架基础        ← 已完成 ✅
阶段2: 稳定性治理       ← 已完成 ✅
阶段3: AI 能力接入      ← 骨架完成，需补充实现
阶段4: CI 与多设备      ← 未开始
阶段5: debox 落地       ← 部分接入，需完善
阶段6: 持续扩展         ← 长期迭代
```

---

## 阶段 1：框架基础 ✅ 已完成

> 已完成的工作，列出供回顾。

- [x] 1.1 项目初始化（Android Library + mavenLocal 发布）
- [x] 1.2 BaseUiTest 基类（Espresso + UiAutomator 整合）
- [x] 1.3 BaseActivityTest（ActivityScenario 生命周期管理）
- [x] 1.4 Espresso 扩展（viewById/viewByText/click/typeText 等）
- [x] 1.5 UiAutomator 扩展（权限弹窗、滑动、等待、点击）
- [x] 1.6 WaitUtil（Espresso 线程等待 + 轮询等待）
- [x] 1.7 ScreenshotRule（失败自动截图）
- [x] 1.8 AppActions（Tab 切换、引导页跳过）
- [x] 1.9 AppAssertions（前台验证、文本/控件可见断言）
- [x] 1.10 TestConfig 分层配置（properties + 命令行参数 + ConfigLoader）
- [x] 1.11 结构化 JSON 报告（RunReport + ReportWriter + ReportCollector）
- [x] 1.12 DSL 步骤层（scenario/step + 报告集成）
- [x] 1.13 RunnerInfo 设备信息收集

---

## 阶段 2：稳定性治理 ✅ 已完成

- [x] 2.1 FlakyClassifier（timeout/超时 → FLAKY，其他 → HARD_FAIL）
- [x] 2.2 RetryPolicy 数据模型
- [x] 2.3 RetryRunner JUnit Rule（FLAKY 自动重试，HARD_FAIL 直接抛出）
- [x] 2.4 WaitPolicy 数据模型
- [x] 2.5 单元测试覆盖（ConfigLoader/FlakyClassifier/RetryRunner/ReportWriter/Scenario/RiskEvaluator）

---

## 阶段 3：AI 能力落地

> 当前状态：接口和骨架已有，但 AIPlugin 无实现类，AIPatchApplier 是空壳。

### 3.1 AI 插件实现
- [ ] 3.1.1 创建 `ai/ClaudePlugin.kt` — 对接 Claude API 的 AIPlugin 实现
  - 支持通过 HTTP 调用 Claude API
  - 处理 prompt 构建、response 解析
  - 支持配置 API Key（通过 TestConfig 或环境变量）
- [ ] 3.1.2 创建 `ai/LocalLLMPlugin.kt` — 本地/自定义 LLM 的 AIPlugin 实现（可选）
- [ ] 3.1.3 完善 `AIPatchApplier` — 实现真正的代码 patch 应用逻辑
  - 解析 diff 格式
  - 应用到目标文件
  - 回滚能力

### 3.2 AI 驱动的测试生成
- [ ] 3.2.1 实现 `ai/TestGenerator.kt` — 根据页面结构/截图生成测试用例
  - 输入：Activity 类名 + 布局 XML / View 层级 dump
  - 输出：可执行的 Kotlin 测试代码
- [ ] 3.2.2 实现 `ai/TestDataGenerator.kt` — AI 生成测试数据
  - 支持边界值、异常值、国际化数据

### 3.3 AI 自动修复 Flaky
- [ ] 3.3.1 实现 `ai/FlakyFixer.kt` — 分析 Flaky 失败日志，生成修复补丁
  - 常见修复：增加等待、调整选择器、处理弹窗
  - 生成 patch 后走风险评估流程
- [ ] 3.3.2 FlakyFixer 与 RetryRunner 联动 — 重试仍失败时触发 AI 分析

### 3.4 风险门控完善
- [ ] 3.4.1 扩展 RiskLevel 为三级：L0（只读/分析）、L1（低风险自动）、L2（需审批）
- [ ] 3.4.2 完善 RiskEvaluator 规则 — 基于变更类型、影响范围评估
- [ ] 3.4.3 实现审批回调机制 — 支持 CLI 交互 / 飞书通知 / CI 手动审批

---

## 阶段 4：CI 与多设备

### 4.1 CI 集成模板
- [ ] 4.1.1 创建 `.circleci/test-config.yml` — CircleCI 自动化测试 job
  - 模拟器启动 + 等待就绪
  - 执行 connectedAndroidTest
  - 收集报告和截图
  - 失败通知（飞书 Webhook）
- [ ] 4.1.2 创建 GitHub Actions workflow（备选方案）
- [ ] 4.1.3 集成到 debox-android 的 CI pipeline

### 4.2 多设备支持
- [ ] 4.2.1 完善 DeviceSelector — 支持指定设备 serial、优先物理机、并发分配
- [ ] 4.2.2 创建 `runner/ParallelRunner.kt` — 多设备并行执行测试
  - 设备发现 + 任务分片
  - 结果合并到统一报告
- [ ] 4.2.3 创建 `runner/EmulatorManager.kt` — 自动创建/启动/销毁模拟器（CI 用）

### 4.3 报告增强
- [ ] 4.3.1 创建 `report/AllureBridge.kt` — 可选的 Allure 报告适配
- [ ] 4.3.2 创建 `report/HtmlReporter.kt` — 独立的 HTML 报告生成（不依赖 Gradle）
- [ ] 4.3.3 报告推送 — 支持上传到 OSS / 发送飞书消息

---

## 阶段 5：debox 项目落地

> debox-android 信息：
> - 包名：`com.tm.security.wallet`
> - 最小 SDK：25，目标 SDK：34
> - 架构：多模块（app/business/im/public/wallet）+ ARouter 路由
> - UI：XML Layout + ViewBinding
> - 已有测试：AppLaunchTest、NavigationTest（基于 Kaspresso）
> - CI：CircleCI + GitHub Actions

### 5.1 接入框架
- [ ] 5.1.1 在 debox-android 的根 build.gradle 添加 `mavenLocal()`
- [ ] 5.1.2 在 app/build.gradle 添加 `androidTestImplementation 'com.autotest:autotest:1.0.0'`
- [ ] 5.1.3 创建 `app/src/androidTest/assets/test-config.properties`
  ```properties
  app.packageName=com.tm.security.wallet
  app.launchTimeout=20000
  app.maxLaunchTime=15000
  app.bottomTabs=首页,社区,发现,我的
  app.screenshotDir=/sdcard/Pictures/debox-autotest
  app.screenshotOnFailure=true
  ```

### 5.2 迁移现有测试
- [ ] 5.2.1 将 AppLaunchTest 从 Kaspresso 迁移到 autotest 框架
  - 保持用例逻辑不变，替换基类和 API
- [ ] 5.2.2 将 NavigationTest 迁移到 autotest 框架
- [ ] 5.2.3 迁移 MainScreen（Page Object）→ 使用 autotest 的 Espresso/UiAutomator 扩展
- [ ] 5.2.4 迁移 LaunchAppScenario → 使用 autotest 的 scenario DSL

### 5.3 debox 专属适配层
- [ ] 5.3.1 创建 `debox/DeboxConfig.kt` — debox 特有配置（登录账号、环境切换）
- [ ] 5.3.2 创建 `debox/DeboxActions.kt` — debox 特有操作
  - 登录流程（手机号/钱包登录）
  - 引导页/升级弹窗处理
  - ARouter 页面跳转辅助
- [ ] 5.3.3 创建 `debox/DeboxAssertions.kt` — debox 特有断言
  - 登录状态验证
  - 钱包连接状态验证

### 5.4 核心路径用例（10+ 个）
- [ ] 5.4.1 冷启动测试 — 启动时间 < 15 秒
- [ ] 5.4.2 启动 + 权限弹窗处理
- [ ] 5.4.3 底部 Tab 切换 — 首页/社区/发现/我的
- [ ] 5.4.4 登录流程 — 手机号登录
- [ ] 5.4.5 登录流程 — 钱包连接登录
- [ ] 5.4.6 首页信息流滑动 + 加载
- [ ] 5.4.7 社区帖子浏览 + 评论
- [ ] 5.4.8 钱包页面基础操作
- [ ] 5.4.9 个人设置页导航
- [ ] 5.4.10 退出登录
- [ ] 5.4.11 升级弹窗处理
- [ ] 5.4.12 网络异常恢复

### 5.5 验收
- [ ] 5.5.1 12 个核心用例在真机上全部通过
- [ ] 5.5.2 Flaky 率 < 5%（RetryRunner 重试后）
- [ ] 5.5.3 CI 模拟器上稳定执行
- [ ] 5.5.4 JSON 报告正确输出（含设备信息、步骤结果、失败分类）

---

## 阶段 6：持续扩展（长期）

### 6.1 框架增强
- [ ] 6.1.1 Page Object 生成器 — 根据布局 XML 自动生成 Page Object 类
- [ ] 6.1.2 测试录制器 — 录制操作并生成 scenario DSL 代码
- [ ] 6.1.3 性能测试模块 — 启动时间、帧率、内存、电量
- [ ] 6.1.4 无障碍测试 — 自动检查 contentDescription、对比度
- [ ] 6.1.5 国际化测试 — 多语言截图对比

### 6.2 AI 能力迭代
- [ ] 6.2.1 视觉回归测试 — AI 对比截图差异，智能判定是否为 bug
- [ ] 6.2.2 自愈测试 — 元素选择器失效时 AI 自动寻找替代选择器
- [ ] 6.2.3 测试覆盖分析 — AI 分析代码变更，推荐需要运行的测试
- [ ] 6.2.4 自然语言测试 — 用中文描述测试步骤，AI 转换为可执行代码

### 6.3 多项目复用
- [ ] 6.3.1 accesscontrolterminal 项目接入
- [ ] 6.3.2 提取通用 App 适配模板（登录、导航、权限等）
- [ ] 6.3.3 发布到私有 Maven 仓库（替代 mavenLocal）
- [ ] 6.3.4 版本管理与兼容性矩阵

---

## 当前进度总结

| 阶段 | 进度 | 说明 |
|---|---|---|
| 阶段 1: 框架基础 | 100% | 13 项全部完成 |
| 阶段 2: 稳定性治理 | 100% | 5 项全部完成 |
| 阶段 3: AI 能力落地 | 20% | 接口骨架完成，实现类未写 |
| 阶段 4: CI 与多设备 | 0% | 未开始 |
| 阶段 5: debox 落地 | 10% | 依赖已加，测试基础存在但未迁移 |
| 阶段 6: 持续扩展 | 0% | 长期目标 |

## 建议执行顺序

```
推荐先做阶段 5（debox 落地）→ 再补阶段 3（AI）→ 最后做阶段 4（CI）

理由：
1. 先在真实项目上跑通，验证框架可用性
2. 在真实用例基础上做 AI 能力更有价值
3. CI 集成最后做，确保本地稳定后再上线
```
