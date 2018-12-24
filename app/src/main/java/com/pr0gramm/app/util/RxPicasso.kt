package com.pr0gramm.app.util

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.Target
import rx.Observable

/**
 */
object RxPicasso {
    fun load(picasso: Picasso, request: RequestCreator): Observable<Bitmap> {
        return createObservable { emitter ->
            val target = object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                    checkMainThread()

                    try {
                        emitter.onNext(bitmap)
                    } finally {
                        emitter.onCompleted()
                    }
                }

                override fun onBitmapFailed(err: Exception?, errorDrawable: Drawable?) {
                    emitter.onError(RuntimeException("Could not load image", err))
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            }

            emitter.setCancellation { picasso.cancelRequest(target) }

            // load!
            request.into(target)
        }
    }
}
