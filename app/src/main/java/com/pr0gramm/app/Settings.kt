package com.pr0gramm.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.ShareService
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.util.VideoQuality
import com.pr0gramm.app.util.getEnumValue
import com.pr0gramm.app.util.getStringOrNull
import com.pr0gramm.app.util.tryEnumValueOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.*

/**
 */
object Settings : SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var context: Application
    private lateinit var preferences: SharedPreferences

    private val preferenceChanged = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val contentTypeSfw: Boolean
        get() = preferences.getBoolean("pref_feed_type_sfw", true)

    val contentTypeNsfw: Boolean
        get() = preferences.getBoolean("pref_feed_type_nsfw", false)

    val contentTypeNsfl: Boolean
        get() = preferences.getBoolean("pref_feed_type_nsfl", false)

    /**
     * Gets a set of all selected content types. This gets called only for logged in users.
     */
    val contentType: EnumSet<ContentType>
        get() {
            val result = EnumSet.of(ContentType.SFW, ContentType.NSFW, ContentType.NSFL)

            if (!contentTypeNsfl)
                result.remove(ContentType.NSFL)

            if (!contentTypeNsfw)
                result.remove(ContentType.NSFW)

            if (result.size > 1 && !contentTypeSfw)
                result.remove(ContentType.SFW)

            if (result.contains(ContentType.SFW))
                result.add(ContentType.NSFP)

            return result
        }

    val downloadTreeUri: Uri?
        get() {
            val path = preferences.getStringOrNull("pref_download_tree_uri")
            return path?.let { Uri.parse(path) }
        }

    val highlightItemsInFeed: Boolean
        get() = preferences.getBoolean("pref_highlight_items_in_feed", true)

    var markItemsAsSeen: Boolean
        get() = preferences.getBoolean("pref_mark_items_as_seen", false)
        set(value) = edit { putBoolean("pref_mark_items_as_seen", value) }

    val fancyScrollVertical: Boolean
        get() = preferences.getBoolean("pref_fancy_scroll_vertical", true)

    val fancyScrollHorizontal: Boolean
        get() = preferences.getBoolean("pref_fancy_scroll_horizontal", true)

    val showPinButton: Boolean
        get() = preferences.getBoolean("pref_show_pin_button", true)

    val showRefreshButton: Boolean
        get() = preferences.getBoolean("pref_show_refresh_button", true)

    val useBetaChannel: Boolean
        get() = preferences.getBoolean("pref_use_beta_channel", false)

    val confirmPlayOnMobile: ConfirmOnMobile
        get() {
            var prefValue = preferences.getString("pref_confirm_play_on_mobile_list", null)
            if (prefValue == null) {
                prefValue = context.getString(R.string.pref_confirm_play_on_mobile_default)
            }

            for (enumValue in ConfirmOnMobile.values()) {
                if (context.getString(enumValue.value) == prefValue) {
                    return enumValue
                }
            }

            return ConfirmOnMobile.VIDEO
        }

    val loadHqInZoomView: Boolean
        get() = preferences.getBoolean("pref_load_hq_image_in_zoomview", false)

    val hideTagVoteButtons: Boolean
        get() = preferences.getBoolean("pref_hide_tag_vote_buttons", false)

    val feedStartAtNew: Boolean
        get() = preferences.getBoolean("pref_feed_start_at_new", false)

    val tagCloudView: Boolean
        get() = preferences.getBoolean("pref_tag_cloud_view", false)

    var feedStartWithUri: Uri?
        get() = preferences.getStringOrNull("pref_feed_start_with_uri")?.let { Uri.parse(it) }
        set(uri) = edit { putString("pref_feed_start_with_uri", uri?.toString()) }

    val feedStartAtSfw: Boolean
        get() = preferences.getBoolean("pref_feed_start_at_sfw", false)

    val showCategoryRandom: Boolean
        get() = preferences.getBoolean("pref_show_category_random", true)

    val showCategoryControversial: Boolean
        get() = preferences.getBoolean("pref_show_category_controversial", true)

    val showCategoryStalk: Boolean
        get() = preferences.getBoolean("pref_show_category_premium", true)

    val useIncognitoBrowser: Boolean
        get() = preferences.getBoolean("pref_use_incognito_browser", false)

    val overrideYouTubeLinks: Boolean
        get() = preferences.getBoolean("pref_override_youtube_links", false)

    var themeName: String
        get() = preferences.getString("pref_theme", Themes.ORANGE.name)!!
        set(value) = edit { putString("pref_theme", value) }

    var upvoteOnCollect: Boolean
        get() = preferences.getBoolean("pref_upvote_on_collect", false)
        set(value) = edit { putBoolean("pref_upvote_on_collect", value) }

    val enableQuickPeek: Boolean
        get() = preferences.getBoolean("pref_enable_quick_peek", true)

    val showContentTypeFlag: Boolean
        get() = preferences.getBoolean("pref_show_content_type_flag_2", false)

    var alwaysShowAds: Boolean
        get() = preferences.getBoolean("pref_always_show_ads", false)
        set(value) = edit { putBoolean("pref_always_show_ads", value) }

    val rotateInFullscreen: Boolean
        get() = preferences.getBoolean("pref_rotate_in_fullscreen", true)

    val audioFocusTransient: Boolean
        get() = preferences.getBoolean("pref_audiofocus_transient", false)

    val useTopTagAsTitle: Boolean
        get() = preferences.getBoolean("pref_use_tag_as_title", true)

    val secureApp: Boolean
        get() = preferences.getBoolean("pref_secure_app", false)

    var backup: Boolean
        get() = preferences.getBoolean("pref_sync_backup", true)
        set(value) = edit { putBoolean("pref_sync_backup", value) }

    val singleTapAction: TapAction
        get() = preferences.getEnumValue("pref_single_tap_action", TapAction.NONE)

    val doubleTapAction: TapAction
        get() = preferences.getEnumValue("pref_double_tap_action", TapAction.UPVOTE)

    val imageSearchEngine: ShareService.ImageSearchEngine
        get() {
            val pref = preferences.getString("pref_image_search_engine", null) ?: ""
            val value = tryEnumValueOf<ShareService.ImageSearchEngine>(pref.lowercase())
            return value ?: ShareService.ImageSearchEngine.IMGOPS
        }

    val videoQuality: VideoQuality
        get() = preferences.getEnumValue("pref_video_quality_list", VideoQuality.Adaptive)

    val privateInput: Boolean
        get() = preferences.getBoolean("pref_private_input", false)

    val colorfulCommentLines: Boolean
        get() = preferences.getBoolean("pref_colorful_comment_lines", false)

    val useDoH: Boolean
        get() = preferences.getBoolean("pref_use_doh2", true)

    val rotateVoteView: Boolean
        get() = preferences.getBoolean("pref_rotate_vote_view", false)

    val syncSiteSettings: Boolean
        get() = preferences.getBoolean("pref_sync_site_settings", false)

    var useSecondaryServers: Boolean
        get() = preferences.getBoolean("pref_use_secondary_servers", false)
        set(value) = edit { putBoolean("pref_use_secondary_servers", value) }

    fun resetContentTypeSettings() {
        // reset settings.
        edit {
            putBoolean("pref_feed_type_sfw", true)
            putBoolean("pref_feed_type_nsfw", false)
            putBoolean("pref_feed_type_nsfl", false)
        }
    }

    fun raw(): SharedPreferences {
        return preferences
    }

    inline fun edit(crossinline edits: SharedPreferences.Editor.() -> Unit) {
        return raw().edit { edits() }
    }

    /**
     * An observable that reacts to changes of properties.
     * All actions are happening on the main thread.
     */
    fun changes(): Flow<String> {
        return preferenceChanged
    }

    enum class ConfirmOnMobile(val value: Int) {
        NONE(R.string.pref_confirm_play_on_mobile_values__play_direct),
        VIDEO(R.string.pref_confirm_play_on_mobile_values__video_only),
        ALL(R.string.pref_confirm_play_on_mobile_values__all)
    }

    enum class TapAction {
        NONE,
        UPVOTE,
        DOWNVOTE,
        FULLSCREEN,
        COLLECT,
    }

    fun <T> changes(what: Settings.() -> T): Flow<T> {
        return changes().map { this.what() }.distinctUntilChanged()
    }

    fun initialize(context: Context) {
        PreferenceManager.setDefaultValues(context, R.xml.preferences, true)

        this.context = context.applicationContext as Application
        this.preferences = PreferenceManager.getDefaultSharedPreferences(Settings.context)

        migrate(preferences)

        // react to changes
        // Caution: The preference manager does not currently store a strong reference to the listener.
        // You must store a strong reference to the listener, or it will be susceptible to garbage collection.
        //   ... nice
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != null) {
            Logger("Settings").debug { "Key was updated: $key" }
            preferenceChanged.tryEmit(key)
        }
    }
}

@Suppress("SameParameterValue")
private fun migrate(p: SharedPreferences) {
    p.edit {
        putString("_supported_abi", Build.SUPPORTED_ABIS.joinToString(","))
    }

    // migrate the old "mark item as seen" property.
    p.getStringOrNull("pref_seen_indicator_style")?.let { previousValue ->
        p.edit {
            putBoolean("pref_mark_items_as_seen", previousValue != "NONE")
            remove("pref_seen_indicator_style")
        }
    }

    // migrate the old "single tap for fullscreen" property.
    if (p.getBoolean("pref_single_tap_for_fullscreen", false)) {
        p.edit {
            putString("pref_single_tap_action", Settings.TapAction.FULLSCREEN.name)
            remove("pref_single_tap_for_fullscreen")
        }
    }

    // migrate the old "double tap for upvote" property.
    if (!p.getBoolean("pref_double_tap_to_upvote", true)) {
        p.edit {
            putString("pref_double_tap_action", Settings.TapAction.NONE.name)
            remove("pref_double_tap_to_upvote")
        }
    }

    listOf("pref_single_tap_action", "pref_double_tap_action").forEach { pref ->
        // migrate the old favorite actions
        if (p.getStringOrNull(pref) == "FAVORITE") {
            p.edit {
                putString(pref, Settings.TapAction.COLLECT.name)
            }
        }
    }

    // Add some random string to the settings. We do this, so that we can better
    // analyse the setting selection and know what the users want. This is completely
    // anonymous.
    if (!p.contains("__unique_settings_id")) {
        p.edit {
            putString("__unique_settings_id", UUID.randomUUID().toString())
        }
    }
}
