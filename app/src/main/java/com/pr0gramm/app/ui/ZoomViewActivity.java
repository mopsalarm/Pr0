package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.view.View;
import android.widget.ImageView;

import com.akodiakson.sdk.simple.Sdk;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.f2prateek.dart.InjectExtra;
import com.google.common.base.Strings;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.services.ThemeHelper;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.services.proxy.ProxyService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.util.decoders.Decoders;
import com.pr0gramm.app.util.decoders.PicassoDecoder;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.Picasso;

import javax.inject.Inject;

import butterknife.BindView;
import rx.Emitter;
import rx.Observable;

import static com.pr0gramm.app.services.ThemeHelper.theme;
import static com.pr0gramm.app.util.AndroidUtility.getTintentDrawable;

public class ZoomViewActivity extends BaseAppCompatActivity {
    private final String tag = "ZoomViewActivity" + System.currentTimeMillis();

    @InjectExtra("ZoomViewActivity__item")
    FeedItem item;

    @InjectExtra("ZoomViewActivity__hq")
    boolean loadHqIfAvailable;

    @BindView(R.id.image)
    SubsamplingScaleImageView imageView;

    @BindView(R.id.busy_indicator)
    View busyIndicator;

    @BindView(R.id.hq)
    ImageView hq;

    @Inject
    Picasso picasso;

    @Inject
    Downloader downloader;

    @Inject
    Settings settings;

    @Inject
    ProxyService proxyService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().fullscreen);
        super.onCreate(savedInstanceState);

        // normal content view
        setContentView(R.layout.activity_zoom_view);

        imageView.setMaxTileSize(4096);
        imageView.setDebug(BuildConfig.DEBUG);
        imageView.setBitmapDecoderFactory(() -> new PicassoDecoder(tag, picasso));
        imageView.setRegionDecoderFactory(() -> Decoders.newFancyRegionDecoder(downloader));

        rxImageLoaded(imageView)
                .compose(bindToLifecycle())
                .subscribe(event -> {
                    hideBusyIndicator();

                    imageView.setMaxScale(Math.max(
                            2 * (float) imageView.getWidth() / imageView.getSWidth(),
                            2 * (float) imageView.getHeight() / imageView.getSWidth()
                    ));
                });

        hq.setImageDrawable(getColoredHqIcon(R.color.grey_700));

        if (loadHqIfAvailable && isHqImageAvailable()) {
            loadHqImage();
        } else {
            loadImage();
        }
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @SuppressLint("InlinedApi")
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && settings.fullscreenZoomView()) {
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

            if (Sdk.isAtLeastJellyBean()) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
            }

            if (Sdk.isAtLeastKitKat())
                flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            decorView.setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onDestroy() {
        picasso.cancelTag(tag);
        super.onDestroy();
    }

    public static Intent newIntent(Context context, FeedItem item, boolean hq) {
        Intent intent = new Intent(context, ZoomViewActivity.class);
        intent.putExtra("ZoomViewActivity__item", item);
        intent.putExtra("ZoomViewActivity__hq", hq);
        return intent;
    }

    private void loadImage() {
        Uri url = UriHelper.of(this).media(item);
        loadImageWithUrl(url);

        if (isHqImageAvailable()) {
            hq.setOnClickListener(v -> loadHqImage());
            hq.animate().alpha(1).start();
        } else {
            hq.setVisibility(View.GONE);
        }
    }

    private boolean isHqImageAvailable() {
        return !Strings.isNullOrEmpty(item.fullsize());
    }

    private void loadHqImage() {
        hq.setOnClickListener(null);
        hq.setImageDrawable(getColoredHqIcon(ThemeHelper.accentColor()));
        hq.animate().alpha(1).start();

        Uri url = UriHelper.of(this).media(item, true);
        loadImageWithUrl(url);
    }

    private Drawable getColoredHqIcon(@ColorRes int colorId) {
        return getTintentDrawable(this, R.drawable.ic_action_high_quality, colorId);
    }

    private void loadImageWithUrl(Uri url) {
        showBusyIndicator();
        picasso.cancelTag(tag);
        imageView.setImage(ImageSource.uri(url));
    }

    private void showBusyIndicator() {
        if (busyIndicator != null) {
            busyIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void hideBusyIndicator() {
        if (busyIndicator == null) {
            return;
        }

        busyIndicator.setVisibility(View.GONE);
    }

    private static Observable<Void> rxImageLoaded(SubsamplingScaleImageView view) {
        return Observable.create((emitter) -> {
            view.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
                @Override
                public void onImageLoaded() {
                    emitter.onNext(null);
                    emitter.onCompleted();
                }
            });
        }, Emitter.BackpressureMode.NONE);
    }
}
