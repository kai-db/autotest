package com.autotest.base

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Before

/**
 * 基于 ActivityScenario 的测试基类。
 * 子类指定泛型 Activity 类型，自动启动和关闭。
 */
abstract class BaseActivityTest<T : Activity> : BaseUiTest() {

    lateinit var scenario: ActivityScenario<T>

    abstract fun getActivityClass(): Class<T>

    @Before
    override fun setUp() {
        super.setUp()
        scenario = ActivityScenario.launch(getActivityClass())
    }

    @After
    override fun tearDown() {
        // E3：setUp 半途失败时 scenario 未初始化，直接访问会用 UninitializedPropertyAccessException
        // 掩盖 setUp 的原始异常（真正根因）
        if (::scenario.isInitialized) scenario.close()
        super.tearDown()
    }

    fun onActivity(action: (T) -> Unit) {
        scenario.onActivity(action)
    }
}
