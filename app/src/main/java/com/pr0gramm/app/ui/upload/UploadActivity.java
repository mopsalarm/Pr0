package com.pr0gramm.app.ui.upload;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.services.UploadService;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;

import javax.inject.Inject;

import static com.pr0gramm.app.services.ThemeHelper.theme;

/**
 */
public class UploadActivity extends BaseAppCompatActivity implements ChooseMediaTypeFragment.Listener {
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

            uploadService.checkIsRateLimited().compose(bindToLifecycleAsync()).subscribe(limited -> {
                if (!limited) {
                    String action = null, mediaType = null;

                    // get from intent
                    Intent intent = getIntent();
                    if (intent != null) {
                        action = intent.getAction();
                        mediaType = intent.getStringExtra(UploadFragment.EXTRA_MEDIA_TYPE);
                    }

                    if (Intent.ACTION_SEND.equals(action)) {
                        showUploadFragment(null, false);

                    } else if (mediaType != null) {
                        showUploadFragment(mediaType, false);

                    } else {
                        showChooseMediaTypeFragment();
                    }

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

    private void showChooseMediaTypeFragment() {
        show(new ChooseMediaTypeFragment(), false);
    }

    private void showSomethingWentWrong() {
        show(new SomethingWentWrongFragment(), false);
    }

    private void showUploadLimitReached() {
        show(new UploadLimitReachedFragment(), false);
    }

    void showUploadFragment(String type, boolean addToBackstack) {
        Bundle arguments = new Bundle();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri url = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            arguments.putParcelable(UploadFragment.EXTRA_LOCAL_URI, url);
        } else {
            arguments.putString(UploadFragment.EXTRA_MEDIA_TYPE, type);
        }

        Fragment fragment = new UploadFragment();
        fragment.setArguments(arguments);
        show(fragment, addToBackstack);
    }

    private void show(Fragment fragment, boolean addToBackstack) {
        @SuppressLint("CommitTransaction")
        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment);

        if (addToBackstack) {
            transaction.addToBackStack(null);
        }

        transaction.commitAllowingStateLoss();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMediaTypeChosen(String type) {
        showUploadFragment(type, true);
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

    public static void openForType(Context context, String mediaType) {
        Intent intent = new Intent(context, UploadActivity.class);
        intent.putExtra(UploadFragment.EXTRA_MEDIA_TYPE, mediaType);
        context.startActivity(intent);
    }

    public static final String MEDIA_TYPE_IMAGE = "image/*";
    public static final String MEDIA_TYPE_VIDEO = "video/*";
}
