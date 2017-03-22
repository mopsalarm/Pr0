package com.pr0gramm.app.ui.fragments;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.ui.AdService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.pr0gramm.app.ApplicationClass.appComponent;

/**
 * A simple adapter that shows one or zero views.
 */
class AdViewAdapter extends RecyclerView.Adapter<AdViewAdapter.AdViewHolder> {
    private static final Logger logger = LoggerFactory.getLogger("AdViewAdapter");

    private final AdService adService = appComponent().adService();

    private AdView viewInstance;
    private boolean showAds;
    private int failureCount = 5;

    AdViewAdapter() {
        setHasStableIds(true);
    }

    private AdView newAdView(Context context, ViewGroup parent) {
        AdView view = new AdView(context.getApplicationContext());

        view.setAdSize(new AdSize(AdSize.FULL_WIDTH, 70));
        view.setAdUnitId(context.getString(R.string.banner_ad_unit_id));
        view.setBackgroundResource(R.color.feed_background);

        logger.info("Starting loading ad now.");

        // now load the ad and show it, once it finishes loading
        adService.load(view, Config.AdType.FEED).subscribe(state -> {
            if (state == AdService.AdLoadState.SUCCESS && view.getParent() == null) {
                parent.post(() -> {
                    logger.info("Ad was loaded, showing ad now.");
                    parent.addView(view);
                });
            }

            // count loads/failures
            int change = state == AdService.AdLoadState.SUCCESS ? -1 : 1;
            failureCount = Math.min(6, Math.max(0, failureCount + change));
        });

        // if it did not fail three or more times, we expect it to work.
        if (failureCount < 3) {
            logger.info("Directly adding view to parent, as loading will probably succeed.");
            parent.addView(view);
        }

        return view;
    }

    public void setShowAds(boolean show) {
        if (showAds != show) {
            this.showAds = show;
            notifyDataSetChanged();

            if (!show) {
                // destroy previous view if needed
                destroy();
            }
        }
    }

    @Override
    public AdViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ViewGroup container = new FrameLayout(parent.getContext());
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // we only keep one instance alive.
        destroy();

        // we remember the current instance!
        viewInstance = newAdView(parent.getContext(), container);

        return new AdViewHolder(container);
    }

    public void destroy() {
        if (viewInstance != null) {
            logger.info("Destroying previous adView");
            viewInstance.destroy();
            viewInstance = null;
        }
    }

    @Override
    public void onBindViewHolder(AdViewHolder holder, int position) {
        // nothing to bind here.
    }

    @Override
    public int getItemCount() {
        return showAds ? 1 : 0;
    }

    static class AdViewHolder extends RecyclerView.ViewHolder {
        AdViewHolder(View itemView) {
            super(itemView);
        }
    }
}
