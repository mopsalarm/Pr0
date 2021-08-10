package com.pr0gramm.app.model.config

import android.os.Build
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Config(
        val maxUploadSizeNormal: Long = 10 * 1024 * 1024,
        val maxUploadSizePremium: Long = 20 * 1024 * 1024,

        val maxUploadPixelsNormal: Long = 20_250_000,
        val maxUploadPixelsPremium: Long = 20_250_000,

        val adTypesLoggedIn: List<AdType> = listOf(),
        val adTypesLoggedOut: List<AdType> = listOf(),

        val interstitialAdIntervalInSeconds: Long = 600,

        val commentsMaxLevels: Int = 18,
        val reportReasons: List<String> = DefaultReportReasons,
        val adminReasons: List<String> = DefaultAdminReasons,
        val syncVersion: Int = 3,
        val userClasses: List<UserClass> = DefaultUserClasses,
        val endOfLifeAndroidVersion: Int = Build.VERSION_CODES.LOLLIPOP,
        val reAffiliate: String = "(?:pornhub|redtube|tube8|youporn|xtube|spankwire|keezmovies|extremetube)\\.com",
        val specialMenuItems: List<MenuItem> = listOf()) {

    enum class AdType {
        NONE,
        FEED,
        FEED_TO_POST_INTERSTITIAL
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
        "Ich habe diesen Beitrag selbst erstellt und möchte ihn gelöscht haben",
        "Regel #1 - Bild unzureichend getagged (nsfp/nsfw/nsfl)",
        "Regel #2.1 - Suggestive oder nackte Darstellung von Minderjährigen",
        "Regel #2.2 - Rohe Gewalt an Minderjährigen",
        "Regel #3.1 - Tierquälerei",
        "Regel #3.2 - Zoophilie",
        "Regel #4.1 - Stumpfer Rassismus/rechtes Gedankengut",
        "Regel #4.2 - Hetze",
        "Regel #4.3 - Aufruf zu Gewalt",
        "Regel #4.4 - Nazi-Nostalgie/Nazi-Nostalgia",
        "Regel #4.5 - Propaganda",
        "Regel #5 - Werbung",
        "Regel #6 - Infos zu Privatpersonen",
        "Regel #7.1 - Bildqualität",
        "Regel #7.2 - Repost",
        "Regel #7.3 - Müllpost/Privatpost",
        "Regel #7.4 - Raid",
        "Regel #8 - Ähnliche Bilder in Reihe",
        "Regel #9.1 - Tag-Vandalismus",
        "Regel #9.2 - Spam/Kommentarvandalismus",
        "Regel #10 - früher Spoiler",
        "Regel #12 - Warez/Logins zu Pay Sites",
        "Regel #13 - übermäßiges Beleidigen/Hetzen",
        "Regel #14 - Screamer/Sound-getrolle",
        "Regel #15.1 - reiner Musikupload",
        "Regel #15.2 - Musikvideo")

private val DefaultAdminReasons = listOf(
        "DMCA (Copyright) Anfrage",
        "Rechtswidriger Inhalt",
        "Auf Anfrage",
        "Regel #1 - Bild unzureichend getagged (nsfp/nsfw/nsfl)",
        "Regel #2.1 - Suggestive oder nackte Darstellung von Minderjährigen",
        "Regel #2.2 - Rohe Gewalt an Minderjährigen",
        "Regel #3.1 - Tierquälerei",
        "Regel #3.2 - Zoophilie",
        "Regel #4.1 - Stumpfer Rassismus/rechtes Gedankengut",
        "Regel #4.2 - Hetze",
        "Regel #4.3 - Aufruf zu Gewalt",
        "Regel #4.4 - Nazi-Nostalgie/Nazi-Nostalgia",
        "Regel #4.5 - Propaganda",
        "Regel #5 - Werbung",
        "Regel #6 - Infos zu Privatpersonen",
        "Regel #7.1 - Bildqualität",
        "Regel #7.2 - Repost",
        "Regel #7.3 - Müllpost/Privatpost",
        "Regel #7.4 - Raid",
        "Regel #8 - Ähnliche Bilder in Reihe",
        "Regel #9.1 - Tag-Vandalismus",
        "Regel #9.2 - Spam/Kommentarvandalismus",
        "Regel #10 - früher Spoiler",
        "Regel #12 - Warez/Logins zu Pay Sites",
        "Regel #13 - übermäßiges Beleidigen/Hetzen",
        "Regel #14 - Screamer/Sound-getrolle",
        "Regel #15.1 - reiner Musikupload",
        "Regel #15.2 - Musikvideo",
        "Regel #16.1 - unnötiges Markieren",
        "Regel #16.2 - Missbrauch der Melden-Funktion")

private val DefaultUserClasses = listOf(
        Config.UserClass("#FFFFFF", "Schwuchtel"),
        Config.UserClass("#E108E9", "Neuschwuchtel"),
        Config.UserClass("#5BB91C", "Altschwuchtel"),
        Config.UserClass("#FF9900", "Admin"),
        Config.UserClass("#444444", "Gesperrt"),
        Config.UserClass("#008FFF", "Moderator"),
        Config.UserClass("#6C432B", "Fliesentischbesitzer"),
        Config.UserClass("#1cb992", "Lebende Legende", symbol = "\u25c6"),
        Config.UserClass("#d23c22", "Wichtel", symbol = "\u25a7"),
        Config.UserClass("#1cb992", "Edler Spender"),
        Config.UserClass("#addc8d", "Mittelaltschwuchtel"),
        Config.UserClass("#7fc7ff", "Alt-Moderator"),
        Config.UserClass("#c52b2f", "Community Helfer", symbol = "\u2764\uFE0E"),
        Config.UserClass("#10366f", "Nutzer-Bot"),
        Config.UserClass("#ffc166", "System-Bot"),
        Config.UserClass("#ea9fa1", "Alt-Helfer"),
)
