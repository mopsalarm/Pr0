package com.pr0gramm.app.ui;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * A new activity that just shows the rules
 */
public class RulesActivity extends AppCompatActivity {
    @Bind(R.id.small_print)
    TextView rulesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);
        ButterKnife.bind(this);

        // add the small print to the view
        int offset = getResources().getDimensionPixelSize(R.dimen.bullet_list_leading_margin);
        rulesView.setText(AndroidUtility.makeBulletList(offset,
                getResources().getStringArray(R.array.upload_small_print)));

    }
}
