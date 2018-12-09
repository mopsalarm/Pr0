package com.pr0gramm.app

import com.pr0gramm.app.api.pr0gramm.Api
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.TimeUnit

typealias Matcher = (RecordedRequest) -> Boolean
typealias Handler = (RecordedRequest) -> Any

class Resource(val matcher: Matcher, val handler: Handler)

class MockServer(private val handlers: List<Resource>) {
    private val server = MockWebServer().apply {
        setDispatcher(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return handleRequest(request)
            }
        })
        start()
    }

    val baseUrl = server.url("/")

    private fun handleRequest(request: RecordedRequest): MockResponse {
        val resource = handlers.first { it.matcher(request) }

        val response = resource.handler(request)
        if (response is MockResponse) {
            return response
        }

        return json(200, response)
    }
}

object Match {
    fun path(path: String): Matcher {
        return { req -> req.requestUrl.encodedPath() == path }
    }

    fun method(m: String): Matcher {
        return { req -> req.method.equals(m, ignoreCase = true) }
    }
}

fun Matcher.or(other: Matcher): Matcher {
    return { req -> this(req) && other(req) }
}

fun Matcher.and(other: Matcher): Matcher {
    return { req -> this(req) && other(req) }
}

fun json(statusCode: Int, value: Any): MockResponse {
    return MockResponse().apply {
        status = "$statusCode Hello"
        setHeader("Content-Type", "application/json")
        setBody(MoshiInstance.adapter(value.javaClass).toJson(value))
    }
}

fun mockServer(block: MockServerSetup.() -> Unit): MockServer {
    return MockServer(MockServerSetup().apply(block).resources)
}

class MockServerSetup {
    internal val resources = mutableListOf<Resource>()

    fun get(path: String, handler: Handler) {
        resources += Resource(Match.path(path).and(Match.method("GET")), handler)
    }

    fun post(path: String, handler: Handler) {
        resources += Resource(Match.path(path).and(Match.method("POST")), handler)
    }
}

val ms = mockServer {
    get("/api/items/get") {
        handleItemsGet()
    }

    get("/api/items/info") { request ->
        handleItemsInfo()
    }

    get("/api/user/sync") {
        handleUserSync()
    }

    post("/api/user/login") {
        handleUserLogin()
    }
}

fun handleUserLogin(): Any {
    return Api.Login(success = true, identifier = "xyz", banInfo = null)
}

fun handleUserSync(): Any {
    return Api.Sync(logLength = 0L, log = "", score = 123)
}

fun handleItemsGet(): Any {
    val items = (1L until 120L).reversed().map { idx ->
        Api.Feed.Item(
                id = idx,
                promoted = idx,
                created = Instant.now().minus(10, TimeUnit.DAYS).plus(idx, TimeUnit.HOURS),
                flags = 1,
                up = idx.toInt(),
                down = 5,
                user = "cha0s",
                image = "https://example.com/image-$idx.jpg",
                thumb = "https://example.com/thumb-$idx.jpg",
                mark = 1,
                audio = false,
                fullsize = "")
    }

    return Api.Feed(isAtEnd = true, isAtStart = true, _items = items)
}

fun handleItemsInfo(): Any {
    return Api.Post(tags = listOf(), comments = listOf())
}
