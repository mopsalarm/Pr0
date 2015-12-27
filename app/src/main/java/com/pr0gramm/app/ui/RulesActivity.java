package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.widget.TextView;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.RulesService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import javax.inject.Inject;

import butterknife.Bind;

/**
 * A new activity that just shows the rules
 */
public class RulesActivity extends BaseAppCompatActivity {
    @Bind(R.id.small_print)
    TextView rulesView;

    @Inject
    RulesService rulesService;

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        rulesService.displayInto(rulesView);
    }
}
