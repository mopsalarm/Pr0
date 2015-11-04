package com.pr0gramm.app.services;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.ContextSingleton;
import com.pr0gramm.app.R;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.api.categories.ExtraCategoryApiProvider;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.feed.FeedFilter;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.orm.Bookmark;

import org.immutables.value.Value;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindDrawable;
import butterknife.ButterKnife;
import rx.Observable;
import rx.subjects.BehaviorSubject;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.singletonList;
import static rx.Observable.combineLatest;

/**
 */
@ContextSingleton
public class NavigationProvider {
    private final Context context;
    private final UserService userService;
    private final InboxService inboxService;
    private final Settings settings;
    private final BookmarkService bookmarkService;
    private final SingleShotService singleShotService;

    @BindDrawable(R.drawable.ic_black_action_favorite)
    Drawable iconFavorites;

    @BindDrawable(R.drawable.ic_black_action_home)
    Drawable iconFeedTypePromoted;

    @BindDrawable(R.drawable.ic_black_action_stelz)
    Drawable iconFeedTypePremium;

    @BindDrawable(R.drawable.ic_black_action_trending)
    Drawable iconFeedTypeNew;

    @BindDrawable(R.drawable.ic_action_random)
    Drawable iconFeedTypeRandom;

    @BindDrawable(R.drawable.ic_category_controversial)
    Drawable iconFeedTypeControversial;

    @BindDrawable(R.drawable.ic_black_action_bookmark)
    Drawable iconBookmark;

    @BindDrawable(R.drawable.ic_action_email)
    Drawable iconInbox;

    @BindDrawable(R.drawable.ic_drawer_bestof)
    Drawable iconFeedTypeBestOf;

    @BindDrawable(R.drawable.ic_black_action_upload)
    Drawable iconUpload;

    private final BehaviorSubject<Boolean> extraCategoryApiAvailable = BehaviorSubject.create(false);

    @Inject
    public NavigationProvider(Activity activity, UserService userService, InboxService inboxService,
                              Settings settings, BookmarkService bookmarkService,
                              SingleShotService singleShotService,
                              ExtraCategoryApiProvider extraCategoryApi) {

        this.context = activity;

        this.userService = userService;
        this.inboxService = inboxService;
        this.settings = settings;
        this.bookmarkService = bookmarkService;
        this.singleShotService = singleShotService;

        // inject the images
        ButterKnife.bind(this, activity);

        // for now, just activate the categories always. We'll fix this
        // later with code like below.
        this.extraCategoryApiAvailable.onNext(true);

//        this.extraCategoryApiAvailable = extraCategoryApi.ping().map(r -> true)
//                .doOnError(err -> logger.error("Could not reach category api", err))
//                .onErrorResumeNext(Observable.just(false))
//                .startWith(true)
//                .cache();
    }

    public Observable<List<NavigationItem>> navigationItems() {
        // observe and merge the menu items from different sources
        return combineLatest(ImmutableList.of(
                categoryNavigationItems(),

                userService.loginState()
                        .flatMap(ignored -> bookmarkService.get())
                        .map(this::bookmarksToNavItem),

                inboxService.unreadMessagesCount()
                        .map(this::inboxNavigationItem)
                        .map(ImmutableList::of),

                Observable.just(singletonList(getUploadNavigationItem()))
        ), args -> {
            ArrayList<NavigationItem> result = new ArrayList<>();
            for (Object arg : args)
                //noinspection unchecked
                result.addAll((List<NavigationItem>) arg);

            return result;
        });
    }

    /**
     * Adds the default "fixed" items to the menu
     */
    public List<NavigationItem> categoryNavigationItems(
            Optional<Info.User> userInfo, boolean extraCategory) {

        List<NavigationItem> items = new ArrayList<>();

        items.add(ImmutableNavigationItem.builder()
                .action(ActionType.FILTER)
                .filter(new FeedFilter().withFeedType(FeedType.PROMOTED))
                .title(getString(R.string.action_feed_type_promoted))
                .icon(iconFeedTypePromoted)
                .build());

        items.add(ImmutableNavigationItem.builder()
                .action(ActionType.FILTER)
                .filter(new FeedFilter().withFeedType(FeedType.NEW))
                .title(getString(R.string.action_feed_type_new))
                .icon(iconFeedTypeNew)
                .build());


        if (extraCategory) {
            if (settings.showCategoryControversial()) {
                items.add(ImmutableNavigationItem.builder()
                        .action(ActionType.FILTER)
                        .filter(new FeedFilter().withFeedType(FeedType.CONTROVERSIAL))
                        .title(getString(R.string.action_feed_type_controversial))
                        .icon(iconFeedTypeControversial)
                        .build());
            }

            if (settings.showCategoryRandom()) {
                items.add(ImmutableNavigationItem.builder()
                        .action(ActionType.FILTER)
                        .filter(new FeedFilter().withFeedType(FeedType.RANDOM))
                        .title(getString(R.string.action_feed_type_random))
                        .icon(iconFeedTypeRandom)
                        .build());
            }

            if (settings.showCategoryBestOf()) {
                items.add(ImmutableNavigationItem.builder()
                        .action(ActionType.FILTER)
                        .filter(new FeedFilter().withFeedType(FeedType.BESTOF))
                        .title(getString(R.string.action_feed_type_bestof))
                        .icon(iconFeedTypeBestOf)
                        .build());
            }
        }

        if (settings.showCategoryPremium()) {
            if (userService.isPremiumUser()) {
                items.add(ImmutableNavigationItem.builder()
                        .action(ActionType.FILTER)
                        .filter(new FeedFilter().withFeedType(FeedType.PREMIUM))
                        .title(getString(R.string.action_feed_type_premium))
                        .icon(iconFeedTypePremium)
                        .build());
            }
        }

        if (userInfo.isPresent()) {
            String name = userInfo.get().getName();
            items.add(ImmutableNavigationItem.builder()
                    .action(ActionType.FILTER)
                    .filter(new FeedFilter().withFeedType(FeedType.NEW).withLikes(name))
                    .title(getString(R.string.action_favorites))
                    .icon(iconFavorites)
                    .build());
        }

        return items;
    }

    /**
     * Returns the menu item that takes the user to the inbox.
     */
    private NavigationItem inboxNavigationItem(Integer unreadCount) {
        return ImmutableNavigationItem.builder()
                .title(getString(R.string.action_inbox))
                .icon(iconInbox)
                .action(ActionType.MESSAGES)
                .layout(R.layout.left_drawer_nav_item_inbox)
                .unreadCount(unreadCount)
                .build();
    }

    private List<NavigationItem> bookmarksToNavItem(List<Bookmark> entries) {
        if (singleShotService.isFirstTimeToday("bookmarksLoaded"))
            Track.bookmarks(entries.size());

        boolean premium = userService.isPremiumUser();
        return FluentIterable.from(entries)
                .filter(entry -> premium || entry.asFeedFilter().getFeedType() != FeedType.PREMIUM)
                .transform(entry -> {
                    Drawable icon = iconBookmark.getConstantState().newDrawable();
                    String title = entry.getTitle().toUpperCase();
                    return (NavigationItem) ImmutableNavigationItem.builder()
                            .title(title).icon(icon).bookmark(entry)
                            .action(ActionType.BOOKMARK)
                            .filter(entry.asFeedFilter())
                            .build();
                })
                .toList();
    }

    private Observable<List<NavigationItem>> categoryNavigationItems() {
        return combineLatest(
                userService.loginState().map(UserService::getUser),
                extraCategoryApiAvailable,
                this::categoryNavigationItems);
    }

    /**
     * Returns the menu item that takes the user to the upload activity.
     */
    private NavigationItem getUploadNavigationItem() {
        return ImmutableNavigationItem.builder()
                .title(getString(R.string.action_upload))
                .icon(iconUpload)
                .action(ActionType.UPLOAD)
                .build();
    }

    /**
     * Short for context.getString(...)
     */
    private String getString(int id) {
        return context.getString(id);
    }


    @Value.Immutable
    public abstract static class NavigationItem {
        abstract public String title();

        /**
         * This is the basic icon for this navigation item. You might want to
         * tint it later on.
         */
        abstract public Drawable icon();

        /**
         * If this item is {@link com.pr0gramm.app.services.NavigationProvider.ActionType#FILTER} or
         * {@link com.pr0gramm.app.services.NavigationProvider.ActionType#BOOKMARK}, then it contains
         * a filter.
         */
        abstract public Optional<FeedFilter> filter();

        /**
         * If this is {@link com.pr0gramm.app.services.NavigationProvider.ActionType#BOOKMARK},
         * this method returns an optional containing the bookmark.
         */
        abstract public Optional<Bookmark> bookmark();

        abstract public ActionType action();

        public boolean hasFilter() {
            return filter().isPresent();
        }

        @Value.Default
        public int layout() {
            return R.layout.left_drawer_nav_item;
        }

        @Value.Default
        public int unreadCount() {
            return 0;
        }

        @Value.Check
        protected void check() {
            checkState(action() != ActionType.BOOKMARK || bookmark().isPresent(),
                    "A bookmark is required, if action() == BOOKMARK");

            checkState((action() != ActionType.FILTER && action() != ActionType.BOOKMARK) || filter().isPresent(),
                    "A filter is required, if action() in (FILTER, BOOKMARK)");
        }
    }

    public enum ActionType {
        FILTER, BOOKMARK, MESSAGES, UPLOAD
    }
}
