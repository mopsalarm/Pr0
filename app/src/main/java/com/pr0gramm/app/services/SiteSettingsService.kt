package com.pr0gramm.app.services

import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.sitesettings.SiteSettings
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.tryEnumValueOf
import com.squareup.moshi.adapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onStart

class SyncSiteSettingsService(
    private val userService: UserService,
) {
    private val logger = Logger("SyncSiteSettingsService")

    init {
        doInBackground {
            // if the user activates sync, we sync data to the cloud.
            Settings.changes { syncSiteSettings }.collectLatest { syncSiteSettings ->
                if (syncSiteSettings) {
                    logger.info { "Sync settings changed, syncing now." }
                    catchAll { syncToServer() }
                }
            }
        }

        doInBackground {
            // If user changes something we sync if sync is currently enabled.
            // onStart/distinctUntilChange/drop is needed to only sync the first time something really did change.
            Settings.changes { localSiteSettings() }
                .onStart { emit(localSiteSettings()) }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { siteSettings ->
                    if (Settings.syncSiteSettings) {
                        logger.info { "Settings changed, syncing now." }
                        catchAll { syncToServer(siteSettings) }
                    }
                }
        }
    }

    private suspend fun syncToServer(settings: SiteSettings = localSiteSettings()) {
        userService.storeSiteSettings(
            MoshiInstance
                .adapter<SiteSettings>()
                .toJsonValue(settings) as Map<String, Any?>
        )
    }
}

class SiteSettingsService {
    private val logger = Logger("SiteSettingsService")

    /**
     * Called by the sync service to update the local settings.
     */
    fun updateLocalSettings(remoteValues: Map<String, Any?>) {
        if (!Settings.syncSiteSettings) {
            logger.info { "Not updating local settings, sync is disabled." }
            return
        }

        // clean values
        val remoteValues = remoteValues.mapValues { (_, value) ->
            when (value) {
                is Number -> value.toInt()
                else -> value
            }
        }

        val currentSettings = localSiteSettings()

        // get local settings as a fallback for empty values
        val fallbackValues = MoshiInstance
            .adapter<SiteSettings>()
            .toJsonValue(currentSettings) as Map<String, Any?>

        logger.info { "Got fallbackValues=$fallbackValues and remote values $remoteValues" }

        // merge and parse
        val newSettings = fallbackValues + remoteValues
        val settings = MoshiInstance.adapter<SiteSettings>().fromJsonValue(newSettings)!!

        logger.info { "Wants to update local settings to $settings" }

        if (currentSettings == settings) {
            logger.info { "Settings have not changed." }
            return
        }

        logger.info { "Setting local settings: $settings" }

        // apply settings
        Settings.alwaysShowAds = settings.showAds
        Settings.upvoteOnCollect = settings.favUpvote
        Settings.useSecondaryServers = settings.secondaryServers
        Settings.themeName = themeIdToThemeName(settings.themeId)
        Settings.backup = settings.enableItemHistory
        Settings.markItemsAsSeen = settings.markSeenItems
    }
}

/**
 * Build a set of local site settings from the apps Settings instance
 */
private fun localSiteSettings(): SiteSettings {
    return SiteSettings(
        themeId = themeNameToThemeId(Settings.themeName),
        showAds = Settings.alwaysShowAds,
        favUpvote = Settings.upvoteOnCollect,
        secondaryServers = Settings.useSecondaryServers,
        enableItemHistory = Settings.backup,
        markSeenItems = Settings.markItemsAsSeen,
    )
}

private fun themeNameToThemeId(name: String): Int {
    return tryEnumValueOf<Themes>(name)?.ordinal ?: 0
}

private fun themeIdToThemeName(themeId: Int): String {
    val theme = Themes.values().getOrNull(themeId) ?: Themes.ORANGE
    return theme.name
}