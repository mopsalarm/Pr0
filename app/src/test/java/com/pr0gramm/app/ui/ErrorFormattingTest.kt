package com.pr0gramm.app.ui

import android.content.Context
import com.google.gson.JsonSyntaxException
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.pr0gramm.app.R
import com.pr0gramm.app.util.ErrorFormatting
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

class ErrorFormattingTest {
    @Test
    fun sslErrorFormatting() {
        val ctx = mock<Context> {
            on { getString(any<Int>(), any()) } doReturn "An ssl error occurred."
        }

        val err = IOException(ConnectException("Can not connect to localhost:443."))
        val formatter = ErrorFormatting.getFormatter(err)

        assert.that(formatter.shouldSendToCrashlytics(), equalTo(false))

        val msg = formatter.getMessage(ctx, err)
        assert.that(msg, equalTo("An ssl error occurred."))
    }

    @Test
    fun simpleHandler() {
        val ctx = mock<Context> {
            on { getString(R.string.error_json) } doReturn "Some error message"
        }

        val err = JsonSyntaxException("some cause")
        ErrorFormatting.getFormatter(err).getMessage(ctx, err)

        verify(ctx).getString(R.string.error_json)
    }
}