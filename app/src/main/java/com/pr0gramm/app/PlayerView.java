package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import pl.droidsonroids.gif.GifDrawable;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

/**
 */
public abstract class PlayerView extends FrameLayout {
    private final Picasso picasso;
    private final Downloader downloader;

    private final ImageView imageView;
    private final TextureView videoView;
    private final ProgressBar progress;
    private final Settings settings;

    private Runnable onAttach = () -> {
    };

    private Runnable onPause = () -> {
    };

    private Runnable onResume = () -> {
    };

    public PlayerView(Context context, Picasso picasso, Downloader downloader) {
        super(context);
        this.settings = Settings.of(context);
        this.picasso = picasso;
        this.downloader = downloader;

        inflate(context, R.layout.player, this);
        imageView = (ImageView) findViewById(R.id.image);
        videoView = (TextureView) findViewById(R.id.video);
        progress = (ProgressBar) findViewById(R.id.progress);
    }

    public <T> void play(String url) {
        // get the url of the posts content (image or video)

        if (url.toLowerCase().endsWith(".webm")) {
            // hide the image view
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);

            displayTypeVideo(url);

        } else {
            // hide the video view
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);

            if (url.toLowerCase().endsWith(".gif")) {
                displayTypeGif(url);
            } else {
                displayTypeImage(url);
            }
        }

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onAttach.run();
    }

    /**
     * Displays the given image url. This will scale down the image to
     * fit in the ui.
     *
     * @param image The image to load and display.
     */
    private void displayTypeImage(String image) {
        int size = getMaxImageSize();
        picasso.load(image)
                .resize(size, size)
                .centerInside()
                .onlyScaleDown()
                .into(imageView);
    }

    /**
     * Loads and displays a gif file.
     *
     * @param image The gif file to load.
     */
    @SuppressLint("NewApi")
    private void displayTypeGif(String image) {
        Observable<GifDrawable> loader = Async.fromCallable(() -> {
            // request the gif file
            Downloader.Response response = downloader.load(Uri.parse(image), 0);

            // and load + parse it
            if (settings.isLoadGifInMemoryEnabled()) {
                return loadGifInMemory(response);
            } else {
                return loadGifUsingTempFile(response);
            }
        }, Schedulers.io());

        // show progress bar while loading
        progress.setVisibility(View.VISIBLE);

        bind(loader).subscribe(gif -> {
            // and set gif on ui thread as drawable
            progress.setVisibility(View.GONE);
            imageView.setImageDrawable(gif);
        });
    }

    protected abstract <T> Observable<T> bind(Observable<T> observable);

    /**
     * Must be called from outside on start of the fragment
     */
    public void onResume() {
        onResume.run();
    }

    /**
     * Must be called from outside on pause of the fragment/activity
     */
    public void onPause() {
        onPause.run();
    }

    private void displayTypeVideo(String url) {
        onResume = () -> {
            Log.i("Player", "on start called");

            MediaPlayer player = new MediaPlayer();

            try {
                player.setDataSource(getContext(), Uri.parse(url));
            } catch (IOException err) {
                Throwables.propagate(err);
            }

            player.setOnPreparedListener(mp -> {
                Log.i("Player", "Player prepared");

                // loop 10/10
                mp.setLooping(true);

                // size of the video
                int width = mp.getVideoWidth();
                int height = mp.getVideoHeight();

                // the moment we are attached to the window, we perform a resize operation
                onAttach = () -> resizeVideoView(width, height);

                // perform initial resizing of the view
                if (getWindowToken() != null)
                    onAttach.run();

                TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        Log.i("Player", "surface texture is available, starting playback");
                        mp.setSurface(new Surface(surface));
                        mp.start();
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        try {
                            if (mp.isPlaying())
                                mp.pause();

                        } catch (IllegalStateException ignored) {
                            // maybe we were already destroyed?
                        }

                        return true;
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    }
                };

                videoView.setSurfaceTextureListener(textureListener);

                // maybe there already is a surface. We'll use it then.
                SurfaceTexture surface = videoView.getSurfaceTexture();
                if (surface != null) {
                    mp.setSurface(new Surface(surface));
                    mp.start();
                }
            });

            onPause = () -> {
                Log.i("Player", "on pause called");
                player.reset();
                player.release();

                videoView.setSurfaceTextureListener(null);
            };

            // lets go!
            player.prepareAsync();
        };
    }

    /**
     * Resizes the video view while keeping the aspect-ratio
     *
     * @param width  The width of the video
     * @param height The height of the video
     */
    private void resizeVideoView(int width, float height) {
        ViewParent parent = videoView.getParent();
        if (parent instanceof View) {
            int parentWidth = ((View) parent).getWidth();
            float aspect = width / height;

            int newHeight = (int) (parentWidth / aspect);
            if (videoView.getHeight() == newHeight)
                return;

            ViewGroup.LayoutParams params = videoView.getLayoutParams();
            params.height = newHeight;
            videoView.setLayoutParams(params);
        }
    }

    /**
     * Loads the data of the gif into memory and then decodes it.
     */
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
    private GifDrawable loadGifUsingTempFile(Downloader.Response response) throws IOException {
        File cacheDir = getContext().getCacheDir();
        File temporary = new File(cacheDir, "tmp" + System.identityHashCode(this) + ".gif");

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

    private int getMaxImageSize() {
        // TODO figure something cool out here.
        return 1024;
    }
}
