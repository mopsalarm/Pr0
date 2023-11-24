package com.pr0gramm.app.ui.fragments.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.feed.FeedFilter
import com.pr0gramm.app.services.InboxService
import com.pr0gramm.app.services.UserInfo
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.util.trace
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class UserStateModel(
        queryForUserInfo: Boolean,
        private val filter: FeedFilter,
        private val userService: UserService,
        private val inboxService: InboxService
) : ViewModel() {

    val userState = MutableStateFlow(UserState(
            ownUsername = userService.name
    ))

    val userInfo: UserInfo?
        get() = userState.value.userInfo

    init {
        viewModelScope.launch { observeLoginState() }

        if (queryForUserInfo) {
            viewModelScope.launch { observeUserInfo() }
        }
    }

    private suspend fun observeLoginState() {
        userService.loginStates.collect { loginState ->
            userState.update { previousState ->
                previousState.copy(ownUsername = loginState.name)
            }
        }
    }

    private suspend fun observeUserInfo() {
        userService.selectedContentTypes.collect { contentType ->
            val userInfo = queryUserInfo(contentType) ?: return@collect
            userState.update { previousState -> previousState.copy(userInfo = userInfo) }
        }
    }

    private suspend fun queryUserInfo(contentTypes: Set<ContentType>): UserInfo? {
        val queryString = filter.username ?: filter.tags

        trace { "queryUserInfo($queryString, $contentTypes)" }

        if (queryString != null && queryString.matches("[A-Za-z0-9_]{2,}".toRegex())) {
            return coroutineScope {
                // fan out
                val first = async {
                    runCatching {
                        userService.info(queryString, contentTypes)
                    }
                }

                val second = async {
                    runCatching {
                        inboxService.getUserComments(queryString, contentTypes)
                    }
                }

                // and wait for responses with defaults
                val info = first.await().getOrNull() ?: return@coroutineScope null
                val comments = second.await().getOrNull()?.comments ?: listOf()

                UserInfo(info, comments)
            }
        }

        return null
    }

    fun openUserComments() {
        if (userService.isAuthorized) {
            userState.update { previousState ->
                previousState.copy(userInfoCommentsOpen = true)
            }
        }
    }

    fun closeUserComments() {
        userState.update { previousState ->
            previousState.copy(userInfoCommentsOpen = false)
        }
    }

    data class UserState(
            val ownUsername: String? = null,
            val userInfo: UserInfo? = null,
            val userInfoCommentsOpen: Boolean = false
    )
}