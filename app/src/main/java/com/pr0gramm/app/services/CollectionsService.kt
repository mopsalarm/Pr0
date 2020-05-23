package com.pr0gramm.app.services

import androidx.lifecycle.MutableLiveData
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.CollectionItemQueries
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.asFlow
import com.pr0gramm.app.util.readOnly
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
}

class CollectionItemsService(private val api: Api, private val db: CollectionItemQueries) {
    private val logger = Logger("CollectionItemsService")

    private val lock = Any()
    private var cache: MutableMap<Long, MutableSet<Long>> = hashMapOf()

    val updateTime = MutableStateFlow<Long>(0L)

    init {
        AsyncScope.launch {
            observeDatabaseUpdates()
        }
    }

    fun clear() {
        logger.time("Remove all entries from database") {
            db.clear()
        }
    }

    suspend fun addToCollection(collectionId: Long, itemId: Long) {
        logger.info { "Adding item $itemId to collection $collectionId" }
        api.collectionsAdd(null, collectionId, itemId)
        db.add(itemId, collectionId)
    }

    suspend fun removeFromCollection(collectionId: Long, itemId: Long) {
        logger.info { "Removing item $itemId from collection $collectionId" }
        api.collectionsRemove(null, collectionId, itemId)
        db.remove(itemId, collectionId)
    }

    fun isItemInAnyCollection(itemId: Long): Boolean {
        synchronized(lock) {
            return cache.values.any { collection -> itemId in collection }
        }
    }

    fun collectionsContaining(itemId: Long): List<Long> {
        synchronized(lock) {
            return cache.mapNotNull { (collectionId, items) -> collectionId.takeIf { itemId in items } }
        }
    }

    private suspend fun observeDatabaseUpdates() {
        db.all().asFlow().mapToList().collect { collectionItems ->
            synchronized(lock) {
                logger.time("Updating memory cache with ${collectionItems.size} entries") {
                    cache = collectionItems
                            .groupBy(keySelector = { it.collectionId }, valueTransform = { it.itemId })
                            .mapValuesTo(mutableMapOf()) { (_, itemIds) -> itemIds.toHashSet() }
                }
            }

            // publish update to listeners
            updateTime.value = System.currentTimeMillis()
        }
    }
}
