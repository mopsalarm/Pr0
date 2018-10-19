package com.pr0gramm.app.services

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.edit
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.LoginCookieHandler
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.dialogs.ignoreError
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.AndroidUtility.checkNotMainThread
import com.squareup.moshi.JsonClass
import rx.Completable
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


/**
 */

class UserService(private val api: Api,
                  private val voteService: VoteService,
                  private val seenService: SeenService,
                  private val inboxService: InboxService,
                  private val cookieHandler: LoginCookieHandler,
                  private val preferences: SharedPreferences,
                  private val database: Holder<SQLiteDatabase>,
                  private val config: Config) {

    private val logger = logger("UserService")

    private val lock = Any()

    private val fullSyncInProgress = AtomicBoolean()

    private val loginStateSubject = BehaviorSubject.create(NOT_AUTHORIZED)

    val loginState: LoginState get() = loginStateSubject.value
    val loginStates: Observable<LoginState> = loginStateSubject

    init {
        // only restore user data if authorized.
        if (cookieHandler.hasCookie()) {
            restoreLatestUserInfo()
        }

        this.cookieHandler.onCookieChanged = { this.onCookieChanged() }

        loginStateSubject.subscribe { state -> persistLatestLoginState(state) }
        Track.updateUserState(loginStateSubject)
    }

    private fun updateLoginState(transformer: (LoginState) -> LoginState): LoginState {
        synchronized(lock) {
            val newLoginState = transformer(this.loginState)

            // persist and publish
            if (newLoginState != loginState) {
                this.loginStateSubject.onNext(newLoginState)
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

    private fun updateUniqueTokenIfNeeded(state: LoginState) {
        if (state.authorized && state.uniqueToken == null) {
            api.identifier().ignoreError().subscribeOnBackground().subscribe { result ->
                updateUniqueToken(result.identifier)
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
            debug {
                logger.info("Found login state: {}", lastLoginState)
            }

            Observable.fromCallable { MoshiInstance.adapter<LoginState>().fromJson(lastLoginState) }
                    .ofType<LoginState>()
                    .onErrorResumeEmpty()
                    .doOnNext { info -> logger.info("Restoring login state: {}", info) }

                    // update once now, and one with recent benis history later.
                    .flatMap { state ->
                        val extendedState = Observable
                                .fromCallable { state.copy(benisHistory = loadBenisHistoryAsGraph(state.id)) }
                                .subscribeOnBackground()

                        Observable.just(state).concatWith(extendedState)
                    }

                    .doOnNext { state -> updateUniqueTokenIfNeeded(state) }

                    .subscribe(
                            { loginState -> updateLoginState({ loginState }) },
                            { error -> logger.warn("Could not restore login state:", error) })

        }
    }

    private fun onCookieChanged() {
        if (cookieHandler.loginCookieValue == null) {
            logout()
        }
    }

    fun login(username: String, password: String): Observable<LoginProgress> {
        return api.login(username, password).flatMap { login ->
            val observables = ArrayList<Observable<*>>()

            if (login.success) {
                observables.add(updateCachedUserInfo()
                        .doOnTerminate { updateUniqueToken(login.identifier) })

                // perform initial sync in background.
                sync().subscribeOnBackground()
                        .ignoreError("Could not perform initial sync during login")
                        .subscribe()
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
            preferences.edit {
                preferences.all.keys
                        .filter { it.startsWith(KEY_LAST_LOF_OFFSET) }
                        .forEach { remove(it) }

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
            Settings.get().resetContentTypeSettings()
        }
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     */
    fun sync(): Observable<Api.Sync> {
        if (!cookieHandler.hasCookie())
            return Observable.empty()

        // tell the sync request where to start
        val lastLogOffset = preferences.getLong(syncOffsetKey(), 0L)
        val fullSync = (lastLogOffset == 0L)

        if (fullSync && !fullSyncInProgress.compareAndSet(false, true)) {
            // fail fast if full sync is in already in progress.
            return Observable.empty()
        }

        return api.sync(lastLogOffset).doAfterTerminate { fullSyncInProgress.set(false) }.flatMap { response ->
            inboxService.publishUnreadMessagesCount(response.inboxCount)

            val userId = loginState.id
            if (userId > 0) {
                // save the current benis value
                BenisRecord.storeValue(database.value, userId, response.score)

                // and load the current benis history
                val scoreGraph = loadBenisHistoryAsGraph(userId)

                updateLoginStateIfAuthorized { loginState ->
                    loginState.copy(score = response.score, benisHistory = scoreGraph)
                }
            }

            try {
                voteService.applyVoteActions(response.log)

                // store syncId for next time.
                if (response.logLength > lastLogOffset) {
                    preferences.edit {
                        putLong(syncOffsetKey(), response.logLength)
                    }
                }
            } catch (error: Throwable) {
                return@flatMap Observable.error<Api.Sync>(error)
            }

            return@flatMap Observable.just(response)
        }
    }

    private fun syncOffsetKey() = KEY_LAST_LOF_OFFSET + ":v1:" + config.syncVersion

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
        return name.justObservable().flatMap { info(it) }
    }

    /**
     * Update the cached user info in the background.
     */
    fun updateCachedUserInfo(): Observable<Api.Info> {
        return info().retry(3).ignoreError().doOnNext { info ->
            val loginState = createLoginStateFromInfo(info)
            updateLoginState { loginState }
        }
    }

    /**
     * Persists the given login state to a preference storage.
     */
    private fun persistLatestLoginState(state: LoginState) {
        try {
            if (state.authorized) {
                logger.debug("Persisting login state now.")

                preferences.edit {
                    val encoded = MoshiInstance.adapter<LoginState>().toJson(state)
                    putString(KEY_LAST_LOGIN_STATE, encoded)
                }
            } else {
                preferences.edit {
                    remove(KEY_LAST_LOGIN_STATE)
                }
            }

        } catch (error: RuntimeException) {
            logger.warn("Could not persist latest user info", error)
        }

    }

    private fun createLoginStateFromInfo(info: Api.Info): LoginState {
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
                benisHistory = loadBenisHistoryAsGraph(user.id),
                uniqueToken = loginState.uniqueToken)
    }

    val userIsAdmin: Boolean
        get() = cookieHandler.cookie?.admin ?: false


    private fun loadBenisHistoryAsGraph(userId: Int): Graph {
        val watch = Stopwatch.createStarted()

        val now = Instant.now()
        val start = now - Duration.days(7)

        // get the values and transform them
        val points = BenisRecord.findValuesLaterThan(database.value, userId, start).map { record ->
            val x = record.time.toDouble()
            val y = record.benis.toDouble()
            Graph.Point(x, y)
        }

        logger.info("Loading benis graph took $watch")
        return Graph(start.millis.toDouble(), now.millis.toDouble(), optimizeValuesBy(points) { it.y })
    }

    /**
     * Loads all benis records for the current user.
     */
    fun loadBenisRecords(after: Instant = Instant(0)): Observable<BenisGraphRecords> {
        val state = loginState

        return database.asObservable().map {
            BenisGraphRecords(state, BenisRecord.findValuesLaterThan(it, state.id, after))
        }
    }

    class BenisGraphRecords(val loginState: LoginState, val records: List<BenisRecord>)

    /**
     * Gets the name of the current user from the cookie. This will only
     * work, if the user is authorized.

     * @return The name of the currently signed in user.
     */
    val name: String?
        get() = cookieHandler.cookie?.name


    /**
     * Returns an observable that produces the unique user token, if a hash is currently
     * available. This produces "null", if the user is currently not signed in.
     *
     * The observable will produce updated tokens on changes.
     */
    fun userToken(): Observable<String> {
        return loginStateSubject.map { value ->
            if (value.authorized) value.uniqueToken else null
        }
    }

    fun requestPasswordRecovery(email: String): Completable {
        return api.requestPasswordRecovery(email).toCompletable()
    }

    fun resetPassword(name: String, token: String, password: String): Observable<Boolean> {
        return api.resetPassword(name, token, password)
                .doOnNext { value -> logger.info("Response is {}", value) }
                .map { response -> response.error == null }
    }

    @JsonClass(generateAdapter = true)
    data class LoginState(
            val id: Int,
            val name: String?,
            val mark: Int,
            val score: Int,
            val uniqueToken: String?,
            val admin: Boolean,
            val premium: Boolean,
            val authorized: Boolean,
            @Transient val benisHistory: Graph? = null)

    class LoginProgress(val login: Api.Login?)

    companion object {
        private const val KEY_LAST_LOF_OFFSET = "UserService.lastLogLength"
        private const val KEY_LAST_USER_INFO = "UserService.lastUserInfo"
        private const val KEY_LAST_LOGIN_STATE = "UserService.lastLoginState"

        private val NOT_AUTHORIZED = LoginState(
                id = -1, score = 0, mark = 0, admin = false,
                premium = false, authorized = false, name = null, uniqueToken = null)

    }
}
