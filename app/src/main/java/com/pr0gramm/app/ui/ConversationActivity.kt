package com.pr0gramm.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.transaction
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.ConversationFragment
import com.pr0gramm.app.util.find


/**
 * The activity that displays the inbox.
 */
class ConversationActivity : BaseAppCompatActivity("ConversationActivity") {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_conversation)
        setSupportActionBar(find(R.id.toolbar))

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // restore previously selected tab
        if (savedInstanceState == null) {
            handleNewIntent(intent)
        }

        // track if we've clicked the notification!
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.inboxNotificationClosed("clicked")
        }

        // inboxService.markAsRead(intent.getLongExtra(EXTRA_MESSAGE_TIMESTAMP, 0))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> false
        }
    }

    private fun handleNewIntent(intent: Intent?) {
        if (intent != null && intent.extras != null) {
            val extras = intent.extras ?: return

            val name = extras.getString(EXTRA_CONVERSATION_NAME) ?: return
            val fragment = ConversationFragment().apply { conversationName = name }

            supportFragmentManager.transaction {
                add(R.id.content, fragment)
            }
        }
    }

    companion object {
        const val EXTRA_FROM_NOTIFICATION = "ConversationActivity.fromNotification"
        const val EXTRA_CONVERSATION_NAME = "ConversationActivity.name"

        fun start(context: Context, name: String) {
            context.startActivity(Intent(context, ConversationActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_NAME, name)
            })
        }
    }
}
