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
        scenario.close()
        super.tearDown()
    }

    fun onActivity(action: (T) -> Unit) {
        scenario.onActivity(action)
    }
}
