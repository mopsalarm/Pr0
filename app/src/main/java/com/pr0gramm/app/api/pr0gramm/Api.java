package com.pr0gramm.app.api.pr0gramm;

import com.pr0gramm.app.api.pr0gramm.response.Feed;
import com.pr0gramm.app.api.pr0gramm.response.Info;
import com.pr0gramm.app.api.pr0gramm.response.Login;
import com.pr0gramm.app.api.pr0gramm.response.MessageFeed;
import com.pr0gramm.app.api.pr0gramm.response.NewComment;
import com.pr0gramm.app.api.pr0gramm.response.NewTag;
import com.pr0gramm.app.api.pr0gramm.response.Post;
import com.pr0gramm.app.api.pr0gramm.response.Posted;
import com.pr0gramm.app.api.pr0gramm.response.PrivateMessageFeed;
import com.pr0gramm.app.api.pr0gramm.response.Sync;
import com.pr0gramm.app.api.pr0gramm.response.Upload;
import com.pr0gramm.app.api.pr0gramm.response.UserComments;
import com.pr0gramm.app.feed.Nothing;
import com.squareup.okhttp.RequestBody;

import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;
import rx.Observable;

/**
 */
public interface Api {
    @GET("/api/items/get")
    Observable<Feed> itemsGet(
            @Query("promoted") Integer promoted,
            @Query("following") Integer following,
            @Query("older") Long older,
            @Query("newer") Long newer,
            @Query("id") Long around,
            @Query("flags") int flags,
            @Query("tags") String tags,
            @Query("likes") String likes,
            @Query("self") Boolean self,
            @Query("user") String user);

    @FormUrlEncoded
    @POST("/api/items/vote")
    Observable<Nothing> vote(@Field("_nonce") Nonce nonce,
                             @Field("id") long id, @Field("vote") int voteValue);

    @FormUrlEncoded
    @POST("/api/tags/vote")
    Observable<Nothing> voteTag(@Field("_nonce") Nonce nonce,
                                @Field("id") long id, @Field("vote") int voteValue);

    @FormUrlEncoded
    @POST("/api/comments/vote")
    Observable<Nothing> voteComment(@Field("_nonce") Nonce nonce,
                                    @Field("id") long id, @Field("vote") int voteValue);

    @FormUrlEncoded
    @POST("/api/user/login")
    Observable<Login> login(
            @Field("name") String username,
            @Field("password") String password);

    @FormUrlEncoded
    @POST("/api/tags/add")
    Observable<NewTag> addTags(
            @Field("_nonce") Nonce nonce,
            @Field("itemId") long lastId,
            @Field("tags") String tags);

    @FormUrlEncoded
    @POST("/api/comments/post")
    Observable<NewComment> postComment(
            @Field("_nonce") Nonce nonce,
            @Field("itemId") long itemId,
            @Field("parentId") long parentId,
            @Field("comment") String comment);

    @GET("/api/items/info")
    Observable<Post> info(@Query("itemId") long itemId);

    @GET("/api/user/sync")
    Observable<Sync> sync(@Query("lastId") long lastId);

    @GET("/api/profile/info")
    Observable<Info> info(@Query("name") String name, @Query("flags") Integer flags);

    @GET("/api/inbox/all")
    Observable<MessageFeed> inboxAll();

    @GET("/api/inbox/unread")
    Observable<MessageFeed> inboxUnread();

    @GET("/api/inbox/messages")
    Observable<PrivateMessageFeed> inboxPrivateMessages();

    @GET("/api/profile/comments")
    Observable<UserComments> userComments(@Query("name") String user,
                                          @Query("before")
                                          long before, @Query("flags") Integer flags);

    @FormUrlEncoded
    @POST("/api/inbox/post")
    Observable<Nothing> sendMessage(
            @Field("_nonce") Nonce nonce,
            @Field("comment") String text,
            @Field("recipientId") long recipient);

    @GET("/api/items/ratelimited")
    Observable<Nothing> ratelimited();

    @POST("/api/items/upload")
    Observable<Upload> upload(@Body RequestBody body);

    @FormUrlEncoded
    @POST("/api/items/post")
    Observable<Posted> post(@Field("_nonce") Nonce nonce,
                            @Field("sfwstatus") String sfwStatus,
                            @Field("tags") String tags,
                            @Field("checkSimilar") int checkSimilar,
                            @Field("key") String key);

    final class Nonce {
        public final String value;

        public Nonce(String userId) {
            this.value = userId.substring(0, 16);
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
