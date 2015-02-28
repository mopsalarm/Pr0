package com.pr0gramm.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import com.google.common.io.ByteStreams;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

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
    private final VideoView videoView;
    private final ProgressBar progress;

    public PlayerView(Context context, Picasso picasso, Downloader downloader) {
        super(context);
        this.picasso = picasso;
        this.downloader = downloader;

        inflate(context, R.layout.player, this);
        imageView = (ImageView) findViewById(R.id.image);
        videoView = (VideoView) findViewById(R.id.video);
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

    /**
     * Displays the given image url. This will scale down the image to
     * fit in the ui.
     *
     * @param image The image to load and display.
     */
    private void displayTypeImage(String image) {
        picasso.load(image)
                .resize(1024, 1024)
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
            // load the gif file
            Downloader.Response response = downloader.load(Uri.parse(image), 0);
            try (InputStream stream = response.getInputStream()) {
                // and decode it
                return new GifDrawable(ByteStreams.toByteArray(stream));
            }
        }, Schedulers.io());

        // show progress bar while loading
        progress.setVisibility(View.VISIBLE);

        bind(loader).subscribe(gif -> {
            // and set gif on ui thread as drawable
            progress.setVisibility(View.GONE);
            imageView.setImageDrawable(gif);

            //onStart = () -> viewImage.setImageDrawable(gif);
            // onStop = () -> viewImage.setImageDrawable(null);

            // and do it once now.
            //onStart.run();
        });
    }

    protected abstract <T> Observable<T> bind(Observable<T> observable);

    private void displayTypeVideo(String image) {
        // hide video controls
        MediaController ctrl = new MediaController(getContext());
        ctrl.setVisibility(View.GONE);
        videoView.setMediaController(ctrl);

        // set video on view
        videoView.setVideoURI(Uri.parse(image));

        // start on play
        videoView.setOnClickListener(v -> {
            videoView.seekTo(0);
        });

        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);

            int width = mp.getVideoWidth();
            int height = mp.getVideoHeight();

            ViewParent parent = videoView.getParent();
            if (parent instanceof View) {
                int parentWidth = ((View) parent).getWidth();
                float aspect = width / (float) height;

                ViewGroup.LayoutParams params = videoView.getLayoutParams();
                params.height = (int) (parentWidth / aspect);
                videoView.setLayoutParams(params);
            }
        });

        videoView.start();

//        onStart = () -> {
//            viewVideo.seekTo(0);
//            viewVideo.start();
//        };
//
//        onStop = viewVideo::pause;
    }
}
