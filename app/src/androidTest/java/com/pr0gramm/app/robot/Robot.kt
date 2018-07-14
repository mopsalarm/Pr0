package com.pr0gramm.app.robot

import android.support.annotation.IdRes
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.ViewAction
import android.support.test.espresso.ViewInteraction
import android.support.test.espresso.action.ViewActions
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pr0gramm.app.R
import org.hamcrest.Matcher

abstract class Robot {
    protected fun clickOn(matcher: Matcher<View>) {
        performOnView(matcher) { click() }
    }

    protected fun performOnView(matcher: Matcher<View>, action: ScopedViewInteraction.() -> Unit) {
        val v = onView(matcher).check(matches(isDisplayed()))
        ScopedViewInteraction(v).action()
    }

    fun hasView(matcher: Matcher<View>) {
        onView(matcher).check(matches(isDisplayed()))
    }
}

class FeedRobot : Robot() {
    fun openPostAt(index: Int) {
        performOnView(withId(R.id.list)) {
            onRecyclerViewItem(index + 2) {
                click()
            }
        }
    }

    fun withSearchPanel(actions: SearchPanelScope.() -> Unit) {
        SearchPanelScope().apply { open() }.actions()
    }

    fun clickUserProfileHint() {
        clickOn(withText(R.string.user_info_uploads))
    }

    class SearchPanelScope : Robot() {
        fun open() {
            clickOn(withId(R.id.action_search))
        }

        fun start() {
            clickOn(withId(R.id.search_button))
        }

        fun clearSearchTerm(term: String) {
            performOnView(withId(R.id.search_term)) {
                perform(ViewActions.clearText())
            }
        }

        fun typeSearchTerm(term: String) {
            performOnView(withId(R.id.search_term)) {
                typeText(term)
            }
        }

        fun performSearch(term: String) {
            typeSearchTerm(term)
            start()
        }
    }
}

typealias ViewInteractionClosure = ScopedViewInteraction.() -> Unit

typealias ViewInteractionConsumer = (action: ViewAction) -> Unit

class ScopedViewInteraction(val perform: ViewInteractionConsumer) {
    fun click() = perform(ViewActions.click())

    fun typeText(text: String) = perform(ViewActions.typeText(text))

    fun pressBack() = perform(ViewActions.pressBack())

    fun pressBackUnconditionally() = perform(ViewActions.pressBackUnconditionally())

    fun onRecyclerViewItem(index: Int, action: ViewInteractionClosure) {
        val recordedActions = mutableListOf<ViewAction>().apply {
            ScopedViewInteraction { add(it) }.action()
        }

        // now execute actions
        recordedActions.forEach { recordedAction ->
            perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(index, recordedAction))
        }
    }

    companion object {
        operator fun invoke(v: ViewInteraction): ScopedViewInteraction {
            return ScopedViewInteraction { v.perform(it) }
        }
    }
}