package com.pr0gramm.app.util;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import rx.Emitter;
import rx.Observable;

/**
 */
public class RxPicasso {
    private RxPicasso() {
    }

    public static Observable<Bitmap> load(Picasso picasso, RequestCreator request) {
        return Observable.create(emitter -> {
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    try {
                        emitter.onNext(bitmap);
                    } finally {
                        emitter.onCompleted();
                    }
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    emitter.onError(new RuntimeException("Could not load image"));
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                }
            };


            emitter.setCancellation(() -> picasso.cancelRequest(target));

            // load!
            request.into(target);
        }, Emitter.BackpressureMode.NONE);
    }
}
