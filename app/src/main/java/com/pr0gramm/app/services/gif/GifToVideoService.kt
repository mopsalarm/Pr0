package com.pr0gramm.app.services.gif

import rx.Observable

/**
 */
interface GifToVideoService {
    fun toVideo(url: String): Observable<Result>

    class Result(val gifUrl: String, val videoUrl: String? = null)
}
