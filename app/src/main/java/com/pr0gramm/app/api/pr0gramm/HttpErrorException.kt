package com.pr0gramm.app.api.pr0gramm

import retrofit2.HttpException
import java.io.IOException

/**
 */
class HttpErrorException(override val cause: HttpException, val errorBody: String) : IOException(cause) {
    val code = cause.code()

    companion object {
        /**
         * Creates a new exception by reading the response of the given one.
         */
        fun from(cause: HttpException): HttpErrorException {
            var body = "error body not available"
            try {
                body = cause.response().errorBody().string()
            } catch (ignored: Exception) {
            }

            return HttpErrorException(cause, body)
        }
    }
}
