# AutoTest 框架测试用例

> 监工模式用例来源。验证框架自身的编译、测试、发布能力。

## P0 — 编译与发布

### TC-001 框架编译
- 步骤：`./gradlew :autotest:compileReleaseKotlin`
- 验证：编译成功，无错误，无警告（Deprecation 警告除外）

### TC-002 单元测试全部通过
- 步骤：`./gradlew :autotest:test`
- 验证：BUILD SUCCESSFUL，0 failures

### TC-003 发布到 mavenLocal
- 步骤：`./gradlew :autotest:publishToMavenLocal`
- 验证：`~/.m2/repository/com/autotest/autotest/1.0.0/` 下有 aar 文件

## P0 — 单元测试覆盖

### TC-004 ConfigLoader 优先级测试
- 文件：`ConfigLoaderTest.kt`
- 验证：cli 层覆盖 env/app/global

### TC-005 FlakyClassifier 分类测试
- 文件：`FlakyClassifierTest.kt`
- 验证：timeout → FLAKY，其他 → HARD_FAIL，null/empty → HARD_FAIL

### TC-006 RetryRunner 重试测试
- 文件：`RetryRunnerTest.kt`
- 验证：FLAKY 自动重试、HARD_FAIL 不重试、首次通过不重试

### TC-007 ReportWriter JSON 输出测试
- 文件：`ReportWriterTest.kt`
- 验证：序列化正确，字段完整

### TC-008 Scenario DSL 测试
- 文件：`ScenarioTest.kt`
- 验证：步骤按序执行、无 collector 时正常工作、步骤失败时正确抛出

## P1 — 新增测试覆盖

### TC-009 FlakyClassifier null/empty 路径
- 验证：`classify(null)` → HARD_FAIL，`classify("")` → HARD_FAIL

### TC-010 Scenario 步骤失败路径
- 验证：某步骤抛异常时，后续步骤不执行，异常正确传播

### TC-011 ReportCollector 多轮累积
- 验证：新建 ReportCollector 实例，failures 不会跨实例污染

### TC-012 WaitUtil.waitUntil 超时
- 验证：条件始终为 false 时，在超时后抛出 AssertionError

## P1 — 代码质量

### TC-013 无编译警告
- 步骤：编译时检查输出
- 验证：除已知 Deprecation 外无新增警告

### TC-014 文档一致性
- 验证：CLAUDE.md / README.md / 05-AI测试手册.md 中的 App 信息与实际一致
