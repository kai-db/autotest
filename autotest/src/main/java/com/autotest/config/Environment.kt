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
    val screenshotDir: String = "/sdcard/Pictures/autotest",
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
