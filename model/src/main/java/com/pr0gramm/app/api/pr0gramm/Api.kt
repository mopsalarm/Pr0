package com.pr0gramm.app.api.pr0gramm

import com.pr0gramm.app.Instant
import com.pr0gramm.app.model.config.Rule
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import java.util.*

interface Api {
    @GET("/api/items/get")
    suspend fun itemsGet(
        @Query("promoted") promoted: Int?,
        @Query("following") following: Int?,
        @Query("older") older: Long?,
        @Query("newer") newer: Long?,
        @Query("id") around: Long?,
        @Query("flags") flags: Int,
        @Query("tags") tags: String?,
        @Query("collection") collection: String?,
        @Query("self") self: Boolean?,
        @Query("user") user: String?
    ): Feed

    @FormUrlEncoded
    @POST("/api/items/vote")
    suspend fun vote(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") id: Long,
        @Field("vote") voteValue: Int
    )

    @FormUrlEncoded
    @POST("/api/tags/vote")
    suspend fun voteTag(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") id: Long,
        @Field("vote") voteValue: Int
    )

    @FormUrlEncoded
    @POST("/api/comments/vote")
    suspend fun voteComment(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") id: Long,
        @Field("vote") voteValue: Int
    )

    @FormUrlEncoded
    @POST("/api/comments/fav")
    suspend fun commentsFav(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") id: Long
    )

    @FormUrlEncoded
    @POST("/api/comments/unfav")
    suspend fun commentsUnfav(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") id: Long
    )

    @FormUrlEncoded
    @POST("/api/user/login")
    suspend fun login(
        @Field("name") username: String,
        @Field("password") password: String,
        @Field("token") token: String,
        @Field("captcha") answer: String
    ): Response<Login>

    @GET("/api/user/identifier")
    suspend fun identifier(): Identifier

    @FormUrlEncoded
    @POST("/api/tags/add")
    suspend fun addTags(
        @Field("_nonce") nonce: Nonce?,
        @Field("itemId") lastId: Long,
        @Field("tags") tags: String
    ): NewTag

    @GET("/api/tags/top")
    suspend fun topTags(): TagTopList

    @FormUrlEncoded
    @POST("/api/comments/post")
    suspend fun postComment(
        @Field("_nonce") nonce: Nonce?,
        @Field("itemId") itemId: Long,
        @Field("parentId") parentId: Long,
        @Field("comment") comment: String
    ): NewComment

    @FormUrlEncoded
    @POST("/api/comments/delete")
    suspend fun hardDeleteComment(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") commentId: Long,
        @Field("reason") reason: String
    )

    @FormUrlEncoded
    @POST("/api/comments/softDelete")
    suspend fun softDeleteComment(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") commentId: Long,
        @Field("reason") reason: String
    )

    @GET("/api/items/info")
    suspend fun info(
        @Query("itemId") itemId: Long,
        @Query("bust") bust: Long?
    ): Post

    @GET("/api/user/sync")
    suspend fun sync(
        @Query("offset") offset: Long
    ): Sync

    @FormUrlEncoded
    @POST("/api/user/sitesettings")
    suspend fun storeSettings(
        @Field("_nonce") nonce: Nonce?,
        @FieldMap settings: GenericSettings,
    )

    @GET("/api/user/info")
    suspend fun accountInfo(): AccountInfo

    @GET("/api/profile/info")
    suspend fun info(
        @Query("name") name: String,
        @Query("flags") flags: Int?
    ): Info

    @GET("/api/inbox/pending")
    suspend fun inboxPending(): Inbox

    @GET("/api/inbox/conversations")
    suspend fun listConversations(
        @Query("older") older: Long?
    ): Conversations

    @FormUrlEncoded
    @POST("/api/inbox/deleteConversation")
    suspend fun deleteConversation(
        @Field("_nonce") nonce: Nonce?,
        @Field("recipientName") name: String
    ): ConversationMessages

    @GET("/api/inbox/messages")
    suspend fun messagesWith(
        @Query("with") name: String,
        @Query("older") older: Long?
    ): ConversationMessages


    @GET("/api/inbox/comments")
    suspend fun inboxComments(
        @Query("older") older: Long?
    ): Inbox

    @GET("/api/inbox/all")
    suspend fun inboxAll(
        @Query("older") older: Long?
    ): Inbox

    @GET("/api/inbox/follows")
    suspend fun inboxFollows(
        @Query("older") older: Long?
    ): Inbox

    @GET("/api/inbox/notifications")
    suspend fun inboxNotifications(
        @Query("older")
        older: Long?
    ): Inbox

    @GET("/api/profile/comments")
    suspend fun userComments(
        @Query("name") user: String,
        @Query("before") before: Long?,
        @Query("flags") flags: Int?
    ): UserComments

    @GET("/api/profile/commentlikes")
    suspend fun userCommentsLike(
        @Query("name") user: String,
        @Query("before") before: Long,
        @Query("flags") flags: Int?
    ): FavedUserComments

    @FormUrlEncoded
    @POST("/api/inbox/post")
    suspend fun sendMessage(
        @Field("_nonce") nonce: Nonce?,
        @Field("comment") text: String,
        @Field("recipientId") recipient: Long
    )

    @FormUrlEncoded
    @POST("/api/inbox/post")
    suspend fun sendMessage(
        @Field("_nonce") nonce: Nonce?,
        @Field("comment") text: String,
        @Field("recipientName") recipient: String
    ): ConversationMessages

    @FormUrlEncoded
    @POST("/api/inbox/read")
    suspend fun markAsRead(
        @Field("_nonce") nonce: Nonce?,
        @Field("type") text: String,
        @Field("messageId") messageId: Long,
    )

    @GET("/api/items/ratelimited")
    suspend fun ratelimited()

    @POST("/api/items/upload")
    suspend fun upload(
        @Body body: RequestBody
    ): Upload

    @FormUrlEncoded
    @POST("/api/items/post")
    suspend fun post(
        @Field("_nonce") nonce: Nonce?,
        @Field("sfwstatus") sfwStatus: String,
        @Field("tags") tags: String,
        @Field("checkSimilar") checkSimilar: Int,
        @Field("key") key: String,
        @Field("processAsync") processAsync: Int?
    ): Posted

    @GET("/api/items/queue")
    suspend fun queue(
        @Query("id") id: Long?
    ): QueueState

    @FormUrlEncoded
    @POST("/api/user/invite")
    suspend fun invite(
        @Field("_nonce") nonce: Nonce?,
        @Field("email") email: String
    ): Invited

    // Extra stuff for admins
    @FormUrlEncoded
    @POST("api/items/delete")
    suspend fun deleteItem(
        @Field("_nonce") nonce: Nonce?,
        @Field("id") id: Long,
        @Field("reason") reason: String,
        @Field("customReason") customReason: String,
        @Field("banUser") banUser: String?,
        @Field("days") days: Float?
    )

    @FormUrlEncoded
    @POST("admin/?view=users&action=ban")
    suspend fun userBan(
        @Field("name") name: String,
        @Field("reason") reason: String,
        @Field("customReason") reasonCustom: String,
        @Field("days") days: Float,
        @Field("mode") mode: BanMode
    )

    @GET("api/tags/details")
    suspend fun tagDetails(
        @Query("itemId") itemId: Long
    ): TagDetails

    @FormUrlEncoded
    @POST("api/tags/delete")
    suspend fun deleteTag(
        @Field("_nonce") nonce: Nonce?,
        @Field("itemId") itemId: Long,
        @Field("banUsers") banUser: String?,
        @Field("days") days: Float?,
        @Field("tags[]") tagId: List<Long> = listOf()
    )

    @FormUrlEncoded
    @POST("api/profile/follow")
    suspend fun profileFollow(
        @Field("_nonce") nonce: Nonce?,
        @Field("name") username: String
    )

    @FormUrlEncoded
    @POST("api/profile/subscribe")
    suspend fun profileSubscribe(
        @Field("_nonce") nonce: Nonce?,
        @Field("name") username: String,
    )

    @FormUrlEncoded
    @POST("api/profile/unsubscribe")
    suspend fun profileUnsubscribe(
        @Field("_nonce") nonce: Nonce?,
        @Field("name") username: String,
        @Field("keepFollow") keepFollow: Boolean? = null,
    )

    @GET("api/profile/suggest")
    suspend fun suggestUsers(
        @Query("prefix") prefix: String
    ): Names

    @FormUrlEncoded
    @POST("api/contact/send")
    suspend fun contactSend(
        @Field("faqCategory") faqCategory: String,
        @Field("subject") subject: String,
        @Field("email") email: String,
        @Field("message") message: String,
        @Field("extraText") extraText: String?
    )

    @FormUrlEncoded
    @POST("api/contact/report")
    suspend fun report(
        @Field("_nonce") nonce: Nonce?,
        @Field("itemId") item: Long,
        @Field("commentId") commentId: Long,
        @Field("reason") reason: String
    )

    @FormUrlEncoded
    @POST("api/user/sendpasswordresetmail")
    suspend fun requestPasswordRecovery(
        @Field("email") email: String
    )

    @FormUrlEncoded
    @POST("api/user/resetpassword")
    suspend fun resetPassword(
        @Field("name") name: String,
        @Field("token") token: String,
        @Field("password") password: String
    ): ResetPassword

    @FormUrlEncoded
    @POST("api/user/handoverrequest")
    suspend fun handoverToken(
        @Field("_nonce") nonce: Nonce?
    ): HandoverToken

    @GET("api/bookmarks/get")
    suspend fun bookmarks(): Bookmarks

    @GET("api/bookmarks/get?default")
    suspend fun defaultBookmarks(): Bookmarks

    @FormUrlEncoded
    @POST("api/bookmarks/add")
    suspend fun bookmarksAdd(
        @Field("_nonce") nonce: Nonce?,
        @Field("name") name: String,
        @Field("link") link: String
    ): Bookmarks

    @FormUrlEncoded
    @POST("api/bookmarks/delete")
    suspend fun bookmarksDelete(
        @Field("_nonce") nonce: Nonce?,
        @Field("name") name: String
    ): Bookmarks

    @GET("media/app-config.json")
    suspend fun remoteConfig(@Query("bust") bust: Long): List<Rule>

    @GET("api/user/captcha")
    suspend fun userCaptcha(): UserCaptcha

    @GET("api/collections/get?thumbs=0")
    suspend fun collectionsGet(): Collections

    @FormUrlEncoded
    @POST("api/collections/create")
    suspend fun collectionsCreate(
        @Field("_nonce") nonce: Nonce?,
        @Field("name") name: String,
        @Field("isPublic") isPublic: Int,
        @Field("isDefault") isDefault: Int
    ): CollectionCreated

    @FormUrlEncoded
    @POST("api/collections/edit")
    suspend fun collectionsEdit(
        @Field("_nonce") nonce: Nonce?,
        @Field("collectionId") id: Long,
        @Field("name") name: String,
        @Field("isPublic") isPublic: Int,
        @Field("isDefault") isDefault: Int
    ): CollectionCreated

    @FormUrlEncoded
    @POST("api/collections/delete")
    suspend fun collectionsDelete(
        @Field("_nonce") nonce: Nonce?,
        @Field("collectionId") collectionId: Long
    ): CollectionCreated

    @FormUrlEncoded
    @POST("api/collections/add")
    suspend fun collectionsAdd(
        @Field("_nonce") nonce: Nonce?,
        @Field("collectionId") collectionId: Long?,
        @Field("itemId") itemId: Long
    ): AddToCollectionResponse

    @FormUrlEncoded
    @POST("api/collections/remove")
    suspend fun collectionsRemove(
        @Field("_nonce") nonce: Nonce?,
        @Field("collectionId") collectionId: Long,
        @Field("itemId") itemId: Long
    ): CollectionResponse

    @GET("api/user/me")
    suspend fun me(): MeInfo

    @GET("api/seen/bits")
    suspend fun seenBitsGet(): SeenBits

    @POST("api/seen/update")
    suspend fun seenBitsUpdate(
        @Header("X-pr0gramm-Nonce") nonce: Nonce?,
        @Header("X-pr0gramm-Bits-Version") version: Int,
        @Body body: RequestBody,
    ): UpdateSeenBitsResponse

    @FormUrlEncoded
    @POST("api/user/validate")
    suspend fun userValidate(
        @Field("token") uriToken: String,
    ): UserValidateResponse

    class Nonce(val value: String) {
        override fun toString(): String = value.take(16)
    }

    @JsonClass(generateAdapter = true)
    class Error(
        val error: String,
        val code: Int,
        val msg: String
    )

    @JsonClass(generateAdapter = true)
    class UserValidateResponse(
        val success: Boolean = false,
    )

    @JsonClass(generateAdapter = true)
    class CollectionResponse(
        val error: String? = null
    )

    @JsonClass(generateAdapter = true)
    class AddToCollectionResponse(
        val error: String? = null,
        val collectionId: Long = 0
    )

    @JsonClass(generateAdapter = true)
    class AccountInfo(
        val account: Account,
        val invited: List<Invite> = listOf()
    ) {

        @JsonClass(generateAdapter = true)
        class Account(
            val email: String,
            val invites: Int
        )

        @JsonClass(generateAdapter = true)
        class Invite(
            val email: String,
            val created: Instant,
            val name: String?,
            val mark: Int?
        )
    }

    @JsonClass(generateAdapter = true)
    data class Comment(
        val id: Long,
        val confidence: Float,
        val name: String,
        val content: String,
        val created: Instant,
        @Json(name = "parent")
        val parentId: Long,
        val up: Int,
        val down: Int,
        val mark: Int
    ) {

        val score: Int get() = up - down
    }

    @JsonClass(generateAdapter = true)
    data class Feed(
        val error: String? = null,
        @Json(name = "items") val _items: List<Item>? = null,
        @Json(name = "atStart") val isAtStart: Boolean = false,
        @Json(name = "atEnd") val isAtEnd: Boolean = false
    ) {

        @Transient
        val items = _items.orEmpty()

        @JsonClass(generateAdapter = true)
        class Item(
            val id: Long,
            val promoted: Long,
            val userId: Long,
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
            val deleted: Boolean = false,
            val subtitles: List<Subtitle> = listOf(),
            val variants: List<Variant> = listOf(),
        )

        @JsonClass(generateAdapter = true)
        class Variant(
            val name: String,
            val path: String,
        )

        @JsonClass(generateAdapter = true)
        class Subtitle(
            val language: String,
            val path: String,
        )
    }

    @JsonClass(generateAdapter = true)
    class Info(
        val user: User,
        val badges: List<Badge> = listOf(),
        val collectedCount: Int,
        override val collections: List<Collection> = listOf(),
        override val curatorCollections: List<Collection> = listOf(),
        val uploadCount: Int,
        val commentCount: Int,
        val tagCount: Int,
//        val likesArePublic: Boolean,
//        val following: Boolean,
        @Json(name = "pr0mium") val premiumTime: Int,
        @Json(name = "pr0miumGift") val premiumGift: Int,
        val appLinks: List<AppLink>? = null
    ) : HasCollections {

        @JsonClass(generateAdapter = true)
        class Badge(
            val created: Instant,
            val link: String,
            val image: String,
            val description: String?
        )

        @JsonClass(generateAdapter = true)
        class User(
            val id: Int,
            val mark: Int,
            val score: Int,
            val name: String,
            val registered: Instant,
            val banned: Boolean = false,
            val bannedUntil: Instant?,
            val inactive: Boolean = false,
            @Json(name = "commentDelete") val commentDeleteCount: Int,
            @Json(name = "itemDelete") val itemDeleteCount: Int,
            val canReceiveMessages: Boolean = true
        )

        @JsonClass(generateAdapter = true)
        class AppLink(
            val text: String,
            val icon: String? = null,
            val link: String? = null
        )
    }

    @JsonClass(generateAdapter = true)
    class Invited(val error: String?)

    @JsonClass(generateAdapter = true)
    class Identifier(val identifier: String)

    @JsonClass(generateAdapter = true)
    class Login(
        val success: Boolean? = null,
        val identifier: String? = null,
        val error: String? = null,
        @Json(name = "ban") val banInfo: BanInfo? = null,
        val settings: Map<String, Any?>? = null,
    ) {

        @JsonClass(generateAdapter = true)
        class BanInfo(
            val banned: Boolean,
            val reason: String,
            @Json(name = "till") val endTime: Instant?
        )
    }

    @JsonClass(generateAdapter = true)
    class Inbox(val messages: List<Item> = listOf()) {
        @JsonClass(generateAdapter = true)
        class Item(
            val type: String,
            val id: Long,
            val read: Boolean,
            @Json(name = "created") val creationTime: Instant,

            val name: String? = null,
            val mark: Int? = null,
            val senderId: Int? = null,
            val score: Int? = null,
            val itemId: Long? = null,
            val message: String? = null,
            val flags: Int? = null,
            val image: String? = null,
            @Json(name = "thumb") val thumbnail: String? = null
        )
    }

    @JsonClass(generateAdapter = true)
    class NewComment(
        val commentId: Long? = null,
        val comments: List<Comment> = listOf()
    )

    @JsonClass(generateAdapter = true)
    class NewTag(
        val tagIds: List<Long> = listOf(),
        val tags: List<Tag> = listOf()
    )

    @JsonClass(generateAdapter = true)
    class Post(
        val tags: List<Tag> = listOf(),
        val comments: List<Comment> = listOf()
    )

    @JsonClass(generateAdapter = true)
    class QueueState(
        val position: Long,
        val item: Posted.PostedItem?,
        val status: String
    )

    @JsonClass(generateAdapter = true)
    class Posted(
        val error: String?,
        val item: PostedItem?,
        val similar: List<SimilarItem> = listOf(),
        val report: VideoReport?,
        val queueId: Long?
    ) {

        val itemId: Long = item?.id ?: -1

        @JsonClass(generateAdapter = true)
        class PostedItem(val id: Long?)

        @JsonClass(generateAdapter = true)
        class SimilarItem(
            val id: Long,
            val image: String,
            @Json(name = "thumb") val thumbnail: String
        )

        @JsonClass(generateAdapter = true)
        class VideoReport(
            val duration: Float = 0f,
            val height: Int = 0,
            val width: Int = 0,
            val format: String?,
            val error: String?,
            val streams: List<MediaStream> = listOf()
        )

        @JsonClass(generateAdapter = true)
        class MediaStream(
            val codec: String?,
            val type: String
        )
    }

    @JsonClass(generateAdapter = true)
    class Sync(
        val logLength: Long,
        val log: String,
        val score: Int,
        val inbox: InboxCounts = InboxCounts(),
        val settings: Map<String, Any?>? = null,
    )

    @JsonClass(generateAdapter = true)
    class InboxCounts(
        val comments: Int = 0,
        val mentions: Int = 0,
        val notifications: Int = 0,
        val follows: Int = 0,
        val messages: Int = 0
    ) {
        val total: Int get() = comments + mentions + messages + notifications + follows
    }

    @JsonClass(generateAdapter = true)
    class Tag(
        val id: Long,
        val confidence: Float,

        @Json(name = "tag")
        val text: String
    ) {
        override fun hashCode(): Int = text.hashCode()
        override fun equals(other: Any?): Boolean = other is Tag && other.text == text
    }

    @JsonClass(generateAdapter = true)
    class Upload(val key: String)

    @JsonClass(generateAdapter = true)
    class UserComments(
        val user: UserInfo,
        val comments: List<UserComment> = listOf()
    ) {

        @JsonClass(generateAdapter = true)
        class UserComment(
            val id: Long,
            val itemId: Long,
            val created: Instant,
            val thumb: String,
            val up: Int,
            val down: Int,
            val content: String
        ) {

            val score: Int get() = up - down
        }

        @JsonClass(generateAdapter = true)
        class UserInfo(
            val id: Int,
            val mark: Int,
            val name: String
        )
    }

    @JsonClass(generateAdapter = true)
    class FavedUserComments(
        val user: UserComments.UserInfo,
        val comments: List<FavedUserComment> = listOf()
    )

    @JsonClass(generateAdapter = true)
    class FavedUserComment(
        val id: Long,
        val itemId: Long,
        val created: Instant,
        val thumb: String,
        val name: String,
        val up: Int,
        val down: Int,
        val mark: Int,
        val content: String,
        // @Json(name = "ccreated") val commentCreated: Instant
    )

    @JsonClass(generateAdapter = true)
    class ResetPassword(val error: String?)

    @JsonClass(generateAdapter = true)
    class TagDetails(
        val tags: List<TagInfo> = listOf()
    ) {

        @JsonClass(generateAdapter = true)
        class TagInfo(
            val id: Long,
            val up: Int,
            val down: Int,
            val confidence: Float,
            val tag: String,
            val user: String,
            val votes: List<Vote> = listOf()
        )

        @JsonClass(generateAdapter = true)
        class Vote(val vote: Int, val user: String)
    }

    @JsonClass(generateAdapter = true)
    class HandoverToken(val token: String)

    @JsonClass(generateAdapter = true)
    class Names(val users: List<String> = listOf())

    @JsonClass(generateAdapter = true)
    class TagTopList(
        val tags: List<String> = listOf(),
        val blacklist: List<String> = listOf()
    )

    @JsonClass(generateAdapter = true)
    class Conversations(
        val conversations: List<Conversation>,
        val atEnd: Boolean
    )

    @JsonClass(generateAdapter = true)
    data class Conversation(
        val lastMessage: Instant,
        val mark: Int,
        val name: String,
        val unreadCount: Int,
        val canReceiveMessages: Boolean = true
    )

    @JsonClass(generateAdapter = true)
    class ConversationMessages(
        val atEnd: Boolean = true,
        val error: String? = null,
        val messages: List<ConversationMessage> = listOf(),
        // Only available in GET /api/inbox/messages
        val with: ConversationMessagePartner?
    ) {
        @JsonClass(generateAdapter = true)
        class ConversationMessagePartner(
            val name: String,
            val blocked: Boolean = false,
            val canReceiveMessages: Boolean = true,
            val mark: Int
        )
    }

    @JsonClass(generateAdapter = true)
    class ConversationMessage(
        val id: Long,
        @Json(name = "created") val creationTime: Instant,
        @Json(name = "message") val messageText: String,
        val sent: Boolean
    )

    @JsonClass(generateAdapter = true)
    class Bookmarks(
        val bookmarks: List<Bookmark> = listOf(),
        val trending: List<Bookmark> = listOf(),
        val error: String? = null
    )

    @JsonClass(generateAdapter = true)
    class Bookmark(
        val name: String,
        val link: String,
        val velocity: Float = 0.0f
    )

    @JsonClass(generateAdapter = true)
    class UserCaptcha(
        val token: String,
        @Json(name = "captcha") val image: String
    )

    interface HasCollections {
        val collections: List<Collection>
        val curatorCollections: List<Collection>

        val allCollections: List<Collection>
            get() = collections + curatorCollections
    }

    @JsonClass(generateAdapter = true)
    class Collections(
        override val collections: List<Collection>,
        override val curatorCollections: List<Collection>,
    ) : HasCollections

    @JsonClass(generateAdapter = true)
    class CollectionCreated(
        val collectionId: Long = 0,
        override val collections: List<Collection> = listOf(),
        override val curatorCollections: List<Collection> = listOf(),
        override val error: String? = null
    ) : HasError, HasCollections

    @JsonClass(generateAdapter = true)
    class Collection(
        val id: Long,
        val name: String,
        val keyword: String,
        val isPublic: Boolean,
        val isDefault: Boolean,
        val owner: String?,
        val ownerMark: Int?,
        // val items: List<Item>,
    ) {
        @JsonClass(generateAdapter = true)
        class Item(
            val id: Long,
            val thumb: String
        )
    }

    @JsonClass(generateAdapter = true)
    data class MeInfo (
        val name: String,
        val registered: Instant,
        val identifier: String,
        val mark: Int,
        val score: Int,
        // "banInfo" omitted because it is irrelevant to the app
    )


    @JsonClass(generateAdapter = true)
    data class SeenBits (
        /** Same user ID as returned by /api/user/me. */
        val userIdentifier: String,
        /** Server sends the base64 encoded value of compressed seen bits. Automatically decoded by Retrofit. */
        val value: ByteArray,
        /** Current version of seen bits. */
        val version: Int,
    )

    /**
     * @property version
     */
    @JsonClass(generateAdapter = true)
    data class UpdateSeenBitsResponse (
        val success: Boolean,
        /** New version of seen bits. */
        val version: Int,
    )

    enum class BanMode {
        Default, Single, Branch;

        override fun toString(): String = name.lowercase(Locale.ROOT)
    }

    interface HasError {
        val error: String?
    }
}

// To work around some quirks in moshis serialization we use java.util.Map here.
typealias GenericSettings = java.util.Map<String, Object?>
