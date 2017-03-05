package com.pr0gramm.app.api.pr0gramm;

import com.google.common.base.Optional;
import com.google.gson.annotations.SerializedName;
import com.pr0gramm.app.feed.FeedItem;
import com.pr0gramm.app.feed.Nothing;
import com.pr0gramm.app.services.HasThumbnail;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

import java.util.List;

import javax.annotation.Nullable;

import okhttp3.RequestBody;
import proguard.annotation.KeepPublicClassMemberNames;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import rx.Observable;

/**
 */
@Value.Enclosing
@Gson.TypeAdapters
@Value.Style(get = {"get*", "is*"})
@KeepPublicClassMemberNames
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
    Observable<Sync> sync(@Query("offset") long offset);

    @GET("/api/user/info")
    Observable<AccountInfo> accountInfo();

    @GET("/api/profile/info")
    Observable<Info> info(@Query("name") String name, @Query("flags") Integer flags);

    @GET("/api/user/score")
    Observable<UserScore> score();

    @GET("/api/inbox/all")
    Observable<MessageFeed> inboxAll();

    @GET("/api/inbox/unread")
    Observable<MessageFeed> inboxUnread();

    @GET("/api/inbox/messages")
    Observable<PrivateMessageFeed> inboxPrivateMessages();

    @GET("/api/profile/comments")
    Observable<UserComments> userComments(@Query("name") String user,
                                          @Query("before") long before, @Query("flags") Integer flags);

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


    @FormUrlEncoded
    @POST("/api/user/invite")
    Observable<Invited> invite(@Field("_nonce") Nonce nonce,
                               @Field("email") String email);

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
            @Field("days") Float days);

    @FormUrlEncoded
    @POST("api/tags/delete")
    Observable<Nothing> deleteTag(
            @Field("_nonce") Nonce nonce,
            @Field("itemId") long itemId,
            @Field("banUsers") String banUser,
            @Field("days") int days,
            @Field("tags[]") long tagId);

    @FormUrlEncoded
    @POST("api/profile/follow")
    Observable<Nothing> profileFollow(@Field("_nonce") Nonce nonce, @Field("name") String username);

    @FormUrlEncoded
    @POST("api/profile/unfollow")
    Observable<Nothing> profileUnfollow(@Field("_nonce") Nonce nonce, @Field("name") String username);

    @GET("api/profile/suggest")
    Call<Names> suggestUsers(@Query("prefix") String prefix);

    @GET("api/user/identifier")
    Observable<UserIdentifier> identifier();

    @FormUrlEncoded
    @POST("api/contact/send")
    Observable<Nothing> contactSend(
            @Field("subject") String subject,
            @Field("email") String email,
            @Field("message") String message);

    @FormUrlEncoded
    @POST("api/user/sendpasswordresetmail")
    Observable<Nothing> requestPasswordRecovery(@Field("email") String email);

    @FormUrlEncoded
    @POST("api/user/resetpassword")
    Observable<ResetPasswordResponse> resetPassword(
            @Field("name") String name,
            @Field("token") String token,
            @Field("password") String password);

    final class Nonce {
        public final String value;

        Nonce(String userId) {
            this.value = userId.substring(0, 16);
        }

        @Override
        public String toString() {
            return value;
        }
    }


    @Value.Immutable
    interface Names {
        List<String> users();
    }

    @Value.Immutable
    interface AccountInfo {
        Account account();

        List<Invite> invited();

        @Value.Immutable
        interface Account {
            String email();

            int invites();
        }

        @Value.Immutable
        interface Invite {
            String email();

            Instant created();

            Optional<String> name();

            Optional<Integer> mark();
        }
    }

    /**
     */
    @Value.Immutable
    interface Comment {
        long getId();

        float getConfidence();

        String getName();

        String getContent();

        Instant getCreated();

        long getParent();

        int getUp();

        int getDown();

        int getMark();
    }

    /**
     * Feed class maps the json returned for a call to the
     * api endpoint <code>/api/items/get</code>.
     */
    @Value.Immutable
    interface Feed {
        boolean isAtStart();

        boolean isAtEnd();

        List<Item> getItems();

        Optional<String> getError();

        @Value.Immutable
        interface Item {
            long getId();

            long getPromoted();

            String getImage();

            String getThumb();

            String getFullsize();

            String getUser();

            int getUp();

            int getDown();

            int getMark();

            int getFlags();

            Optional<Integer> width();

            Optional<Integer> height();

            Optional<Boolean> audio();

            Instant getCreated();
        }
    }

    /**
     */
    @Value.Immutable
    interface Info {
        User getUser();

        int getLikeCount();

        int getUploadCount();

        int getCommentCount();

        int getTagCount();

        boolean likesArePublic();

        boolean following();

        List<UserComments.UserComment> getComments();

        @Value.Immutable
        abstract class User {
            public abstract int getId();

            public abstract int getMark();

            public abstract int getScore();

            public abstract String getName();

            public abstract Instant getRegistered();

            @Value.Default
            public int isBanned() {
                return 0;
            }

            @Nullable
            public abstract Instant getBannedUntil();
        }
    }

    /**
     */
    @Value.Immutable
    interface Invited {
        @Nullable
        String error();
    }

    /**
     */
    @Value.Immutable
    interface Login {
        boolean success();

        Optional<String> identifier();

        @SerializedName("ban")
        Optional<BanInfo> banInfo();

        @Value.Immutable
        interface BanInfo {
            boolean banned();

            Instant till();

            String reason();
        }
    }

    /**
     * A message received from the pr0gramm api.
     */
    @Value.Immutable
    abstract class Message implements HasThumbnail {
        /**
         * If this message is a comment, this is the id of the comment.
         */
        public abstract long id();

        @Gson.Named("created")
        public abstract Instant creationTime();

        public abstract long itemId();

        public abstract int mark();

        public abstract String message();

        public abstract String name();

        public abstract int score();

        public abstract int senderId();

        public boolean isComment() {
            return itemId() != 0;
        }

        public long commentId() {
            return id();
        }

        @Nullable
        @Gson.Named("thumb")
        public abstract String thumbnail();

        public static Message of(PrivateMessage message) {
            return ImmutableApi.Message.builder()
                    .message(message.getMessage())
                    .id(message.getId())
                    .itemId(0)
                    .creationTime(message.getCreated())
                    .mark(message.getSenderMark())
                    .name(message.getSenderName())
                    .senderId(message.getSenderId())
                    .score(0)
                    .thumbnail(null)
                    .build();
        }

        public static Message of(FeedItem item, Comment comment) {
            return ImmutableApi.Message.builder()
                    .id((int) comment.getId())
                    .itemId((int) item.id())
                    .message(comment.getContent())
                    .senderId(0)
                    .name(comment.getName())
                    .mark(comment.getMark())
                    .score(comment.getUp() - comment.getDown())
                    .creationTime(comment.getCreated())
                    .thumbnail(item.thumbnail())
                    .build();
        }

        public static Message of(UserComments.UserInfo sender, UserComments.UserComment comment) {
            return of(sender.getId(), sender.getName(), sender.getMark(), comment);
        }

        public static Message of(Info.User sender, UserComments.UserComment comment) {
            return of(sender.getId(), sender.getName(), sender.getMark(), comment);
        }

        public static Message of(int senderId, String name, int mark, UserComments.UserComment comment) {
            return ImmutableApi.Message.builder()
                    .id((int) comment.getId())
                    .creationTime(comment.getCreated())
                    .score(comment.getUp() - comment.getDown())
                    .itemId((int) comment.getItemId())
                    .mark(mark)
                    .name(name)
                    .senderId(senderId)
                    .message(comment.getContent())
                    .thumbnail(comment.getThumb())
                    .build();
        }
    }

    /**
     */
    @Value.Immutable
    interface MessageFeed {
        List<Message> getMessages();
    }

    /**
     */
    @Value.Immutable
    interface NewComment {
        long getCommentId();

        List<Comment> getComments();
    }

    /**
     */
    @Value.Immutable
    interface NewTag {
        List<Long> getTagIds();

        List<Tag> getTags();
    }

    /**
     */
    @Value.Immutable
    interface Post {
        List<Tag> getTags();

        List<Comment> getComments();
    }

    /**
     */
    @Value.Immutable
    abstract class Posted {
        @Value.Derived
        @Gson.Ignore
        public long getItemId() {
            //noinspection ConstantConditions
            return getItem() != null
                    ? getItem().getId()
                    : -1;
        }

        @android.support.annotation.Nullable
        public abstract String getError();

        @android.support.annotation.Nullable
        public abstract PostedItem getItem();

        public abstract List<SimilarItem> getSimilar();

        public abstract Optional<VideoReport> getReport();

        @Value.Immutable
        public interface PostedItem {
            long getId();
        }

        @Value.Immutable
        public interface SimilarItem extends HasThumbnail {
            long id();

            @Gson.Named("thumb")
            String thumbnail();
        }

        @Value.Immutable
        public static abstract class VideoReport {
            public float duration() {
                return 0;
            }

            public int height() {
                return 0;
            }

            public int width() {
                return 0;
            }

            @Nullable
            public abstract String format();

            @android.support.annotation.Nullable
            public abstract String error();

            public abstract List<MediaStream> streams();
        }

        @Value.Immutable
        public interface MediaStream {
            String codec();

            String type();
        }
    }

    /**
     */
    @Value.Immutable
    interface PrivateMessage {
        int getId();

        Instant getCreated();

        int getRecipientId();

        int getRecipientMark();

        String getRecipientName();

        int getSenderId();

        int getSenderMark();

        String getSenderName();

        boolean isSent();

        String getMessage();
    }

    /**
     */
    @Value.Immutable
    interface PrivateMessageFeed {
        List<PrivateMessage> getMessages();
    }

    /**
     */
    @Value.Immutable
    interface Sync {
        long logLength();

        String log();

        int score();

        int inboxCount();
    }

    /**
     */
    @Value.Immutable
    interface Tag {
        @Value.Auxiliary
        long getId();

        @Value.Auxiliary
        float getConfidence();

        String getTag();
    }

    /**
     * Response after uploading a file.
     */
    @Value.Immutable
    interface Upload {
        String getKey();
    }

    /**
     */
    @Value.Immutable
    interface UserComments {
        UserInfo getUser();

        List<UserComment> getComments();

        @Value.Immutable
        interface UserComment {
            long getId();

            long getItemId();

            Instant getCreated();

            String getThumb();

            int getUp();

            int getDown();

            String getContent();
        }

        @Value.Immutable
        interface UserInfo {
            int getId();

            int getMark();

            String getName();
        }
    }

    @Value.Immutable
    interface UserIdentifier {
        String identifier();
    }

    @Value.Immutable
    interface UserScore {
        int score();
    }

    @Value.Immutable
    interface ResetPasswordResponse {
        @Nullable
        String error();
    }
}
