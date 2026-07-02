package com.autotest.config

import androidx.test.platform.app.InstrumentationRegistry
import java.util.Properties

/**
 * 测试配置中心。
 *
 * 读取优先级：
 * 1. Instrumentation 参数（命令行 -e key value）
 * 2. androidTest/assets/test-config.properties 文件
 * 3. 代码中的默认值
 *
 * 换项目只需要改 test-config.properties，不用动框架代码。
 */
object TestConfig {

    private val props = Properties()
    private val instrArgs: android.os.Bundle by lazy {
        InstrumentationRegistry.getArguments()
    }
    private var initialized = false
    private var config: Map<String, String> = emptyMap()

    fun init(loader: ConfigLoader = defaultLoader()) {
        config = loader.load()
        initialized = true
    }

    /** 无参入口（既有公开 API）；Kotlin 优先精确匹配，不与 init(loader) 歧义。 */
    fun init() {
        init(defaultLoader())
    }

    /**
     * @param envOverrides 注入 ConfigLoader 的 env 层（覆盖顺序 global < app < env < cli，即高于
     *   properties/低于命令行 -e）。由 [EnvironmentManager.applyTo] 显式调用打通两套配置。
     *   （单独命名以避免与无参 [init] 的重载歧义。）
     */
    fun initWithEnv(envOverrides: Map<String, String>) {
        init(defaultLoader(envOverrides))
    }

    private fun defaultLoader(envOverrides: Map<String, String> = emptyMap()): ConfigLoader {
        props.clear()
        try {
            val ctx = InstrumentationRegistry.getInstrumentation().context
            ctx.assets.open("test-config.properties").use { props.load(it) }
        } catch (_: Exception) {
            // 没有配置文件也能跑，全用默认值
        }
        return ConfigLoader(
            global = propsAsMap(),
            app = emptyMap(),
            env = envOverrides,
            cli = bundleAsMap(instrArgs)
        )
    }

    private fun propsAsMap(): Map<String, String> {
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    private fun bundleAsMap(bundle: android.os.Bundle): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (key in bundle.keySet()) {
            val value = bundle.getString(key)
            if (value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun ensureInit() {
        if (!initialized) {
            init(defaultLoader())
        }
    }

    /** 获取字符串配置 */
    fun getString(key: String, default: String = ""): String {
        ensureInit()
        return config[key] ?: default
    }

    /** 获取 Long 配置 */
    fun getLong(key: String, default: Long): Long {
        return getString(key, "").toLongOrNull() ?: default
    }

    /** 获取 Int 配置 */
    fun getInt(key: String, default: Int): Int {
        return getString(key, "").toIntOrNull() ?: default
    }

    /** 获取 Boolean 配置 */
    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val v = getString(key, "")
        return if (v.isNotEmpty()) v.toBoolean() else default
    }

    /** 获取字符串列表（逗号分隔） */
    fun getList(key: String): List<String> {
        return getString(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ==================== 常用 Key ====================

    /** 被测 App 包名 */
    val packageName: String
        get() {
            val pkg = getString(ConfigKeys.APP_PACKAGE_NAME)
            require(pkg.isNotEmpty()) {
                "app.packageName 未配置。请在 test-config.properties 中设置，或通过命令行 -e app.packageName 指定。"
            }
            return pkg
        }

    /** App 启动超时（毫秒） */
    val launchTimeout: Long get() = getLong(ConfigKeys.APP_LAUNCH_TIMEOUT, 15000L)

    /** 最大可接受启动时间（毫秒） */
    val maxLaunchTime: Long get() = getLong(ConfigKeys.APP_MAX_LAUNCH_TIME, 10000L)

    /** 底部 Tab 文本列表 */
    val bottomTabs: List<String> get() = getList(ConfigKeys.APP_BOTTOM_TABS)

    /**
     * 截图/报告/日志保存目录。
     * 未显式配置时默认用 **app 私有外部目录**（`getExternalFilesDir`）——
     * Android 10+ scoped storage 下写公共目录（如 /sdcard/Pictures）会 EPERM，私有目录免权限。
     */
    val screenshotDir: String
        get() {
            val configured = getString(ConfigKeys.APP_SCREENSHOT_DIR)
            if (configured.isNotEmpty()) return configured
            // 用 targetContext（被测 App 进程 UID 有权写自己的外部目录）——instrumentation 跑在目标 App uid 下，
            // 用 test 包 context 的外部目录会 scoped-storage 拒写/未 provision（G3-③ 教训，此处框架侧根治）。
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            return (ctx.getExternalFilesDir("autotest") ?: ctx.filesDir).absolutePath
        }

    /**
     * UiAutomator `Configurator.waitForIdleTimeout`（毫秒）。默认 **0**：被测 App 永不 idle（实时列表/动画/轮询）
     * 时，非 0 会让每次查找前空等而拖死查找；需要 idle 语义的 App 可经 `app.waitForIdleTimeout` 调回。
     */
    val waitForIdleTimeout: Long get() = getLong(ConfigKeys.APP_WAIT_FOR_IDLE_TIMEOUT, 0L)

    /** 是否在失败时自动截图 */
    val screenshotOnFailure: Boolean get() = getBoolean(ConfigKeys.APP_SCREENSHOT_ON_FAILURE, true)

    // ==================== 危险操作守卫（铁律#7 代码层） ====================

    /** 守卫开关（默认开；关闭仅限调试/非钱包接入，且会留审计事件） */
    val safetyEnabled: Boolean get() = getBoolean(ConfigKeys.SAFETY_ENABLED, true)

    /** 项目危险词表（逗号分隔，默认与内置合并） */
    val safetyDangerousTexts: List<String> get() = getList(ConfigKeys.SAFETY_DANGEROUS_TEXTS)

    /** 项目词表是否完全替换内置（显式 opt-in，默认合并） */
    val safetyDangerousTextsReplace: Boolean get() = getBoolean(ConfigKeys.SAFETY_DANGEROUS_TEXTS_REPLACE, false)
}
