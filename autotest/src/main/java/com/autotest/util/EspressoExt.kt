package com.autotest.util

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import com.autotest.safety.GuardRegistry
import com.autotest.safety.toClickTarget
import org.hamcrest.Matcher

fun viewById(id: Int): ViewInteraction = onView(ViewMatchers.withId(id))
fun viewByText(text: String): ViewInteraction = onView(ViewMatchers.withText(text))

/** 点击（过危险操作守卫，铁律#7） */
fun ViewInteraction.click(allowDangerous: Boolean = false, allowUnverifiable: Boolean = false): ViewInteraction =
    perform(GuardedClickAction(allowDangerous, allowUnverifiable))

/**
 * 带守卫的点击 ViewAction：perform 时先以 view 的 text/res-id/desc 构造可审计目标过守卫，
 * 再委托 [ViewActions.click]。守卫未注册（GuardRegistry 为空）时行为与原生 click 一致。
 */
internal class GuardedClickAction(
    private val allowDangerous: Boolean,
    private val allowUnverifiable: Boolean
) : ViewAction {
    private val delegate = ViewActions.click()

    override fun getConstraints(): Matcher<View> = delegate.constraints

    override fun getDescription(): String = "guarded ${delegate.description}"

    override fun perform(uiController: UiController, view: View) {
        GuardRegistry.current?.checkClick(view.toClickTarget(), allowDangerous, allowUnverifiable)
        delegate.perform(uiController, view)
    }
}

fun ViewInteraction.typeText(text: String): ViewInteraction =
    perform(ViewActions.typeText(text), ViewActions.closeSoftKeyboard())

fun ViewInteraction.replaceText(text: String): ViewInteraction =
    perform(ViewActions.replaceText(text), ViewActions.closeSoftKeyboard())

fun ViewInteraction.isDisplayed(): ViewInteraction =
    check(matches(ViewMatchers.isDisplayed()))

fun ViewInteraction.hasText(text: String): ViewInteraction =
    check(matches(ViewMatchers.withText(text)))

fun ViewInteraction.checkMatches(matcher: Matcher<View>): ViewInteraction =
    check(matches(matcher))

fun ViewInteraction.scrollTo(): ViewInteraction =
    perform(ViewActions.scrollTo())
