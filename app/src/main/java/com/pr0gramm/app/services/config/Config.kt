package com.pr0gramm.app.services.config

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Config(
        val extraCategories: Boolean = true,
        val maxUploadSizeNormal: Long = 6 * 1024 * 1024,
        val maxUploadSizePremium: Long = 12 * 1024 * 1024,
        val secretSanta: Boolean = false,
        val adType: AdType = AdType.NONE,
        val trackItemView: Boolean = false,
        val trackVotes: Boolean = false,
        val commentsMaxLevels: Int = 18,
        val reportReasons: List<String> = DefaultReportReasons,
        val syncVersion: Int = 1,
        val userClasses: List<UserClass> = DefaultUserClasses,
        val specialMenuItems: List<Config.MenuItem> = listOf()) {

    val reportItemsActive: Boolean
        get() = reportReasons.isNotEmpty()

    enum class AdType {
        NONE,
        FEED,
        MAIN /* deprecated - dont use */
    }

    @JsonClass(generateAdapter = true)
    data class MenuItem(
            val name: String, val icon: String, val link: String,
            val requireLogin: Boolean = false,
            val noHighlight: Boolean = false,
            val lower: Boolean = false)

    @JsonClass(generateAdapter = true)
    data class UserClass(val color: String, val name: String, val symbol: String = "\u25CF")
}

private val DefaultReportReasons = listOf(
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
        "Regel #15 - reiner Musikupload",
        "Regel #18 - Hetze/Aufruf zu Gewalt",
        "Verstoß in den Tags",
        "Ich habe diesen Beitrag selbst erstellt und möchte ihn gelöscht haben")

private val DefaultUserClasses = listOf(
        Config.UserClass("#FFFFFF", "Schwuchtel"),
        Config.UserClass("#E108E9", "Neuschwuchtel"),
        Config.UserClass("#5BB91C", "Altschwuchtel"),
        Config.UserClass("#FF9900", "Admin"),
        Config.UserClass("#444444", "Gebannt"),
        Config.UserClass("#008FFF", "Moderator"),
        Config.UserClass("#6C432B", "Fliesentischbesitzer"),
        Config.UserClass("#1cb992", "Legende", symbol = "\u25c6"),
        Config.UserClass("#d23c22", "Wichtel", symbol = "\u25a7"),
        Config.UserClass("#1cb992", "Pr0mium"),
        Config.UserClass("#addc8d", "Mittelaltschwuchtel"),
        Config.UserClass("#7fc7ff", "Altmod"),
        Config.UserClass("#c52b2f", "Community Helfer", symbol = "\u2764\uFE0E"))
