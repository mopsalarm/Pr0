package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import com.pr0gramm.app.services.config.ConfigEvaluator
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Deferred
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*
import rx.Observable

@Suppress("MemberVisibilityCanBePrivate")
interface Api {
    @GET("/api/items/get")
    fun itemsGet(
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
    fun vote(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/tags/vote")
    fun voteTag(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/comments/vote")
    fun voteComment(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") id: Long,
            @Field("vote") voteValue: Int): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/user/login")
    fun login(
            @Field("name") username: String,
            @Field("password") password: String): Observable<Login>

    @FormUrlEncoded
    @POST("/api/tags/add")
    fun addTags(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") lastId: Long,
            @Field("tags") tags: String): Deferred<NewTag>

    @GET("/api/tags/top")
    fun topTags(): Deferred<TagTopList>

    @FormUrlEncoded
    @POST("/api/comments/post")
    fun postComment(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") itemId: Long,
            @Field("parentId") parentId: Long,
            @Field("comment") comment: String): Deferred<NewComment>

    @FormUrlEncoded
    @POST("/api/comments/delete")
    fun hardDeleteComment(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") commentId: Long,
            @Field("reason") reason: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("/api/comments/softDelete")
    fun softDeleteComment(
            @Field("_nonce") nonce: Nonce?,
            @Field("id") commentId: Long,
            @Field("reason") reason: String): Deferred<Unit>

    @GET("/api/items/info")
    fun info(
            @Query("itemId") itemId: Long,
            @Query("bust") bust: Long?): Deferred<Post>

    @GET("/api/user/sync")
    fun sync(
            @Query("offset") offset: Long): Deferred<Sync>

    @GET("/api/user/info")
    fun accountInfo(): Deferred<AccountInfo>

    @GET("/api/profile/info")
    fun info(
            @Query("name") name: String,
            @Query("flags") flags: Int?): Observable<Info>

    @GET("/api/inbox/all")
    fun inboxAll(): Observable<MessageFeed>

    @GET("/api/inbox/unread")
    fun inboxUnread(): Observable<MessageFeed>

    @GET("/api/inbox/messages")
    fun inboxPrivateMessages(): Observable<PrivateMessageFeed>

    @GET("/api/profile/comments")
    fun userComments(
            @Query("name") user: String,
            @Query("before") before: Long,
            @Query("flags") flags: Int?): Observable<UserComments>

    @GET("/api/profile/commentlikes")
    fun userCommentsLike(
            @Query("name") user: String,
            @Query("before") before: Long,
            @Query("flags") flags: Int?): Observable<FavedUserComments>

    @FormUrlEncoded
    @POST("/api/inbox/post")
    fun sendMessage(
            @Field("_nonce") nonce: Nonce?,
            @Field("comment") text: String,
            @Field("recipientId") recipient: Long): Deferred<Unit>

    @GET("/api/items/ratelimited")
    fun ratelimited(): Deferred<Unit>

    @POST("/api/items/upload")
    fun upload(
            @Body body: RequestBody): Observable<Upload>

    @FormUrlEncoded
    @POST("/api/items/post")
    fun post(
            @Field("_nonce") nonce: Nonce?,
            @Field("sfwstatus") sfwStatus: String,
            @Field("tags") tags: String,
            @Field("checkSimilar") checkSimilar: Int,
            @Field("key") key: String,
            @Field("processAsync") processAsync: Int?): Observable<Posted>

    @GET("/api/items/queue")
    fun queue(
            @Query("id") id: Long?): Deferred<QueueState>

    @FormUrlEncoded
    @POST("/api/user/invite")
    fun invite(
            @Field("_nonce") nonce: Nonce?,
            @Field("email") email: String): Deferred<Invited>

    // Extra stuff for admins
    @FormUrlEncoded
    @POST("api/items/delete")
    fun deleteItem(
            @Field("_nonce") none: Nonce?,
            @Field("id") id: Long,
            @Field("reason") reason: String,
            @Field("customReason") customReason: String,
            @Field("banUser") banUser: String?,
            @Field("days") days: Float?): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/user/ban")
    fun userBan(
            @Field("_nonce") none: Nonce?,
            @Field("name") name: String,
            @Field("reason") reason: String,
            @Field("customReason") customReason: String,
            @Field("days") days: Float,
            @Field("mode") mode: Int?): Deferred<Unit>

    @GET("api/tags/details")
    fun tagDetails(
            @Query("itemId") itemId: Long): Deferred<TagDetails>

    @FormUrlEncoded
    @POST("api/tags/delete")
    fun deleteTag(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") itemId: Long,
            @Field("banUsers") banUser: String?,
            @Field("days") days: Float?,
            @Field("tags[]") tagId: List<Long> = listOf()): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/profile/follow")
    fun profileFollow(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") username: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/profile/unfollow")
    fun profileUnfollow(
            @Field("_nonce") nonce: Nonce?,
            @Field("name") username: String): Deferred<Unit>

    @GET("api/profile/suggest")
    fun suggestUsers(
            @Query("prefix") prefix: String): Call<Names>

    @GET("api/user/identifier")
    fun identifier(): Deferred<UserIdentifier>

    @FormUrlEncoded
    @POST("api/contact/send")
    fun contactSend(
            @Field("subject") subject: String,
            @Field("email") email: String,
            @Field("message") message: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/contact/report")
    fun report(
            @Field("_nonce") nonce: Nonce?,
            @Field("itemId") item: Long,
            @Field("commentId") commentId: Long,
            @Field("reason") reason: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/user/sendpasswordresetmail")
    fun requestPasswordRecovery(
            @Field("email") email: String): Deferred<Unit>

    @FormUrlEncoded
    @POST("api/user/resetpassword")
    fun resetPassword(
            @Field("name") name: String,
            @Field("token") token: String,
            @Field("password") password: String): Deferred<ResetPassword>

    @FormUrlEncoded
    @POST("api/user/handoverrequest")
    fun handoverToken(
            @Field("_nonce") nonce: Nonce?): Deferred<HandoverToken>

    @GET("media/app-config.json")
    fun remoteConfig(@Query("bust") bust: Long): Deferred<List<ConfigEvaluator.Rule>>

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
            val error: String?,
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
            @Json(name = "ban") val banInfo: BanInfo?) {

        @JsonClass(generateAdapter = true)
        data class BanInfo(
                val banned: Boolean,
                val reason: String,
                @Json(name = "till") val endTime: Instant?)
    }

    @JsonClass(generateAdapter = true)
    data class Message(
            override val id: Long,
            val itemId: Long = 0,
            val mark: Int,
            val message: String,
            val name: String,
            val score: Int,
            val senderId: Int,
            @Json(name = "created") val creationTime: Instant,
            @Json(name = "thumb") override val thumbnail: String?) : HasThumbnail {

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
                override val id: Long,
                val image: String,
                @Json(name = "thumb") override val thumbnail: String) : HasThumbnail

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
    data class PrivateMessage(
            val id: Long,
            val created: Instant,
            val recipientId: Int,
            val recipientMark: Int,
            val recipientName: String,
            val senderId: Int,
            val senderMark: Int,
            val senderName: String,
            val message: String,
            @Json(name = "sent") val isSent: Boolean)

    @JsonClass(generateAdapter = true)
    data class PrivateMessageFeed(val messages: List<PrivateMessage> = listOf())

    @JsonClass(generateAdapter = true)
    data class Sync(
            val logLength: Long,
            val log: String,
            val score: Int,
            val inboxCount: Int)

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
    data class UserIdentifier(val identifier: String)

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
}


