package com.pr0gramm.app.services.config

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
open class Config(
        val extraCategories: Boolean = true,
        val maxUploadSizeNormal: Long = 6 * 1024 * 1024,
        val maxUploadSizePremium: Long = 12 * 1024 * 1024,
        val searchUsingTagService: Boolean = false,
        val secretSanta: Boolean = false,
        val adType: AdType = AdType.NONE,
        val trackItemView: Boolean = false,
        val trackVotes: Boolean = false,
        val questionableTags: List<String> = defaultQuestionableTags,
        val commentsMaxLevels: Int = 18,
        val reportReasons: List<String> = defaultReportReasons,
        val syncVersion: Int = 1,
        val specialMenuItem: Config.MenuItem? = null) {

    val reportItemsActive: Boolean
        get() = reportReasons.isNotEmpty()


    enum class AdType {
        NONE,
        FEED,
        MAIN /* deprecated - dont use */
    }

    @JsonClass(generateAdapter = true)
    data class MenuItem(val name: String, val icon: String, val link: String)

    companion object {
        private val defaultQuestionableTags = listOf(
                "0815", "kann weg", "heil hitler", "ban pls", "deshalb",
                "ab ins gas", "und weiter", "alles ist", "hure", "da drückste",
                "pr0paganda", "pr0gida", "für mehr", "dein scheiß", "kann ich auch")

        private val defaultReportReasons = listOf(
                "Repost",
                "Regel #1 - Bild unzureichend getagged (nsfw/nsfl)",
                "Regel #2 - Gore/Porn/Suggestive Bilder mit Minderjährigen",
                "Regel #3 - Tierporn",
                "Regel #4 - Stumpfer Rassismus/Nazi-Nostalgie",
                "Regel #5 - Werbung/Spam",
                "Regel #6 - Infos zu Privatpersonen",
                "Regel #7 - Bildqualität",
                "Regel #8 - Ähnliche Bilder in Reihe",
                "Regel #12 - Warez/Logins zu Pay Sites",
                "Regel #14 - Screamer/Sound-getrolle",
                "Regel #15 - Reiner Musikupload",
                "Regel #18 - Hetze/Aufruf zur Gewalt",
                "Verstoß in den Tags",
                "Ich habe diesen Beitrag selbst erstellt und möchte ihn gelöscht haben")
    }
}