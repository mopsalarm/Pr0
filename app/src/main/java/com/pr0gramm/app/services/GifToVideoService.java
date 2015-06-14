package com.pr0gramm.app.services;

import com.google.common.base.Optional;

import rx.Observable;

/**
 */
public interface GifToVideoService {
    Observable<Result> toVideo(String url);

    class Result {
        private final String gifUrl;
        private final String videoUrl;

        public Result(String gifUrl) {
            this(gifUrl, null);
        }

        public Result(String gifUrl, String videoUrl) {
            this.gifUrl = gifUrl;
            this.videoUrl = videoUrl;
        }

        public String getGifUrl() {
            return gifUrl;
        }

        /**
         * The url of the converted video. If conversion failed, this will
         * be an empty optional.
         */
        public Optional<String> getVideoUrl() {
            return Optional.fromNullable(videoUrl);
        }
    }
}
