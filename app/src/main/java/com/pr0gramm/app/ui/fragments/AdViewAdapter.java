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

import rx.android.schedulers.AndroidSchedulers;

import static com.pr0gramm.app.ApplicationClass.appComponent;

/**
 * A simple adapter that shows one or zero views.
 */
class AdViewAdapter extends RecyclerView.Adapter<AdViewAdapter.AdViewHolder> {
    private final AdService adService = appComponent().adService();

    private boolean showAds;

    private AdView newAdView(Context context) {
        AdView view = new AdView(context);
        view.setAdSize(AdSize.LARGE_BANNER);
        view.setAdUnitId(context.getString(R.string.banner_ad_unit_id));
        view.setBackgroundResource(R.color.feed_background);

        // This object will be destroyed once the adView loses the reference to the object.
        // It then can correctly destroy the adView.
        view.setTag(new Object() {
            @Override
            protected void finalize() throws Throwable {
                super.finalize();

                // finalize no main thread
                AndroidSchedulers.mainThread().createWorker().schedule(view::destroy);
            }
        });

        // now load the ad.
        adService.load(view, Config.AdType.FEED);

        return view;
    }

    public void setShowAds(boolean show) {
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
