package com.autotest.config

/**
 * 测试环境定义。
 * 不同环境可有不同的包名、超时、截图目录等配置。
 * 通过 test-config.properties 中的 test.env 指定，或命令行 -e test.env CI 覆盖。
 */
enum class TestEnvironment {
    LOCAL,      // 本地开发
    CI,         // CI 流水线
    STAGING     // 预发布环境
}

/**
 * 环境配置数据类。
 */
data class EnvironmentConfig(
    val env: TestEnvironment,
    val appPackage: String,
    val launchTimeout: Long = 15000,
    val maxLaunchTime: Long = 10000,
    /**
     * 产物目录。**null（默认）= 未显式指定，回退 [TestConfig.screenshotDir]**（targetContext 私有外部目录）。
     * 不再硬编码 `/sdcard/Pictures/autotest`（scoped storage 必死，且与 TestConfig 单一来源矛盾）。
     */
    val screenshotDir: String? = null,
    val extraConfig: Map<String, String> = emptyMap()
)

/**
 * 环境配置管理器。
 *
 * 使用方式1 — 代码注册：
 * ```
 * EnvironmentManager.register(
 *     EnvironmentConfig(LOCAL, "com.example.debug"),
 *     EnvironmentConfig(CI, "com.example", launchTimeout = 20000)
 * )
 * val config = EnvironmentManager.current()
 * ```
 *
 * 使用方式2 — 自动从 TestConfig 读取：
 * ```
 * // test-config.properties 中写 test.env=CI
 * val config = EnvironmentManager.current() // 自动读取 CI 环境
 * ```
 */
object EnvironmentManager {

    private val configs = mutableMapOf<TestEnvironment, EnvironmentConfig>()
    private var currentEnv: TestEnvironment? = null

    fun register(vararg envConfigs: EnvironmentConfig) {
        envConfigs.forEach { configs[it.env] = it }
    }

    fun setEnvironment(env: TestEnvironment) {
        currentEnv = env
    }

    fun current(): EnvironmentConfig {
        val env = currentEnv ?: resolveFromTestConfig()
        return configs[env] ?: defaultConfig(env)
    }

    /**
     * 打通 EnvironmentManager → TestConfig 单一读取入口（消除两套平行配置）：
     * 把 [current] 的 EnvironmentConfig 关键项注入 TestConfig 的 env 层。
     *
     * - **显式调用**（不在 TestConfig.defaultLoader 里自动调用），避免
     *   `current()`→`resolveFromTestConfig()`→`TestConfig`→`defaultLoader` 的 init 循环递归。
     * - **幂等**：可重复调用，每次以最新 EnvironmentConfig 重建 env override（同输入同结果，不累积）。
     * - **覆盖顺序**：注入 env 层，优先级 `global < app < env < cli`——高于 properties 文件、低于命令行 `-e`。
     */
    fun applyTo() {
        val c = current()
        val overrides = buildMap {
            put(ConfigKeys.APP_PACKAGE_NAME, c.appPackage)
            put(ConfigKeys.APP_LAUNCH_TIMEOUT, c.launchTimeout.toString())
            put(ConfigKeys.APP_MAX_LAUNCH_TIME, c.maxLaunchTime.toString())
            c.screenshotDir?.let { put(ConfigKeys.APP_SCREENSHOT_DIR, it) } // null=不覆盖，回退 TestConfig 默认
            putAll(c.extraConfig)
        }
        TestConfig.initWithEnv(overrides)
    }

    fun reset() {
        configs.clear()
        currentEnv = null
    }

    private fun resolveFromTestConfig(): TestEnvironment {
        val envStr = TestConfig.getString("test.env", "LOCAL").uppercase()
        return try {
            TestEnvironment.valueOf(envStr)
        } catch (_: IllegalArgumentException) {
            TestEnvironment.LOCAL
        }
    }

    private fun defaultConfig(env: TestEnvironment): EnvironmentConfig {
        return EnvironmentConfig(
            env = env,
            appPackage = TestConfig.packageName,
            launchTimeout = TestConfig.launchTimeout,
            maxLaunchTime = TestConfig.maxLaunchTime,
            screenshotDir = TestConfig.screenshotDir
        )
    }
}
