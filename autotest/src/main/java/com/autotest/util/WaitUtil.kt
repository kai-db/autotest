package com.autotest.util

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import org.hamcrest.Matcher

object WaitUtil {

    /** Espresso 线程内等待 */
    fun waitFor(millis: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription(): String = "等待 ${millis}ms"
            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadForAtLeast(millis)
            }
        }
    }

    /** 轮询等待条件满足 */
    fun waitUntil(
        timeoutMillis: Long = 10000,
        intervalMillis: Long = 500,
        condition: () -> Boolean
    ) {
        val startTime = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                throw AssertionError("等待超时 (${timeoutMillis}ms)")
            }
            Thread.sleep(intervalMillis)
        }
    }

    fun delay(millis: Long) {
        onView(isRoot()).perform(waitFor(millis))
    }
}
