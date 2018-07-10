package com.pr0gramm.app.ui

import android.content.Intent
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.click
import android.support.test.espresso.action.ViewActions.typeText
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.contrib.DrawerActions
import android.support.test.espresso.contrib.DrawerMatchers
import android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.filters.LargeTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.support.v7.widget.RecyclerView
import com.pr0gramm.app.R
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Rule
    @JvmField
    val activity = MainActivityWithNoUpdateTestRule()

    @Test
    fun openContactActivityUsingDrawer() {
        onView(withId(R.id.drawer_layout))
                .check(matches(DrawerMatchers.isClosed()))
                .perform(DrawerActions.open())

        onView(withText(R.string.action_contact))
                .perform(click())

        onView(withText(R.string.feedback_appfeedback))
    }

    @Test
    fun performSearchAndOpenProfile() {
        onView(withText("Top"))
        onView(withId(R.id.action_search)).perform(click())
        onView(withId(R.id.search_term)).perform(typeText("Mopsalarm"))
        onView(withId(R.id.search_button)).perform(click())

        onView(withText(R.string.user_info_uploads)).perform(click())
        onView(withId(R.id.user_extra_info))
                .check(matches(withText("Dabei seit 3 Jahren")))
    }

    @Test
    fun viewPost() {
        onView(withText("Top"))

        onView(withId(R.id.list)).perform(
                actionOnItemAtPosition<RecyclerView.ViewHolder>(2, click()))

        onView(withText("579")).check(matches(isDisplayed()))
        onView(withText(containsString("rib"))).check(matches(isDisplayed()))

        onView(withText("pr0n")).check(matches(isDisplayed()))
        onView(withText("girlfriends"))
                .check(matches(isDisplayed()))
                .perform(click())
    }
}

class MainActivityWithNoUpdateTestRule : ActivityTestRule<MainActivity>(MainActivity::class.java) {
    override fun getActivityIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            putExtra("MainActivity.quiet", true)
        }
    }
}
