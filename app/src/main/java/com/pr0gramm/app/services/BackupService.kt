package com.pr0gramm.app.services

import android.content.ContentResolver
import android.net.Uri
import com.google.gson.Gson
import com.pr0gramm.app.Settings
import com.pr0gramm.app.util.time
import org.slf4j.LoggerFactory
import rx.Observable
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupService(val seenService: SeenService,
                    val settings: Settings,
                    val userService: UserService,
                    val bookmarkService: BookmarkService) {

    private val logger = LoggerFactory.getLogger("BackupService")
    private val gson = Gson()

    fun backup(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.setLevel(9)

            logger.time("Writing username to backup file") {
                writeUsername(zip)
            }

            logger.time("Backing up seen service.") {
                backupSeenService(zip)
            }

            logger.time("Backing up settings") {
                backupSettings(zip)
            }

            logger.time("Backing up benis history") {
                backupBenisHistory(zip)
            }

            logger.time("Backing up bookmarks") {
                backupBookmarks(zip)
            }
        }
    }

    private fun backupBookmarks(zip: ZipOutputStream) {
        val bookmarks = bookmarkService.get().toBlocking().firstOrDefault(listOf()).map { bookmark ->
            val filter = bookmark.asFeedFilter()
            return@map mapOf(
                    "title" to bookmark.title,
                    "filterTags" to filter.tags,
                    "filterUsername" to filter.username,
                    "filterFeedType" to filter.feedType.name)
        }

        zip.entry("bookmarks.json") { out ->
            out.writer().use { gson.toJson(bookmarks, it) }
        }
    }

    private fun writeUsername(zip: ZipOutputStream) {
        zip.entry("username.txt") { out ->
            out.writer().use { writer ->
                writer.write(userService.name ?: "")
            }
        }
    }

    private fun backupBenisHistory(zip: ZipOutputStream) {
        val values = userService.loadBenisRecords()
                .toBlocking()
                .firstOrDefault(listOf())

        zip.entry("benis-history.json") { out ->
            out.bufferedWriter().use { writer ->
                // write the benis history as list of (time, benis) tuples.
                gson.newJsonWriter(writer).use { json ->
                    json.beginArray()
                    values.forEach { (time, benis) ->
                        json.beginArray()
                        json.value(time)
                        json.value(benis)
                        json.endArray()
                    }
                    json.endArray()
                }
            }
        }
    }

    private fun backupSettings(zip: ZipOutputStream) {
        val config = settings.raw().all.toMap()

        zip.entry("settings.json") { out ->
            out.writer().use { writer ->
                gson.toJson(config, writer)
            }
        }
    }

    private fun backupSeenService(zip: ZipOutputStream) {
        seenService.withBuffer { buffer ->
            buffer.rewind()

            zip.entry("seen.bin") { out ->
                Channels.newChannel(out).use { channel ->
                    channel.write(buffer)
                }
            }
        }
    }

    fun backup(resolver: ContentResolver, backupUri: Uri): Observable<Unit> {
        return Observable.fromCallable {
            resolver.openFileDescriptor(backupUri, "w").use { fp ->
                FileOutputStream(fp.fileDescriptor).use { out ->
                    backup(out)
                }
            }
        }
    }
}

fun ZipOutputStream.entry(name: String, block: (OutputStream) -> Unit) {
    putNextEntry(ZipEntry(name))

    block(object : FilterOutputStream(this) {
        override fun close() {
            // do not forward close as we will close the current
            // zip entry using closeEntry().
        }
    })

    closeEntry()
}
