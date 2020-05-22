package com.pr0gramm.app.services

import androidx.lifecycle.MutableLiveData
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.asFlow
import com.pr0gramm.app.util.readOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.*

class CollectionsService(private val api: Api, private val userService: UserService) {
    private val _collections: MutableLiveData<List<Api.Collection>> = MutableLiveData()

    val collections = _collections.readOnly()

    init {
        AsyncScope.launchIgnoreErrors {
            userService.loginStates.asFlow().collect { loginState ->
                if (loginState.authorized && loginState.premium) {
                    launch {
                        _collections.postValue(api.collectionsGet().collections)
                    }
                } else {
                    _collections.postValue(listOf())
                }
            }
        }
    }

    fun isValidNameForNewCollection(name: String): Boolean {
        val existing = _collections.value.orEmpty().map { it.name.toLowerCase(Locale.getDefault()) }
        return name.length > 3 && name !in existing
    }

    suspend fun create(name: String): Long {
        val response = api.collectionsCreate(null, name)
        _collections.postValue(response.collections)
        return response.collectionId
    }

    suspend fun addToCollection(collectionId: Long, itemId: Long) {
        api.collectionsAdd(null, collectionId, itemId)
    }

    suspend fun removeFromCollection(collectionId: Long, itemId: Long) {
        api.collectionsRemove(null, collectionId, itemId)
    }

    suspend fun isItemInAnyCollection(itemId: Long): Boolean {
        return false
    }
}