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

    fun init() {
        init(defaultLoader())
    }

    private fun defaultLoader(): ConfigLoader {
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
            env = emptyMap(),
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
    val packageName: String get() = getString(ConfigKeys.APP_PACKAGE_NAME)

    /** App 启动超时（毫秒） */
    val launchTimeout: Long get() = getLong(ConfigKeys.APP_LAUNCH_TIMEOUT, 15000L)

    /** 最大可接受启动时间（毫秒） */
    val maxLaunchTime: Long get() = getLong(ConfigKeys.APP_MAX_LAUNCH_TIME, 10000L)

    /** 底部 Tab 文本列表 */
    val bottomTabs: List<String> get() = getList(ConfigKeys.APP_BOTTOM_TABS)

    /** 截图保存目录 */
    val screenshotDir: String get() = getString(ConfigKeys.APP_SCREENSHOT_DIR, "/sdcard/Pictures/autotest")

    /** 是否在失败时自动截图 */
    val screenshotOnFailure: Boolean get() = getBoolean(ConfigKeys.APP_SCREENSHOT_ON_FAILURE, true)
}
