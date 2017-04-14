package com.pr0gramm.app.ui.fragments

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.pr0gramm.app.ApplicationClass.appComponent
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.AdService
import com.pr0gramm.app.util.observeChange
import com.trello.rxlifecycle.android.RxLifecycleAndroid
import org.slf4j.LoggerFactory

/**
 * A simple adapter that shows one or zero views.
 */
internal class AdViewAdapter : RecyclerView.Adapter<AdViewAdapter.AdViewHolder>() {
    private val adService = appComponent().adService()

    private var viewInstance: AdView? = null

    // destroy previous view if needed
    var showAds: Boolean by observeChange(false) {
        notifyDataSetChanged()
        if (!showAds) {
            destroy()
        }
    }

    init {
        setHasStableIds(true)
    }

    private fun newAdView(context: Context, parent: ViewGroup): AdView {
        val view = adService.newAdView(context)
        view.adSize = AdSize(AdSize.FULL_WIDTH, 70)

        logger.info("Starting loading ad now.")

        // now load the ad and show it, once it finishes loading
        adService.load(view, Config.AdType.FEED)
                .compose(RxLifecycleAndroid.bindView(view))
                .compose(RxLifecycleAndroid.bindView(parent))
                .subscribe { state ->
                    if (state == AdService.AdLoadState.SUCCESS && view.parent == null) {
                        logger.info("Ad was loaded, showing ad now.")
                        parent.addView(view)
                    }

                    // on failure we hide the view - which should also collapse the parent.
                    if (state == AdService.AdLoadState.FAILURE) {
                        view.visibility = View.GONE
                    }

                    // count loads/failures
                    val change = if (state == AdService.AdLoadState.SUCCESS) -1 else 1
                    failureCount = Math.min(6, Math.max(0, failureCount + change))
                }

        // if it did not fail three or more times, we expect it to work.
        if (failureCount < 3) {
            logger.info("Directly adding view to parent, as loading will probably succeed.")
            parent.addView(view)
        }

        return view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdViewHolder {
        val container = FrameLayout(parent.context)
        container.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        // we only keep one instance alive.
        destroy()

        // we remember the current instance!
        viewInstance = newAdView(parent.context, container)

        return AdViewHolder(container)
    }

    fun destroy() {
        viewInstance?.destroy()
        viewInstance = null
    }

    override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
        // nothing to bind here.
    }

    override fun getItemCount(): Int {
        return if (this.showAds) 1 else 0
    }

    internal class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        private val logger = LoggerFactory.getLogger("AdViewAdapter")

        // count failures in all adapter instances
        private var failureCount = 0
    }
}
