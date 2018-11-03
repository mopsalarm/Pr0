package com.pr0gramm.app.ui.upload

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UploadService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import kotlinx.coroutines.launch
import org.kodein.di.erased.instance

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

            launch {
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
        val mediaType: String? = intent?.getStringExtra(EXTRA_MEDIA_TYPE)

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

    internal fun showUploadFragment(type: String?, addToBackstack: Boolean) {
        val fragment = if (intent?.action == Intent.ACTION_SEND) {
            val url = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            UploadFragment.forLocalUri(url)
        } else {
            UploadFragment.forMediaType(type)
        }

        show(fragment, addToBackstack)
    }

    private fun show(fragment: androidx.fragment.app.Fragment, addToBackstack: Boolean) {
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

    override fun onMediaTypeChosen(type: String) {
        showUploadFragment(type, addToBackstack = true)
    }

    class CheckUploadAllowedFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_upload_check, container, false)
        }
    }

    class UploadLimitReachedFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_upload_limit_reached, container, false)
        }
    }

    class SomethingWentWrongFragment : androidx.fragment.app.Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.fragment_upload_something_went_wrong, container, false)
        }
    }

    companion object {
        const val MEDIA_TYPE_IMAGE = "image/*"
        const val MEDIA_TYPE_VIDEO = "video/*"

        const val EXTRA_MEDIA_TYPE = "UploadActivity.mediaType"

        @JvmStatic
        fun openForType(context: Context, mediaType: String) {
            val intent = Intent(context, UploadActivity::class.java)
            intent.putExtra(EXTRA_MEDIA_TYPE, mediaType)
            context.startActivity(intent)
        }
    }
}
