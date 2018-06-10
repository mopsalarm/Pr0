package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.Base64ByteArrayAdapter
import com.pr0gramm.app.api.pr0gramm.InstantAdapter
import com.pr0gramm.app.api.pr0gramm.adapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.removeClassJsonAdapter


val MoshiInstance = run {
    removeClassJsonAdapter()

    Moshi.Builder()
            .adapter(InstantAdapter)
            .adapter(NothingAdapter)
            .adapter(Base64ByteArrayAdapter)
            .build()
}

