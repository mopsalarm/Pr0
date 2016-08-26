package com.pr0gramm.app.services;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.services.config.ConfigService;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.adapter.rxjava.HttpException;
import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 */
@Singleton
public class UploadService {
    private static final Logger logger = LoggerFactory.getLogger("UploadService");

    private final Api api;
    private final UserService userService;
    private final Picasso picasso;
    private final ConfigService configService;

    @Inject
    public UploadService(Api api, UserService userService, Picasso picasso, ConfigService configService) {
        this.api = api;
        this.userService = userService;
        this.picasso = picasso;
        this.configService = configService;
    }

    private Observable<Long> maxSize() {
        return userService.loginState()
                .take(1)
                .map(state -> state.premium()
                        ? configService.config().maxUploadSizePremium()
                        : configService.config().maxUploadSizeNormal());
    }

    public Observable<Boolean> sizeOkay(File file) {
        return maxSize().map(maxSize -> file.length() < maxSize);
    }

    @SuppressLint("NewApi")
    public Observable<File> downsize(File file) {
        return maxSize().flatMap(maxSize -> Observable.fromCallable(() -> {
            logger.info("Try to scale {}kb image down to max of {}kb",
                    file.length() / 1024, maxSize / 1024);

            Bitmap bitmap = picasso.load(file)
                    .config(Bitmap.Config.ARGB_8888)
                    .centerInside()
                    .resize(2048, 2048)
                    .onlyScaleDown()
                    .get();

            logger.info("Image loaded at {}x{}px", bitmap.getWidth(), bitmap.getHeight());

            // scale down to temp file
            File target = File.createTempFile("upload", "jpg", file.getParentFile());
            target.deleteOnExit();

            Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
            int quality = 90;
            do {
                try (OutputStream output = new FileOutputStream(target)) {
                    logger.info("Compressing to {} at quality={}", format, quality);
                    if (!bitmap.compress(format, quality, output))
                        throw new IOException("Could not compress image data");

                    logger.info("Size is now {}kb", target.length() / 1024);
                }

                // decrease quality to shrink even further
                quality -= 10;
            } while (target.length() >= maxSize && quality > 30);

            logger.info("Finished downsizing with an image size of {}kb", target.length() / 1024);
            return target;
        }));
    }

    private Observable<UploadInfo> upload(File file) {
        if (!file.exists() || !file.isFile() || !file.canRead())
            return Observable.error(new FileNotFoundException("Can not read file to upload"));

        Subject<UploadInfo, UploadInfo> result = BehaviorSubject.create(new UploadInfo(0.f)).toSerialized();

        RequestBody output = new RequestBody() {
            @Override
            public MediaType contentType() {
                try {
                    String type = MimeTypeHelper.guess(file).or("image/jpeg");
                    return MediaType.parse(type);
                } catch (IOException ignored) {
                    return MediaType.parse("image/jpeg");
                }
            }

            @Override
            public long contentLength() throws IOException {
                return file.length();
            }

            @SuppressLint("NewApi")
            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                float length = (float) file.length();
                byte[] buffer = new byte[16 * 1024];

                long lastTime = 0L;
                try (InputStream input = new FileInputStream(file)) {
                    // send first progress report.
                    result.onNext(new UploadInfo(0.f));

                    int len, sent = 0;
                    while ((len = input.read(buffer)) >= 0) {
                        sink.write(buffer, 0, len);
                        sent += len;

                        long now = System.currentTimeMillis();
                        if (now - lastTime >= 100) {
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

        MultipartBody body = new MultipartBody.Builder()
                .setType(MediaType.parse("multipart/form-data"))
                .addFormDataPart("image", file.getName(), output)
                .build();

        long size = file.length();

        // perform the upload!
        api.upload(body)
                .doOnEach(n -> Track.upload(size))
                .map(response -> new UploadInfo(response.getKey(), Collections.emptyList()))
                .subscribeOn(BackgroundScheduler.instance())
                .subscribe(result::onNext, result::onError, result::onCompleted);

        return result.ignoreElements().mergeWith(result);
    }

    private Observable<Api.Posted> post(String key, ContentType contentType, Set<String> tags, boolean checkSimilar) {
        String sfwType = contentType.name().toLowerCase();
        String tagStr = FluentIterable.from(tags)
                .filter(tag -> !INVALID_TAGS.contains(tag.toLowerCase()))
                .append(sfwType)
                .transform(String::trim)
                .filter(tag -> !"sfw".equals(tag.toLowerCase()))
                .join(Joiner.on(","));

        return api.post(null, sfwType, tagStr, checkSimilar ? 1 : 0, key);
    }

    public Observable<UploadInfo> upload(File file, ContentType sfw, Set<String> tags) {
        return upload(file).flatMap(status -> {
            if (status.key != null) {
                return post(status, sfw, tags, true);
            } else {
                return Observable.just(status);
            }
        });
    }

    public Observable<UploadInfo> post(UploadInfo status, ContentType sfw, Set<String> tags, boolean checkSimilar) {
        return post(status.key, sfw, tags, checkSimilar).flatMap(response -> {
            if (response.getSimilar().size() > 0) {
                return Observable.just(new UploadInfo(status.key, response.getSimilar()));

            } else if (response.getError() != null) {
                return Observable.error(new UploadFailedException(response.getError(), response.getReport()));

            } else {
                return Observable.just(new UploadInfo(response.getItemId()));
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
            if (error instanceof HttpException) {
                // a http error code 403 tells us that the user is rate limited.
                if (((HttpException) error).code() == 403)
                    return Observable.just(true);
            }

            // just forward the error
            return Observable.error(error);
        });
    }

    public static class UploadInfo {
        final String key;
        private final List<HasThumbnail> similar;
        private final long id;
        private final float progress;

        private UploadInfo(long id) {
            this.id = id;
            this.key = null;
            this.progress = -1;
            this.similar = Collections.emptyList();
        }

        private UploadInfo(String key, List<? extends HasThumbnail> similar) {
            this.id = 0;
            this.key = key;
            this.progress = -1;
            this.similar = ImmutableList.copyOf(similar);
        }

        private UploadInfo(float progress) {
            this.id = 0;
            this.key = null;
            this.progress = progress;
            this.similar = Collections.emptyList();
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

        public boolean hasSimilar() {
            return similar.size() > 0;
        }

        public List<HasThumbnail> similar() {
            return similar;
        }
    }

    private static final ImmutableSet<Object> INVALID_TAGS = ImmutableSet.of(
            "sfw", "nsfw", "nsfl", "gif", "webm", "sound");

    public static final class UploadFailedException extends Exception {
        @NonNull
        public final Optional<Api.Posted.VideoReport> report;

        public UploadFailedException(String message, @NonNull Optional<Api.Posted.VideoReport> report) {
            super(message);

            this.report = report;
        }

        public String getErrorCode() {
            return getMessage();
        }
    }
}
