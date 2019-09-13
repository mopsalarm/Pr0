package com.pr0gramm.app.util

import android.widget.TextView
import androidx.collection.LruCache
import com.pr0gramm.app.R
import com.pr0gramm.app.ui.SimpleTextWatcher

object TextViewCache {
    private val cache = LruCache<String, String>(128)

    fun invalidate(view: TextView) {
        val previousWatcher = view.getTag(R.id.textContent) as? Watcher
        if (previousWatcher != null) {
            view.removeTextChangedListener(previousWatcher)

            // remove any cached value
            cache.remove(previousWatcher.key)
        }
    }

    fun addCaching(view: TextView, key: String, defaultText: String = "") {
        val previousWatcher = view.getTag(R.id.textContent) as? Watcher
        if (previousWatcher != null) {
            view.removeTextChangedListener(previousWatcher)
        }

        // restore previous value or set default
        view.text = cache[key] ?: defaultText

        // register a new watcher
        val watcher = Watcher(key)
        view.addTextChangedListener(watcher)
        view.setTag(R.id.textContent, watcher)
    }

    private class Watcher(val key: String) : SimpleTextWatcher() {
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            trace { "Caching $key: '$s'" }
            cache.put(key, s.toString())
        }
    }
}