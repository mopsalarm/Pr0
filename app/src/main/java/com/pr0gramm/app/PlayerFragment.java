package com.pr0gramm.app;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import com.google.common.io.ByteStreams;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;

import javax.inject.Inject;

import pl.droidsonroids.gif.GifDrawable;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.pr0gramm.app.AndroidUtility.checkMainThread;
import static com.pr0gramm.app.ErrorDialogFragment.errorDialog;
import static java.lang.System.identityHashCode;
import static rx.android.observables.AndroidObservable.bindFragment;

/**
 */
public class PlayerFragment extends NestingFragment {
    private final String TAG = "Pr0Player " + Integer.toString(
            identityHashCode(this), Character.MAX_RADIX);

    private static final Handler handler = new Handler(Looper.getMainLooper());

    @InjectView(R.id.image)
    private ImageView imageView;

    @InjectView(R.id.video)
    private TextureView videoView;

    @InjectView(R.id.progress)
    private View progress;

    @Inject
    private Settings settings;

    @Inject
    private Picasso picasso;

    @Inject
    private Downloader downloader;

    @Inject
    private GifToWebmService gifToWebmService;

    @Inject
    private MediaPlayerService mediaPlayerService;

    private Runnable onDestroy = () -> {
    };

    private Runnable onPause = () -> {
    };

    private Runnable onResume = () -> {
    };

    private String mediaUrl;
    private MediaPlayerService.MediaPlayerHolder holder;
    private VideoSurfaceTextureListener textureListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaUrl = getArguments().getString("mediaUrl");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.player, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // show a busy indicator until the media is initialized.
        showBusyIndicator();

        if (mediaUrl.toLowerCase().endsWith(".webm")) {
            displayTypeVideo(mediaUrl);

        } else {
            if (mediaUrl.toLowerCase().endsWith(".gif")) {
                if (settings.convertGifToWebm()) {
                    displayTypeGifToWebm(mediaUrl);
                    return;
                }

                displayTypeGif(mediaUrl);

            } else {
                displayTypeImage(mediaUrl);
            }
        }
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
                Log.i("Gif2Webm", "Converted successfully");
                displayTypeVideo(result.getUrl());

            } else {
                Log.i("Gif2Webm", "Conversion did not work, showing gif");
                displayTypeGif(result.getUrl());
            }

            // if the fragment is currently resumed, we call the correct callback.
            if (isResumed())
                onResume.run();
        });
    }

    /**
     * Displays the given image url. This will scale down the image to
     * fit in the ui.
     *
     * @param image The image to load and display.
     */
    private void displayTypeImage(String image) {
        // hide the video view
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        int size = getMaxImageSize();
        picasso.load(image)
                .resize(size, size)
                .centerInside()
                .onlyScaleDown()
                .into(imageView, new HideBusyIndicator(this));
    }

    /**
     * We need to wrap the fragment into a weak reference so that the callback
     * will not create a memory leak.
     */
    private static class HideBusyIndicator implements Callback {
        private WeakReference<PlayerFragment> playerFragment;

        public HideBusyIndicator(PlayerFragment playerFragment) {
            this.playerFragment = new WeakReference<>(playerFragment);
        }

        @Override
        public void onSuccess() {
            PlayerFragment player = playerFragment.get();
            if (player != null)
                player.hideBusyIndicator();
        }

        @Override
        public void onError() {
            //  just indicate that we are finished.
            onSuccess();
        }
    }

    /**
     * Loads and displays a gif file.
     *
     * @param image The gif file to load.
     */
    private void displayTypeGif(String image) {
        // hide the video view
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

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
     * Binds the observable to this fragments lifecycle and adds an error dialog,
     * if the observable produces an error.
     *
     * @param observable The observable to bind
     * @return The bound observable.
     */
    private <T> Observable<T> bind(Observable<T> observable) {
        return bindFragment(this, observable).lift(errorDialog(this));
    }

    @Override
    public void onResume() {
        super.onResume();
        onResume.run();
    }

    @Override
    public void onPause() {
        onPause.run();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        onDestroy.run();
        super.onDestroy();
    }

    private void displayTypeVideo(String url) {
        // hide the image view
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.VISIBLE);

        Log.i(TAG, "Want to play video at " + this);
        if (holder != null) {
            Log.i(TAG, "Holder already exists.");
            videoView.setSurfaceTextureListener(textureListener);

            // if we are recreating the views, we need to re-attach the SurfaceTexture
            if (textureListener.hasTexture()) {
                Log.i(TAG, "Restoring SurfaceTexture: " + textureListener.getTexture());
                videoView.setSurfaceTexture(textureListener.getTexture());
            }

            return;
        }

        Log.i(TAG, "Create new MediaPlayer instance to play video");

        holder = new MediaPlayerService.MediaPlayerHolder(getActivity(), url);
        textureListener = new VideoSurfaceTextureListener(holder);
        videoView.setSurfaceTextureListener(textureListener);

        onResume = () -> {
            Log.i(TAG, "onResume called");

            // if we are recreating the views, we need to re-attach the SurfaceTexture
            if (textureListener.hasTexture() && videoView.getSurfaceTexture() != textureListener.getTexture()) {
                Log.i(TAG, "Restoring SurfaceTexture: " + textureListener.getTexture());
                videoView.setSurfaceTexture(textureListener.getTexture());
            }

            holder.setOnPreparedListener(mp -> {
                Log.i(TAG, "MediaPlayer is prepared");

                // loop 10/10
                mp.setLooping(true);

                // size of the video
                resizeVideoView(mp.getVideoWidth() / (float) mp.getVideoHeight(), 10);

                // start playback.
                holder.getPlayer().start();
                hideBusyIndicator();
            });
        };

        onPause = () -> {
            Log.i(TAG, "onPause called");
            // videoView.setSurfaceTextureListener(null);

            holder.setOnPreparedListener(null);

            if (holder.isPrepared()) {
                MediaPlayer player = holder.getPlayer();
                if (player.isPlaying())
                    player.pause();
            }
        };

        onDestroy = () -> {
            Log.i(TAG, "onDestroy called");
            holder.destroy();
            textureListener.destroy();
        };
    }

    private class VideoSurfaceTextureListener implements TextureView.SurfaceTextureListener {
        private final MediaPlayerService.MediaPlayerHolder holder;
        private SurfaceTexture texture;
        private boolean destroy;

        public VideoSurfaceTextureListener(MediaPlayerService.MediaPlayerHolder holder) {
            this.holder = holder;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.i(TAG, "SurfaceTexture available: " + texture);
            if (this.texture == null) {
                this.texture = texture;
                holder.getPlayer().setSurface(new Surface(texture));

            } else if (this.texture != texture) {
                Log.w(TAG, "Another TextureSurface became available.");
                return;
            }

            if (holder.isPrepared() && !holder.getPlayer().isPlaying()) {
                Log.i(TAG, "Starting playback");
                holder.getPlayer().start();
                hideBusyIndicator();
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (holder.isPrepared()) {
                try {
                    MediaPlayer player = holder.getPlayer();
                    if (player.isPlaying())
                        player.pause();

                } catch (IllegalStateException ignored) {
                }
            }

            if (destroy) {
                Log.i(TAG, "Destroying SurfaceTexture: " + surface);
            } else {
                Log.i(TAG, "Ignoring destroy of SurfaceTexture: " + surface);
            }
            return destroy;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged");
            onSurfaceTextureAvailable(surface, width, height);
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }

        public SurfaceTexture getTexture() {
            return texture;
        }

        public boolean hasTexture() {
            return texture != null;
        }

        public void destroy() {
            destroy = true;
            if (texture != null)
                texture.release();
        }
    }

    /**
     * Displays an indicator that something is loading. You need to pair
     * this with a call to {@link #hideBusyIndicator()}
     */
    private void showBusyIndicator() {
        progress.setVisibility(View.VISIBLE);
    }

    /**
     * Hides the busy indicator that was shown in {@link #showBusyIndicator()}.
     */
    private void hideBusyIndicator() {
        progress.setVisibility(View.GONE);
    }

    /**
     * Resizes the video view while keeping the given aspect ratio.
     */
    private void resizeVideoView(float aspect, int retries) {
        Log.i(TAG, "Setting aspect of the TextureView to " + aspect);

        ViewParent parent = videoView.getParent();
        if (parent instanceof View) {
            int parentWidth = ((View) parent).getWidth();
            if (parentWidth == 0) {
                // relayout again in a short moment
                if (retries > 0) {
                    Log.i(TAG, "Delay resizing of TextureView for 100ms");
                    handler.postDelayed(() -> resizeVideoView(aspect, retries - 1), 100);
                }

                return;
            }

            int newHeight = (int) (parentWidth / aspect);
            if (videoView.getHeight() == newHeight) {
                Log.i(TAG, "View already correctly sized at " + parentWidth + "x" + newHeight);
                return;
            }

            Log.i(TAG, "Setting size of TextureView to " + parentWidth + "x" + newHeight);
            ViewGroup.LayoutParams params = videoView.getLayoutParams();
            params.height = newHeight;
            videoView.setLayoutParams(params);

        } else {
            Log.w(TAG, "TextureView has no parent, can not set size.");
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

    private int getMaxImageSize() {
        // TODO figure something cool out here.
        return 1024;
    }
}
