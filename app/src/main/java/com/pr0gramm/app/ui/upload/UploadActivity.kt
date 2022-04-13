package com.pr0gramm.app.ui.upload

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.Fragment
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UploadService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.util.di.instance

/**
 */
class UploadActivity : BaseAppCompatActivity("UploadActivity"), ChooseMediaTypeFragment.Listener {
    private val uploadService: UploadService by instance()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_upload)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            val fragment = CheckUploadAllowedFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()

            launchUntilDestroy {
                try {
                    if (uploadService.checkIsRateLimited()) {
                        showUploadLimitReached()
                    } else {
                        limitCheckPassed()
                    }
                } catch (err: Throwable) {
                    showSomethingWentWrong()
                }
            }
        }
    }

    private fun limitCheckPassed() {
        val action: String? = intent?.action
        val mediaType: UploadMediaType? = intent
            ?.getIntExtra(EXTRA_MEDIA_TYPE, -1)
            ?.let { idx -> UploadMediaType.values().getOrNull(idx) }

        when {
            action == Intent.ACTION_SEND -> showUploadFragment(null, addToBackstack = false)
            mediaType != null -> showUploadFragment(mediaType, addToBackstack = false)
            else -> showChooseMediaTypeFragment()
        }
    }

    private fun showChooseMediaTypeFragment() {
        show(ChooseMediaTypeFragment(), addToBackstack = false)
    }

    private fun showSomethingWentWrong() {
        show(SomethingWentWrongFragment(), addToBackstack = false)
    }

    private fun showUploadLimitReached() {
        show(UploadLimitReachedFragment(), addToBackstack = false)
    }

    private fun showUploadFragment(type: UploadMediaType?, addToBackstack: Boolean) {
        val fragment = when {
            intent?.action == Intent.ACTION_SEND -> {
                val url = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                UploadFragment.forLocalUri(url)
            }

            else -> {
                UploadFragment.forMediaType(type ?: UploadMediaType.IMAGE)
            }
        }

        show(fragment, addToBackstack)
    }

    private fun show(fragment: Fragment, addToBackstack: Boolean) {
        @SuppressLint("CommitTransaction")
        val transaction = supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if (addToBackstack) {
            transaction.addToBackStack(null)
        }

        transaction.commitAllowingStateLoss()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onMediaTypeChosen(type: UploadMediaType) {
        showUploadFragment(type, addToBackstack = true)
    }

    class CheckUploadAllowedFragment : Fragment(R.layout.fragment_upload_check)
    class UploadLimitReachedFragment : Fragment(R.layout.fragment_upload_limit_reached)
    class SomethingWentWrongFragment : Fragment(R.layout.fragment_upload_something_went_wrong)

    companion object {
        const val MEDIA_TYPE_IMAGE = "image/*"
        const val MEDIA_TYPE_VIDEO = "video/*"

        const val EXTRA_MEDIA_TYPE = "UploadActivity.mediaType"


        fun openForType(context: Context, mediaType: UploadMediaType) {
            val intent = Intent(context, UploadActivity::class.java)
            intent.putExtra(EXTRA_MEDIA_TYPE, mediaType.ordinal)
            context.startActivity(intent)
        }
    }

}
