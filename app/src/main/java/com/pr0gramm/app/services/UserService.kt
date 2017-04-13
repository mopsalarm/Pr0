package com.pr0gramm.app.services

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import com.google.common.base.Optional
import com.google.common.base.Stopwatch
import com.google.gson.*
import com.pr0gramm.app.Settings
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.checkNotMainThread
import com.pr0gramm.app.util.AndroidUtility.doInBackground
import org.joda.time.Duration.standardDays
import org.joda.time.Instant
import org.slf4j.LoggerFactory
import rx.Completable
import rx.Observable
import rx.Single
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 */
@Singleton
@org.immutables.gson.Gson.TypeAdapters
class UserService @Inject constructor(private val api: Api,
                                      private val voteService: VoteService,
                                      private val seenService: SeenService,
                                      private val inboxService: InboxService,
                                      private val cookieHandler: LoginCookieHandler,
                                      private val preferences: SharedPreferences,
                                      private val settings: Settings,
                                      private val gson: Gson,
                                      private val database: Holder<SQLiteDatabase>) {

    private val lock = Any()

    private val fullSyncInProgress = AtomicBoolean()

    // login state and observable for that.
    private var loginState: LoginState = NOT_AUTHORIZED
    private val loginStateObservable = BehaviorSubject.create(loginState).toSerialized()

    init {

        // only restore user data if authorized.
        if (cookieHandler.hasCookie()) {
            restoreLatestUserInfo()
        }

        this.cookieHandler.onCookieChanged = { this.onCookieChanged() }

        loginStateObservable.subscribe { this.persistLatestLoginState(it) }

        // this is not nice, and will get removed in one or two versions!
        // TODO REMOVE THIS ASAP.
        loginStateObservable
                .filter { state -> state.authorized && state.uniqueToken == null }
                .switchMap { api.identifier().subscribeOnBackground() }
                .map { it.identifier() }
                .onErrorResumeEmpty()
                .subscribe { this.updateUniqueToken(it) }

        loginStateObservable.subscribe { state -> Track.updateUserState(state) }
    }

    private fun updateLoginState(transformer: (LoginState) -> LoginState): LoginState {
        synchronized(lock) {
            val newLoginState = transformer(this.loginState)

            // persist and publish
            if (newLoginState !== loginState) {
                this.loginState = newLoginState
                this.loginStateObservable.onNext(newLoginState)
            }

            return newLoginState
        }
    }

    private fun updateLoginStateIfAuthorized(transformer: (LoginState) -> LoginState): LoginState {
        return updateLoginState { loginState ->
            if (loginState.authorized) {
                transformer(loginState)
            } else {
                // if we are not authorized, we always fall back to the not-authorized token.
                NOT_AUTHORIZED
            }
        }
    }

    private fun updateUniqueToken(uniqueToken: String?) {
        if (uniqueToken == null)
            return

        updateLoginStateIfAuthorized { loginState ->
            loginState.copy(uniqueToken = uniqueToken)
        }
    }

    /**
     * Restore the latest user info from the shared preferences
     */
    private fun restoreLatestUserInfo() {
        preferences.getString(KEY_LAST_LOGIN_STATE, null)?.let { lastLoginState ->
            Observable.fromCallable { gson.fromJson(lastLoginState, LoginState::class.java) }
                    .onErrorResumeEmpty()
                    .doOnNext { info -> logger.info("Restoring login state: {}", info) }

                    // update once now, and one with recent benis history later.
                    .flatMap { state ->
                        Observable.just(state)
                                .concatWith(Observable
                                        .fromCallable { state.copy(benisHistory = loadBenisHistory(state.id)) }
                                        .subscribeOnBackground())
                    }

                    .subscribe(
                            { loginState -> updateLoginState({ loginState }) },
                            { error -> logger.warn("Could not restore login state: " + error) })

        }
    }

    private fun onCookieChanged() {
        val cookie = cookieHandler.loginCookieValue
        if (!cookie.isPresent) {
            logout()
        }
    }

    fun login(username: String, password: String): Observable<LoginProgress> {
        return api.login(username, password).flatMap { login ->
            val observables = ArrayList<Observable<*>>()

            if (login.success()) {
                observables.add(updateCachedUserInfo()
                        .doOnTerminate { updateUniqueToken(login.identifier().orNull()) }
                        .toObservable<Any>())

                // perform initial sync in background.
                sync().subscribeOnBackground().subscribe({},
                        { err -> logger.error("Could not perform initial sync during login", err) })
            }

            // wait for sync to complete before emitting login result.
            observables.add(Observable.just(LoginProgress(login)))
            Observable.concatDelayError(observables).ofType(LoginProgress::class.java)
        }
    }

    /**
     * Check if we can do authorized requests.
     */
    val isAuthorized: Boolean
        get() = cookieHandler.hasCookie() && loginState.authorized

    /**
     * Checks if the user has paid for a pr0mium account
     */
    val isPremiumUser: Boolean
        get() = cookieHandler.isPaid

    /**
     * Performs a logout of the user.
     */
    fun logout(): Completable {
        return doInBackground {
            updateLoginState { NOT_AUTHORIZED }

            // removing cookie from requests
            cookieHandler.clearLoginCookie(false)

            // remove sync id
            preferences.edit() {
                remove(KEY_LAST_LOF_OFFSET)
                remove(KEY_LAST_USER_INFO)
                remove(KEY_LAST_LOGIN_STATE)
            }

            // clear all the vote cache
            voteService.clear()

            // clear the seen items
            seenService.clear()

            // no more read messages.
            inboxService.forgetReadMessage()
            inboxService.publishUnreadMessagesCount(0)

            // and reset the content user, because only signed in users can
            // see the nsfw and nsfl stuff.
            settings.resetContentTypeSettings()
        }
    }

    fun loginState(): Observable<LoginState> {
        return loginStateObservable.asObservable()
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     */
    fun sync(): Observable<Api.Sync> {
        if (!cookieHandler.hasCookie())
            return Observable.empty()

        // tell the sync request where to start
        val lastLogOffset = preferences.getLong(KEY_LAST_LOF_OFFSET, 0L)
        val fullSync = (lastLogOffset == 0L)

        if (fullSync && !fullSyncInProgress.compareAndSet(false, true)) {
            // fail fast if full sync is in already in progress.
            return Observable.empty()
        }

        return api.sync(lastLogOffset).doAfterTerminate { fullSyncInProgress.set(false) }.flatMap { response ->
            inboxService.publishUnreadMessagesCount(response.inboxCount())

            val userId = loginState.id
            if (userId > 0) {
                // save the current benis value
                BenisRecord.storeValue(database.value(), userId, response.score())

                // and load the current benis history
                val scoreGraph = loadBenisHistory(userId)

                updateLoginStateIfAuthorized { loginState ->
                    loginState.copy(score = response.score(), benisHistory = scoreGraph)
                }
            }

            try {
                voteService.applyVoteActions(response.log())

                // store syncId for next time.
                if (response.logLength() > lastLogOffset) {
                    preferences.edit() {
                        putLong(KEY_LAST_LOF_OFFSET, response.logLength())
                    }
                }
            } catch (error: Throwable) {
                return@flatMap Observable.error<Api.Sync>(error)
            }

            return@flatMap Observable.just(response)
        }
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    fun info(username: String): Observable<Api.Info> {
        return api.info(username, null)
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    fun info(username: String, contentTypes: Set<ContentType>): Observable<Api.Info> {
        return api.info(username, ContentType.combine(contentTypes))
    }

    /**
     * Returns information for the current user, if a user is signed in.

     * @return The info, if the user is currently signed in.
     */
    fun info(): Observable<Api.Info> {
        return Observable.from(name.asSet()).flatMap { info(it) }
    }

    /**
     * Update the cached user info in the background.
     */
    fun updateCachedUserInfo(): Completable {
        val publishSubject = PublishSubject.create<Any>()

        info().retry(3)
                .subscribeOnBackground()
                .doOnNext { info ->
                    val loginState = createLoginState(info)
                    updateLoginState { loginState }
                }
                .doOnTerminate({ publishSubject.onCompleted() })
                .subscribe({}, { error -> logger.warn("Could not update user info.", error) })

        return publishSubject.toCompletable()
    }

    /**
     * Persists the given login state to a preference storage.
     */
    private fun persistLatestLoginState(state: LoginState) {
        try {
            if (state.authorized) {
                logger.info("persisting logins state now.")

                val encoded = gson.toJson(state)
                preferences.edit() {
                    putString(KEY_LAST_LOGIN_STATE, encoded)
                }
            } else {
                preferences.edit() {
                    remove(KEY_LAST_LOGIN_STATE)
                }
            }

        } catch (error: RuntimeException) {
            logger.warn("Could not persist latest user info", error)
        }

    }

    private fun createLoginState(info: Api.Info): LoginState {
        checkNotMainThread()

        val user = info.user
        return LoginState(
                id = user.id,
                name = user.name,
                mark = user.mark,
                score = user.score,
                authorized = true,
                premium = isPremiumUser,
                admin = userIsAdmin,
                benisHistory = loadBenisHistory(user.id),
                uniqueToken = null)
    }

    val userIsAdmin: Boolean
        get() = cookieHandler.cookie.map { it.admin }.or(false)


    private fun loadBenisHistory(userId: Int): Graph {
        val watch = Stopwatch.createStarted()

        val historyLength = standardDays(7)
        val now = Instant.now()
        val start = now.minus(historyLength)

        // get the values and transform them
        val points = BenisRecord.findValuesLaterThan(database.value(), userId, start).map { record ->
            val x = record.time.toDouble()
            Graph.Point(x, record.benis.toDouble())
        }

        logger.info("Loading benis graph took " + watch)
        return Graph(start.millis.toDouble(), now.millis.toDouble(), points)
    }

    /**
     * Gets the name of the current user from the cookie. This will only
     * work, if the user is authorized.

     * @return The name of the currently signed in user.
     */
    val name: Optional<String>
        get() = cookieHandler.cookie.map { cookie -> cookie.name }


    /**
     * Returns an observable that produces the unique user token, if a hash is currently
     * available. This produces "null", if the user is currently not signed in.
     */
    fun userToken(): Observable<String> {
        return loginStateObservable.take(1).map { value ->
            if (value.authorized) value.uniqueToken else null
        }
    }

    fun requestPasswordRecovery(email: String): Completable {
        return api.requestPasswordRecovery(email).toCompletable()
    }

    fun resetPassword(name: String, token: String, password: String): Single<Boolean> {
        return api.resetPassword(name, token, password)
                .doOnNext { value -> logger.info("Response is {}", value) }
                .map { response -> response.error() == null }
                .toSingle()
    }

    data class LoginState(
            val id: Int,
            val name: String?,
            val mark: Int,
            val score: Int,
            val uniqueToken: String?,
            val admin: Boolean,
            val premium: Boolean,
            val authorized: Boolean,
            val benisHistory: Graph? = null)

    class LoginStateAdapter : JsonDeserializer<LoginState>, JsonSerializer<LoginState> {
        override fun deserialize(value: JsonElement, type: Type?, ctx: JsonDeserializationContext?): LoginState {
            if (value is JsonObject) {
                return LoginState(
                        id = value.getIfPrimitive("id")!!.asInt,
                        name = value.getIfPrimitive("name")?.asString,
                        mark = value.getIfPrimitive("mark")!!.asInt,
                        score = value.getIfPrimitive("score")!!.asInt,
                        uniqueToken = value.getIfPrimitive("uniqueToken")?.asString,
                        admin = value.getIfPrimitive("admin")!!.asBoolean,
                        premium = value.getIfPrimitive("premium")!!.asBoolean,
                        authorized = value.getIfPrimitive("authorized")!!.asBoolean)

            } else {
                return NOT_AUTHORIZED
            }
        }

        override fun serialize(value: LoginState, type: Type?, ctx: JsonSerializationContext?): JsonElement {
            val obj = JsonObject()
            obj.addProperty("id", value.id)
            obj.addProperty("name", value.name)
            obj.addProperty("mark", value.mark)
            obj.addProperty("score", value.score)
            obj.addProperty("uniqueToken", value.uniqueToken)
            obj.addProperty("admin", value.admin)
            obj.addProperty("premium", value.premium)
            obj.addProperty("authorized", value.authorized)
            return obj
        }
    }


    class LoginProgress internal constructor(private val login: Api.Login?) {
        fun getLogin(): Optional<Api.Login> {
            return Optional.fromNullable(login)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger("UserService")

        private val KEY_LAST_LOF_OFFSET = "UserService.lastLogLength"
        private val KEY_LAST_USER_INFO = "UserService.lastUserInfo"
        private val KEY_LAST_LOGIN_STATE = "UserService.lastLoginState"

        private val NOT_AUTHORIZED = LoginState(
                id = -1, score = 0, mark = 0, admin = false,
                premium = false, authorized = false, name = null, uniqueToken = null)
    }
}
