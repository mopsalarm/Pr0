package com.pr0gramm.app

import com.pr0gramm.app.resources.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

class GenericDispatcher : Dispatcher() {
    private val NotFound = MockResponse().setResponseCode(404)

    override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.requestUrl.encodedPath()) {
            "/api/items/get" -> handleItemsGet(request)
            "/api/items/info" -> handleItemsInfo(request)
            "/api/profile/info" -> handleUserProfile(request)
            else -> NotFound
        }
    }

    private fun handleItemsInfo(request: RecordedRequest): MockResponse {
        if (request.path == "/api/items/info?itemId=4") {
            return json(ItemsInfo4)
        } else {
            return NotFound
        }
    }

    private fun handleUserProfile(request: RecordedRequest): MockResponse {
        if ("Mopsalarm" in request.path) {
            return json(ProfileMopsalarm)
        } else {
            return NotFound
        }
    }

    private fun handleItemsGet(request: RecordedRequest): MockResponse {
        return when (request.path) {
            "/api/items/get?promoted=1&flags=1" -> json(ItemsGetSFW)
            "/api/items/get?promoted=1&older=3&flags=1&tags=repost" -> json(ItemsGetRepost)
            else -> json(ItemsGetEmpty)
        }
    }

    private fun json(encoded: String): MockResponse {
        return MockResponse().apply {
            setResponseCode(200)
            setHeader("Content-Type", "application/json")
            setBody(encoded)
        }
    }
}
