package com.pr0gramm.app.ui


import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.action.ViewActions.replaceText
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.core.deps.guava.util.concurrent.Uninterruptibles
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.pr0gramm.app.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Rule
    var mActivityTestRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun mainActivityTest() {
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS)

        val textView = onView(
                allOf(withId(R.id.action_search), isDisplayed()))

        textView.check(matches(isDisplayed()))

        val actionMenuItemView = onView(
                allOf(withId(R.id.action_search), isDisplayed()))

        actionMenuItemView.perform(click())

        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS)

        val searchAutoComplete = onView(
                allOf(withId(R.id.search_src_text),
                        withParent(allOf(withId(R.id.search_plate),
                                withParent(withId(R.id.search_edit_frame)))),
                        isDisplayed()))

        searchAutoComplete.perform(replaceText("test"))

    }
}
