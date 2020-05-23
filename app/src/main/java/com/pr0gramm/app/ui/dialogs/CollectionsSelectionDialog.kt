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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.services.CollectionItemsService
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.base.launchWhenCreated
import com.pr0gramm.app.ui.delegateAdapterOf
import com.pr0gramm.app.ui.resolveDialogTheme
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


class CollectionsSelectionDialog : BottomSheetDialogFragment(), LazyInjectorAware {
    private val logger = Logger("CollectionsSelectionDialog")

    override val injector: PropertyInjector = PropertyInjector()

    private val itemId: Long by fragmentArgument("itemId")
    private val collectionsService: CollectionsService by instance()
    private val collectionItemsService: CollectionItemsService by instance()

    override fun getTheme(): Int = R.style.MyBottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val themedInflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view: View = themedInflater.inflate(R.layout.dialog_collections, container, false)

        val adapter = delegateAdapterOf("CollectionsAdapter",
                CollectionAdapterDelegate(this::onCollectionClicked))

        // gets the collections containing the itemId
        val selectedCollections = collectionItemsService.collectionsContaining(itemId)

        // take the current collections only. must already be loaded.
        val collections = collectionsService.collections.value.orEmpty()
        adapter.submitList(collections.map { c ->
            CollectionAdapterDelegate.Item(c, c.id in selectedCollections)
        })

        val recyclerView: RecyclerView = view.find(R.id.collections)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val done: View = view.find(R.id.done)

        done.setOnClickListener {
            logger.info { "Closing dialog" }
            dismiss()
        }

        return view
    }

    private fun onCollectionClicked(collection: Api.Collection, isSelected: Boolean) {
        logger.info { "Updating state for ${collection.keyword}: $isSelected" }

        launchWhenCreated {
            withContext(NonCancellable) {
                if (isSelected) {
                    collectionItemsService.addToCollection(collection.id, itemId)
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
    }
}

private class CollectionAdapterDelegate(private val collectionClicked: (collection: Api.Collection, isSelected: Boolean) -> Unit)
    : ListItemTypeAdapterDelegate<CollectionAdapterDelegate.Item, CollectionAdapterDelegate.CollectionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup): CollectionViewHolder {
        return CollectionViewHolder(parent)
    }

    override fun onBindViewHolder(holder: CollectionViewHolder, value: Item) {
        val imageId = when (value.collection.isPublic) {
            true -> R.drawable.ic_collection_public
            false -> R.drawable.ic_collection_private
        }

        holder.name.text = value.collection.name
        holder.icon.setImageResource(imageId)

        holder.checkbox.setOnCheckedChangeListenerWithInitial(value.selected) { isChecked ->
            collectionClicked(value.collection, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.toggle()
        }
    }

    private class CollectionViewHolder(parent: ViewGroup)
        : RecyclerView.ViewHolder(parent.inflateDetachedChild(R.layout.row_collection)) {

        val name: TextView = find(R.id.name)
        val icon: ImageView = find(R.id.icon)
        val checkbox: CheckBox = find(R.id.checkbox)
    }

    class Item(val collection: Api.Collection, val selected: Boolean)
}

