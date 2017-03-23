package com.pr0gramm.app.api.pr0gramm;

import java.io.IOException;

import retrofit2.HttpException;

/**
 */
public class HttpErrorException extends IOException {
    private final String body;

    HttpErrorException(HttpException cause, String body) {
        super(cause);
        this.body = body;
    }

    public String getErrorBody() {
        return body;
    }

    @Override
    public HttpException getCause() {
        return (HttpException) super.getCause();
    }

    public int code() {
        return getCause().code();
    }

    /**
     * Creates a new exception by reading the response of the given one.
     */
    public static HttpErrorException from(HttpException cause) {
        String body = "error body not available";
        try {
            body = cause.response().errorBody().string();
        } catch (Exception ignored) {
        }

        return new HttpErrorException(cause, body);
    }
}
