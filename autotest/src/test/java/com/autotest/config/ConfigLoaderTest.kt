package com.autotest.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigLoaderTest {
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

    /** F：env 层不再是死层——高于 global（properties），仍低于 cli（命令行 -e）。 */
    @Test
    fun envLayer_overridesGlobal_butLosesToCli() {
        val loader = ConfigLoader(
            global = mapOf("app.launchTimeout" to "1", "app.maxLaunchTime" to "10"),
            app = emptyMap(),
            env = mapOf("app.launchTimeout" to "2", "app.maxLaunchTime" to "20"), // env 由 applyTo 注入
            cli = mapOf("app.launchTimeout" to "3") // 命令行最高
        )
        val cfg = loader.load()
        assertEquals("env 层高于 global", "20", cfg["app.maxLaunchTime"]) // env 覆盖 global
        assertEquals("cli 高于 env", "3", cfg["app.launchTimeout"])       // cli 覆盖 env
    }
}
