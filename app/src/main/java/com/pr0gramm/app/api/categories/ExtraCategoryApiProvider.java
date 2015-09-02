package com.pr0gramm.app.api.categories;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.pr0gramm.app.api.pr0gramm.ApiGsonBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;

import retrofit.Converter;
import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;

/**
 */
public class ExtraCategoryApiProvider implements Provider<ExtraCategoryApi> {
    private final ExtraCategoryApi api;

    @Inject
    public ExtraCategoryApiProvider(OkHttpClient httpClient) {
        this.api = new Retrofit.Builder()
                .client(httpClient)
                .baseUrl("http://pr0.wibbly-wobbly.de/api/categories/v1/")
                .addConverter(byte[].class, new ByteConverter())
                .addConverterFactory(GsonConverterFactory.create(ApiGsonBuilder.builder().create()))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .build()
                .create(ExtraCategoryApi.class);
    }

    @Override
    public ExtraCategoryApi get() {
        return api;
    }

    private static class ByteConverter implements Converter<byte[]> {

        @Override
        public byte[] fromBody(ResponseBody body) throws IOException {
            return body.bytes();
        }

        @Override
        public RequestBody toBody(byte[] value) {
            return RequestBody.create(MediaType.parse("application/octet-stream"), value);
        }
    }
}
