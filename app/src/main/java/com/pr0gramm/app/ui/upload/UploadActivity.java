package com.pr0gramm.app.ui.upload;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UploadService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static com.pr0gramm.app.services.ThemeHelper.theme;

/**
 */
public class UploadActivity extends BaseAppCompatActivity {
    private static final Logger logger = LoggerFactory.getLogger("UploadActivity");

    @Inject
    UploadService uploadService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(theme().basic);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_upload);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            CheckUploadAllowedFragment fragment = new CheckUploadAllowedFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();

            uploadService.checkIsRateLimited().compose(bindToLifecycle()).subscribe(limited -> {
                if (!limited) {
                    showUploadFragment();

                } else {
                    showUploadLimitReached();
                }
            }, this::onError);
        }
    }

    @Override
    protected void injectComponent(ActivityComponent appComponent) {
        appComponent.inject(this);
    }

    private void onError(Throwable throwable) {
        showSomethingWentWrong();
    }

    private void showSomethingWentWrong() {
        Fragment fragment = new SomethingWentWrongFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void showUploadLimitReached() {
        Fragment fragment = new UploadLimitReachedFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void showUploadFragment() {
        Bundle arguments = new Bundle();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri url = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            arguments.putParcelable(UploadFragment.EXTRA_LOCAL_URI, url);
        }

        Fragment fragment = new UploadFragment();
        fragment.setArguments(arguments);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class CheckUploadAllowedFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_upload_check, container, false);
        }
    }

    public static class UploadLimitReachedFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_upload_limit_reached, container, false);
        }
    }

    public static class SomethingWentWrongFragment extends Fragment {
        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_upload_something_went_wrong, container, false);
        }
    }
}
