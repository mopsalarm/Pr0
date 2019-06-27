package com.pr0gramm.app.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Outline
import android.graphics.PorterDuff
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.dip2px
import com.pr0gramm.app.util.find
import java.util.*

typealias OnMenuItemClickListener = (MenuItem) -> Unit

/**
 * A SheetView that can represent a menu resource as a list.
 *
 *
 * A list can support submenus, and will include a divider and header for them where appropriate.
 *
 */
@SuppressLint("ViewConstructor")
class MenuSheetView(context: Context, @StringRes titleRes: Int, listener: OnMenuItemClickListener) : FrameLayout(context) {
    private val menu: Menu = androidx.appcompat.widget.PopupMenu(context, this).menu

    private val items = ArrayList<SheetMenuItem>()
    private val absListView: AbsListView
    private val titleView: TextView
    private val originalListPaddingTop: Int

    init {
        // Set up the menu
        // Dirty hack to get a menu instance since MenuBuilder isn't public ಠ_ಠ

        // Inflate the appropriate view and set up the absListView
        View.inflate(context, R.layout.list_sheet_view, this)
        absListView = findViewById<View>(R.id.list) as AbsListView
        absListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, id ->
            val item = absListView.adapter.getItem(position) as? SheetMenuItem
            if (item?.menuItem != null) {
                listener(item.menuItem)
            }
        }

        // Set up the title
        titleView = findViewById<View>(R.id.title) as TextView
        originalListPaddingTop = absListView.paddingTop
        setTitle(context.getString(titleRes))

        ViewCompat.setElevation(this, getContext().dip2px(16).toFloat())
    }

    /**
     * Inflates a menu resource into the menu backing this sheet.
     *
     * @param menuRes Menu resource ID
     */
    fun inflateMenu(@MenuRes menuRes: Int) {
        if (menuRes != -1) {
            val inflater = (context as AppCompatActivity).menuInflater
            inflater.inflate(menuRes, menu)
        }

        prepareMenuItems()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        absListView.adapter = Adapter()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        outlineProvider = ShadowOutline(w, h)
    }

    /**
     * A helper class for providing a shadow on sheets
     */
    @TargetApi(21)
    internal class ShadowOutline(private val width: Int, private val height: Int) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRect(0, 0, width, height)
        }
    }

    /**
     * Flattens the visible menu items of [.menu] into [.items],
     * while inserting separators between items when necessary.
     *
     *
     * Adapted from the Design support library's NavigationMenuPresenter implementation
     */
    private fun prepareMenuItems() {
        items.clear()
        var currentGroupId = 0

        // Iterate over the menu items
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.isVisible) {
                if (item.hasSubMenu()) {
                    // Flatten the submenu
                    val subMenu = item.subMenu
                    if (subMenu.hasVisibleItems()) {
                        items.add(SheetMenuItem.SEPARATOR)

                        // Add a header item if it has text
                        if (!TextUtils.isEmpty(item.title)) {
                            items.add(SheetMenuItem.of(item))
                        }

                        // Add the sub-items
                        var subI = 0
                        val size = subMenu.size()
                        while (subI < size) {
                            val subMenuItem = subMenu.getItem(subI)
                            if (subMenuItem.isVisible) {
                                items.add(SheetMenuItem.of(subMenuItem))
                            }
                            subI++
                        }

                        // Add one more separator to the end to close it off if we have more items
                        if (i != menu.size() - 1) {
                            items.add(SheetMenuItem.SEPARATOR)
                        }
                    }
                } else {
                    val groupId = item.groupId
                    if (groupId != currentGroupId) {
                        items.add(SheetMenuItem.SEPARATOR)
                    }
                    items.add(SheetMenuItem.of(item))
                    currentGroupId = groupId
                }
            }
        }
    }

    /**
     * Sets the title text of the sheet
     *
     * @param title Title text to use
     */
    private fun setTitle(title: CharSequence?) {
        if (!TextUtils.isEmpty(title)) {
            titleView.text = title
        } else {
            titleView.visibility = View.GONE

            // Add some padding to the top to account for the missing title
            absListView.setPadding(absListView.paddingLeft,
                    originalListPaddingTop + context.dip2px(8),
                    absListView.paddingRight, absListView.paddingBottom)
        }
    }

    private inner class Adapter : BaseAdapter() {
        private val inflater: LayoutInflater = LayoutInflater.from(context)

        private val VIEW_TYPE_NORMAL = 0
        private val VIEW_TYPE_SUBHEADER = 1
        private val VIEW_TYPE_SEPARATOR = 2

        override fun getCount(): Int {
            return items.size
        }

        override fun getItem(position: Int): SheetMenuItem {
            return items[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewTypeCount(): Int {
            return 3
        }

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return if (item.isSeparator) {
                VIEW_TYPE_SEPARATOR
            } else if (item.menuItem!!.hasSubMenu()) {
                VIEW_TYPE_SUBHEADER
            } else {
                VIEW_TYPE_NORMAL
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView

            val item = getItem(position)
            val viewType = getItemViewType(position)

            when (viewType) {
                VIEW_TYPE_NORMAL -> {
                    val holder: NormalViewHolder
                    if (view == null) {
                        view = inflater.inflate(R.layout.sheet_list_item, parent, false)
                        holder = NormalViewHolder(view!!)
                        view.tag = holder
                    } else {
                        holder = view.tag as NormalViewHolder
                    }
                    holder.bindView(item)
                }
                VIEW_TYPE_SUBHEADER -> {
                    if (view == null) {
                        view = inflater.inflate(R.layout.sheet_list_item_subheader, parent, false)
                    }
                    val subHeader = view as TextView?
                    subHeader!!.text = item.menuItem!!.title
                }
                VIEW_TYPE_SEPARATOR -> if (view == null) {
                    view = inflater.inflate(R.layout.sheet_list_item_separator, parent, false)
                }
            }

            return view!!
        }

        override fun areAllItemsEnabled(): Boolean {
            return false
        }

        override fun isEnabled(position: Int): Boolean {
            return getItem(position).isEnabled
        }

        internal inner class NormalViewHolder(root: View) {
            val icon: ImageView = root.find(R.id.icon) as ImageView
            val label: TextView = root.find(R.id.label) as TextView

            fun bindView(item: SheetMenuItem) {
                val iconColor = AndroidUtility.resolveColorAttribute(icon.context,
                        android.R.attr.textColorSecondary)

                val menuItem = item.menuItem ?: return

                menuItem.icon?.let { icon ->
                    icon.mutate().setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                    this.icon.setImageDrawable(icon)
                }

                label.text = menuItem.title
            }
        }
    }

    private class SheetMenuItem private constructor(val menuItem: MenuItem?) {

        val isSeparator: Boolean
            get() = this === SEPARATOR

        // Separators and subheaders never respond to click
        val isEnabled: Boolean
            get() = menuItem != null && !menuItem.hasSubMenu() && menuItem.isEnabled

        companion object {

            internal val SEPARATOR = SheetMenuItem(null)

            fun of(item: MenuItem): SheetMenuItem {
                return SheetMenuItem(item)
            }
        }

    }
}
