package com.autotest.data

import com.autotest.config.EnvironmentConfig
import com.autotest.config.EnvironmentManager
import com.autotest.config.TestEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class TestDataManagerTest {

    @Before
    fun setUp() {
        TestDataManager.reset()
        EnvironmentManager.reset()
        EnvironmentManager.register(EnvironmentConfig(TestEnvironment.LOCAL, "com.test"))
        EnvironmentManager.setEnvironment(TestEnvironment.LOCAL)
    }

    @After
    fun tearDown() {
        TestDataManager.reset()
        EnvironmentManager.reset()
    }

    @Test
    fun registerAndGet() {
        TestDataManager.registerAccount(
            TestEnvironment.LOCAL,
            TestAccount(name = "test", phone = "138")
        )
        val account = TestDataManager.getAccount(TestEnvironment.LOCAL)
        assertEquals("test", account.name)
        assertEquals("138", account.phone)
    }

    @Test
    fun customData() {
        TestDataManager.put("key1", "value1")
        assertEquals("value1", TestDataManager.get("key1"))
        assertEquals("default", TestDataManager.get("missing", "default"))
    }

    @Test
    fun loadFromJson() {
        val json = """
        {
            "LOCAL": {
                "accounts": [{"name": "local_user", "phone": "13800000001"}]
            },
            "CI": {
                "accounts": [{"name": "ci_user", "phone": "13900000001"}]
            }
        }
        """.trimIndent()

        TestDataManager.loadFromJson(ByteArrayInputStream(json.toByteArray()))

        val localAccount = TestDataManager.getAccount(TestEnvironment.LOCAL)
        assertEquals("local_user", localAccount.name)

        val ciAccount = TestDataManager.getAccount(TestEnvironment.CI)
        assertEquals("ci_user", ciAccount.name)
    }

    @Test
    fun reset_clearsAll() {
        TestDataManager.registerAccount(TestEnvironment.LOCAL, TestAccount(name = "x"))
        TestDataManager.put("k", "v")
        TestDataManager.reset()

        assertEquals("", TestDataManager.get("k"))
        assertEquals(0, TestDataManager.getAllAccounts(TestEnvironment.LOCAL).size)
    }
}
