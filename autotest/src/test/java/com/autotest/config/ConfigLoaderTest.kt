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
}
