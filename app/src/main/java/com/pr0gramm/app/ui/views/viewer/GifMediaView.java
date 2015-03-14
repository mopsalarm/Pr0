package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.google.common.io.ByteStreams;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.squareup.picasso.Downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static java.lang.System.identityHashCode;

/**
 */
@SuppressLint("ViewConstructor")
public class GifMediaView extends MediaView {
    @Inject
    private Downloader downloader;

    @Inject
    private Settings settings;

    @InjectView(R.id.image)
    private ImageView imageView;

    // the gif that is shown
    private GifDrawable gif;

    public GifMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_image, url);
        loadGif();
    }

    private void loadGif() {
        Observable<GifDrawable> loader = Async.fromCallable(() -> {
            // request the gif file
            Downloader.Response response = downloader.load(Uri.parse(url), 0);

            // and load + parse it
            if (settings.loadGifInMemory()) {
                return loadGifInMemory(response);
            } else {
                return loadGifUsingTempFile(response);
            }
        }, Schedulers.io());

        binder.bind(loader).subscribe(gif -> {
            // and set gif on ui thread as drawable
            hideBusyIndicator();

            this.gif = gif;
            imageView.setImageDrawable(gif);

            if (!isPlaying())
                gif.stop();
        });
    }


    /**
     * Loads the data of the gif into memory and then decodes it.
     */
    @SuppressLint("NewApi")
    private GifDrawable loadGifInMemory(Downloader.Response response) throws IOException {
        try {
            try (InputStream stream = response.getInputStream()) {
                // and decode it
                return new GifDrawable(ByteStreams.toByteArray(stream));
            }
        } catch (OutOfMemoryError oom) {
            binder.onError(getContext().getString(R.string.error_out_of_memory_while_decoding_gif));

            // fall back to using a temp-file
            return loadGifUsingTempFile(response);
        }
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    @SuppressLint("NewApi")
    private GifDrawable loadGifUsingTempFile(Downloader.Response response) throws IOException {
        File cacheDir = getContext().getCacheDir();
        File temporary = new File(cacheDir, "tmp" + identityHashCode(this) + ".gif");

        try {
            Log.i("Gif", "storing data into temporary file");
            try (FileOutputStream ra = new FileOutputStream(temporary)) {
                try (InputStream stream = response.getInputStream()) {
                    ByteStreams.copy(stream, ra);
                }
            }

            Log.i("Gif", "loading gif from file");
            return new GifDrawable(temporary);

        } finally {
            // delete the temp file
            if (!temporary.delete())
                Log.w("Gif", "Could not clean up");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (gif != null && isPlaying())
            gif.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (gif != null && isPlaying())
            gif.pause();
    }

    @Override
    public void playMedia() {
        super.playMedia();
        if (gif != null && isPlaying())
            gif.start();
    }

    @Override
    public void stopMedia() {
        super.stopMedia();
        if (gif != null)
            gif.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        imageView.setImageDrawable(null);
        gif.recycle();
    }
}
