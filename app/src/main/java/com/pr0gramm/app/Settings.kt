package com.pr0gramm.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.ShareService
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.util.getEnumValue
import com.pr0gramm.app.util.getStringOrNull
import com.pr0gramm.app.util.tryEnumValueOf
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.*

/**
 */
class Settings(private val app: Application) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferenceChanged = BroadcastChannel<String>(1)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(app)

    init {
        this.preferences.registerOnSharedPreferenceChangeListener(this)
    }

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

//    val downloadTarget: Path
//        get() {
//            val defaultValue by lazy(LazyThreadSafetyMode.NONE) {
//                AndroidFiles
//                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//                        .resolve("pr0gramm")
//            }
//
//            val path = preferences.getStringOrNull("pref_download_path")
//            return path?.let { Paths.get(path) } ?: defaultValue
//        }

    val downloadTarget2: Uri?
        get() {
            val path = preferences.getStringOrNull("pref_download_path")
            return path?.let { Uri.parse(path) }
        }

    val markItemsAsSeen: Boolean
        get() = preferences.getBoolean("pref_mark_items_as_seen", false)

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
                prefValue = app.getString(R.string.pref_confirm_play_on_mobile_default)
            }

            for (enumValue in ConfirmOnMobile.values()) {
                if (app.getString(enumValue.value) == prefValue) {
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
        set(uri) = preferences.edit { putString("pref_feed_start_with_uri", uri?.toString()) }

    val feedStartAtSfw: Boolean
        get() = preferences.getBoolean("pref_feed_start_at_sfw", false)

    val showCategoryRandom: Boolean
        get() = preferences.getBoolean("pref_show_category_random", true)

    val showCategoryControversial: Boolean
        get() = preferences.getBoolean("pref_show_category_controversial", true)

    val showCategoryPremium: Boolean
        get() = preferences.getBoolean("pref_show_category_premium", true)

    val useIncognitoBrowser: Boolean
        get() = preferences.getBoolean("pref_use_incognito_browser", false)

    val overrideYouTubeLinks: Boolean
        get() = preferences.getBoolean("pref_override_youtube_links", false)

    val themeName: String
        get() = preferences.getString("pref_theme", Themes.ORANGE.name)!!

    val mockApi: Boolean
        get() = BuildConfig.DEBUG && preferences.getBoolean("pref_debug_mock_api", false)

    val enableQuickPeek: Boolean
        get() = preferences.getBoolean("pref_enable_quick_peek", true)

    val showContentTypeFlag: Boolean
        get() = preferences.getBoolean("pref_show_content_type_flag_2", false)

    val alwaysShowAds: Boolean
        get() = preferences.getBoolean("pref_always_show_ads", false)

    val rotateInFullscreen: Boolean
        get() = preferences.getBoolean("pref_rotate_in_fullscreen", true)

    val audioFocusTransient: Boolean
        get() = preferences.getBoolean("pref_audiofocus_transient", false)

    val useTopTagAsTitle: Boolean
        get() = preferences.getBoolean("pref_use_tag_as_title", true)

    val secureApp: Boolean
        get() = preferences.getBoolean("pref_secure_app", false)

    val backup: Boolean
        get() = preferences.getBoolean("pref_sync_backup", true)

    val singleTapAction: TapAction
        get() = preferences.getEnumValue("pref_single_tap_action", TapAction.NONE)

    val doubleTapAction: TapAction
        get() = preferences.getEnumValue("pref_double_tap_action", TapAction.UPVOTE)

    val imageSearchEngine: ShareService.ImageSearchEngine
        get() {
            val pref = preferences.getString("pref_image_search_engine", null) ?: ""
            val value = tryEnumValueOf<ShareService.ImageSearchEngine>(pref.toUpperCase(Locale.ROOT))
            return value ?: ShareService.ImageSearchEngine.IMGOPS
        }

    val privateInput: Boolean
        get() = preferences.getBoolean("pref_private_input", false)

    val colorfulCommentLines: Boolean
        get() = preferences.getBoolean("pref_colorful_comment_lines", false)

    fun resetContentTypeSettings() {
        // reset settings.
        preferences.edit {
            putBoolean("pref_feed_type_sfw", true)
            putBoolean("pref_feed_type_nsfw", false)
            putBoolean("pref_feed_type_nsfl", false)
        }
    }

    fun raw(): SharedPreferences {
        return preferences
    }

    fun edit(edits: SharedPreferences.Editor.() -> Unit) {
        return preferences.edit { edits() }
    }

    /**
     * An observable that reacts to changes of properties.
     * All actions are happening on the main thread.
     */
    fun changes(): Flow<String> {
        return preferenceChanged.asFlow()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        preferenceChanged.offer(key)
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
        FAVORITE,
        FULLSCREEN
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: Settings

        fun initialize(context: Context) {
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
            instance = Settings(context.applicationContext as Application)

            val p = instance.preferences

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
                    putString("pref_single_tap_action", TapAction.FULLSCREEN.name)
                    remove("pref_single_tap_for_fullscreen")
                }
            }

            // migrate the old "double tap for upvote" property.
            if (!p.getBoolean("pref_double_tap_to_upvote", true)) {
                p.edit {
                    putString("pref_double_tap_action", TapAction.NONE.name)
                    remove("pref_double_tap_to_upvote")
                }
            }

            // Add some random string to the settings. We do this, so that we can better
            // analyse the setting selection and know what the users want. This is completelly
            // anonymous.
            if (!p.contains("__unique_settings_id")) {
                p.edit {
                    putString("__unique_settings_id", UUID.randomUUID().toString())
                }
            }
        }

        fun get(): Settings {
            return instance
        }
    }
}
