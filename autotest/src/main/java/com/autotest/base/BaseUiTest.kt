package com.autotest.base

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autotest.assertion.AiAsserter
import com.autotest.assertion.AiAssertionEvaluator
import com.autotest.config.TestConfig
import com.autotest.intercept.DialogDismissInterceptor
import com.autotest.intercept.InterceptorChain
import com.autotest.intercept.LogcatInterceptor
import com.autotest.intercept.LoggingInterceptor
import com.autotest.intercept.MemoryInterceptor
import com.autotest.intercept.PerformanceInterceptor
import com.autotest.intercept.ScreenshotInterceptor
import com.autotest.lifecycle.TestLifecycleManager
import com.autotest.log.DefaultTestLogger
import com.autotest.log.TestLogger
import com.autotest.report.ReportCollector
import com.autotest.report.ReportWriter
import com.autotest.runner.RunnerInfo
import com.autotest.selector.AiLocatorFallback
import com.autotest.selector.FingerprintStore
import com.autotest.selector.SelfHealingLocator
import com.autotest.util.ScreenshotRule
import com.autotest.util.TestArtifacts
import com.autotest.util.allowPermission
import com.autotest.util.waitForApp
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI 自动化测试基类，整合 Espresso + UiAutomator + 拦截器 + 日志。
 * 与具体项目零耦合，所有项目相关信息从 TestConfig 读取。
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseUiTest {

    lateinit var device: UiDevice
    lateinit var context: Context
    lateinit var logger: TestLogger

    val idlingResource = CountingIdlingResource("BaseUiTest")
    val interceptors = InterceptorChain()
    val lifecycle = TestLifecycleManager()

    @get:Rule
    val reportCollector = ReportCollector()

    @get:Rule(order = Int.MAX_VALUE)
    val screenshotRule = ScreenshotRule()

    // ==================== 三级自愈定位 + AI 软断言 ====================

    /** 元素指纹库（自愈降级链的数据底座），文件随产物目录走 */
    val fingerprintStore: FingerprintStore by lazy {
        FingerprintStore(File(TestConfig.screenshotDir, "fingerprints.json"), logger)
    }

    private val guardDelegate = lazy {
        com.autotest.safety.DangerousOpsGuard(
            dangerousTexts = com.autotest.safety.DangerousOpsGuard.buildWordlist(
                projectTexts = TestConfig.safetyDangerousTexts,
                replace = TestConfig.safetyDangerousTextsReplace
            ),
            logger = logger,
            evidenceCapture = { prefix ->
                val file = File(TestConfig.screenshotDir, "${prefix}_${System.currentTimeMillis()}.png")
                val parent = file.parentFile
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    // 危险操作审计证据不能静默丢失（Silent failure 零容忍）：留痕失败路径
                    logger.w("Guard", "证据截图目录创建失败，危险操作截图可能丢失: ${parent.absolutePath}")
                }
                if (device.takeScreenshot(file)) {
                    file.absolutePath
                } else {
                    logger.w("Guard", "危险操作证据截图失败（takeScreenshot 返回 false）: ${file.absolutePath}")
                    null
                }
            }
        )
    }

    /** 危险操作点击守卫（铁律#7 代码层）：词表 = 内置 + 项目配置合并；事件自动回填报告 */
    val guard: com.autotest.safety.DangerousOpsGuard by guardDelegate

    private val locatorDelegate = lazy {
        SelfHealingLocator(
            device, fingerprintStore,
            aiFallback = createAiLocatorFallback(),
            logger = logger,
            guard = if (TestConfig.safetyEnabled) guard else null
        )
    }

    /** 三级降级定位器：确定性 → 指纹自愈 → AI 兜底；自愈事件自动回填报告 */
    val locator: SelfHealingLocator by locatorDelegate

    /** AI 软断言入口（无评估器时记 SKIPPED，不影响确定性执行） */
    val aiAsserter: AiAsserter by lazy {
        AiAsserter(
            evaluator = createAiAssertionEvaluator(),
            collector = reportCollector,
            logger = logger,
            screenshotProvider = { captureForAiAssert() }
        )
    }

    /** AI 在环时覆写注入 LLM 定位兜底；CI 回归保持 null（执行期零 LLM 依赖） */
    protected open fun createAiLocatorFallback(): AiLocatorFallback? = null

    /** AI 在环时覆写注入 LLM 断言评估器；CI 回归保持 null */
    protected open fun createAiAssertionEvaluator(): AiAssertionEvaluator? = null

    private fun captureForAiAssert(): String? = try {
        val file = File(TestConfig.screenshotDir, "ai_assert_${System.currentTimeMillis()}.png")
        file.parentFile?.mkdirs()
        if (device.takeScreenshot(file)) file.absolutePath else null
    } catch (e: Throwable) {
        logger.w("AiAssert", "断言截图失败: ${e.message}")
        null
    }

    /** setUp 前的 Configurator.waitForIdleTimeout 原值，tearDown 恢复（进程级单例，防跨测试类泄漏） */
    private var savedIdleTimeout: Long = -1

    @Before
    open fun setUp() {
        TestConfig.init()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
        // 被测 App 可能永不进入 idle（消息轮询/动画），UiAutomator 默认每次查找前等 idle 10s 会拖死查找；
        // 默认置 0 立即查找（可经 app.waitForIdleTimeout 调回）。记录原值，tearDown 恢复，避免污染同进程后续测试类。
        val configurator = androidx.test.uiautomator.Configurator.getInstance()
        savedIdleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = TestConfig.waitForIdleTimeout
        IdlingRegistry.getInstance().register(idlingResource)

        logger = DefaultTestLogger(logDir = TestConfig.screenshotDir)
        // 清理旧产物防无限堆积；指纹库是跨 run 持久资产，护住不被按数量清掉（B3）
        TestArtifacts.cleanup(File(TestConfig.screenshotDir), protectedNames = setOf("fingerprints.json"))
        interceptors.logger = logger
        // E1/E6 留痕通道：logger 就绪后统一注入（与 interceptors.logger 同模式）
        lifecycle.logger = logger
        screenshotRule.logger = logger
        reportCollector.logger = logger
        // 危险操作守卫（铁律#7 代码层）：注册到全局供扩展函数入口取用；关闭时留审计事件，不允许静默
        if (TestConfig.safetyEnabled) {
            com.autotest.safety.GuardRegistry.current = guard
        } else {
            guard.recordDisabled()
            com.autotest.safety.GuardRegistry.current = null
        }
        val screenshotInterceptor = ScreenshotInterceptor(logger = logger)
        // 弹窗恢复只挂 behavior 链：watcher 链契约是纯旁路观察，主动点击违反契约（P0-1 收敛）
        interceptors.addBehavior(DialogDismissInterceptor(logger))
        interceptors.addAll(
            LoggingInterceptor(logger),
            screenshotInterceptor,
            LogcatInterceptor(logger),       // 步骤失败时自动收集设备 logcat（含 crash/FATAL）
            PerformanceInterceptor(logger),  // 步骤耗时超阈值告警
            MemoryInterceptor(logger, TestConfig.packageName)  // 每步采集 PSS，相对基线增长超阈值告警（泄漏）
        )
        // 失败截图回填报告，补全证据链
        reportCollector.screenshotProvider = {
            screenshotInterceptor.getAllScreenshots().values.toList()
        }
        // 自愈/AI 兜底定位事件回填报告（仅在 locator 被用过时取，避免无谓初始化）
        reportCollector.healingEventsProvider = {
            if (locatorDelegate.isInitialized()) locator.events else emptyList()
        }
        // 测试失败时取最近 logcat 做根因分析（crash/ANR/OOM 签名回填报告）
        reportCollector.logcatProvider = { device.executeShellCommand("logcat -d -t 400") }
        reportCollector.rootCausePackage = TestConfig.packageName
        // 危险操作守卫事件回填报告（阻断/放行/禁用全留痕）
        reportCollector.guardEventsProvider = {
            if (guardDelegate.isInitialized()) guard.events else emptyList()
        }

        logger.i("BaseUiTest", "setUp 完成，设备: ${android.os.Build.MODEL}")
    }

    @After
    open fun tearDown() {
        com.autotest.safety.GuardRegistry.current = null
        // 恢复 Configurator.waitForIdleTimeout 原值（进程级单例，防污染后续测试类）
        if (savedIdleTimeout >= 0) {
            androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = savedIdleTimeout
        }
        IdlingRegistry.getInstance().unregister(idlingResource)
        try {
            Espresso.onIdle()
        } finally {
            val runnerInfo = RunnerInfo.collect(TestConfig.packageName)
            val report = reportCollector.buildReport(
                appPackage = TestConfig.packageName,
                endTime = System.currentTimeMillis(),
                device = runnerInfo.deviceName,
                runnerInfo = runnerInfo
            )
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val reportFile = File(TestConfig.screenshotDir, "report_$timestamp.json")
            ReportWriter.write(report, reportFile)
            logger.i("BaseUiTest", "报告已写入: ${reportFile.absolutePath}")
        }
    }

    // ==================== App 生命周期 ====================

    /** 通过包名启动被测 App */
    fun launchApp(
        packageName: String = TestConfig.packageName,
        timeout: Long = TestConfig.launchTimeout
    ) {
        pressHome()
        sleep(300)
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("找不到启动 Intent，请确认包名: $packageName（跨进程测外部 App 时需在 androidTest manifest 声明 <queries>）")
        // 从非 Activity context（instrumentation）启动外部 App 必须带 NEW_TASK；CLEAR_TASK 也要求与 NEW_TASK 同用
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.waitForApp(packageName, timeout)
    }

    /** 启动 App 并自动处理权限弹窗 */
    fun launchAppAndDismissDialogs(
        packageName: String = TestConfig.packageName,
        timeout: Long = TestConfig.launchTimeout,
        settleTime: Long = 2000
    ) {
        launchApp(packageName, timeout)
        sleep(settleTime)
        device.allowPermission()
    }

    /** 启动指定 Activity */
    fun launchActivity(cls: Class<*>) {
        val intent = Intent(context, cls).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.waitForIdle()
    }

    /**
     * 验证当前前台 App 是否为目标包名（委托 [com.autotest.assertion.AppAssertions]，消除双实现）。
     * 原实现用 Kotlin `assert()`——依赖 JVM -ea 开关，ART 上默认关闭是恒过的空操作（P0-4）。
     */
    fun assertAppInForeground(packageName: String = TestConfig.packageName) {
        com.autotest.assertion.AppAssertions.assertInForeground(device, packageName)
    }

    // ==================== 设备操作 ====================

    fun pressBack() = device.pressBack()
    fun pressHome() = device.pressHome()
    fun sleep(millis: Long = 1000) = Thread.sleep(millis)

    /** 截图保存 */
    fun takeScreenshot(name: String) {
        val dir = File(TestConfig.screenshotDir)
        dir.mkdirs()
        device.takeScreenshot(File(dir, "${name}.png"))
    }
}
