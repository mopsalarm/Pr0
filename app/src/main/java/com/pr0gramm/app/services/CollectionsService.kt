package com.pr0gramm.app.services

import androidx.lifecycle.MutableLiveData
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.asFlow
import com.pr0gramm.app.util.readOnly
import kotlinx.coroutines.flow.collect
import java.util.*

class CollectionsService(private val api: Api, private val userService: UserService) {
    private val logger = Logger("CollectionsService")
    private val _collections: MutableLiveData<List<Api.Collection>> = MutableLiveData()

    val collections = _collections.readOnly()

    init {
        AsyncScope.launchIgnoreErrors {
            userService.loginStates.asFlow().collect { loginState ->
                if (loginState.authorized && loginState.premium) {
                    launchIgnoreErrors {
                        val collections = api.collectionsGet().collections
                        logger.info { "Found ${collections.size} collections" }

                        // take default collection first, sort the rest by name
                        val (defaultCollections, otherCollections) = collections.partition { it.isDefault }
                        val sortedCollections = defaultCollections + otherCollections.sortedBy { it.name }

                        _collections.postValue(sortedCollections)
                    }
                } else {
                    logger.info { "Reset all collections." }
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