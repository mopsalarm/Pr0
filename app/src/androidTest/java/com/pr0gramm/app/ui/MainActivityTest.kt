package com.pr0gramm.app.ui

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import com.pr0gramm.app.R
import com.pr0gramm.app.robot.FeedRobot
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
        val feed = FeedRobot()

        feed.withSearchPanel {
            performSearch("Mopsalarm")
        }

        feed.clickUserProfileHint()

        // check that the view is openable
        feed.hasView(withId(R.id.user_extra_info))
    }

    @Test
    fun viewPost() {
        onView(withText("Top"))

        val feed = FeedRobot()

        feed.openPostAt(0)

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
