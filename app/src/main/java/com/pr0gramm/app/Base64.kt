package com.pr0gramm.app

import android.util.Base64
import java.nio.charset.Charset


fun String.decodeBase64(urlSafe: Boolean = false): ByteArray {
    val flags = if (urlSafe) Base64.URL_SAFE else 0
    return Base64.decode(this, flags)
}

fun String.decodeBase64String(urlSafe: Boolean = false, charset: Charset = Charsets.UTF_8): String {
    return decodeBase64(urlSafe).toString(charset)
}

fun ByteArray.encodeBase64(urlSafe: Boolean = false): String {
    val flags = if (urlSafe) Base64.URL_SAFE else 0
    return Base64.encodeToString(this, flags or Base64.NO_WRAP)
}
