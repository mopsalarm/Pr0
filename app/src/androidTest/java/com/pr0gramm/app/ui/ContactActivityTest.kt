package com.pr0gramm.app.ui

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.action.ViewActions.typeText
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.filters.LargeTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.pr0gramm.app.R
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4::class)
class ContactActivityTest {

    @Rule
    @JvmField
    val activity = ActivityTestRule<ContactActivity>(ContactActivity::class.java)

    @Test
    fun buttonEnableState() {
        onView(withText(R.string.feedback_appfeedback))
                .perform(click())

        onView(withId(R.id.submit))
                .check(matches(not(ViewMatchers.isEnabled())))

        onView(withId(R.id.feedback_name))
                .check(matches(isDisplayed()))
                .perform(typeText("Name"))

        onView(withId(R.id.feedback_text))
                .perform(typeText("My feedback"))

        onView(withId(R.id.submit))
                .check(matches(ViewMatchers.isEnabled()))
    }


    @Test
    fun switchToNormalSupport() {
        onView(withText(R.string.feedback_general))
                .perform(click())

        onView(withId(R.id.feedback_subject))
                .check(matches(isDisplayed()))
                .perform(typeText("Subject"))

        onView(withId(R.id.feedback_email))
                .check(matches(isDisplayed()))
                .perform(typeText("mail@example.com"))

        onView(withId(R.id.feedback_text))
                .check(matches(isDisplayed()))
                .perform(typeText("My feedback"))

        onView(withId(R.id.submit))
                .check(matches(ViewMatchers.isEnabled()))
    }
}

