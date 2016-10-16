package com.pr0gramm.app.ui.views.viewer.video;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.pr0gramm.app.io.GreedyInputStreamCache;
import com.pr0gramm.app.io.InputStreamCache;
import com.pr0gramm.app.util.BackgroundScheduler;

import org.apache.commons.io.input.AutoCloseInputStream;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;
import rx.functions.Func0;
import rx.util.async.Async;

/**
 */
class InputStreamCacheDataSource implements BufferedDataSource {
    final SettableFuture<HttpResult> response = SettableFuture.create();
    private final Uri uri;

    private long totalSize = -1;
    private InputStream inputStream;

    InputStreamCacheDataSource(Context context, OkHttpClient okHttpClient, Uri uri) {
        this.uri = uri;

        Request request = new Request.Builder().url(uri.toString()).build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                response.set(new HttpResult(error, null, null));
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    // forward errors to the failure handler.
                    String msg = String.format("Server responded with %d '%s'", resp.code(), resp.message());
                    onFailure(call, new IOException(msg));
                    return;
                }

                response.set(new HttpResult(null, resp, new GreedyInputStreamCache(context,
                        new AutoCloseInputStream(resp.body().byteStream()))));
            }
        });
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        HttpResult result = Futures.getUnchecked(response);
        if (result.error != null)
            throw new IOException("::pr0:: network error", result.error);

        inputStream = result.cache.get();

        // seeks/skips to the correct positions
        ByteStreams.skipFully(inputStream, dataSpec.position);

        totalSize = result.response.body().contentLength();
        return totalSize;
    }

    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        return ByteStreams.read(inputStream, buffer, offset, readLength);
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    /**
     * Returns the percentage that is buffered, or -1, if unknown
     */
    public float buffered() {
        return totalSize > 0
                ? Futures.getUnchecked(response).cache.cacheSize() / (float) totalSize
                : -1;
    }

    private static class HttpResult {
        final IOException error;
        final Response response;
        final InputStreamCache cache;

        HttpResult(IOException error, Response response, InputStreamCache cache) {
            this.error = error;
            this.response = response;
            this.cache = cache;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            // close the response in a background thread.
            Async.start(new Closer(this.response), BackgroundScheduler.instance())
                    .onErrorResumeNext(Observable.empty())
                    .subscribe();
        }
    }

    private static class Closer implements Func0<Void> {
        private final Response response;

        Closer(Response response) {
            this.response = response;
        }

        @Override
        public Void call() {
            if (this.response != null)
                response.close();

            return null;
        }
    }
}
