package com.pr0gramm.app.ui.fragments

import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.Message
import com.pr0gramm.app.api.pr0gramm.Thumbnail
import com.pr0gramm.app.databinding.DigestsHeaderBinding
import com.pr0gramm.app.databinding.FeedItemViewBinding
import com.pr0gramm.app.databinding.FragmentInboxBinding
import com.pr0gramm.app.services.DigestsService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UriHelper
import com.pr0gramm.app.ui.DelegateAdapter
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.MainActivity
import com.pr0gramm.app.ui.Pagination
import com.pr0gramm.app.ui.base.BaseFragment
import com.pr0gramm.app.ui.base.bindViews
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.layoutInflater
import com.pr0gramm.app.util.observeChange
import com.squareup.picasso.Picasso
import kotlin.math.min

/**
 */
class DigestsFragment : BaseFragment("DigestsFragment", R.layout.fragment_inbox) {
    private val views by bindViews(FragmentInboxBinding::bind)
    private val digestsService: DigestsService by instance()

    private var state by observeChange(State()) { updateAdapterValues() }
    private val adapter = DigestsAdapter { itemId -> itemClicked(itemId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadInboxContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spanCount = thumbnailColumnCount
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = adapter.SpanSizeLookup(spanCount)

        views.messages.itemAnimator = null
        views.messages.layoutManager = layoutManager
        views.messages.adapter = adapter

        views.refresh.setOnRefreshListener { reloadInboxContent() }
        views.refresh.setColorSchemeResources(ThemeHelper.accentColor)
    }

    private fun reloadInboxContent() {
        launchWhenCreated {
            state = state.copy(loading = true)

            // get digests from server and update state
            val digests = digestsService.digests()
            state = state.copy(digests = digests, loading = false)

            // not refreshing anymore
            views.refresh.isRefreshing = false
        }
    }

    private fun itemClicked(itemId: Long) {
        // open the post
        startActivity(MainActivity.openItemIntent(requireContext(), itemId))
    }

    /**
     * Depending on whether the screen is landscape or portrait, and how large
     * the screen is, we show a different number of items per row.
     */
    private val thumbnailColumnCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        val config = resources.configuration
        val portrait = config.screenWidthDp < config.screenHeightDp

        val screenWidth = config.screenWidthDp
        min((screenWidth / 120.0 + 0.5).toInt(), if (portrait) 5 else 7)
    }

    private fun updateAdapterValues() {
        val context = context ?: return

        val values = state.digests.flatMap { digest ->
            listOf(digest) + digest.items
        }

        adapter.submitList(values)
    }

    data class State(
        val digests: List<Api.DigestsInbox.Digest> = listOf(),
        val loading: Boolean = true,
        val tailState: Pagination.EndState<Message> = Pagination.EndState(hasMore = true)
    )
}

private class DigestsAdapter(itemClicked: (id: Long) -> Unit) : DelegateAdapter<Any>() {
    init {
        delegates += DigestHeaderDelegate()
        delegates += FeedItemDelegate(itemClicked)
    }

    inner class SpanSizeLookup(private val spanCount: Int) : GridLayoutManager.SpanSizeLookup() {
        init {
            isSpanIndexCacheEnabled = true
        }

        override fun getSpanSize(position: Int): Int {
            val item = getItem(position)

            if (item is Api.DigestsInbox.Digest) {
                return spanCount
            }

            // default is just ne column
            return 1
        }
    }
}

private class DigestHeaderDelegate :
    ListItemTypeAdapterDelegate<Api.DigestsInbox.Digest, Any, DigestsHeaderViewHolder>(Api.DigestsInbox.Digest::class) {
    override fun onCreateViewHolder(parent: ViewGroup): DigestsHeaderViewHolder {
        val views = DigestsHeaderBinding.inflate(parent.layoutInflater, parent, false)
        return DigestsHeaderViewHolder(views)
    }

    override fun onBindViewHolder(holder: DigestsHeaderViewHolder, value: Api.DigestsInbox.Digest) {
        holder.views.title.isVisible = value.pushNotification.title.isNotBlank()
        holder.views.title.text = value.pushNotification.title

        holder.views.body.isVisible = value.pushNotification.body.isNotBlank()
        holder.views.body.text = value.pushNotification.body

        holder.views.extraText.isVisible = !value.notice.isNullOrBlank()
        holder.views.extraText.text = value.notice ?: ""
    }
}

private class FeedItemDelegate(private val itemClicked: (id: Long) -> Unit) :
    ListItemTypeAdapterDelegate<Api.DigestsInbox.ItemHighlight, Any, DigestsItemViewHolder>(Api.DigestsInbox.ItemHighlight::class) {
    override fun onCreateViewHolder(parent: ViewGroup): DigestsItemViewHolder {
        val views = FeedItemViewBinding.inflate(parent.layoutInflater)
        return DigestsItemViewHolder(views)
    }

    override fun onBindViewHolder(holder: DigestsItemViewHolder, value: Api.DigestsInbox.ItemHighlight) {
        val imageView = holder.views.image

        imageView.setOnClickListener { itemClicked(value.id) }

        val thumbnail = Thumbnail(value.id, value.thumbnail)
        val imageUri = UriHelper.of(imageView.context).thumbnail(thumbnail)

        val picasso = imageView.context.injector.instance<Picasso>()

        picasso.load(imageUri)
            .config(Bitmap.Config.RGB_565)
            .placeholder(ColorDrawable(0xff333333.toInt()))
            .into(imageView)
    }
}

private class DigestsHeaderViewHolder(val views: DigestsHeaderBinding) : RecyclerView.ViewHolder(views.root)
private class DigestsItemViewHolder(val views: FeedItemViewBinding) : RecyclerView.ViewHolder(views.root)
