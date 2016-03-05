package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.MenuRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;

import java.util.ArrayList;

/**
 * A SheetView that can represent a menu resource as a list or grid.
 * <p>
 * A list can support submenus, and will include a divider and header for them where appropriate.
 * Grids currently don't support submenus, and don't in the Material Design spec either.
 * </p>
 */
@SuppressLint("ViewConstructor")
public class MenuSheetView extends FrameLayout {
    @ColorInt
    private final int defaultColor = ColorStateList.valueOf(Color.BLACK)
            .withAlpha(127)
            .getDefaultColor();

    /**
     * A listener for menu item clicks in the sheet
     */
    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    private Menu menu;
    private ArrayList<SheetMenuItem> items = new ArrayList<>();
    private Adapter adapter;
    private AbsListView absListView;
    private final TextView titleView;
    protected final int originalListPaddingTop;

    /**
     * @param context  Context to construct the view with
     * @param titleRes String resource ID for the title
     * @param listener Listener for menu item clicks in the sheet
     */
    public MenuSheetView(final Context context, @StringRes final int titleRes, final OnMenuItemClickListener listener) {
        this(context, context.getString(titleRes), listener);
    }

    /**
     * @param context  Context to construct the view with
     * @param title    Title for the sheet. Can be null
     * @param listener Listener for menu item clicks in the sheet
     */
    public MenuSheetView(final Context context, @Nullable final CharSequence title, final OnMenuItemClickListener listener) {
        super(context);

        // Set up the menu
        this.menu = new PopupMenu(context, null).getMenu();  // Dirty hack to get a menu instance since MenuBuilder isn't public ಠ_ಠ

        // Inflate the appropriate view and set up the absListView
        inflate(context, R.layout.list_sheet_view, this);
        absListView = (AbsListView) findViewById(R.id.list);
        if (listener != null) {
            absListView.setOnItemClickListener((parent, view, position, id) ->
                    listener.onMenuItemClick(adapter.getItem(position).getMenuItem()));
        }

        // Set up the title
        titleView = (TextView) findViewById(R.id.title);
        originalListPaddingTop = absListView.getPaddingTop();
        setTitle(title);

        ViewCompat.setElevation(this, AndroidUtility.dp(getContext(), 16));
    }

    /**
     * Inflates a menu resource into the menu backing this sheet.
     *
     * @param menuRes Menu resource ID
     */
    public void inflateMenu(@MenuRes int menuRes) {
        if (menuRes != -1) {
            MenuInflater inflater = new MenuInflater(getContext());
            inflater.inflate(menuRes, menu);
        }

        prepareMenuItems();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.adapter = new Adapter();
        absListView.setAdapter(this.adapter);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Necessary for showing elevation on 5.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ShadowOutline(w, h));
        }
    }

    /**
     * A helper class for providing a shadow on sheets
     */
    @TargetApi(21)
    static class ShadowOutline extends ViewOutlineProvider {
        private final int width;
        private final int height;

        ShadowOutline(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRect(0, 0, width, height);
        }
    }

    /**
     * Flattens the visible menu items of {@link #menu} into {@link #items},
     * while inserting separators between items when necessary.
     * <p>
     * Adapted from the Design support library's NavigationMenuPresenter implementation
     */
    private void prepareMenuItems() {
        items.clear();
        int currentGroupId = 0;

        // Iterate over the menu items
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.isVisible()) {
                if (item.hasSubMenu()) {
                    // Flatten the submenu
                    SubMenu subMenu = item.getSubMenu();
                    if (subMenu.hasVisibleItems()) {
                        items.add(SheetMenuItem.SEPARATOR);

                        // Add a header item if it has text
                        if (!TextUtils.isEmpty(item.getTitle())) {
                            items.add(SheetMenuItem.of(item));
                        }

                        // Add the sub-items
                        for (int subI = 0, size = subMenu.size(); subI < size; subI++) {
                            MenuItem subMenuItem = subMenu.getItem(subI);
                            if (subMenuItem.isVisible()) {
                                items.add(SheetMenuItem.of(subMenuItem));
                            }
                        }

                        // Add one more separator to the end to close it off if we have more items
                        if (i != menu.size() - 1) {
                            items.add(SheetMenuItem.SEPARATOR);
                        }
                    }
                } else {
                    int groupId = item.getGroupId();
                    if (groupId != currentGroupId) {
                        items.add(SheetMenuItem.SEPARATOR);
                    }
                    items.add(SheetMenuItem.of(item));
                    currentGroupId = groupId;
                }
            }
        }
    }

    /**
     * @return The current {@link Menu} instance backing this sheet. Note that this is mutable, and
     * you should call {@link #updateMenu()} after any changes.
     */
    public Menu getMenu() {
        return this.menu;
    }

    /**
     * Invalidates the current internal representation of the menu and rebuilds it. Should be used
     * if the developer dynamically adds items to the Menu returned by {@link #getMenu()}
     */
    public void updateMenu() {
        // Invalidate menu and rebuild it, useful if the user has dynamically added menu items.
        prepareMenuItems();
    }

    /**
     * Sets the title text of the sheet
     *
     * @param resId String resource ID for the text
     */
    public void setTitle(@StringRes int resId) {
        setTitle(getResources().getText(resId));
    }

    /**
     * Sets the title text of the sheet
     *
     * @param title Title text to use
     */
    public void setTitle(CharSequence title) {
        if (!TextUtils.isEmpty(title)) {
            titleView.setText(title);
        } else {
            titleView.setVisibility(GONE);

            // Add some padding to the top to account for the missing title
            absListView.setPadding(absListView.getPaddingLeft(),
                    originalListPaddingTop + AndroidUtility.dp(getContext(), 8),
                    absListView.getPaddingRight(), absListView.getPaddingBottom());
        }
    }

    /**
     * @return The current title text of the sheet
     */
    public CharSequence getTitle() {
        return titleView.getText();
    }

    private class Adapter extends BaseAdapter {

        private static final int VIEW_TYPE_NORMAL = 0;
        private static final int VIEW_TYPE_SUBHEADER = 1;
        private static final int VIEW_TYPE_SEPARATOR = 2;

        private final LayoutInflater inflater;

        public Adapter() {
            this.inflater = LayoutInflater.from(getContext());
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public SheetMenuItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            SheetMenuItem item = getItem(position);
            if (item.isSeparator()) {
                return VIEW_TYPE_SEPARATOR;
            } else if (item.getMenuItem().hasSubMenu()) {
                return VIEW_TYPE_SUBHEADER;
            } else {
                return VIEW_TYPE_NORMAL;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            SheetMenuItem item = getItem(position);
            int viewType = getItemViewType(position);

            switch (viewType) {
                case VIEW_TYPE_NORMAL:
                    NormalViewHolder holder;
                    if (convertView == null) {
                        convertView = inflater.inflate(R.layout.sheet_list_item, parent, false);
                        holder = new NormalViewHolder(convertView);
                        convertView.setTag(holder);
                    } else {
                        holder = (NormalViewHolder) convertView.getTag();
                    }
                    holder.bindView(item);
                    break;
                case VIEW_TYPE_SUBHEADER:
                    if (convertView == null) {
                        convertView = inflater.inflate(R.layout.sheet_list_item_subheader, parent, false);
                    }
                    TextView subHeader = (TextView) convertView;
                    subHeader.setText(item.getMenuItem().getTitle());
                    break;
                case VIEW_TYPE_SEPARATOR:
                    if (convertView == null) {
                        convertView = inflater.inflate(R.layout.sheet_list_item_separator, parent, false);
                    }
                    break;
            }

            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }

        class NormalViewHolder {
            final ImageView icon;
            final TextView label;

            NormalViewHolder(View root) {
                icon = (ImageView) root.findViewById(R.id.icon);
                label = (TextView) root.findViewById(R.id.label);
            }

            public void bindView(SheetMenuItem item) {
                Drawable icon = item.getMenuItem().getIcon();
                icon.mutate().setColorFilter(defaultColor, PorterDuff.Mode.SRC_IN);

                this.icon.setImageDrawable(icon);
                label.setText(item.getMenuItem().getTitle());
            }
        }
    }

    private static class SheetMenuItem {

        private static final SheetMenuItem SEPARATOR = new SheetMenuItem(null);

        private final MenuItem menuItem;

        private SheetMenuItem(MenuItem item) {
            menuItem = item;
        }

        public static SheetMenuItem of(MenuItem item) {
            return new SheetMenuItem(item);
        }

        public boolean isSeparator() {
            return this == SEPARATOR;
        }

        public MenuItem getMenuItem() {
            return menuItem;
        }

        public boolean isEnabled() {
            // Separators and subheaders never respond to click
            return menuItem != null && !menuItem.hasSubMenu() && menuItem.isEnabled();
        }

    }
}
