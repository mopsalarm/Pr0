package com.pr0gramm.app.feed

sealed class FeedException : RuntimeException() {
    class GeneralFeedException(val key: String) : FeedException()
    class NotPublicException : FeedException()
    class NotFoundException : FeedException()
    class InvalidContentTypeException(val requiredType: ContentType) : FeedException()
}