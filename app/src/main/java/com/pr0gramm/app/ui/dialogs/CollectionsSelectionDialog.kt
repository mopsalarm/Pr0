package com.pr0gramm.app.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.text.buildSpannedString
import androidx.core.text.italic
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.services.PostCollection
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.AsyncListAdapter
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.configureNewStyle
import com.pr0gramm.app.ui.delegateAdapterOf
import com.pr0gramm.app.ui.resolveDialogTheme
import com.pr0gramm.app.ui.views.appendUsernameAndMark
import com.pr0gramm.app.util.arguments
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.fragmentArgument
import com.pr0gramm.app.util.inflateDetachedChild
import com.pr0gramm.app.util.setOnCheckedChangeListenerWithInitial
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


class CollectionsSelectionDialog : BottomSheetDialogFragment(), LazyInjectorAware {
    private val logger = Logger("CollectionsSelectionDialog")

    override val injector: PropertyInjector = PropertyInjector()

    private val itemId: Long by fragmentArgument("itemId")
    private val collectionsService: CollectionsService by instance()
    private val collectionItemsService: CollectionItemsService by instance()
    private val userService: UserService by instance()

    override fun getTheme(): Int = R.style.MyBottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val themedInflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view: View = themedInflater.inflate(R.layout.dialog_collections, container, false)

        val adapter = delegateAdapterOf(
                CollectionAdapterDelegate(this::onCollectionClicked),
                detectMoves = true,
                diffCallback = AsyncListAdapter.KeyDiffCallback { it.collection.id },
        )

        // gets the collections containing the itemId
        val selectedCollections = collectionItemsService.collectionsContaining(itemId)

        launchUntilDestroy {
            // always refresh once in background when we show this
            collectionsService.refresh()
        }

        val actionNew = view.find<View>(R.id.action_new)
        actionNew.setOnClickListener {
            CollectionDialog().show(parentFragmentManager, null)
        }

        // observe changes to collections
        collectionsService.collections.observe(this) { collections ->
            val userIsPremium = userService.userIsPremium

            adapter.submitList(collections.map { c ->
                val isSelected = c.id in selectedCollections
                val isEnabled = c.isDefault || userIsPremium
                CollectionAdapterDelegate.Item(c, isSelected, isEnabled)
            })

            actionNew.isEnabled = userIsPremium || collections.isEmpty()
        }

        val recyclerView: RecyclerView = view.find(R.id.collections)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        view.find<View>(R.id.done).setOnClickListener {
            dismiss()
        }

        return view
    }

    private fun onCollectionClicked(collection: PostCollection, isSelected: Boolean) {
        logger.info { "Updating state for ${collection.key}: $isSelected" }

        launchWhenCreated {
            withContext(NonCancellable) {
                if (isSelected) {
                    // TODO error handling
                    collectionItemsService.addToCollection(itemId, collection.id)
                } else {
                    collectionItemsService.removeFromCollection(collection.id, itemId)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        injector.inject(requireContext())

        val theme = resolveDialogTheme(requireContext(), R.style.MyBottomSheetDialog)
        val ctx = ContextThemeWrapper(requireContext(), theme)

        return BottomSheetDialog(ctx, theme).apply {
            setOnShowListener {
                val bottomSheet = requireDialog().findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet is ViewGroup) {
                    catchAll {
                        BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED)
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance(itemId: Long): CollectionsSelectionDialog {
            return CollectionsSelectionDialog().arguments {
                putLong("itemId", itemId)
            }
        }

        fun addToCollection(parent: Fragment, itemId: Long) {
            val fragmentView = parent.view ?: return
            val context = fragmentView.context

            val collectionItemsService: CollectionItemsService = context.injector.instance()

            // if the item is already part of a collection, directly show the dialog.
            if (collectionItemsService.isItemInAnyCollection(itemId)) {
                return newInstance(itemId).show(parent.childFragmentManager, null)
            }

            val collectionsService: CollectionsService = context.injector.instance()

            parent.launchWhenCreated {
                val snackbar = Snackbar
                        .make(fragmentView, R.string.collecton_adding, Snackbar.LENGTH_LONG)
                        .configureNewStyle()

                // show info that we are currently doing the request
                snackbar.show()

                // add to default collection
                val result = collectionItemsService.addToCollection(itemId, collectionId = null)

                when (result) {
                    is CollectionItemsService.Result.ItemAdded -> {
                        val collection = collectionsService.byId(result.collectionId)

                        val messageText: CharSequence = buildSpannedString {
                            append(context.getString(R.string.collecton_added))

                            if (collection != null) {
                                append(" ")
                                italic { append(collection.uniqueTitle) }
                            }
                        }

                        snackbar.setText(messageText)
                        snackbar.duration = Snackbar.LENGTH_SHORT

                        snackbar.setAction(R.string.action_change) {
                            newInstance(itemId).show(parent.childFragmentManager, null)
                        }

                        snackbar.show()
                    }

                    else -> {}
                }
            }
        }
    }
}

private class CollectionAdapterDelegate(private val collectionClicked: (collection: PostCollection, isSelected: Boolean) -> Unit)
    : ListItemTypeAdapterDelegate<CollectionAdapterDelegate.Item, CollectionAdapterDelegate.Item, CollectionAdapterDelegate.CollectionViewHolder>(
    Item::class) {

    override fun onCreateViewHolder(parent: ViewGroup): CollectionViewHolder {
        return CollectionViewHolder(parent)
    }

    override fun onBindViewHolder(holder: CollectionViewHolder, value: Item) {
        val imageId = when (value.collection.isPublic) {
            true -> R.drawable.ic_collection_public
            false -> R.drawable.ic_collection_private
        }

        holder.name.text = buildSpannedString {
            append(value.collection.uniqueTitle)

            value.collection.owner?.let { owner ->
                append(" (")
                appendUsernameAndMark(holder.name, owner.name, owner.mark)
                append(")")
            }
        }

        holder.icon.setImageResource(imageId)

        for (view in listOf(holder.itemView, holder.checkbox, holder.edit)) {
            view.isEnabled = value.enabled || value.selected
        }

        holder.checkbox.setOnCheckedChangeListenerWithInitial(value.selected) { isChecked ->
            collectionClicked(value.collection, isChecked)

            // update state
            for (view in listOf(holder.itemView, holder.checkbox, holder.edit)) {
                view.isEnabled = value.enabled
            }
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.toggle()
        }

        holder.edit.isInvisible = value.collection.isCuratorCollection

        holder.edit.setOnClickListener { view ->
            val fragment: Fragment = FragmentManager.findFragment(view)
            val dialog = CollectionDialog.newInstance(value.collection)
            dialog.show(fragment.childFragmentManager, null)
        }
    }

    class CollectionViewHolder(parent: ViewGroup) :
        RecyclerView.ViewHolder(parent.inflateDetachedChild(R.layout.row_collection)) {

        val name: TextView = find(R.id.name)
        val icon: ImageView = find(R.id.icon)
        val checkbox: CheckBox = find(R.id.checkbox)
        val edit: View = find(R.id.action_edit)
    }

    data class Item(val collection: PostCollection, val selected: Boolean, val enabled: Boolean)
}

