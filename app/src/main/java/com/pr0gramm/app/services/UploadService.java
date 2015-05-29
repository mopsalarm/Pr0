package com.pr0gramm.app.services;

import android.annotation.SuppressLint;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.response.Posted;
import com.pr0gramm.app.feed.ContentType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedOutput;
import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 */
@Singleton
public class UploadService {
    private final Api api;

    @Inject
    public UploadService(Api api) {
        this.api = api;
    }

    private Observable<UploadInfo> upload(File file) {
        BehaviorSubject<UploadInfo> result = BehaviorSubject.create(new UploadInfo(0.f));

        TypedOutput output = new TypedOutput() {
            @Override
            public String fileName() {
                return file.getName();
            }

            @Override
            public String mimeType() {
                return isPngImage(file) ? "image/png" : "image/jpeg";
            }

            @Override
            public long length() {
                return file.length();
            }

            @SuppressLint("NewApi")
            @Override
            public void writeTo(OutputStream out) throws IOException {
                float length = (float) length();
                byte[] buffer = new byte[16 * 1024];

                long lastTime = 0L;
                try (InputStream input = new FileInputStream(file)) {
                    // send first progress report.
                    result.onNext(new UploadInfo(0.f));

                    int len, sent = 0;
                    while ((len = input.read(buffer)) >= 0) {
                        out.write(buffer, 0, len);
                        sent += len;

                        long now = System.currentTimeMillis();
                        if (now - lastTime >= 50) {
                            lastTime = now;

                            // send progress now.
                            float progress = sent / length;
                            result.onNext(new UploadInfo(progress));
                        }
                    }

                    // tell that the file is sent
                    result.onNext(new UploadInfo(1.f));
                }
            }
        };

        // perform the upload!
        api.upload(output).subscribe(
                response -> result.onNext(new UploadInfo(response.getKey())),
                result::onError, result::onCompleted);

        return result.ignoreElements().mergeWith(result);
    }

    private Observable<Posted> post(String key, ContentType contentType, Set<String> tags) {
        String sfwType = contentType.name().toLowerCase();
        String tagStr = FluentIterable.from(tags)
                .filter(tag -> !INVALID_TAGS.contains(tag.toLowerCase()))
                .append(sfwType)
                .transform(String::trim)
                .filter(tag -> !"sfw".equals(tag.toLowerCase()))
                .join(Joiner.on(","));

        return api.post(null, sfwType, tagStr, 0, key);
    }

    public Observable<UploadInfo> upload(File file, ContentType sfw, Set<String> app) {
        return upload(file).flatMap(status -> {
            if (status.key != null) {
                return post(status.key, sfw, app).flatMap(response -> {
                    if (response.getError() != null) {
                        return Observable.error(new UploadFailedException(response.getError()));
                    } else {
                        return Observable.just(new UploadInfo(response.getItemId()));
                    }
                });
            } else {
                return Observable.just(status);
            }
        });
    }

    /**
     * Checks if the current user is rate limited. Returns true, if the user
     * is not allowed to upload an image right now. Returns false, if the user is
     * allowed to upload an image.
     */
    public Observable<Boolean> checkIsRateLimited() {
        return api.ratelimited().map(v -> false).onErrorResumeNext(error -> {
            if (error instanceof RetrofitError) {
                // a http error code 403 tells us that the user is rate limited.
                Response response = ((RetrofitError) error).getResponse();
                if (response != null && response.getStatus() == 403)
                    return Observable.just(true);
            }

            // just forward the error
            return Observable.error(error);
        });
    }

    public static class UploadInfo {
        final String key;
        private final long id;
        private final float progress;

        private UploadInfo(long id) {
            this.id = id;
            this.key = null;
            this.progress = -1;
        }

        private UploadInfo(String key) {
            this.id = 0;
            this.key = key;
            this.progress = -1;
        }

        private UploadInfo(float progress) {
            this.id = 0;
            this.key = null;
            this.progress = progress;
        }

        public boolean isFinished() {
            return id > 0;
        }

        public float getProgress() {
            return progress;
        }

        public long getId() {
            return id;
        }
    }

    @SuppressLint("NewApi")
    private static boolean isPngImage(File file) {
        try {
            try (InputStream input = new FileInputStream(file)) {
                // read the first four bytes to check file type
                byte[] buffer = new byte[4];
                ByteStreams.readFully(input, buffer);

                // and compare to well known values
                return buffer[0] == (byte) 0x89
                        && buffer[1] == 'P' && buffer[2] == 'N' && buffer[3] == 'G';
            }
        } catch (Exception error) {
            error.printStackTrace();
            return false;
        }
    }

    private static final ImmutableSet<Object> INVALID_TAGS = ImmutableSet.of(
            "sfw", "nsfw", "nsfl", "gif", "webm");

    public static final class UploadFailedException extends Exception {
        public UploadFailedException(String message) {
            super(message);
        }

        public String getErrorCode() {
            return getMessage();
        }
    }
}
