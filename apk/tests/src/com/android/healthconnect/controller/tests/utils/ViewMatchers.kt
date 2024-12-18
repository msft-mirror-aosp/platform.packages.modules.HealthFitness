/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.utils

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.matcher.BoundedMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/**
 * A custom matcher used when there are more than one view with the same resourceId/text/contentDesc
 * etc.
 *
 * @param matcher a view matcher for UI element which may have potentially more than one matched.
 *   Typical example is the element that is repeated in ListView or RecyclerView
 * @param index the index to select matcher if there are more than one matcher. it's started at
 *   zero.
 * @return the view matcher selected from given matcher and index.
 */
fun withIndex(matcher: Matcher<View?>, index: Int): Matcher<View?> {
    return object : TypeSafeMatcher<View?>() {
        var currentIndex = 0

        override fun describeTo(description: Description) {
            description.appendText("with index: ")
            description.appendValue(index)
            matcher.describeTo(description)
        }

        override fun matchesSafely(view: View?): Boolean {
            return matcher.matches(view) && currentIndex++ == index
        }
    }
}

fun atPosition(position: Int, itemMatcher: Matcher<View?>): Matcher<View?> {
    return object : BoundedMatcher<View?, RecyclerView>(RecyclerView::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("has item at position $position: ")
            itemMatcher.describeTo(description)
        }

        override fun matchesSafely(view: RecyclerView): Boolean {
            val viewHolder: RecyclerView.ViewHolder =
                view.findViewHolderForAdapterPosition(position)
                    ?: // has no item on such position
                    return false
            return itemMatcher.matches(viewHolder.itemView)
        }
    }
}

fun isAbove(otherViewMatcher: Matcher<View>): TypeSafeMatcher<View> {
    return object : TypeSafeMatcher<View>() {
        private var otherView: View? = null

        override fun describeTo(description: Description) {
            description.appendText("is above view: ")
            otherViewMatcher.describeTo(description)
        }

        override fun matchesSafely(view: View): Boolean {
            if (otherView == null) {
                otherView = view.rootView.findViewByMatcher(otherViewMatcher)
                if (otherView == null) return false // Other view not found
            }

            val location1 = IntArray(2)
            val location2 = IntArray(2)
            view.getLocationOnScreen(location1)
            otherView!!.getLocationOnScreen(location2) // Safe call as otherView might be null

            return location1[1] < location2[1] // Compare y-coordinates
        }
    }
}

// Extension function to find a view by matcher
private fun View.findViewByMatcher(matcher: Matcher<View>): View? {
    if (matcher.matches(this)) return this // Check if this view itself matches
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val foundView = child.findViewByMatcher(matcher)
            if (foundView != null) return foundView
        }
    }
    return null // No match found
}
