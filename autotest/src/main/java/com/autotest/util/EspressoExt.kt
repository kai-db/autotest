package com.autotest.util

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matcher

fun viewById(id: Int): ViewInteraction = onView(ViewMatchers.withId(id))
fun viewByText(text: String): ViewInteraction = onView(ViewMatchers.withText(text))

fun ViewInteraction.click(): ViewInteraction = perform(ViewActions.click())

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
