package com.pr0gramm.app.services;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.gson.GsonBuilder;
import com.pr0gramm.app.api.InstantTypeAdapter;
import com.pr0gramm.app.api.pr0gramm.response.ImmutableMessage;
import com.pr0gramm.app.api.pr0gramm.response.Message;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.okhttp.OkHttpClient;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;
import rx.Observable;

/**
 */
@Singleton
@Gson.TypeAdapters
public class CommentService {
    private static final Logger logger = LoggerFactory.getLogger("CommentService");

    private final HttpInterface api;
    private final Observable<String> userHash;

    @Inject
    public CommentService(UserService userService, OkHttpClient okHttpClient) {
        this.api = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl("http://pr0.wibbly-wobbly.de/api/comments/v1/")
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder()
                        .registerTypeAdapterFactory(new GsonAdaptersCommentService())
                        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                        .create()))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .validateEagerly()
                .build()
                .create(HttpInterface.class);

        // subscribe to the login state.
        userHash = userService.loginState()
                .observeOn(BackgroundScheduler.instance())

                .switchMap(state -> state.isAuthorized()
                        ? userService.accountInfo()
                        : Observable.just(null))

                .map(accountInfo -> accountInfo == null ? null : Hashing.md5()
                        .hashString(accountInfo.account().email(), Charsets.UTF_8)
                        .toString())

                .onErrorResumeNext(Observable.empty())
                .replay(1)
                .autoConnect();
    }

    public Observable<Void> save(FavedComment comment) {
        logger.info("save comment-fav with id {}", comment.id());

        return userHash
                .filter(CommentService::isUserHashAvailable)
                .take(1)
                .flatMap(hash -> api.save(hash, comment.id(), comment))
                .ignoreElements();
    }

    public Observable<List<FavedComment>> list(EnumSet<ContentType> contentType) {
        int flags = ContentType.combine(contentType);

        return userHash
                .filter(CommentService::isUserHashAvailable)
                .take(1)
                .flatMap(hash -> api.list(hash, flags));
    }

    public Observable<Void> delete(long commentId) {
        logger.info("delete comment-fav with id {}", commentId);

        return userHash
                .filter(CommentService::isUserHashAvailable)
                .take(1)
                .flatMap(hash -> api.delete(hash, commentId))
                .ignoreElements();
    }

    private static boolean isUserHashAvailable(String userHash) {
        return userHash != null;
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
    }

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

    public static Message commentToMessage(FavedComment comment) {
        String thumbnail = comment.thumb().replaceFirst("^.*pr0gramm.com/", "/");
        return ImmutableMessage.builder()
                .withId(comment.id())
                .withItemId(comment.itemId())
                .withName(comment.name())
                .withMessage(comment.content())
                .withScore(comment.up() - comment.down())
                .withThumb(thumbnail)
                .withCreated(comment.created())
                .withMark(comment.mark())

                        // we dont have the sender :/
                .withSenderId(0)

                .build();
    }
}
