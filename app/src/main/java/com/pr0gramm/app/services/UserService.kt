package com.pr0gramm.app.services

import android.content.SharedPreferences
import androidx.core.content.edit
import com.pr0gramm.app.*
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.api.pr0gramm.GenericSettings
import com.pr0gramm.app.api.pr0gramm.LoginCookieJar
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.model.user.LoginCookie
import com.pr0gramm.app.model.user.LoginState
import com.pr0gramm.app.orm.BenisRecord
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.*
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


class UserService(
    private val api: Api,
    private val voteService: VoteService,
    private val seenService: SeenService,
    private val inboxService: InboxService,
    private val cookieJar: LoginCookieJar,
    private val preferences: SharedPreferences,
    private val benisService: BenisRecordService,
    private val collectionsItemsService: CollectionItemsService,
    private val favedCommentService: FavedCommentService,
    private val settingsService: SiteSettingsService,
    private val config: Config,
) {

    private val logger = Logger("UserService")

    @Suppress("PrivatePropertyName")
    private val NotAuthorized = LoginState(
        id = -1, score = 0, mark = 0, admin = false,
        premium = false, authorized = false, name = null,
        uniqueToken = null, verified = false,
    )

    private val fullSyncInProgress = AtomicBoolean()

    private val loginStateLock = ReentrantLock()
    private val loginStateSubject = MutableStateFlow(NotAuthorized)

    val loginState: LoginState get() = loginStateSubject.value
    val loginStates: Flow<LoginState> = loginStateSubject

    init {
        restoreLatestUserInfo()

        doInBackground {
            // observe changes to the login cookie to handle login/logout behaviour.
            cookieJar.observeCookie.distinctUntilChanged().collect { onCookieChanged(it) }
        }

        AsyncScope.launch {
            // persist the login state every time it changes.
            loginStates.collect { state -> persistLatestLoginState(state) }
        }
    }

    private fun updateLoginState(state: LoginState) {
        if (this.loginStateSubject.value == state) {
            return
        }

        logger.debug { "LoginState is now: $state" }

        // update the current 'auth' state for tracking
        Track.updateUserState(Track.AuthState(state.authorized, state.premium))

        this.loginStateSubject.value = state
    }


    private fun updateUniqueTokenIfNeeded() {
        val state = loginState
        if (state.authorized && state.uniqueToken == null) {
            doInBackground {
                val result = api.identifier()
                updateUniqueToken(result.identifier)
            }
        }
    }

    private fun updateUniqueToken(uniqueToken: String?) {
        if (uniqueToken == null)
            return

        loginStateLock.withLock {
            val updatedLoginState = loginState.copy(uniqueToken = uniqueToken)
            updateLoginState(updatedLoginState)
        }
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

                updateUniqueTokenIfNeeded()

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

        } else {
            // cookie has changed, re-build cached user info
            AsyncScope.launchIgnoreErrors {
                updateCachedUserInfo()
            }
        }
    }

    suspend fun login(username: String, password: String, captchaToken: String, captchaAnswer: String): LoginResult {
        logger.debug {
            // 'debug' is not compiled into release builds, this is only visible during development!
            "Login with $username/$password (token=$captchaToken, captcha=$captchaAnswer)"
        }

        val response = api.login(username, password, captchaToken, captchaAnswer)

        // in case of errors, just return the Failure
        val login = response.body() ?: run {
            logger.debug { "Request failed, no body." }
            return LoginResult.FailureError
        }

        login.banInfo?.let { banInfo ->
            logger.debug { "User is banned." }
            return LoginResult.Banned(banInfo)
        }

        // handle error codes
        when (login.error) {
            "invalidLogin" -> return LoginResult.FailureLogin
            "invalidCaptcha" -> return LoginResult.FailureCaptcha
        }

        // probably redundant.
        if (login.success == false) {
            logger.debug { "Field login.success is false" }
            return LoginResult.FailureLogin
        }

        // extract login cookie from response
        val loginCookie = Cookie
            .parseAll(response.raw().request.url, response.raw().headers)
            .firstOrNull { cookie -> cookie.name == "me" }

        if (loginCookie == null) {
            logger.debug { "No login cookie found" }
            return LoginResult.FailureError
        }

        // store the cookie
        if (!cookieJar.updateLoginCookie(loginCookie)) {
            logger.debug { "CookieJar did not accept cookie $loginCookie" }
            return LoginResult.FailureError
        }

        // update app settings
        login.settings?.let { settings ->
            settingsService.updateLocalSettings(settings)
        }

        val userInfo = updateCachedUserInfo(login.identifier)
        if (userInfo == null) {
            catchAll { logout() }
            throw IllegalStateException("Could not fetch initial user info")
        }

        // start sync now
        doInBackground { sync() }

        return LoginResult.Success
    }

    /**
     * Performs a logout of the user.
     */
    suspend fun logout() = withContext(NonCancellable + Dispatchers.Default) {
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

        // remove all cached collection items
        collectionsItemsService.clear()

        // remove all faved comments
        favedCommentService.clear()

        // no more read messages.
        inboxService.forgetUnreadMessages()
        inboxService.publishUnreadMessagesCount(Api.InboxCounts())

        // and reset the content user, because only signed in users can
        // see the nsfw and nsfl stuff.
        Settings.resetContentTypeSettings()

        // do not load automatically anymore
        Settings.feedStartWithUri = null
    }

    val selectedContentType: Set<ContentType>
        get() {
            return if (isAuthorized) {
                Settings.contentType
            } else {
                EnumSet.of(ContentType.SFW)
            }
        }

    val selectedContentTypes: Flow<Set<ContentType>> = run {
        val types = loginStates.flatMapLatest { loginState ->
            if (loginState.authorized) {
                Settings.changes()
                    .map { Settings.contentType }
                    .onStart { emit(Settings.contentType) }
            } else {
                flowOf(EnumSet.of(ContentType.SFW))
            }
        }

        types.distinctUntilChanged()
    }

    /**
     * Performs a sync. This updates the vote cache with all the votes that
     * where performed since the last call to sync.
     */
    suspend fun sync(): Api.Sync? {
        if (!cookieJar.hasCookie()) {
            return null
        }

        // tell the sync request where to start
        val lastLogOffset = preferences.getLong(syncOffsetKey(), 0L)
        val fullSync = (lastLogOffset == 0L)

        if (fullSync && !fullSyncInProgress.compareAndSet(false, true)) {
            // fail if full sync is in already in progress.
            return null
        }

        try {
            val response = api.sync(lastLogOffset)

            // update app settings if changed
            response.settings?.let { settings ->
                settingsService.updateLocalSettings(settings)
            }

            inboxService.publishUnreadMessagesCount(response.inbox)

            val userId = loginState.id
            if (userId > 0) {
                // save the current benis value
                benisService.storeValue(userId, response.score)

                loginStateLock.withLock {
                    // and update login state
                    val updatedLoginState = loginState.copy(score = response.score)
                    if (updatedLoginState.authorized) {
                        updateLoginState(updatedLoginState)
                    }
                }
            }

            voteService.applyVoteActions(response.log)

            // store syncId for next time.
            if (response.logLength > lastLogOffset) {
                preferences.edit {
                    putLong(syncOffsetKey(), response.logLength)
                }
            }

            debugOnly {
                debugConfig.pendingNotifications?.let { pending ->
                    val counts = Api.InboxCounts(comments = pending.messages.size)
                    return Api.Sync(response.logLength, response.log, response.score, counts)
                }
            }

            return response

        } finally {
            if (fullSync) {
                fullSyncInProgress.set(false)
            }
        }
    }

    private suspend fun querySyncOnly(): Api.Sync? {
        if (!cookieJar.hasCookie()) {
            return null
        }

        val lastLogOffset = preferences.getLong(syncOffsetKey(), 0L)
        return api.sync(lastLogOffset)
    }

    private fun syncOffsetKey(): String {
        return KEY_LAST_LOF_OFFSET + ":v6:" + config.syncVersion
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    suspend fun info(username: String): Api.Info {
        return api.info(username, null)
    }

    /**
     * Retrieves the user data and stores part of the data in the database.
     */
    suspend fun info(username: String, contentTypes: Set<ContentType>): Api.Info {
        return api.info(username, ContentType.combine(contentTypes))
    }

    /**
     * Store the given settings
     */
    suspend fun storeSiteSettings(update: Map<String, Any?>) {
        val sync = runCatching { querySyncOnly() }.getOrNull() ?: return

        val settings = HashMap(sync.settings.orEmpty() + update)

        logger.info { "Storing remote settings: $settings" }

        val fields = settings.mapValues { (_, value) ->
            when (value) {
                is Boolean -> value.toInt()
                else -> value
            }
        }

        return api.storeSettings(null, fields as GenericSettings)
    }

    /**
     * Update the cached user info in the background. Will throw an exception if
     * the user info can not be updated.
     */
    suspend fun updateCachedUserInfo(token: String? = null): Api.Info? {
        val name = name.takeUnless { it.isNullOrEmpty() } ?: return null

        return info(name).also { userInfo ->
            val updatedLoginState = createLoginStateFromInfo(
                userInfo.user,
                cookieJar.parsedCookie, token ?: loginState.uniqueToken
            )

            loginStateLock.withLock {
                if (cookieJar.parsedCookie != null) {
                    updateLoginState(updatedLoginState)
                }
            }
        }
    }

    /**
     * Persists the given login state to a preference storage.
     */
    private fun persistLatestLoginState(state: LoginState) {
        try {
            if (state.authorized) {
                logger.debug { "Persisting login state now: $loginState" }

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

    val isAuthorized: Boolean
        get() = loginState.authorized && cookieJar.parsedCookie?.id?.isNotBlank() == true

    val userIsPremium: Boolean
        get() = isAuthorized && loginState.premium

    val userIsAdmin: Boolean
        get() = isAuthorized && loginState.admin

    val userIsVerified: Boolean
        get() = isAuthorized && loginState.verified

    val loginStateWithBenisGraph = run {
        val graphs = loginStates.mapLatest { loginState ->
            if (loginState.authorized) {
                LoginStateWithScoreGraph(loginState, loadScoreHistoryAsGraph(loginState.id))
            } else {
                LoginStateWithScoreGraph(loginState, scoreGraph = null)
            }
        }

        ticker(Duration.minutes(10)).flatMapLatest { graphs }
    }

    private suspend fun loadScoreHistoryAsGraph(userId: Int): Graph = logger.time("Loading benis graph") {
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
        return BenisGraphRecords(benisService.findValuesLaterThan(loginState.id, after))
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
        api.requestPasswordRecovery(email)
    }

    suspend fun resetPassword(name: String, token: String, password: String): Boolean {
        val result = api.resetPassword(name, token, password)
        return result.error == null
    }

    suspend fun userCaptcha(): Api.UserCaptcha {
        return api.userCaptcha()
    }

    suspend fun blockUser(name: String) {
        api.block(null, name)
    }

    class LoginStateWithScoreGraph(
        val loginState: LoginState, val scoreGraph: Graph?
    )

    sealed class LoginResult {
        object Success : LoginResult()

        object FailureError : LoginResult()
        object FailureCaptcha : LoginResult()
        object FailureLogin : LoginResult()

        class Banned(val ban: Api.Login.BanInfo) : LoginResult()
    }
}

private const val KEY_LAST_LOF_OFFSET = "UserService.lastLogLength"
private const val KEY_LAST_USER_INFO = "UserService.lastUserInfo"
private const val KEY_LAST_LOGIN_STATE = "UserService.lastLoginState"

private fun createLoginStateFromInfo(user: Api.Info.User, cookie: LoginCookie?, token: String?): LoginState {
    return LoginState(
        authorized = true,
        id = user.id,
        name = user.name,
        mark = user.mark,
        score = user.score,
        premium = cookie?.paid == true,
        admin = cookie?.admin == true,
        verified = cookie?.verified == true,
        uniqueToken = token
    )
}