package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import com.pr0gramm.app.model.config.Rule
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Deferred
import okhttp3.RequestBody
import retrofit2.http.*

interface Api {
    @GET("/api/items/get")
    fun itemsGetAsync(
            @Query("promoted") promoted: Int?,
            @Query("following") following: Int?,
            @Query("older") older: Long?,
            @Query("newer") newer: Long?,
            @Query("id") around: Long?,
            @Query("flags") flags: Int,
            @Query("tags") tags: String?,
            @Query("likes") likes: String?,
            @Query("self") self: Boolean?,
            @Query("user") user: String?): Deferred<Feed>

    @FormUrlEncoded
    @POST("/api/items/vote")
    fun voteAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/tags/vote")
    fun voteTagAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/comments/vote")
    fun voteCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/user/login")
    fun loginAsync(
            @Field("name") username: String,
            @Field("password") password: String): Deferred<retrofit2.Response<Login>>

    @FormUrlEncoded
    @POST("/api/tags/add")
    fun addTagsAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") lastId: Long,
            @Field("tags") tags: String): Deferred<NewTag>

    @GET("/api/tags/top")
    fun topTagsAsync(): Deferred<TagTopList>

    @FormUrlEncoded
    @POST("/api/comments/post")
    fun postCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") itemId: Long,
            @Field("parentId") parentId: Long,
            @Field("comment") comment: String): Deferred<NewComment>

    @FormUrlEncoded
    @POST("/api/comments/delete")
    fun hardDeleteCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") commentId: Long,
            @Field("reason") reason: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/comments/softDelete")
    fun softDeleteCommentAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") commentId: Long,
            @Field("reason") reason: String): Deferred<Unit>

    @GET("/api/items/info")
    fun infoAsync(
            @Query("itemId") itemId: Long,
            @Query("bust") bust: Long?): Deferred<Post>

    @GET("/api/user/sync")
    fun syncAsync(
            @Query("offset") offset: Long): Deferred<Sync>

    @GET("/api/user/info")
    fun accountInfoAsync(): Deferred<AccountInfo>

    @GET("/api/profile/info")
    fun infoAsync(
            @Query("name") name: String,
            @Query("flags") flags: Int?): Deferred<Info>

    @GET("/api/inbox/pending")
    fun inboxPendingAsync(): Deferred<MessageFeed>

    @GET("/api/inbox/conversations")
    fun listConversationsAsync(
            @Query("older") older: Long?): Deferred<Conversations>

    @GET("/api/inbox/messages")
    fun messagesWithAsync(
            @Query("with") name: String,
            @Query("older") older: Long?): Deferred<ConversationMessages>

    @GET("/api/inbox/comments")
    fun inboxCommentsAsync(
            @Query("older") older: Long?): Deferred<MessageFeed>

    @GET("/api/profile/comments")
    fun userCommentsAsync(
            @Query("name") user: String,
            @Query("before") before: Long?,
            @Query("flags") flags: Int?): Deferred<UserComments>

    @GET("/api/profile/commentlikes")
    fun userCommentsLikeAsync(
            @Query("name") user: String,
            @Query("before") before: Long,
            @Query("flags") flags: Int?): Deferred<FavedUserComments>

    @FormUrlEncoded
    @POST("/api/inbox/post")
    fun sendMessageAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("comment") text: String,
            @Field("recipientId") recipient: Long): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/inbox/post")
    fun sendMessageAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("comment") text: String,
            @Field("recipientName") recipient: String): Deferred<ConversationMessages>

    @GET("/api/items/ratelimited")
    fun ratelimitedAsync(): Deferred<Unit>

    @POST("/api/items/upload")
    fun uploadAsync(
            @Body body: RequestBody): Deferred<Upload>

    @FormUrlEncoded
    @POST("/api/items/post")
    fun postAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("sfwstatus") sfwStatus: String,
            @Field("tags") tags: String,
            @Field("checkSimilar") checkSimilar: Int,
            @Field("key") key: String,
            @Field("processAsync") processAsync: Int?): Deferred<Posted>

    @GET("/api/items/queue")
    fun queueAsync(
            @Query("id") id: Long?): Deferred<QueueState>

    @FormUrlEncoded
    @POST("/api/user/invite")
    fun inviteAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("email") email: String): Deferred<Invited>

    // Extra stuff for admins
    @FormUrlEncoded
    @POST("api/items/delete")
    fun deleteItemAsync(
            @Field("_nonce") none: Nonce?,
            @Field("id") id: Long,
            @Field("reason") reason: String,
            @Field("customReason") customReason: String,
            @Field("banUser") banUser: String?,
            @Field("days") days: Float?): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/user/ban")
    fun userBanAsync(
            @Field("_nonce") none: Nonce?,
            @Field("name") name: String,
            @Field("reason") reason: String,
            @Field("customReason") customReason: String,
            @Field("days") days: Float,
            @Field("mode") mode: Int?): Deferred<Unit>

    @GET("api/tags/details")
    fun tagDetailsAsync(
            @Query("itemId") itemId: Long): Deferred<TagDetails>

    @FormUrlEncoded
    @POST("api/tags/delete")
    fun deleteTagAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") itemId: Long,
            @Field("banUsers") banUser: String?,
            @Field("days") days: Float?,
            @Field("tags[]") tagId: List<Long> = listOf()): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/profile/follow")
    fun profileFollowAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") username: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/profile/unfollow")
    fun profileUnfollowAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") username: String): Deferred<Unit>

    @GET("api/profile/suggest")
    fun suggestUsersAsync(
            @Query("prefix") prefix: String): Deferred<Names>

    @FormUrlEncoded
    @POST("api/contact/send")
    fun contactSendAsync(
            @Field("subject") subject: String,
            @Field("email") email: String,
            @Field("message") message: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/contact/report")
    fun reportAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") item: Long,
            @Field("commentId") commentId: Long,
            @Field("reason") reason: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/user/sendpasswordresetmail")
    fun requestPasswordRecoveryAsync(
            @Field("email") email: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/user/resetpassword")
    fun resetPasswordAsync(
            @Field("name") name: String,
            @Field("token") token: String,
            @Field("password") password: String): Deferred<ResetPassword>

    @FormUrlEncoded
    @POST("api/user/handoverrequest")
    fun handoverTokenAsync(
            @Field("_nonce") nonce: Nonce?): Deferred<HandoverToken>

    @GET("api/bookmarks/get")
    fun bookmarksAsync(): Deferred<Bookmarks>

    @GET("api/bookmarks/get?default")
    fun defaultBookmarksAsync(): Deferred<Bookmarks>

    @FormUrlEncoded
    @POST("api/bookmarks/add")
    fun bookmarksAddAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") name: String,
            @Field("link") link: String): Deferred<Bookmarks>

    @FormUrlEncoded
    @POST("api/bookmarks/delete")
    fun bookmarksDeleteAsync(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") name: String): Deferred<Bookmarks>

    @GET("media/app-config.json")
    fun remoteConfigAsync(@Query("bust") bust: Long): Deferred<List<Rule>>

    data class Nonce(val value: String) {
        override fun toString(): String = value.take(16)
    }

    @JsonClass(generateAdapter = true)
    data class AccountInfo(
            val account: Account,
            val invited: List<Invite> = listOf()) {

        @JsonClass(generateAdapter = true)
        data class Account(
                val email: String,
                val invites: Int)

        @JsonClass(generateAdapter = true)
        data class Invite(
                val email: String,
                val created: Instant,
                val name: String?,
                val mark: Int?)
    }

    @JsonClass(generateAdapter = true)
    data class Comment(
            val id: Long,
            val confidence: Float,
            val name: String,
            val content: String,
            val created: Instant,
            val parent: Long,
            val up: Int,
            val down: Int,
            val mark: Int) {

        val score: Int get() = up - down
    }

    @JsonClass(generateAdapter = true)
    data class Feed(
            val error: String? = null,
            @Json(name = "items") val _items: List<Item>? = null,
            @Json(name = "atStart") val isAtStart: Boolean = false,
            @Json(name = "atEnd") val isAtEnd: Boolean = false) {

        @Transient
        val items = _items.orEmpty()

        @JsonClass(generateAdapter = true)
        data class Item(
                val id: Long,
                val promoted: Long,
                val image: String,
                val thumb: String,
                val fullsize: String,
                val user: String,
                val up: Int,
                val down: Int,
                val mark: Int,
                val flags: Int,
                val width: Int = 0,
                val height: Int = 0,
                val created: Instant,
                val audio: Boolean = false,
                val deleted: Boolean = false)
    }


    @JsonClass(generateAdapter = true)
    data class Info(
            val user: User,
            val badges: List<Badge> = listOf(),
            val likeCount: Int,
            val uploadCount: Int,
            val commentCount: Int,
            val tagCount: Int,
            val likesArePublic: Boolean,
            val following: Boolean) {

        @JsonClass(generateAdapter = true)
        data class Badge(
                val created: Instant,
                val link: String,
                val image: String,
                val description: String?)

        @JsonClass(generateAdapter = true)
        data class User(
                val id: Int,
                val mark: Int,
                val score: Int,
                val name: String,
                val registered: Instant,
                val banned: Boolean = false,
                val bannedUntil: Instant?,
                val inactive: Boolean = false,
                @Json(name = "commentDelete") val commentDeleteCount: Int,
                @Json(name = "itemDelete") val itemDeleteCount: Int)
    }

    @JsonClass(generateAdapter = true)
    data class Invited(val error: String?)

    @JsonClass(generateAdapter = true)
    data class Login(
            val success: Boolean,
            val identifier: String?,
            @Json(name = "ban") val banInfo: BanInfo? = null) {

        @JsonClass(generateAdapter = true)
        data class BanInfo(
                val banned: Boolean,
                val reason: String,
                @Json(name = "till") val endTime: Instant?)
    }

    @JsonClass(generateAdapter = true)
    data class Message(
            val id: Long,
            val itemId: Long = 0,
            val mark: Int,
            val message: String,
            val name: String,
            val score: Int,
            val senderId: Int,
            val read: Boolean = true,
            @Json(name = "created") val creationTime: Instant,
            @Json(name = "thumb") val thumbnail: String?) {

        val isComment: Boolean get() = itemId != 0L

        val commentId: Long get() = id
    }

    @JsonClass(generateAdapter = true)
    data class MessageFeed(val messages: List<Message> = listOf())

    @JsonClass(generateAdapter = true)
    data class NewComment(
            val commentId: Long,
            val comments: List<Comment> = listOf())


    @JsonClass(generateAdapter = true)
    data class NewTag(
            val tagIds: List<Long> = listOf(),
            val tags: List<Tag> = listOf())

    @JsonClass(generateAdapter = true)
    data class Post(
            val tags: List<Tag> = listOf(),
            val comments: List<Comment> = listOf())

    @JsonClass(generateAdapter = true)
    data class QueueState(
            val position: Long,
            val item: Posted.PostedItem?,
            val status: String)

    @JsonClass(generateAdapter = true)
    data class Posted(
            val error: String?,
            val item: PostedItem?,
            val similar: List<SimilarItem> = listOf(),
            val report: VideoReport?,
            val queueId: Long?) {

        val itemId: Long = item?.id ?: -1

        @JsonClass(generateAdapter = true)
        data class PostedItem(val id: Long?)

        @JsonClass(generateAdapter = true)
        data class SimilarItem(
                val id: Long,
                val image: String,
                @Json(name = "thumb") val thumbnail: String)

        @JsonClass(generateAdapter = true)
        data class VideoReport(
                val duration: Float = 0f,
                val height: Int = 0,
                val width: Int = 0,
                val format: String?,
                val error: String?,
                val streams: List<MediaStream> = listOf())

        @JsonClass(generateAdapter = true)
        data class MediaStream(
                val codec: String?,
                val type: String)
    }

    @JsonClass(generateAdapter = true)
    data class Sync(
            val logLength: Long,
            val log: String,
            val score: Int,
            val inbox: InboxCounts = InboxCounts())

    @JsonClass(generateAdapter = true)
    data class InboxCounts(
            val comments: Int = 0,
            val mentions: Int = 0,
            val messages: Int = 0) {

        val total: Int get() = comments + mentions + messages
    }

    @JsonClass(generateAdapter = true)
    data class Tag(
            val id: Long,
            val confidence: Float,
            val tag: String) {

        override fun hashCode(): Int = tag.hashCode()

        override fun equals(other: Any?): Boolean = other is Tag && other.tag == tag
    }

    @JsonClass(generateAdapter = true)
    data class Upload(val key: String)

    @JsonClass(generateAdapter = true)
    data class UserComments(
            val user: UserInfo,
            val comments: List<UserComment> = listOf()) {

        @JsonClass(generateAdapter = true)
        data class UserComment(
                val id: Long,
                val itemId: Long,
                val created: Instant,
                val thumb: String,
                val up: Int,
                val down: Int,
                val content: String) {

            val score: Int get() = up - down
        }

        @JsonClass(generateAdapter = true)
        data class UserInfo(
                val id: Int,
                val mark: Int,
                val name: String)
    }

    @JsonClass(generateAdapter = true)
    data class FavedUserComments(
            val user: UserComments.UserInfo,
            val comments: List<FavedUserComment> = listOf())

    @JsonClass(generateAdapter = true)
    data class FavedUserComment(
            val id: Long,
            val itemId: Long,
            val created: Instant,
            val thumb: String,
            val name: String,
            val up: Int,
            val down: Int,
            val mark: Int,
            val content: String,
            @Json(name = "ccreated") val commentCreated: Instant)

    @JsonClass(generateAdapter = true)
    data class ResetPassword(val error: String?)

    @JsonClass(generateAdapter = true)
    data class TagDetails(
            val tags: List<TagInfo> = listOf()) {

        @JsonClass(generateAdapter = true)
        data class TagInfo(
                val id: Long,
                val up: Int,
                val down: Int,
                val confidence: Float,
                val tag: String,
                val user: String,
                val votes: List<Vote> = listOf())

        @JsonClass(generateAdapter = true)
        data class Vote(val vote: Int, val user: String)
    }

    @JsonClass(generateAdapter = true)
    data class HandoverToken(val token: String)

    @JsonClass(generateAdapter = true)
    data class Names(val users: List<String> = listOf())

    @JsonClass(generateAdapter = true)
    data class TagTopList(
            val tags: List<String> = listOf(),
            val blacklist: List<String> = listOf())

    @JsonClass(generateAdapter = true)
    data class Conversations(
            val conversations: List<Conversation>,
            val atEnd: Boolean)

    @JsonClass(generateAdapter = true)
    data class Conversation(
            val lastMessage: Instant,
            val mark: Int,
            val name: String,
            val unreadCount: Int)

    @JsonClass(generateAdapter = true)
    data class ConversationMessages(
            val atEnd: Boolean = true,
            val error: String? = null,
            val messages: List<ConversationMessage> = listOf())

    @JsonClass(generateAdapter = true)
    data class ConversationMessage(
            val id: Long,
            @Json(name = "created") val creationTime: Instant,
            val message: String,
            val sent: Boolean)

    @JsonClass(generateAdapter = true)
    data class Bookmarks(val bookmarks: List<Bookmark> = listOf(), val error: String? = null)

    @JsonClass(generateAdapter = true)
    data class Bookmark(
            val name: String,
            val link: String)
}
