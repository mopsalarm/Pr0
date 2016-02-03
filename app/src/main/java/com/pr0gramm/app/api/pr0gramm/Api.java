package com.pr0gramm.app.api.pr0gramm;

import com.pr0gramm.app.api.pr0gramm.response.AccountInfo;
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
import com.pr0gramm.app.api.pr0gramm.response.ThemeInfo;
import com.pr0gramm.app.api.pr0gramm.response.Upload;
import com.pr0gramm.app.api.pr0gramm.response.UserComments;
import com.pr0gramm.app.feed.Nothing;

import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
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

    @GET("/api/user/info")
    Observable<AccountInfo> accountInfo();

    @GET("/api/user/theme")
    Observable<ThemeInfo> themeInfo();

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


    // Extra stuff for admins
    @FormUrlEncoded
    @POST("api/items/delete")
    Observable<Nothing> deleteItem(
            @Field("_nonce") Nonce none,
            @Field("id") long id,
            @Field("reason") String reason,
            @Field("customReason") String customReason,
            @Field("notifyUser") String notifyUser,
            @Field("banUser") String banUser,
            @Field("days") Integer days);


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
