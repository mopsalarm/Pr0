package com.pr0gramm.app.ui

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.ImmutableApi
import org.joda.time.Instant

fun apiFeed(conf: ImmutableApi.Feed.Builder.() -> Unit = {}): Api.Feed {
    return ImmutableApi.Feed.builder()
            .atStart(true)
            .atEnd(false)
            .apply { conf() }
            .build()
}

fun apiItem(id: Long, conf: ImmutableApi.Item.Builder.() -> Unit = {}): Api.Feed.Item {
    return ImmutableApi.Item.builder()
            .id(id)
            .promoted(id)
            .image("/image" + id)
            .thumb("/thumb" + id)
            .fullsize("/full" + id)
            .user("user-" + id)
            .up(10)
            .down(3)
            .mark(1)
            .flags(1)
            .created(Instant(1337))
            .apply { conf() }
            .build()
}