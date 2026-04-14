package com.autotest.data

import com.autotest.config.EnvironmentManager
import com.autotest.config.TestEnvironment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream

/**
 * 测试账号数据。
 */
data class TestAccount(
    val name: String = "",
    val phone: String = "",
    val password: String = "",
    val verifyCode: String = ""
)

/**
 * 测试数据管理器。
 *
 * 支持两种方式提供数据：
 * 1. 代码注册：`TestDataManager.registerAccount(LOCAL, TestAccount(...))`
 * 2. JSON 文件：放置 `androidTest/assets/test-data.json`
 *
 * JSON 格式示例：
 * ```json
 * {
 *   "LOCAL": { "accounts": [{ "name": "test", "phone": "138...", "password": "123" }] },
 *   "CI": { "accounts": [{ "name": "ci_user", "phone": "139..." }] }
 * }
 * ```
 */
object TestDataManager {

    private val accounts = mutableMapOf<TestEnvironment, MutableList<TestAccount>>()
    private val customData = mutableMapOf<String, String>()

    fun registerAccount(env: TestEnvironment, account: TestAccount) {
        accounts.getOrPut(env) { mutableListOf() }.add(account)
    }

    fun getAccount(env: TestEnvironment = EnvironmentManager.current().env): TestAccount {
        return accounts[env]?.firstOrNull()
            ?: error("没有为环境 $env 注册测试账号，请调用 registerAccount() 或加载 test-data.json")
    }

    fun getAllAccounts(env: TestEnvironment = EnvironmentManager.current().env): List<TestAccount> {
        return accounts[env] ?: emptyList()
    }

    fun put(key: String, value: String) {
        customData[key] = value
    }

    fun get(key: String, default: String = ""): String {
        return customData[key] ?: default
    }

    /**
     * 从 JSON InputStream 加载测试数据。
     * 通常在 setUp 中调用：
     * ```
     * val ctx = InstrumentationRegistry.getInstrumentation().context
     * TestDataManager.loadFromJson(ctx.assets.open("test-data.json"))
     * ```
     */
    fun loadFromJson(inputStream: InputStream) {
        val json = inputStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, EnvData>>() {}.type
        val data: Map<String, EnvData> = Gson().fromJson(json, type)

        data.forEach { (envStr, envData) ->
            val env = try {
                TestEnvironment.valueOf(envStr.uppercase())
            } catch (_: IllegalArgumentException) {
                return@forEach
            }
            envData.accounts.forEach { registerAccount(env, it) }
        }
    }

    fun reset() {
        accounts.clear()
        customData.clear()
    }

    private data class EnvData(
        val accounts: List<TestAccount> = emptyList()
    )
}
