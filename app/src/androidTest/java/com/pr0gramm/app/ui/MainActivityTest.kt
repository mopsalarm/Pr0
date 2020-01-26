package com.pr0gramm.app.ui


import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.gu.toolargetool.sizeAsParcel
import com.jakewharton.espresso.OkHttp3IdlingResource
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.debugConfig
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.views.viewer.MediaView
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.TypeSafeMatcher
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private val logger = Logger("MainActivityTest")
    @Rule
    @JvmField
    var mActivityTestRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun mainActivityTest() {
        val okHttpClient = dependencyInstance<OkHttpClient>()
        IdlingRegistry.getInstance().register(OkHttp3IdlingResource.create("OkHttp", okHttpClient))

        MainView.login()

        FeedView.search("foo")

        FeedView.clickItem(5)

        for (idx in 0 until 50) {
            PostView.next()

            PostView.scrollTo(50)
            PostView.scrollTo(0)
        }

        val bundle = mActivityTestRule.runOnUiThreadForResult {
            val outState = Bundle()
            mActivityTestRule.activity.onSaveInstanceState(outState, PersistableBundle())
            outState
        }

        logger.info { "Bundle size after onSaveInstanceState is: ${sizeAsParcel(bundle)}" }

        // MainView.logout()
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun prepare() = runBlocking {
            debugConfig = debugConfig.copy(mockApiUrl = "https://private-09a286-pr0gramm.apiary-mock.com")

            val userService = dependencyInstance<UserService>()
            userService.logout()
        }
    }
}
typealias VH = RecyclerView.ViewHolder


object MainView {
    object Drawer {
        inline fun open(block: () -> Unit) {
            open()
            block()
            close()
        }

        fun open() {
            onView(withId(R.id.drawer_layout)) {
                perform(DrawerActions.open())
            }
        }

        fun close() {
            onView(withId(R.id.drawer_layout)) {
                perform(DrawerActions.close())
            }
        }

        fun click(text: String) {
            onView(withId(R.id.drawer_nav_list)) {
                perform(actionOnItem<VH>(withText(text), click()))
            }
        }

        fun click(@StringRes textId: Int) {
            click(currentApplicationContext.getString(textId))
        }
    }

    fun login() {
        Drawer.open {
            Drawer.click(R.string.action_login)
            LoginView.login()
        }
    }

    fun logout() {
        Drawer.open {
            Drawer.click(R.string.action_logout)
            DialogView.okay()
        }
    }
}

object DialogView {
    fun okay() {
        onView(withId(android.R.id.button1)) {
            perform(click())
        }
    }
}

object LoginView {
    fun login() {
        onView(withId(R.id.username)) {
            perform(replaceText("username"))
        }

        onView(withId(R.id.password)) {
            perform(replaceText("password"))
        }

        onView(withId(R.id.captcha_answer)) {
            perform(replaceText("12345"))
            perform(closeSoftKeyboard())
        }

        onView(withId(R.id.login)) {
            perform(click())
        }
    }
}

object FeedView {
    fun search(query: String) {
        onView(ViewMatchers.withContentDescription("Suchen")) {
            perform(click())
        }

        onView(R.id.search_term) {
            perform(replaceText(query))
            perform(closeSoftKeyboard())
        }

        onView(R.id.search_button) {
            perform(click())
        }
    }

    fun clickItem(idx: Int) {
        onView(withId(R.id.list)) {
            perform(actionOnItemAtPosition<VH>(idx, click()))
        }
    }
}

object PostView {
    private val ActivePost = allOf(
            withId(R.id.post_fragment_root),
            withTagKey(R.id.ui_test_activestate, equalTo(true)))

    private val InActivePost = isDescendantOfA(ActivePost)

    fun next() {
        onView(allOf(isAssignableFrom<MediaView>(), InActivePost)) {
            val start = CoordinatesProvider { view: View ->
                val xy = IntArray(2).apply { view.getLocationOnScreen(this) }
                val visibleParts = Rect().apply { view.getGlobalVisibleRect(this) }

                val x = xy[0] + visibleParts.width() * 0.9f
                val y = xy[1] + visibleParts.height() * 0.5f
                floatArrayOf(x, y)
            }

            val end = CoordinatesProvider { view: View ->
                val xy = IntArray(2).apply { view.getLocationOnScreen(this) }
                val visibleParts = Rect().apply { view.getGlobalVisibleRect(this) }

                val x = xy[0].toFloat()
                val y = xy[1] + visibleParts.height() * 0.5f
                floatArrayOf(x, y)
            }

            perform(GeneralSwipeAction(Swipe.FAST, start, end, Press.FINGER))
        }
    }

    fun scrollTo(idx: Int) {
        val recyclerViewMatcher = allOf(withId(R.id.post_content), InActivePost)

        onView(recyclerViewMatcher) {
            perform(RecyclerViewActions.scrollToPosition<VH>(idx))
        }
    }
}

val currentApplicationContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

inline fun <reified T : Any> dependencyInstance(): T {
    return currentApplicationContext.injector.instance()
}

inline fun onView(matcher: Matcher<View>, block: ViewInteraction.() -> Unit) {
    onView(matcher).block()
}

inline fun onView(@IdRes id: Int, block: ViewInteraction.() -> Unit) {
    onView(withId(id), block)
}

inline fun <reified T : View> isAssignableFrom(): Matcher<View> {
    return ViewMatchers.isAssignableFrom(T::class.java)
}


private fun firstChildOf(parentMatcher: Matcher<View>): Matcher<View> {
    return childAtPosition(parentMatcher, 0)
}

private fun childAtPosition(parentMatcher: Matcher<View>, position: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("Child at position $position in parent ")
            parentMatcher.describeTo(description)
        }

        public override fun matchesSafely(view: View): Boolean {
            val parent = view.parent
            return parent is ViewGroup && parentMatcher.matches(parent)
                    && view == parent.getChildAt(position)
        }
    }
}

fun <T> ActivityTestRule<*>.runOnUiThreadForResult(block: () -> T): T {
    var result: T? = null
    runOnUiThread { result = block() }
    return result!!
}
