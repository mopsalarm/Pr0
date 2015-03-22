package com.pr0gramm.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.google.inject.Inject;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.squareup.picasso.Picasso;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectExtra;
import roboguice.inject.InjectView;

@ContentView(R.layout.activity_zoom_view)
public class ZoomViewActivity extends RoboActionBarActivity {
    @InjectExtra("ZoomViewActivity.imageUrl")
    private String imageUrl;

    @InjectView(R.id.image)
    private ImageViewTouch imageView;

    @Inject
    private Picasso picasso;

    @Inject
    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_TO_SCREEN);

        picasso.load(imageUrl)
                .resize(settings.maxImageSize(), settings.maxImageSize())
                .onlyScaleDown()
                .into(imageView);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && settings.fullscreenZoomView()) {
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            decorView.setSystemUiVisibility(flags);
        }
    }

    @Override
    protected void onDestroy() {
        picasso.cancelRequest(imageView);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    public static Intent newIntent(Context context, String imageUrl) {
        Intent intent = new Intent(context, ZoomViewActivity.class);
        intent.putExtra("ZoomViewActivity.imageUrl", "http://img.pr0gramm.com/" + imageUrl);
        return intent;
    }
}
