package com.pr0gramm.app.ui.fragments;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.config.Config;
import com.pr0gramm.app.ui.Ad;

/**
 * A simple adapter that shows one or zero views.
 */
class AdViewAdapter extends RecyclerView.Adapter<AdViewAdapter.AdViewHolder> {
    private boolean showAds;

    private AdView newAdView(Context context) {
        AdView view = new AdView(context);
        view.setAdSize(AdSize.SMART_BANNER);
        view.setAdUnitId(context.getString(R.string.banner_ad_unit_id));
        view.setAdListener(new Ad.TrackingAdListener(Config.AdType.FEED));

        // This object will be destroyed once the adView loses the reference to the object.
        // It then can correctly destroy the adView.
        view.setTag(new Object() {
            @Override
            protected void finalize() throws Throwable {
                super.finalize();
                view.destroy();
            }
        });

        return view;
    }

    public void showAds(boolean show) {
        if (showAds != show) {
            this.showAds = show;
            notifyDataSetChanged();
        }
    }

    @Override
    public AdViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AdViewHolder(newAdView(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(AdViewHolder holder, int position) {
        Ad.load(holder.view);
    }

    @Override
    public int getItemCount() {
        return showAds ? 1 : 0;
    }

    static class AdViewHolder extends RecyclerView.ViewHolder {
        final AdView view;

        AdViewHolder(View itemView) {
            super(itemView);

            view = (AdView) itemView;
        }
    }
}
