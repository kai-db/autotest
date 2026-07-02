package com.autotest.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F 收敛坐实：EnvironmentManager.applyTo() 把环境配置注入 TestConfig 的 env 层，
 * 之后 TestConfig 单一读取入口能读到（打通两套平行配置）。需 instrumentation（applyTo → initWithEnv → assets）。
 */
@RunWith(AndroidJUnit4::class)
class EnvApplyToSmokeTest {

    @After
    fun tearDown() {
        EnvironmentManager.reset()
        TestConfig.init() // 复位到默认加载，避免污染后续测试
    }

    @Test
    fun applyTo_injectsEnvLayer_readableViaTestConfig() {
        EnvironmentManager.register(
            EnvironmentConfig(
                env = TestEnvironment.CI,
                appPackage = "com.autotest.envtest",
                launchTimeout = 24680,
                extraConfig = mapOf("app.bottomTabs" to "消息,朋友")
            )
        )
        EnvironmentManager.setEnvironment(TestEnvironment.CI)
        EnvironmentManager.applyTo()

        // env 层已注入，TestConfig 读得到
        assertEquals("com.autotest.envtest", TestConfig.packageName)
        assertEquals(24680L, TestConfig.launchTimeout)
        assertEquals(listOf("消息", "朋友"), TestConfig.bottomTabs)
    }

    @Test
    fun applyTo_isIdempotent() {
        EnvironmentManager.register(EnvironmentConfig(TestEnvironment.CI, "com.autotest.a", launchTimeout = 1000))
        EnvironmentManager.setEnvironment(TestEnvironment.CI)
        EnvironmentManager.applyTo()
        EnvironmentManager.applyTo() // 重复调用幂等
        assertEquals(1000L, TestConfig.launchTimeout)
        assertEquals("com.autotest.a", TestConfig.packageName)
    }
}
