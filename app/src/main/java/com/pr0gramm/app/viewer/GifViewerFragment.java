package com.pr0gramm.app.viewer;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.google.common.io.ByteStreams;
import com.pr0gramm.app.ErrorDialogFragment;
import com.pr0gramm.app.GifToWebmService;
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

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static java.lang.System.identityHashCode;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class GifViewerFragment extends ViewerFragment {
    @Inject
    private Settings settings;

    @Inject
    private Downloader downloader;

    @Inject
    private GifToWebmService gifToWebmService;

    @InjectView(R.id.image)
    private ImageView imageView;

    public GifViewerFragment() {
        super(R.layout.player_image);
    }

    /**
     * Binds the observable to this fragments lifecycle and adds an error dialog,
     * if the observable produces an error.
     *
     * @param observable The observable to bind
     * @return The bound observable.
     */
    private <T> Observable<T> bind(Observable<T> observable) {
        return bindFragment(this, observable).lift(ErrorDialogFragment.errorDialog(this));
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String url = getUrlArgument();

        // check if we do the webm-conversion.
        if (settings.convertGifToWebm()) {
            displayTypeGifToWebm(url);
            return;
        }

        displayTypeGif(url);
    }

    /**
     * Converts the gif to a webm on the server.
     *
     * @param url The url of the gif file to convert.
     */
    private void displayTypeGifToWebm(String url) {
        Log.i("Gif2Webm", "Start converting gif to webm");
        bind(gifToWebmService.convertToWebm(url)).subscribe(result -> {
            checkMainThread();

            // dispatch the result
            if (result.isWebm()) {
                Log.i("Gif2Webm", "Converted successfully, replace with webm fragment");
                ViewerFragment webmFragment = VideoViewerFragment.newInstance(settings, result.getUrl());
                getFragmentManager().beginTransaction()
                        .replace(getId(), webmFragment)
                        .commit();

            } else {
                Log.i("Gif2Webm", "Conversion did not work, showing gif");
                displayTypeGif(result.getUrl());
            }
        });
    }

    /**
     * Loads and displays a gif file.
     *
     * @param image The gif file to load.
     */
    private void displayTypeGif(String image) {
        Observable<GifDrawable> loader = Async.fromCallable(() -> {
            // request the gif file
            Downloader.Response response = downloader.load(Uri.parse(image), 0);

            // and load + parse it
            if (settings.loadGifInMemory()) {
                return loadGifInMemory(response);
            } else {
                return loadGifUsingTempFile(response);
            }
        }, Schedulers.io());

        bind(loader).subscribe(gif -> {
            // and set gif on ui thread as drawable
            hideBusyIndicator();
            imageView.setImageDrawable(gif);
        });
    }


    /**
     * Loads the data of the gif into memory and then decodes it.
     */
    @SuppressLint("NewApi")
    private GifDrawable loadGifInMemory(Downloader.Response response) throws IOException {
        try (InputStream stream = response.getInputStream()) {
            // and decode it
            return new GifDrawable(ByteStreams.toByteArray(stream));
        }
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    @SuppressLint("NewApi")
    private GifDrawable loadGifUsingTempFile(Downloader.Response response) throws IOException {
        File cacheDir = getActivity().getCacheDir();
        File temporary = new File(cacheDir, "tmp" + identityHashCode(this) + ".gif");

        try {
            Log.i("Gif", "storing data into temporary file");
            try (FileOutputStream ra = new FileOutputStream(temporary)) {
                try (InputStream stream = response.getInputStream()) {
                    byte[] buffer = new byte[1024 * 16];

                    int length;
                    while ((length = stream.read(buffer)) >= 0)
                        ra.write(buffer, 0, length);
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
}
