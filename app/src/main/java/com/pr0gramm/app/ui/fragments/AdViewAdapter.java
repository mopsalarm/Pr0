package com.pr0gramm.app.ui.fragments;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.ui.AdService;

import static com.pr0gramm.app.ApplicationClass.appComponent;

/**
 * A simple adapter that shows one or zero views.
 */
class AdViewAdapter extends RecyclerView.Adapter<AdViewAdapter.AdViewHolder> {
    private final AdService adService = appComponent().adService();

    private AdView viewInstance;
    private boolean showAds;

    AdViewAdapter() {
        setHasStableIds(true);
    }

    private AdView newAdView(Context context) {
        AdView view = new AdView(context.getApplicationContext());

        view.setAdSize(new AdSize(AdSize.FULL_WIDTH, 70));
        view.setAdUnitId(context.getString(R.string.banner_ad_unit_id));
        view.setBackgroundResource(R.color.feed_background);

        // now load the ad.
        adService.load(view, Config.AdType.FEED);

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
        if (viewInstance != null) {
            // check if we can re-use the previous viewInstance
            if (viewInstance.getParent() != null) {
                destroy();
            }
        }

        if (viewInstance == null) {
            viewInstance = newAdView(parent.getContext());
        }

        return new AdViewHolder(viewInstance);
    }

    public void destroy() {
        if (viewInstance != null) {
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
