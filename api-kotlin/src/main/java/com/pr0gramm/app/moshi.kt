package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.InstantAdapter
import com.pr0gramm.app.api.pr0gramm.adapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.removeClassJsonAdapter

val MoshiInstance = Moshi.Builder()
        .adapter(InstantAdapter)
        .adapter(NothingAdapter)
        .build()
        .removeClassJsonAdapter()

