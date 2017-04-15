package com.pr0gramm.app.ui

import android.os.Bundle
import android.widget.TextView
import com.github.salomonbrys.kodein.instance
import com.pr0gramm.app.ActivityComponent
import com.pr0gramm.app.R
import com.pr0gramm.app.services.RulesService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import kotterknife.bindView

/**
 * A new activity that just shows the rules
 */
class RulesActivity : BaseAppCompatActivity() {
    private val rulesView: TextView by bindView(R.id.small_print)
    private val rulesService: RulesService by instance()

    override fun injectComponent(appComponent: ActivityComponent) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_rules)

        rulesService.displayInto(rulesView)
    }
}
