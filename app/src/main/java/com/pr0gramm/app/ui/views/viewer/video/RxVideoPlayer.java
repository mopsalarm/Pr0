package com.pr0gramm.app.ui.views.viewer.video;

import android.support.annotation.Nullable;

import rx.Observable;
import rx.subjects.BehaviorSubject;

/**
 * This is a base for {@link VideoPlayer}s with some rx features.
 */
public abstract class RxVideoPlayer implements VideoPlayer {
    private final BehaviorSubject<Boolean> buffering = BehaviorSubject.create(false);
    private Callbacks clientCallbacks = new EmptyVideoCallbacks();

    @Override
    public void setVideoCallbacks(@Nullable Callbacks callbacks) {
        this.clientCallbacks = callbacks != null ? callbacks : new EmptyVideoCallbacks();
    }

    public Observable<Boolean> buffering() {
        return buffering;
    }

    protected final Callbacks callbacks = new Callbacks() {
        @Override
        public void onVideoBufferingStarts() {
            clientCallbacks.onVideoBufferingStarts();
            buffering.onNext(true);
        }

        @Override
        public void onVideoBufferingEnds() {
            clientCallbacks.onVideoBufferingEnds();
            buffering.onNext(false);
        }

        @Override
        public void onVideoRenderingStarts() {
            onVideoBufferingEnds();
            clientCallbacks.onVideoRenderingStarts();
        }

        @Override
        public void onVideoSizeChanged(int width, int height) {
            clientCallbacks.onVideoSizeChanged(width, height);
        }

        @Override
        public void onVideoError(String message, ErrorKind kind) {
            clientCallbacks.onVideoError(message, kind);
        }

        @Override
        public void onDroppedFrames(int count) {
            clientCallbacks.onDroppedFrames(count);
        }
    };
}
