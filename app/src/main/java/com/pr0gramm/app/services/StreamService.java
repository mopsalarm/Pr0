package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import rx.Observable;
import rx.Subscriber;
import rx.subscriptions.Subscriptions;

import static com.pr0gramm.app.util.AndroidUtility.toFile;

/**
 * Helps in building an observable that streams a file from
 * the network or from the filesystem.
 */
@Singleton
public class StreamService {
    private final OkHttpClient okHttpClient;

    @Inject
    public StreamService(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * Streams the given http url. This does not support backpressure and it will
     * reuse the {@link com.pr0gramm.app.services.StreamService.Chunk} instances.
     * You must not hold on to those instances!
     */
    public Observable<Chunk> stream(HttpUrl uri) {
        return request(uri).flatMap(this::stream);
    }

    private Observable<Chunk> stream(Response response) {
        return Observable.create(subscriber -> {
            try {
                streamTo(subscriber, response.body());
                subscriber.onCompleted();
            } catch (Exception error) {
                subscriber.onError(error);
            }
        });
    }

    public Observable<Response> request(HttpUrl uri) {
        return Observable.create(subscriber -> {
            try {
                Request request = new Request.Builder().url(uri.toString()).build();
                Call call = okHttpClient.newCall(request);

                // cancel the request on unsubscribe
                subscriber.add(Subscriptions.create(call::cancel));

                Response response = call.execute();
                if (response.isSuccessful()) {
                    subscriber.onNext(response);
                    subscriber.onCompleted();
                } else {
                    subscriber.onError(new IOException("response failed with " + response.code()));
                }
            } catch (Throwable error) {
                subscriber.onError(error);
            }
        });
    }

    @SuppressLint("NewApi")
    public Observable<InputStream> inputStream(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return Observable.create(subscriber -> {
                try (InputStream inputStream = new FileInputStream(toFile(uri))) {
                    subscriber.onNext(inputStream);
                    subscriber.onCompleted();
                } catch (Throwable error) {
                    subscriber.onError(error);
                }
            });
        } else {
            return request(HttpUrl.parse(uri.toString())).flatMap(response -> Observable.create(subscriber -> {
                try (InputStream inputStream = response.body().byteStream()) {
                    subscriber.onNext(inputStream);
                    subscriber.onCompleted();
                } catch (Throwable error) {
                    subscriber.onError(error);
                }
            }));
        }
    }

    @SuppressLint("NewApi")
    public Observable<InputStream> inputStreamNoClose(Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return Observable.create(subscriber -> {
                try {
                    subscriber.onNext(new FileInputStream(toFile(uri)));
                    subscriber.onCompleted();
                } catch (Throwable error) {
                    subscriber.onError(error);
                }
            });
        } else {
            return request(HttpUrl.parse(uri.toString())).flatMap(response -> Observable.create(subscriber -> {
                try {
                    subscriber.onNext(response.body().byteStream());
                    subscriber.onCompleted();
                } catch (Throwable error) {
                    subscriber.onError(error);
                }
            }));
        }
    }

    @SuppressLint("NewApi")
    public Observable<Chunk> stream(File file) {
        return Observable.create(subscriber -> {
            try (InputStream inputStream = new FileInputStream(file)) {
                streamTo(subscriber, file.length(), inputStream);
                subscriber.onCompleted();
            } catch (Throwable error) {
                subscriber.onError(error);
            }
        });
    }

    public Observable<Chunk> stream(Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return stream(toFile(uri));
        } else {
            return stream(HttpUrl.parse(uri.toString()));
        }
    }

    @SuppressLint("NewApi")
    private static void streamTo(Subscriber<? super Chunk> subscriber, ResponseBody body) throws IOException {
        try (InputStream inputStream = body.byteStream()) {
            streamTo(subscriber, body.contentLength(), inputStream);
        }
    }

    private static void streamTo(Subscriber<? super Chunk> subscriber,
                                 long contentLength, InputStream inputStream) throws IOException {

        Chunk chunk = new Chunk(contentLength, new byte[16 * 1024]);
        int count;
        while ((count = inputStream.read(chunk.buffer)) >= 0) {
            if (count > 0) {
                if (count == chunk.buffer.length) {
                    subscriber.onNext(chunk);
                } else {
                    byte[] payload = Arrays.copyOfRange(chunk.buffer, 0, count);
                    Chunk smallChunk = new Chunk(chunk.contentLength, payload);
                    subscriber.onNext(smallChunk);
                }
            }
        }
    }

    public static class Chunk {
        public final long contentLength;
        public final byte[] buffer;

        public Chunk(long contentLength, byte[] buffer) {
            this.contentLength = contentLength;
            this.buffer = buffer;
        }
    }
}
