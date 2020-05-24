package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api

sealed class Result<T : Any> {
    data class Error<T : Any>(val errorCode: String) : Result<T>()
    data class Success<T : Any>(val value: T) : Result<T>()

    inline fun <R : Any> map(transform: (T) -> R): Result<R> {
        return when (this) {
            is Error -> Error(errorCode)
            is Success -> Success(transform(value))
        }
    }

    companion object {
        fun <T : Api.HasError> ofValue(value: T): Result<T> {
            value.error?.let { errorCode ->
                return Error(errorCode)
            }

            return Success(value)
        }
    }
}
