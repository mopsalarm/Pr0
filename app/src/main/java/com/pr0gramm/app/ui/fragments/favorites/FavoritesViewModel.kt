package com.pr0gramm.app.ui.fragments.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.pr0gramm.app.services.CollectionsService
import com.pr0gramm.app.services.PostCollection
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.equalsIgnoreCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class FavoritesViewModel(
        val user: String,
        private val userService: UserService,
        private val collectionsService: CollectionsService,
) : ViewModel() {

    private val mutableCollectionsState = MutableStateFlow<List<PostCollection>>(listOf())

    val myView = userService.loginState.name.equalsIgnoreCase(user)
    val collectionsState: StateFlow<List<PostCollection>> = mutableCollectionsState

    init {
        val collections = when {
            myView -> {
                // observe our collections
                collectionsService.collections.asFlow()
            }

            else -> flow {
                // fetch collections once
                val info = userService.info(user)
                emit(PostCollection.fromApi(info))
            }
        }

        viewModelScope.launchIgnoreErrors { observeCollections(collections) }

        if (myView) {
            // trigger an async refresh of my collections
            doInBackground { collectionsService.refresh() }
        }
    }

    private suspend fun observeCollections(collectionsToObserve: Flow<List<PostCollection>>) {
        collectionsToObserve.collect { collections ->
            mutableCollectionsState.value = collections
        }
    }
}