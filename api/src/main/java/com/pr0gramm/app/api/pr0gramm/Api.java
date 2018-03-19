package com.pr0gramm.app.api.pr0gramm;

import com.google.gson.annotations.SerializedName;
import com.pr0gramm.app.HasThumbnail;
import com.pr0gramm.app.Nothing;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.joda.time.Instant;

import java.util.List;

import javax.annotation.Nullable;

import okhttp3.RequestBody;
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

    @FormUrlEncoded
    @POST("/api/comments/delete")
    Observable<Nothing> hardDeleteComment(@Field("_nonce") Nonce nonce, @Field("id") long commentId, @Field("reason") String reason);

    @FormUrlEncoded
    @POST("/api/comments/softDelete")
    Observable<Nothing> softDeleteComment(@Field("_nonce") Nonce nonce, @Field("id") long commentId, @Field("reason") String reason);

    @GET("/api/items/info")
    Observable<Post> info(@Query("itemId") long itemId);

    @GET("/api/user/sync")
    Observable<Sync> sync(@Query("offset") long offset);

    @GET("/api/user/info")
    Observable<AccountInfo> accountInfo();

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
            @Field("banUser") String banUser,
            @Field("days") Float days);

    @FormUrlEncoded
    @POST("api/user/ban")
    Observable<Nothing> userBan(
            @Field("_nonce") Nonce none,
            @Field("name") String name,
            @Field("reason") String reason,
            @Field("customReason") String customReason,
            @Field("days") float days,
            @Field("mode") Integer mode);

    @GET("api/tags/details")
    Observable<TagDetails> tagDetails(@Query("itemId") long itemId);

    @FormUrlEncoded
    @POST("api/tags/delete")
    Observable<Nothing> deleteTag(
            @Field("_nonce") Nonce nonce,
            @Field("itemId") long itemId,
            @Field("banUsers") String banUser,
            @Field("days") Float days,
            @Field("tags[]") List<Long> tagId);

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

    @FormUrlEncoded
    @POST("api/user/handoverrequest")
    Observable<HandoverTokenResponse> handoverToken(@Field("_nonce") Nonce nonce);

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
        List<String> getUsers();
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

            @Nullable
            String name();

            @Nullable
            Integer mark();
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
    abstract class Feed {
        @Value.Default
        public boolean isAtStart() {
            return false;
        }

        @Value.Default
        public boolean isAtEnd() {
            return false;
        }

        public abstract List<Item> getItems();

        @Nullable
        public abstract String getError();

        @Value.Immutable
        public interface Item {
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

            @Nullable
            Integer getWidth();

            @Nullable
            Integer getHeight();

            @Nullable
            Boolean getAudio();

            Instant getCreated();
        }
    }

    /**
     */
    @Value.Immutable
    interface Info {
        User getUser();

        List<Badge> getBadges();

        int getLikeCount();

        int getUploadCount();

        int getCommentCount();

        int getTagCount();

        boolean likesArePublic();

        boolean following();

        List<UserComments.UserComment> getComments();

        @Value.Immutable
        interface Badge {
            Instant getCreated();

            String getLink();

            String getImage();

            @Nullable
            String getDescription();
        }

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
        boolean isSuccess();

        @Nullable
        String getIdentifier();

        @Nullable
        @SerializedName("ban")
        BanInfo getBanInfo();

        @Value.Immutable
        interface BanInfo {
            boolean banned();

            @Nullable
            @SerializedName("till")
            Instant endTime();

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

        @Value.Default
        public long itemId() {
            return 0;
        }

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

        @Nullable
        public abstract String getError();

        @Nullable
        public abstract PostedItem getItem();

        public abstract List<SimilarItem> getSimilar();

        @Nullable
        public abstract VideoReport getReport();

        @Value.Immutable
        public interface PostedItem {
            long getId();
        }

        @Value.Immutable
        public interface SimilarItem extends HasThumbnail {
            long id();

            @Gson.Named("thumb")
            String thumbnail();

            String getImage();
        }

        @Value.Immutable
        public static abstract class VideoReport {
            @Value.Default
            public float getDuration() {
                return 0;
            }

            @Value.Default
            public int getHeight() {
                return 0;
            }

            @Value.Default
            public int getWidth() {
                return 0;
            }

            @Nullable
            public abstract String getFormat();

            @Nullable
            public abstract String getError();

            public abstract List<MediaStream> getStreams();
        }

        @Value.Immutable
        public interface MediaStream {
            @Nullable
            String getCodec();

            String getType();
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
        String getIdentifier();
    }

    @Value.Immutable
    interface ResetPasswordResponse {
        @Nullable
        String getError();
    }

    @Value.Immutable
    interface TagDetails {
        List<TagInfo> tags();

        @Value.Immutable
        interface TagInfo {
            long id();

            int up();

            int down();

            float confidence();

            String tag();

            String user();

            List<Vote> votes();
        }

        @Value.Immutable
        interface Vote {
            int vote();

            String user();
        }
    }

    @Value.Immutable
    interface HandoverTokenResponse {
        String getToken();
    }
}
