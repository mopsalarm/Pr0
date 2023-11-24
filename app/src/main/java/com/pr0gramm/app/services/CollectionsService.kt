package com.pr0gramm.app.services

import androidx.lifecycle.MutableLiveData
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.pr0gramm.app.Logger
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.db.CollectionItemQueries
import com.pr0gramm.app.time
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.readOnly
import com.pr0gramm.app.util.toInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.Locale

data class PostCollection(
        val id: Long,
        val key: String,
        val title: String,
        val uniqueTitle: String,
        val isPublic: Boolean,
        val isDefault: Boolean,
        val owner: Owner?,
) {

    val isCuratorCollection: Boolean
        get() = owner != null

    val titleWithOwner = buildString {
        append(title)
        owner?.let {
            append(" (")
            append(owner.name)
            append(")")
        }
    }

    class Owner(val name: String, val mark: Int)

    companion object {
        private fun Api.Collection.titleWithOwner(): String {
            return name + ":" + owner.orEmpty()
        }

        fun fromApi(input: Api.HasCollections): List<PostCollection> {
            val collections = input.allCollections

            return collections.map { apiCollection ->
                val titleIsUnique = collections.count { it.titleWithOwner() == apiCollection.titleWithOwner() } == 1

                val collectionOwner = apiCollection.owner
                val collectionOwnerMark = apiCollection.ownerMark

                val owner = if (collectionOwner != null && collectionOwnerMark != null) {
                    Owner(collectionOwner, collectionOwnerMark)
                } else {
                    null
                }

                PostCollection(
                        id = apiCollection.id,
                        key = apiCollection.keyword,
                        title = apiCollection.name,
                        uniqueTitle = if (titleIsUnique) apiCollection.name else "${apiCollection.name} (${apiCollection.keyword})",
                        isPublic = apiCollection.isPublic,
                        isDefault = apiCollection.isDefault,
                        owner = owner,
                )
            }
        }
    }
}


class CollectionsService(private val api: Api, private val userService: UserService) {
    private val logger = Logger("CollectionsService")
    private val _collections: MutableLiveData<List<PostCollection>> = MutableLiveData(listOf())

    val collections = _collections.readOnly()

    val defaultCollection: PostCollection?
        get() = collections.value?.firstOrNull(PostCollection::isDefault)

    init {
        AsyncScope.launchIgnoreErrors {
            userService.loginStates.collect { loginState ->
                logger.debug { "Update list of collections after loginState changed: user=${loginState.name}" }

                if (loginState.authorized) {
                    launchIgnoreErrors { refresh() }
                } else {
                    logger.info { "Reset all collections." }
                    publishCollections(listOf())
                }
            }
        }
    }

    fun isValidNameForNewCollection(name: String): Boolean {
        val existing = _collections.value.orEmpty().map { it.title.lowercase(Locale.getDefault()) }
        return name.length in 2..20 && name !in existing
    }

    suspend fun refresh() {
        publishCollections(PostCollection.fromApi(api.collectionsGet()))
    }

    private fun publishCollections(collections: List<PostCollection>) {
        logger.debug { "Publishing ${collections.size} collections: ${collections.joinToString { it.uniqueTitle }}" }

        // take default collection first, sort the rest by name
        val (defaultCollections, otherCollections) = collections.partition { it.isDefault }
        val sortedCollections = defaultCollections + otherCollections.sortedBy { it.uniqueTitle }

        _collections.postValue(sortedCollections)
    }

    suspend fun delete(collectionId: Long): Result<Unit> {
        val response = Result.ofValue(api.collectionsDelete(null, collectionId))
        handleCollectionUpdated(response)
        return response.map { }
    }

    suspend fun create(name: String, isPublic: Boolean, isDefault: Boolean): Result<Long> {
        val response = Result.ofValue(api.collectionsCreate(null, name, isPublic.toInt(), isDefault.toInt()))
        handleCollectionUpdated(response)
        return response.map { it.collectionId }
    }

    suspend fun edit(id: Long, name: String, isPublic: Boolean, isDefault: Boolean): Result<Long> {
        val response = Result.ofValue(api.collectionsEdit(null, id, name, isPublic.toInt(), isDefault.toInt()))
        handleCollectionUpdated(response)
        return response.map { it.collectionId }
    }

    private fun handleCollectionUpdated(response: Result<Api.CollectionCreated>) {
        if (response is Result.Success) {
            publishCollections(PostCollection.fromApi(response.value))
        }
    }

    fun byId(collectionId: Long): PostCollection? {
        return _collections.value?.firstOrNull { it.id == collectionId }
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

    suspend fun removeFromCollection(collectionId: Long, itemId: Long) {
        logger.info { "Removing item $itemId from collection $collectionId" }

        val response = api.collectionsRemove(null, collectionId, itemId)

        if (response.error == null) {
            // delete on server side was okay, remove locally
            runInterruptible(Dispatchers.IO) {
                db.remove(itemId, collectionId)
            }
        }
    }

    suspend fun addToCollection(itemId: Long, collectionId: Long?): Result {
        logger.info { "Adding item $itemId to collection $collectionId" }

        val response = api.collectionsAdd(null, collectionId, itemId)

        val result = when (val error = response.error) {
            null -> Result.ItemAdded(response.collectionId)
            "collectionNotFound" -> Result.CollectionNotFound
            else -> Result.UnknownError(error)
        }

        if (result is Result.ItemAdded) {
            // mimic adding to collection locally
            runInterruptible(Dispatchers.IO) {
                db.add(itemId, result.collectionId)
            }
        }

        return result
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

    suspend fun deleteCollection(collectionId: Long) {
        runInterruptible(Dispatchers.Default) {
            db.removeCollection(collectionId)
        }
    }

    private suspend fun observeDatabaseUpdates() {
        db.all().asFlow().mapToList(Dispatchers.IO).collect { collectionItems ->
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

    sealed class Result {
        class ItemAdded(val collectionId: Long) : Result()
        object CollectionNotFound : Result()
        class UnknownError(val errorCode: String) : Result()
    }
}
