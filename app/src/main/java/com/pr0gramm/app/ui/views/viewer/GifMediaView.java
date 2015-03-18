package com.pr0gramm.app.ui.views.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.common.io.ByteStreams;
import com.google.common.io.FileBackedOutputStream;
import com.google.common.util.concurrent.Uninterruptibles;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.squareup.picasso.Downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.util.async.Async;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.AndroidUtility.checkNotMainThread;
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

    // subscribe to get information about the download progress.
    private final BehaviorSubject<Float> downloadProgress = BehaviorSubject.create(0.f);
    private Subscription dlGifSubscription;

    public GifMediaView(Context context, Binder binder, String url) {
        super(context, binder, R.layout.player_gif, url);

        loadGif();
    }

    private void loadGif() {
        Observable<GifDrawable> loader = Async.fromCallable(() -> {
            // request the gif file
            Downloader.Response response = downloader.load(Uri.parse(url), 0);

            // and load + parse it
            return loadGifUsingTempFile(response);
        }, Schedulers.io());

        dlGifSubscription = binder.bind(loader).subscribe(gif -> {
            // and set gif on ui thread as drawable
            hideBusyIndicator();

            this.gif = gif;
            imageView.setImageDrawable(gif);

            if (!isPlaying())
                gif.stop();
        });

        downloadProgress.asObservable()
                .sample(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnCompleted(() -> onDownloadProgress(1.f))
                .subscribe(this::onDownloadProgress);
    }

    /**
     * Loads the data of the gif into a temporary file. The method then
     * loads the gif from this temporary file. The temporary file is removed
     * after loading the gif (or on failure).
     */
    @SuppressLint("NewApi")
    private GifDrawable loadGifUsingTempFile(Downloader.Response response) throws IOException, InterruptedException {
        File cacheDir = getContext().getCacheDir();
        File temporary = new File(cacheDir, "tmp" + identityHashCode(this) + ".gif");

        Log.i("Gif", "storing data into temporary file");
        RandomAccessFile storage = new RandomAccessFile(temporary, "rw");

        // remove entry from filesystem now - the system will remove the data
        // when the stream closes.
        //noinspection ResultOfMethodCallIgnored
        temporary.delete();

        // copy data to the file.
        try (InputStream stream = response.getInputStream()) {
            int length, count = 0;
            byte[] buffer = new byte[16 * 1024];
            while ((length = stream.read(buffer)) >= 0) {
                storage.write(buffer, 0, length);
                count += length;

                // publish download progress
                downloadProgress.onNext(count / (float) response.getContentLength());
            }
        }

        downloadProgress.onCompleted();

        Log.i("Gif", "loading gif from file");
        return new GifDrawable(storage.getFD());
    }

    private void onDownloadProgress(float progress) {
        checkMainThread();

        View progressView = getProgressView();
        if (progressView instanceof ProgressBar) {
            ProgressBar bar = (ProgressBar) progressView;

            bar.setMax(100);
            bar.setIndeterminate(false);
            bar.setProgress((int) (100 * progress));
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
        // unsubscribe and destroy downloader
        if (dlGifSubscription != null)
            dlGifSubscription.unsubscribe();

        imageView.setImageDrawable(null);

        if (gif != null)
            gif.recycle();

        super.onDestroy();
    }


}
