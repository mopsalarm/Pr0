package com.pr0gramm.app.services;

import android.util.Pair;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.api.InstantTypeAdapter;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.api.pr0gramm.ImmutableApi;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.util.BackgroundScheduler;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import gnu.trove.TCollections;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import rx.Observable;
import rx.functions.Actions;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import static com.google.common.collect.Lists.transform;

/**
 */
@Singleton
@Gson.TypeAdapters
public class FavedCommentService {
    private static final Logger logger = LoggerFactory.getLogger("CommentService");

    private final HttpInterface api;
    private final Observable<String> userHash;
    private final TLongSet favCommentIds = new TLongHashSet();
    private final Subject<TLongSet, TLongSet> favCommentIdsObservable = BehaviorSubject
            .<TLongSet>create(new TLongHashSet()).toSerialized();

    private final Subject<String, String> forceUpdateUserHash = PublishSubject.<String>create().toSerialized();

    @Inject
    public FavedCommentService(UserService userService, OkHttpClient okHttpClient, SingleShotService singleShotService) {
        this.api = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("https://pr0.wibbly-wobbly.de/api/comments/v1/")
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                        .registerTypeAdapterFactory(new GsonAdaptersFavedCommentService())
                        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                        .create()))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .validateEagerly(true)
                .build()
                .create(HttpInterface.class);

        // subscribe to the login state.
        userHash = userService.userToken()
                .distinctUntilChanged()
                .replay(1)
                .autoConnect();

        // update comments when the login state changes
        userHash.mergeWith(forceUpdateUserHash.observeOn(BackgroundScheduler.instance()))
                .switchMap(userHash -> userHash == null
                        ? Observable.just(Collections.<FavedComment>emptyList())
                        : api.list(userHash, ContentType.combine(EnumSet.allOf(ContentType.class))).onErrorResumeNext(Observable.empty()))

                .subscribeOn(BackgroundScheduler.instance())
                .subscribe(comments -> {
                    updateCommentIds(new TLongHashSet(transform(comments, FavedComment::id)));
                });

        // migrate if not yet done
        userHash.filter(FavedCommentService::isUserHashAvailable)
                .filter(hash -> singleShotService.firstTimeToday("kfav.migrate2-" + hash))
                .zipWith(userService.accountInfo().retry(2), Pair::create)
                .flatMap(values -> {
                    String userHash = values.first;
                    Api.AccountInfo accountInfo = values.second;

                    String emailHash = Hashing.md5()
                            .hashString(accountInfo.account().email(), Charsets.UTF_8)
                            .toString();

                    logger.info("migrating from {} to {} now", emailHash, userHash);
                    return api.migrate(emailHash, userHash).retry(2);
                })
                .doOnError(error -> logger.warn("Could not do the migration."))
                .onErrorResumeNext(Observable.empty())
                .subscribe(Actions.empty());
    }

    private void updateCommentIds(TLongHashSet commentIds) {
        logger.info("updating comment cache, setting {} comments", commentIds.size());

        synchronized (favCommentIds) {
            favCommentIds.clear();
            favCommentIds.addAll(commentIds);
            updateAfterChange();
        }
    }

    public Observable<TLongSet> favedCommentIds() {
        return favCommentIdsObservable.asObservable();
    }

    public Observable<Void> save(FavedComment comment) {
        logger.info("save comment-fav with id {}", comment.id());

        Track.commentFaved();

        synchronized (favCommentIds) {
            if (favCommentIds.add(comment.id()))
                updateAfterChange();
        }

        return userHash
                .filter(FavedCommentService::isUserHashAvailable)
                .take(1)
                .flatMap(hash -> api.save(hash, comment.id(), comment))
                .ignoreElements();
    }

    public Observable<List<FavedComment>> list(EnumSet<ContentType> contentType) {
        int flags = ContentType.combine(contentType);

        Track.listFavedComments();

        // update cache too
        updateCache();

        return userHash
                .filter(FavedCommentService::isUserHashAvailable)
                .take(1)
                .flatMap(hash -> api.list(hash, flags));
    }

    public Observable<Void> delete(long commentId) {
        logger.info("delete comment-fav with id {}", commentId);

        synchronized (favCommentIds) {
            if (favCommentIds.remove(commentId))
                updateAfterChange();
        }

        return userHash
                .filter(FavedCommentService::isUserHashAvailable)
                .take(1)
                .flatMap(hash -> api.delete(hash, commentId))
                .ignoreElements();
    }

    private void updateAfterChange() {
        // sends the hash set to all the observers
        favCommentIdsObservable.onNext(TCollections.unmodifiableSet(new TLongHashSet(favCommentIds)));
    }

    private static boolean isUserHashAvailable(String userHash) {
        return userHash != null;
    }

    public void updateCache() {
        userHash.take(1).subscribe(forceUpdateUserHash::onNext, Actions.empty());
    }

    private interface HttpInterface {
        @DELETE("{userHash}/{commentId}")
        Observable<Void> delete(
                @Path("userHash") String userHash,
                @Path("commentId") long commentId);

        @PUT("{userHash}/{commentId}")
        Observable<Void> save(
                @Path("userHash") String userHash,
                @Path("commentId") long commentId,
                @Body FavedComment comment);

        @GET("{userHash}")
        Observable<List<FavedComment>> list(@Path("userHash") String userHash,
                                            @Query("flags") int flags);

        /**
         * Migrate from the given user id to the new one.
         */
        @POST("migrate")
        Observable<Void> migrate(@Query("from") String from, @Query("to") String to);
    }

    @SuppressWarnings("WeakerAccess")
    @Value.Immutable
    public interface FavedComment {
        long id();

        @Gson.Named("item_id")
        long itemId();

        String name();

        String content();

        int up();

        int down();

        int mark();

        Instant created();

        String thumb();

        int flags();
    }

    public static Api.Message commentToMessage(FavedComment comment) {
        String thumbnail = comment.thumb().replaceFirst("^.*pr0gramm.com/", "/");
        return ImmutableApi.Message.builder()
                .id(comment.id())
                .itemId(comment.itemId())
                .name(comment.name())
                .message(comment.content())
                .score(comment.up() - comment.down())
                .thumbnail(thumbnail)
                .created(comment.created())
                .mark(comment.mark())

                /* we dont have the sender :/ */
                .senderId(0)

                .build();
    }
}
