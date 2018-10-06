package com.pr0gramm.app.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
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

