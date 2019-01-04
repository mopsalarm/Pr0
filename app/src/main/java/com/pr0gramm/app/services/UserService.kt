package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.LoginCookieJar
import com.pr0gramm.app.api.pr0gramm.LoginCookieJar.LoginCookie
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.services.config.Config
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.withBackgroundContext
import com.pr0gramm.app.util.*
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import okhttp3.Cookie
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class UserService(private val api: Api,
                  private val voteService: VoteService,
                  private val seenService: SeenService,
                  private val inboxService: InboxService,
                  private val cookieJar: LoginCookieJar,
                  private val preferences: SharedPreferences,
                  private val benisService: BenisRecordService,
                  private val config: Config) {

    private val logger = Logger("UserService")

    @Suppress("PrivatePropertyName")
    private val NotAuthorized = LoginState(
            id = -1, score = 0, mark = 0, admin = false,
            premium = false, authorized = false, name = null, uniqueToken = null)

    private val fullSyncInProgress = AtomicBoolean()

    private val loginStateSubject = BehaviorSubject.create(NotAuthorized)

    val loginState: LoginState get() = loginStateSubject.value
    val loginStates: Observable<LoginState> = loginStateSubject.distinctUntilChanged()

    init {
        restoreLatestUserInfo()

        // observe changes to the login cookie to handle login/logout behaviour.
        cookieJar.observeCookie.distinctUntilChanged().subscribe { onCookieChanged(it) }

        // persist the login state every time it changes.
        loginStates.observeOn(BackgroundScheduler).subscribe { state -> persistLatestLoginState(state) }

        Track.updateUserState(loginStates)
    }

    private fun updateLoginState(newLoginState: LoginState) {
        this.loginStateSubject.onNext(newLoginState)
    }

    /**
     * Restore the latest user info from the shared preferences
     */
    private fun restoreLatestUserInfo() {
        val encodedLoginState = preferences.getString(KEY_LAST_LOGIN_STATE, null)
        logger.debug { "Found login state: $encodedLoginState" }

        if (encodedLoginState == null || encodedLoginState == "null") {
            if (cookieJar.hasCookie()) {
                logger.warn { "Got cookie but no loginState, discarding cookie." }
                cookieJar.clearLoginCookie()
            }

            return
        }

        if (!cookieJar.hasCookie()) {
            logger.warn { "Got loginState but no cookie, discarding login state." }
            preferences.edit { remove(KEY_LAST_LOGIN_STATE) }
            return
        }

        doInBackground {
            try {
                val loginState = MoshiInstance.adapter<LoginState>()
                        .fromJson(encodedLoginState) ?: return@doInBackground

                logger.debug { "Restoring login state: $loginState" }
                updateLoginState(loginState)

            } catch (err: Exception) {
                logger.warn("Could not restore login state:", err)
                logout()
            }
        }
    }

    private fun onCookieChanged(cookie: LoginCookie?) {
        if (cookie == null) {
            logger.info { "LoginCookie was removed, performing logout now." }
            AsyncScope.launch { logout() }
        }
    }

    suspend fun login(username: String, password: String): LoginResult {
        val response = api.login(username, password).await()

        // in case of errors, just return the Failure
        val login = response.body() ?: run {
            logger.debug { "Request failed, no body." }
            return LoginResult.Failure
        }

        if (login.banInfo != null) {
            logger.debug { "User is banned." }
            return LoginResult.Banned(login.banInfo)
        }

        if (!login.success) {
            logger.debug { "Field login.success is false" }
            return LoginResult.Failure
        }

        // extract login cookie from response
        val loginCookie = Cookie
                .parseAll(response.raw().request().url(), response.raw().headers())
                .firstOrNull { cookie -> cookie.name() == "me" }

        if (loginCookie == null) {
            logger.debug { "No login cookie found" }
            return LoginResult.Failure
        }

        // store the cookie
        if (!cookieJar.updateLoginCookie(loginCookie)) {
            logger.debug { "CookieJar did not accept cookie $loginCookie" }
            return LoginResult.Failure
        }

        val userInfo = updateCachedUserInfo()
        if (userInfo == null) {
            ignoreException { logout() }
            throw IllegalStateException("Could not fetch initial user info")
        }

        // start sync now
        doInBackground { sync() }

        return LoginResult.Success
    }

    /**
     * Performs a logout of the user.
     */
    suspend fun logout() = withBackgroundContext(NonCancellable) {
        updateLoginState(NotAuthorized)

        // removing cookie from requests
        cookieJar.clearLoginCookie()

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
        inboxService.publishUnreadMessagesCount(Api.InboxCounts())

        // and reset the content user, because only signed in users can
        // see the nsfw and nsfl stuff.
        Settings.get().resetContentTypeSettings()
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     */
    suspend fun sync(): Api.Sync? {
        if (!cookieJar.hasCookie())
            return null

        // tell the sync request where to start
        val lastLogOffset = preferences.getLong(syncOffsetKey(), 0L)
        val fullSync = (lastLogOffset == 0L)

        if (fullSync && !fullSyncInProgress.compareAndSet(false, true)) {
            // fail if full sync is in already in progress.
            return null
        }

        try {
            val response = api.sync(lastLogOffset).await()

            inboxService.publishUnreadMessagesCount(response.inbox)

            val userId = loginState.id
            if (userId > 0) {
                // save the current benis value
                benisService.storeValue(userId, response.score)

                // and update login state
                val updatedLoginState = loginState.copy(score = response.score)
                if (updatedLoginState.authorized) {
                    updateLoginState(updatedLoginState)
                }
            }

            voteService.applyVoteActions(response.log)

            // store syncId for next time.
            if (response.logLength > lastLogOffset) {
                preferences.edit {
                    putLong(syncOffsetKey(), response.logLength)
                }
            }

            return response

        } finally {
            if (fullSync) {
                fullSyncInProgress.set(false)
            }
        }
    }

    private fun syncOffsetKey() = KEY_LAST_LOF_OFFSET + ":v1:" + config.syncVersion

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    suspend fun info(username: String): Api.Info {
        return api.info(username, null).await()
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    suspend fun info(username: String, contentTypes: Set<ContentType>): Api.Info {
        return api.info(username, ContentType.combine(contentTypes)).await()
    }

    /**
     * Update the cached user info in the background. Will throw an exception if
     * the user info can not be updated.
     */
    suspend fun updateCachedUserInfo(): Api.Info? {
        val name = name.takeUnless { it.isNullOrEmpty() } ?: return null

        return info(name).also { userInfo ->
            updateLoginState(createLoginStateFromInfo(userInfo))
        }
    }

    /**
     * Persists the given login state to a preference storage.
     */
    private fun persistLatestLoginState(state: LoginState) {
        try {
            if (state.authorized) {
                logger.debug { "Persisting login state now." }

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
        return LoginState(
                authorized = true,
                id = info.user.id,
                name = info.user.name,
                mark = info.user.mark,
                score = info.user.score,
                premium = userIsPremium,
                admin = userIsAdmin,
                uniqueToken = loginState.uniqueToken)
    }

    val isAuthorized: Boolean
        get() = loginState.authorized && cookieJar.parsedCookie?.id?.isNotBlank() == true

    val userIsPremium: Boolean
        get() = loginState.authorized && cookieJar.parsedCookie?.paid == true

    val userIsAdmin: Boolean
        get() = loginState.authorized && cookieJar.parsedCookie?.admin == true

    val loginStateWithBenisGraph: Observable<LoginStateWithBenisGraph> = run {
        val rxStart = loginStates.first().map { LoginStateWithBenisGraph(loginState) }

        val rxGraphed = loginStates.observeOn(BackgroundScheduler).map { loginState ->
            if (loginState.authorized) {
                LoginStateWithBenisGraph(loginState, loadBenisHistoryAsGraph(loginState.id))
            } else {
                LoginStateWithBenisGraph(loginState)
            }
        }

        val ticker = Observable.interval(0, 10, TimeUnit.MINUTES)
        rxStart.concatWith(ticker.switchMap { rxGraphed })
    }

    private fun loadBenisHistoryAsGraph(userId: Int): Graph = logger.time("Loading benis graph") {
        val now = Instant.now()
        val start = now - Duration.days(7)

        // get the values and transform them
        val points = benisService.findValuesLaterThan(userId, start).map { record ->
            val x = record.time.toDouble()
            val y = record.benis.toDouble()
            Graph.Point(x, y)
        }

        return Graph(start.millis.toDouble(), now.millis.toDouble(), optimizeValuesBy(points) { it.y })
    }

    /**
     * Loads all benis records for the current user.
     */
    suspend fun loadBenisRecords(after: Instant = Instant(0)): BenisGraphRecords {
        val state = loginState

        return withBackgroundContext {
            BenisGraphRecords(benisService.findValuesLaterThan(state.id, after))
        }
    }

    class BenisGraphRecords(val records: List<BenisRecord>)

    /**
     * Gets the name of the current user from the cookie. This will only
     * work, if the user is authorized.

     * @return The name of the currently signed in user.
     */
    val name: String?
        get() = cookieJar.parsedCookie?.name

    suspend fun requestPasswordRecovery(email: String) {
        api.requestPasswordRecovery(email).await()
    }

    suspend fun resetPassword(name: String, token: String, password: String): Boolean {
        val result = api.resetPassword(name, token, password).await()
        return result.error == null
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
            val authorized: Boolean)

    class LoginStateWithBenisGraph(
            val loginState: LoginState, val benisGraph: Graph? = null)

    sealed class LoginResult {
        object Success : LoginResult()
        object Failure : LoginResult()

        class Banned(val ban: Api.Login.BanInfo) : LoginResult()
    }

    companion object {
        private const val KEY_LAST_LOF_OFFSET = "UserService.lastLogLength"
        private const val KEY_LAST_USER_INFO = "UserService.lastUserInfo"
        private const val KEY_LAST_LOGIN_STATE = "UserService.lastLoginState"
    }
}
