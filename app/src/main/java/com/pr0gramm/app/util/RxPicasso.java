package com.pr0gramm.app.util;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import rx.Observable;
import rx.subscriptions.Subscriptions;

/**
 */
public class RxPicasso {
    private RxPicasso() {
    }

    public static Observable<Bitmap> load(Picasso picasso, RequestCreator request) {
        return Observable.create(ob -> {
            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    try {
                        ob.onNext(bitmap);
                    } finally {
                        ob.onCompleted();
                    }
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    ob.onError(new RuntimeException("Could not load image"));
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                }
            };


            ob.add(Subscriptions.create(() -> picasso.cancelRequest(target)));

            // load!
            request.into(target);
        });
    }
}
