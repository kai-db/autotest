package com.autotest.config

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentManagerTest {

    @After
    fun tearDown() {
        EnvironmentManager.reset()
    }

    @Test
    fun register_andRetrieve() {
        EnvironmentManager.register(
            EnvironmentConfig(TestEnvironment.CI, "com.test.ci", launchTimeout = 20000)
        )
        EnvironmentManager.setEnvironment(TestEnvironment.CI)

        val config = EnvironmentManager.current()
        assertEquals("com.test.ci", config.appPackage)
        assertEquals(20000L, config.launchTimeout)
    }

    @Test
    fun multipleEnvironments() {
        EnvironmentManager.register(
            EnvironmentConfig(TestEnvironment.LOCAL, "com.test.local"),
            EnvironmentConfig(TestEnvironment.CI, "com.test.ci")
        )

        EnvironmentManager.setEnvironment(TestEnvironment.LOCAL)
        assertEquals("com.test.local", EnvironmentManager.current().appPackage)

        EnvironmentManager.setEnvironment(TestEnvironment.CI)
        assertEquals("com.test.ci", EnvironmentManager.current().appPackage)
    }

    @Test
    fun reset_clearsRegisteredConfigs() {
        EnvironmentManager.register(
            EnvironmentConfig(TestEnvironment.CI, "com.test.ci", launchTimeout = 99999)
        )
        EnvironmentManager.setEnvironment(TestEnvironment.CI)
        assertEquals("com.test.ci", EnvironmentManager.current().appPackage)

        EnvironmentManager.reset()
        // After reset, re-register and verify old config is gone
        EnvironmentManager.register(
            EnvironmentConfig(TestEnvironment.CI, "com.test.new")
        )
        EnvironmentManager.setEnvironment(TestEnvironment.CI)
        assertEquals("com.test.new", EnvironmentManager.current().appPackage)
    }
}
