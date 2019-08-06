package com.pr0gramm.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.llamalab.safs.Path
import com.llamalab.safs.Paths
import com.llamalab.safs.android.AndroidFiles
import com.pr0gramm.app.feed.ContentType
import com.pr0gramm.app.services.ShareService
import com.pr0gramm.app.ui.Themes
import com.pr0gramm.app.ui.fragments.IndicatorStyle
import com.pr0gramm.app.util.getStringOrNull
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

    val downloadTarget: Path
        get() {
            val defaultValue by lazy(LazyThreadSafetyMode.NONE) {
                AndroidFiles
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .resolve("pr0gramm")
            }

            val path = preferences.getStringOrNull("pref_download_path")
            return path?.let { Paths.get(path) } ?: defaultValue
        }

    val seenIndicatorStyle: IndicatorStyle
        get() {
            val value = preferences.getString("pref_seen_indicator_style", null)
            return tryEnumValueOf<IndicatorStyle>(value) ?: IndicatorStyle.NONE
        }

    val doubleTapToUpvote: Boolean
        get() = preferences.getBoolean("pref_double_tap_to_upvote", true)

    val doubleTapToSeek: Boolean
        get() = preferences.getBoolean("pref_double_tap_to_seek", true)

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

    var feedStartWithUri: Uri?
        get() = preferences.getStringOrNull("pref_feed_start_with_uri")?.let { Uri.parse(it) }
        set(uri) = preferences.edit { putString("pref_feed_start_with_uri", uri?.toString()) }

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

    val volumeNavigation: VolumeNavigationType
        get() {
            val pref = preferences.getString("pref_volume_navigation", null) ?: ""
            val value = tryEnumValueOf<VolumeNavigationType>(pref.toUpperCase())
            return value ?: VolumeNavigationType.DISABLED
        }

    val showContentTypeFlag: Boolean
        get() = preferences.getBoolean("pref_show_content_type_flag_2", false)

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

    val imageSearchEngine: ShareService.ImageSearchEngine
        get() {
            val pref = preferences.getString("pref_image_search_engine", null) ?: ""
            val value = tryEnumValueOf<ShareService.ImageSearchEngine>(pref.toUpperCase())
            return value ?: ShareService.ImageSearchEngine.GOOGLE
        }

    val useDoH: Boolean
        get() = preferences.getBoolean("pref_use_doh", true)

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
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true)
            instance = Settings(context.applicationContext as Application)

            // Add some random string to the settings. We do this, so that we can better
            // analyse the setting selection and know what the users want. This is completelly
            // anonymous.
            if (!instance.preferences.contains("__unique_settings_id")) {
                instance.edit {
                    putString("__unique_settings_id", UUID.randomUUID().toString())
                }
            }
        }

        fun get(): Settings {
            return instance
        }
    }
}
