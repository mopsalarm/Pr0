package com.pr0gramm.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.ShareHelper
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.ui.fragments.IndicatorStyle
import com.pr0gramm.app.util.edit
import com.pr0gramm.app.util.tryEnumValueOf
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

/**
 */
class Settings(private val app: Application) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferenceChanged = PublishSubject.create<String>()
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

    val convertGifToWebm: Boolean
        get() = preferences.getBoolean("pref_convert_gif_to_webm", false)

    val downloadLocation: String
        get() {
            val def = app.getString(R.string.pref_downloadLocation_default)
            return preferences.getString("pref_downloadLocation", def)!!
        }

    val seenIndicatorStyle: IndicatorStyle
        get() {
            val value = preferences.getString("pref_seen_indicator_style", null)
            return tryEnumValueOf<IndicatorStyle>(value) ?: IndicatorStyle.NONE
        }

    val doubleTapToUpvote: Boolean
        get() = preferences.getBoolean("pref_double_tap_to_upvote", true)

    val fullscreenZoomView: Boolean
        get() = preferences.getBoolean("pref_fullscreen_zoom_view", true)

    val showPinButton: Boolean
        get() = preferences.getBoolean("pref_show_pin_button", true)

    val showRefreshButton: Boolean
        get() = preferences.getBoolean("pref_show_refresh_button", true)

    val useBetaChannel: Boolean
        get() = preferences.getBoolean("pref_use_beta_channel", false)

    val showNotifications: Boolean
        get() = preferences.getBoolean("pref_show_notifications", true)

    val keepScreenOn: Boolean
        get() = preferences.getBoolean("pref_keep_screen_on", true)

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

    val tagCloudView: Boolean
        get() = preferences.getBoolean("pref_tag_cloud_view", false)

    val feedStartAtNew: Boolean
        get() = preferences.getBoolean("pref_feed_start_at_new", false)

    val feedStartAtSfw: Boolean
        get() = preferences.getBoolean("pref_feed_start_at_sfw", false)

    val singleTapForFullscreen: Boolean
        get() = preferences.getBoolean("pref_single_tap_for_fullscreen", false)

    val showCategoryRandom: Boolean
        get() = preferences.getBoolean("pref_show_category_random", true)

    val showCategoryControversial: Boolean
        get() = preferences.getBoolean("pref_show_category_controversial", true)

    val showCategoryPremium: Boolean
        get() = preferences.getBoolean("pref_show_category_premium", true)

    val bestOfBenisThreshold: Int
        get() {
            val value = preferences.getString("pref_bestof_threshold", "2000")!!
            return value.toIntOrNull() ?: 0
        }

    val useIncognitoBrowser: Boolean
        get() = preferences.getBoolean("pref_use_incognito_browser", false)

    val overrideYouTubeLinks: Boolean
        get() = preferences.getBoolean("pref_override_youtube_links", false)

    val themeName: String
        get() = preferences.getString("pref_theme", Themes.ORANGE.name)!!

    val showCategoryBestOf: Boolean
        get() = bestOfBenisThreshold > 0

    val mockApi: Boolean
        get() = BuildConfig.DEBUG && preferences.getBoolean("pref_debug_mock_api", false)

    val enableQuickPeek: Boolean
        get() = preferences.getBoolean("pref_enable_quick_peek", true)

    val useExoPlayer: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                // new player is not yet supported
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // old player is not supported anymore.
                return true
            }

            return preferences.getBoolean("pref_use_exo_player", true)
        }

    val videoCodec: String
        get() = preferences.getString("pref_video_codec", "hardware")!!

    val audioCodec: String
        get() = preferences.getString("pref_audio_codec", "hardware")!!

    val disableAudio: Boolean
        get() = preferences.getBoolean("pref_disable_audio", false)

    val useTextureView: Boolean
        get() = preferences.getBoolean("pref_use_texture_view_new", true)

    val volumeNavigation: VolumeNavigationType
        get() {
            val pref = preferences.getString("pref_volume_navigation", null) ?: ""
            val value = tryEnumValueOf<VolumeNavigationType>(pref.toUpperCase())
            return value ?: VolumeNavigationType.DISABLED
        }

    val showCategoryText: Boolean
        get() = preferences.getBoolean("pref_show_category_text", true)

    val showContentTypeFlag: Boolean
        get() = preferences.getBoolean("pref_show_content_type_flag", true)

    val alwaysShowAds: Boolean
        get() = preferences.getBoolean("pref_always_show_ads", false)

    val rotateInFullscreen: Boolean
        get() = preferences.getBoolean("pref_rotate_in_fullscreen", true)

    val audioFocusTransient: Boolean
        get() = preferences.getBoolean("pref_audiofocus_transient", false)

    val secureApp: Boolean
        get() = preferences.getBoolean("pref_secure_app", false)

    val backup: Boolean
        get() = preferences.getBoolean("pref_sync_backup", true)

    val feedScrollOnBack: Boolean
        get() = preferences.getBoolean("pref_feed_scroll_on_back", true)

    val imageSearchEngine: ShareHelper.ImageSearchEngine
        get() {
            val pref = preferences.getString("pref_image_search_engine", null) ?: ""
            val value = tryEnumValueOf<ShareHelper.ImageSearchEngine>(pref.toUpperCase())
            return value ?: ShareHelper.ImageSearchEngine.GOOGLE
        }

    val privateInput: Boolean
        get() = preferences.getBoolean("pref_private_input", false)

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
    fun changes(): Observable<String> {
        return preferenceChanged.asObservable()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        preferenceChanged.onNext(key)
    }

    enum class ConfirmOnMobile(val value: Int) {
        NONE(R.string.pref_confirm_play_on_mobile_values__play_direct),
        VIDEO(R.string.pref_confirm_play_on_mobile_values__video_only),
        ALL(R.string.pref_confirm_play_on_mobile_values__all)
    }

    enum class VolumeNavigationType {
        DISABLED, UP, DOWN
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: Settings

        fun initialize(context: Context) {
            PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
            instance = Settings(context.applicationContext as Application)
        }

        fun get(): Settings {
            return instance
        }
    }
}
