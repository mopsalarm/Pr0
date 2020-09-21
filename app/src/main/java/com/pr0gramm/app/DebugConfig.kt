package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.Api
import okhttp3.HttpUrl.Companion.toHttpUrl

private val actualDebugConfig = DebugConfig(
        // ignoreUnreadState = true,
        // delayApiRequests = true,
        // pendingNotifications = Api.Inbox(listOf(
        //         PendingNotifications.Comment("UserA", 571121),
        //         PendingNotifications.Comment("UserB", 571121),
        //         PendingNotifications.Comment("UserA", 571122, flags = 2)
        // )),
        // mockApiUrl = "https://2b3c5b6e8275.eu.ngrok.io",
        // versionOverride = 1860,
)

var debugConfig = if (BuildConfig.DEBUG) actualDebugConfig else DebugConfig()

data class DebugConfig(
        val ignoreUnreadState: Boolean = false,
        val pendingNotifications: Api.Inbox? = null,
        val delayApiRequests: Boolean = false,
        val mockApiUrl: String? = null,
        val versionOverride: Int? = null) {

    val mockApiHost: String? = mockApiUrl?.toHttpUrl()?.host
}


private object PendingNotifications {
    object Comment {
        private var nextId = 10000

        operator fun invoke(name: String, itemId: Long, flags: Int = 1): Api.Inbox.Item {
            return Api.Inbox.Item(
                    type = "comment",
                    id = (++nextId).toLong(),
                    message = "Dies ist der Kommentar mit der Id ${nextId}",
                    name = name,
                    mark = 9,
                    flags = flags,
                    itemId = itemId,
                    read = false,
                    creationTime = Instant.now().minus(Duration.hours(1)),
                    thumbnail = "2020/02/03/c749715fed87247c.jpg")
        }
    }

    object Notification {
        val Birthday = Api.Inbox.Item(
                id = 1353318,
                type = "notification",
                message = "Hallo,↵↵das pr0gramm feiert heute seinen 13ten Geburtstag. Zur Feier des Tages spendieren wir jedem 3 Tage pr0mium die bereits deinem Profil gutgeschrieben wurden. Auf viele weiter Jahre!↵↵herzlich,↵das pr0gramm↵↵",
                read = false,
                creationTime = Instant.ofEpochSeconds(1579781358))
    }

    object Stalk {
        val ImageSFW = Api.Inbox.Item(
                type = "follows",
                id = 138802,
                itemId = 3608673,
                thumbnail = "2020/02/09/632eefd938e17b1b.jpg",
                image = "2020/02/09/632eefd938e17b1b.jpg",
                flags = 1,
                name = "Mos",
                mark = 9,
                senderId = 340604,
                score = 32,
                creationTime = Instant.ofEpochSeconds(1581221338),
                message = null,
                read = false
        )

        val ImageNSFW = Api.Inbox.Item(
                type = "follows",
                id = 138803,
                itemId = 3608207,
                thumbnail = "2020/01/06/e92f583d10038bee.jpg",
                image = "2020/01/06/e92f583d10038bee.jpg",
                flags = 2,
                name = "cha0s",
                mark = 9,
                senderId = 340604,
                score = 37,
                creationTime = Instant.ofEpochSeconds(1581222531),
                message = null,
                read = false
        )

        val VideoSFW = Api.Inbox.Item(
                type = "follows",
                id = 138804,
                itemId = 3608807,
                thumbnail = "2020/01/06/62822a6e036a84a4.jpg",
                image = "2020/01/06/62822a6e036a84a4.mp4",
                flags = 1,
                name = "cha0s",
                mark = 9,
                senderId = 340604,
                score = 37,
                creationTime = Instant.ofEpochSeconds(1581222531),
                message = null,
                read = false
        )
    }
}
