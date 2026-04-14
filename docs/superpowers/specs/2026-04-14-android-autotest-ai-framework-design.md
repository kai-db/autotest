# Android AutoTest + AI 技术方案 / 架构 / 开发计划

日期：2026-04-14  
范围：以 `android-autotest` 作为独立库，优先服务 debox，同时保证多 App 复用与扩展性。  
入口：Claude / Hermes / ChatGPT 对话入口，结合本地 CLI/CI 管道。

## 1. 目标与范围

**目标**
- 作为独立库接入（mavenLocal/私服），不与业务项目强耦合。
- 支持真机、模拟器、CI（GitHub Actions/Jenkins/自建 Runner）。
- 兼容 JUnit4 常规写法 + DSL 步骤式写法（并存、互不冲突）。
- AI 能力可插拔：生成用例、自动修复 flaky、生成数据、报告摘要。
- 低风险自动执行，高风险必须人工审核。

**非目标**
- 不在一期内实现“完全无人值守的高风险变更执行”。
- 不在一期内替代现有业务测试策略，仅做增量融合。

## 2. 现有基础

`android-autotest` 已具备：
- BaseUiTest / BaseActivityTest 基类。
- TestConfig 统一配置入口（assets + CLI 参数）。
- AppActions / AppAssertions。
- Espresso / UiAutomator 扩展与 ScreenshotRule。
- mavenLocal 发布。

## 3. 总体架构

采用 5 层分离：

1. **Test Core（核心执行层）**
   - 统一执行入口、生命周期管理
   - 可靠的等待策略、重试策略、失败捕获
   - 标准化日志 / 截图 / 失败上下文收集

2. **DSL Layer（语义步骤层）**
   - `step {}` / `scenario {}` 语义包装
   - 兼容 JUnit4 原生写法
   - DSL 只做“可读性增强”，不改变执行语义

3. **AI Plugin Layer（AI 插件层）**
   - 接口统一：`GenerateTest`, `FixFlaky`, `GenerateData`, `SummarizeRun`
   - 多入口：Hermes/ChatGPT 作为请求源，产出统一 Patch/建议
   - 插件可开关，可替换模型（Claude 作为主力）

4. **Risk & Approval（风险与审批层）**
   - 变更风险分级：L0/L1/L2
   - L0/L1 自动执行，L2 必须审批
   - 所有变更可审计、可回滚（补丁记录）

5. **Adapters（项目适配层）**
   - debox 适配：登录态、业务路径、权限弹窗、环境初始化
   - 新 App 仅需要增加 Adapter + Config

## 4. 风险分级与自动执行策略

**L0（只读）**
- 生成报告摘要
- 生成测试用例草稿（不自动执行）

**L1（低风险自动执行）**
- 插入等待 / 轻量重试
- 调整超时策略
- 追加截图/日志采集

**L2（高风险人工审核）**
- 修改断言
- 修改业务流程步骤
- 变更配置影响范围广的逻辑

## 5. 关键模块设计

### 5.1 TestConfig 扩展
- 新增配置分层：
  - `global.properties`（通用）
  - `app.properties`（App 级）
  - `env.properties`（环境/CI）
- CLI 参数覆盖最高优先级

### 5.2 稳定性治理（Flaky Control）
- 统一等待策略：`WaitUtil` 以条件式等待为主
- 重试策略：按元素/页面级重试
- Flaky 判定：失败后重试 + 失败类型归因

### 5.3 日志/报告
- 标准输出：结构化 JSON + 关键截图索引
- Allure 集成预留接口
- 报告摘要由 AI 生成，附在测试结果后

### 5.4 AI 插件接口
- `AIPlugin` 统一入口：
  - `generate_tests(context)`
  - `fix_flaky(logs, screenshot)`
  - `generate_data(config)`
  - `summarize_run(report)`
- 输出格式：`patch + explanation + risk_level`

## 6. 执行流程

1. 执行测试 → 收集日志/截图/失败上下文  
2. AI 插件分析 → 输出 patch/建议  
3. 风险评估 → 自动应用或人工审核  
4. 再执行验证 → 产出报告摘要  

## 7. CI/真机/模拟器支持

- 统一 runner 接口：ADB 检测 + 设备选择策略  
- CI：使用 emulator + adb 命令启动  
- 真机：支持多设备并发，按序列号分配  

## 8. 扩展性保障

为新 App 增加以下即可复用：
- `app.properties`
- `AppAdapter`（登录/启动/权限弹窗）
- 业务级 `AppActions` / `AppAssertions`

## 9. 开发计划（里程碑）

**阶段 1：基础整合（1-2 周）**
- 完成 TestConfig 分层
- 统一日志/截图输出
- 基础 DSL 包装

**阶段 2：稳定性治理（1-2 周）**
- 等待策略标准化
- Flaky 判定 + 重试机制

**阶段 3：AI 插件层（2 周）**
- AI 插件接口定义
- Hermes/ChatGPT 接入 demo
- 风险分级与审批门控

**阶段 4：CI/多设备（2 周）**
- 统一 runner
- 多设备并发策略
- CI 模板示例

**阶段 5：debox 落地（持续）**
- debox Adapter
- 核心用例模板化
- 稳定性迭代

## 10. 验收标准

- debox 核心路径至少 10 条用例稳定通过
- 一次 flaky 自动修复能够通过 L1 规则自动落地
- CI 上模拟器可稳定执行
- AI 能生成可执行用例并通过人工审核

