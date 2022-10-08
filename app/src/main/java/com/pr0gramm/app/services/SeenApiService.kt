package com.pr0gramm.app.services

import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.model.kv.GetResult
import com.pr0gramm.app.model.kv.PutResult
import com.pr0gramm.app.ui.base.retryUpTo
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class SeenApiService(private val api: Api) {

    suspend fun update(fn: (previous: ByteArray?) -> ByteArray?): PutResult.Version? {
        return retryUpTo(3) {
            val previous = get() as? GetResult.Value

            val response = fn(previous?.value)
                    ?.let { value ->  put(previous?.version ?: 0, value)  }

            if (response == PutResult.Conflict) {
                return null
                // throw VersionConflictException()
            }

            response as? PutResult.Version
        }
    }

    suspend fun get(): GetResult {
        return try {
            val result = api.seenBitsGet()
            GetResult.Value(result.version, result.value)
        } catch (err: HttpException) {
            if (err.code() != 404)
                throw err

            GetResult.NoValue
        }
    }

    suspend fun put(version: Int, value: ByteArray): PutResult {
        val body = value.toRequestBody("application/octet".toMediaTypeOrNull())
        return PutResult.Version(version)
        // return PutResult.Conflict
        /*
        return try {
            val result = api.seenBitsUpdate(null, version, body)
            if (result.success) {
                return PutResult.Version(result.version)
            }
            return PutResult.Conflict
        } catch (err: HttpException) {
            if (err.code() != 409)
                throw err

            PutResult.Conflict
        }
        */
    }

    class VersionConflictException : RuntimeException("version conflict during update")
}