# Android AutoTest + AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Evolve `android-autotest` into a reusable, AI-augmented Android UI test library that supports debox now and other apps later, with stable execution, structured reporting, and guarded AI auto-fix.

**Architecture:** Keep the test core lean (BaseUiTest/TestConfig/Actions/Assertions) and add three opt-in layers: DSL steps, AI plugin hooks, and risk/approval gates. AI outputs patches + metadata; low-risk changes auto-apply, higher risk requires human approval. All outputs are logged and reproducible.

**Tech Stack:** Kotlin, JUnit4, Espresso, UiAutomator, Gradle (Android Library), optional Allure hooks.

---

## File Structure (Planned)

**Modify**
- `autotest/src/main/java/com/autotest/config/TestConfig.kt`
- `autotest/src/main/java/com/autotest/base/BaseUiTest.kt`
- `autotest/src/main/java/com/autotest/util/ScreenshotRule.kt`
- `autotest/build.gradle`
- `README.md`

**Create**
- `autotest/src/main/java/com/autotest/config/ConfigLoader.kt`
- `autotest/src/main/java/com/autotest/config/ConfigKeys.kt`
- `autotest/src/main/java/com/autotest/report/RunReport.kt`
- `autotest/src/main/java/com/autotest/report/ReportWriter.kt`
- `autotest/src/main/java/com/autotest/report/ReportCollector.kt`
- `autotest/src/main/java/com/autotest/report/AllureBridge.kt` (optional hook)
- `autotest/src/main/java/com/autotest/dsl/Steps.kt`
- `autotest/src/main/java/com/autotest/dsl/Scenario.kt`
- `autotest/src/main/java/com/autotest/stability/RetryPolicy.kt`
- `autotest/src/main/java/com/autotest/stability/WaitPolicy.kt`
- `autotest/src/main/java/com/autotest/stability/FlakyClassifier.kt`
- `autotest/src/main/java/com/autotest/ai/AIPlugin.kt`
- `autotest/src/main/java/com/autotest/ai/AIResult.kt`
- `autotest/src/main/java/com/autotest/ai/AIPatchApplier.kt`
- `autotest/src/main/java/com/autotest/ai/AIHooks.kt`
- `autotest/src/main/java/com/autotest/risk/RiskLevel.kt`
- `autotest/src/main/java/com/autotest/risk/RiskEvaluator.kt`
- `autotest/src/main/java/com/autotest/risk/ApprovalGate.kt`
- `autotest/src/main/java/com/autotest/runner/DeviceSelector.kt`
- `autotest/src/main/java/com/autotest/runner/RunnerInfo.kt`

**Test**
- `autotest/src/test/java/com/autotest/config/ConfigLoaderTest.kt`
- `autotest/src/test/java/com/autotest/stability/FlakyClassifierTest.kt`
- `autotest/src/test/java/com/autotest/ai/RiskEvaluatorTest.kt`
- `autotest/src/test/java/com/autotest/report/ReportWriterTest.kt`

---

### Task 1: Config 分层与扩展

**Files**
- Create: `autotest/src/main/java/com/autotest/config/ConfigLoader.kt`
- Create: `autotest/src/main/java/com/autotest/config/ConfigKeys.kt`
- Modify: `autotest/src/main/java/com/autotest/config/TestConfig.kt`
- Test: `autotest/src/test/java/com/autotest/config/ConfigLoaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun loadConfig_respectsPriorityOrder() {
    val loader = ConfigLoader(
        global = mapOf("app.packageName" to "a"),
        app = mapOf("app.packageName" to "b"),
        env = mapOf("app.packageName" to "c"),
        cli = mapOf("app.packageName" to "d")
    )
    val cfg = loader.load()
    assertEquals("d", cfg["app.packageName"])
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :autotest:test --tests ConfigLoaderTest`  
Expected: FAIL (ConfigLoader not implemented)

- [ ] **Step 3: Implement minimal loader**

```kotlin
class ConfigLoader(
    private val global: Map<String, String>,
    private val app: Map<String, String>,
    private val env: Map<String, String>,
    private val cli: Map<String, String>
) {
    fun load(): Map<String, String> =
        global + app + env + cli
}
```

- [ ] **Step 4: Wire into TestConfig**

Add a `init(loader: ConfigLoader = default)` path and keep existing API stable.

- [ ] **Step 5: Run tests**

Run: `./gradlew :autotest:test --tests ConfigLoaderTest`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add autotest/src/main/java/com/autotest/config autotest/src/test/java/com/autotest/config
git commit -m "feat: add layered config loader"
```

---

### Task 2: 统一报告结构与输出

**Files**
- Create: `autotest/src/main/java/com/autotest/report/RunReport.kt`
- Create: `autotest/src/main/java/com/autotest/report/ReportWriter.kt`
- Create: `autotest/src/main/java/com/autotest/report/ReportCollector.kt`
- Modify: `autotest/src/main/java/com/autotest/base/BaseUiTest.kt`
- Test: `autotest/src/test/java/com/autotest/report/ReportWriterTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun reportWriter_writesJson() {
    val report = RunReport(testName = "T1", status = "PASS")
    val out = ReportWriter.write(report)
    assertTrue(out.contains("\"testName\""))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :autotest:test --tests ReportWriterTest`  
Expected: FAIL (ReportWriter not implemented)

- [ ] **Step 3: Implement minimal report model + writer**

```kotlin
data class RunReport(val testName: String, val status: String, val screenshots: List<String> = emptyList())
object ReportWriter { fun write(report: RunReport): String = Gson().toJson(report) }
```

- [ ] **Step 4: Hook into BaseUiTest**

Add `ReportCollector` to capture failures and save JSON to `TestConfig.screenshotDir/report.json`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :autotest:test --tests ReportWriterTest`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add autotest/src/main/java/com/autotest/report autotest/src/main/java/com/autotest/base/BaseUiTest.kt
git commit -m "feat: add structured run report output"
```

---

### Task 3: DSL 步骤层

**Files**
- Create: `autotest/src/main/java/com/autotest/dsl/Steps.kt`
- Create: `autotest/src/main/java/com/autotest/dsl/Scenario.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun scenario_runsSteps() {
    var hit = 0
    scenario("demo") {
        step("s1") { hit++ }
        step("s2") { hit++ }
    }
    assertEquals(2, hit)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :autotest:test --tests ScenarioTest`  
Expected: FAIL (scenario/step missing)

- [ ] **Step 3: Implement minimal DSL**

```kotlin
fun scenario(name: String, block: Scenario.() -> Unit) = Scenario(name).apply(block).run()
class Scenario(private val name: String) {
    private val steps = mutableListOf<Pair<String, () -> Unit>>()
    fun step(title: String, block: () -> Unit) { steps += title to block }
    fun run() = steps.forEach { it.second.invoke() }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :autotest:test --tests ScenarioTest`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add autotest/src/main/java/com/autotest/dsl
git commit -m "feat: add minimal DSL step/scenario"
```

---

### Task 4: 稳定性治理（等待/重试/Flaky 分类）

**Files**
- Create: `autotest/src/main/java/com/autotest/stability/RetryPolicy.kt`
- Create: `autotest/src/main/java/com/autotest/stability/WaitPolicy.kt`
- Create: `autotest/src/main/java/com/autotest/stability/FlakyClassifier.kt`
- Test: `autotest/src/test/java/com/autotest/stability/FlakyClassifierTest.kt`
- Modify: `autotest/src/main/java/com/autotest/base/BaseUiTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun flakyClassifier_marksTimeoutAsFlaky() {
    val cls = FlakyClassifier()
    val result = cls.classify("Timeout waiting for view")
    assertEquals(FlakyClassifier.Type.FLAKY, result)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :autotest:test --tests FlakyClassifierTest`  
Expected: FAIL

- [ ] **Step 3: Implement minimal classifier**

```kotlin
class FlakyClassifier {
    enum class Type { FLAKY, HARD_FAIL }
    fun classify(msg: String): Type =
        if (msg.contains("Timeout", true)) Type.FLAKY else Type.HARD_FAIL
}
```

- [ ] **Step 4: Add RetryPolicy**

Implement `RetryPolicy(retries: Int, delayMs: Long)` and hook to BaseUiTest failure flow.

- [ ] **Step 5: Run tests**

Run: `./gradlew :autotest:test --tests FlakyClassifierTest`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add autotest/src/main/java/com/autotest/stability autotest/src/main/java/com/autotest/base/BaseUiTest.kt
git commit -m "feat: add flaky classifier and retry policy"
```

---

### Task 5: AI 插件接口与风险门控

**Files**
- Create: `autotest/src/main/java/com/autotest/ai/AIPlugin.kt`
- Create: `autotest/src/main/java/com/autotest/ai/AIResult.kt`
- Create: `autotest/src/main/java/com/autotest/ai/AIPatchApplier.kt`
- Create: `autotest/src/main/java/com/autotest/ai/AIHooks.kt`
- Create: `autotest/src/main/java/com/autotest/risk/RiskLevel.kt`
- Create: `autotest/src/main/java/com/autotest/risk/RiskEvaluator.kt`
- Create: `autotest/src/main/java/com/autotest/risk/ApprovalGate.kt`
- Test: `autotest/src/test/java/com/autotest/ai/RiskEvaluatorTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun riskEvaluator_lowRiskForWaitOnly() {
    val r = RiskEvaluator().evaluate(changeSummary = "insert wait")
    assertEquals(RiskLevel.L1, r)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :autotest:test --tests RiskEvaluatorTest`  
Expected: FAIL

- [ ] **Step 3: Implement minimal RiskEvaluator**

```kotlin
class RiskEvaluator {
    fun evaluate(changeSummary: String): RiskLevel =
        if (changeSummary.contains("wait", true)) RiskLevel.L1 else RiskLevel.L2
}
```

- [ ] **Step 4: Define AIPlugin interface**

```kotlin
interface AIPlugin {
    fun generateTests(context: String): AIResult
    fun fixFlaky(logs: String): AIResult
    fun generateData(config: String): AIResult
    fun summarizeRun(report: String): AIResult
}
```

- [ ] **Step 5: Implement ApprovalGate stub**

Low risk auto-apply, high risk require explicit approval callback.

- [ ] **Step 6: Run tests**

Run: `./gradlew :autotest:test --tests RiskEvaluatorTest`  
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add autotest/src/main/java/com/autotest/ai autotest/src/main/java/com/autotest/risk autotest/src/test/java/com/autotest/ai
git commit -m "feat: add AI plugin interfaces and risk gate"
```

---

### Task 6: 设备选择与 Runner 信息

**Files**
- Create: `autotest/src/main/java/com/autotest/runner/DeviceSelector.kt`
- Create: `autotest/src/main/java/com/autotest/runner/RunnerInfo.kt`

- [ ] **Step 1: Add DeviceSelector skeleton**

Implement a small helper that reads `adb devices -l` output and selects based on config (first, specific serial, or prefer physical).

- [ ] **Step 2: Provide RunnerInfo**

Collect SDK, device, app package, build info, embed into report.

- [ ] **Step 3: Commit**

```bash
git add autotest/src/main/java/com/autotest/runner
git commit -m "feat: add device selector and runner metadata"
```

---

### Task 7: README 与接入文档

**Files**
- Modify: `README.md`

- [ ] **Step 1: Document config layering and AI plugin**
- [ ] **Step 2: Add risk/approval description**
- [ ] **Step 3: Add CI usage snippet**

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: update guide for AI and stability features"
```

---

## Plan Review Loop

Per instructions, dispatch a plan-document-reviewer subagent with:
- Plan path: `docs/superpowers/plans/2026-04-14-android-autotest-ai-framework.md`
- Spec path: `docs/superpowers/specs/2026-04-14-android-autotest-ai-framework-design.md`

If issues are found, fix and re-run.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-14-android-autotest-ai-framework.md`.
Two execution options:

1. Subagent-Driven (recommended) — use superpowers:subagent-driven-development  
2. Inline Execution — use superpowers:executing-plans

Which approach?

