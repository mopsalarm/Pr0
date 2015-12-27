package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RequestCodes;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.RulesService;
import com.pr0gramm.app.services.SingleShotService;
import com.pr0gramm.app.services.UploadService;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.ui.base.BaseAppCompatActivity;
import com.pr0gramm.app.ui.base.BaseFragment;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.views.BusyIndicator;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.pr0gramm.app.util.ErrorFormatting;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.trello.rxlifecycle.FragmentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.Observable;
import rx.util.async.Async;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;

/**
 */
public class UploadActivity extends BaseAppCompatActivity {
    private static final Logger logger = LoggerFactory.getLogger("UploadActivity");

    @Inject
    UploadService uploadService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    public static class UploadFragment extends BaseFragment {
        public static final String EXTRA_LOCAL_URI = "UploadFragment.localUri";

        @Inject
        Picasso picasso;

        @Inject
        UploadService uploadService;

        @Inject
        SingleShotService singleShotService;

        @Inject
        RulesService rulesService;

        @Bind(R.id.preview)
        ImageView preview;

        @Bind(R.id.upload)
        Button upload;

        @Bind(R.id.busy_indicator)
        BusyIndicator busyIndicator;

        @Bind(R.id.tags)
        MultiAutoCompleteTextView tags;

        @Bind(R.id.scrollView)
        ScrollView scrollView;

        @Bind(R.id.content_type_group)
        RadioGroup contentTypeGroup;

        private File file;


        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_upload, container, false);
        }

        @Override
        protected void injectComponent(ActivityComponent activityComponent) {
            activityComponent.inject(this);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            Optional<Uri> uri = getUrlArgument();
            if (uri.isPresent()) {
                handleImageUri(uri.get());

            } else {
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, RequestCodes.SELECT_IMAGE);
            }

            // enable auto-complete
            TagInputView.setup(tags);

            // add the small print to the view
            TextView smallPrintView = ButterKnife.findById(view, R.id.small_print);
            rulesService.displayInto(smallPrintView);

            upload.setOnClickListener(v -> onUploadClicked());

            // give the upload-button the primary-tint
            int color = ContextCompat.getColor(getActivity(), R.color.primary);
            ViewCompat.setBackgroundTintList(upload, ColorStateList.valueOf(color));
        }

        private void onUploadClicked() {
            checkState(file != null, "File must be set on the activity");

            setFormEnabled(false);

            busyIndicator.setVisibility(View.VISIBLE);
            busyIndicator.setProgress(0.f);

            // get those from UI
            ContentType type = getSelectedContentType();
            Set<String> tags = ImmutableSet.copyOf(Splitter.on(",")
                    .trimResults().omitEmptyStrings()
                    .split(this.tags.getText().toString()));

            logger.info("Start upload of type {} with tags {}", type, tags);
            uploadService.upload(file, type, tags)
                    .compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
                    .subscribe(status -> {
                        if (status.isFinished()) {
                            logger.info("finished! item id is {}", status.getId());
                            onUploadComplete(status.getId());

                        } else if (status.getProgress() >= 0) {
                            float progress = status.getProgress();
                            logger.info("uploading, progress is {}", progress);
                            busyIndicator.setProgress(progress);

                        } else {
                            logger.info("upload finished, posting now.");
                            busyIndicator.spin();
                        }
                    }, this::onUploadError);

            // scroll back up
            scrollView.fullScroll(View.FOCUS_UP);
        }

        private void setFormEnabled(boolean enabled) {
            upload.setEnabled(enabled);
            tags.setEnabled(enabled);

            for (int idx = 0; idx < contentTypeGroup.getChildCount(); idx++) {
                View view = contentTypeGroup.getChildAt(idx);
                view.setEnabled(enabled);
            }
        }

        private void onUploadComplete(long postId) {
            logger.info("go to new post: {}", postId);

            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(UriHelper.of(getActivity()).post(FeedType.NEW, postId));
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

            getActivity().finish();
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent intent) {
            super.onActivityResult(requestCode, resultCode, intent);

            logger.info("got response from image picker: rc={}, intent={}", resultCode, intent);
            if (requestCode == RequestCodes.SELECT_IMAGE) {
                if (resultCode == Activity.RESULT_OK) {
                    Uri image = intent.getData();
                    handleImageUri(image);
                } else {
                    getActivity().finish();
                }
            }
        }

        private void handleImageUri(Uri image) {
            busyIndicator.setVisibility(View.VISIBLE);

            logger.info("copy image to private memory");
            copy(getActivity(), image)
                    .compose(bindToLifecycle())
                    .subscribe(this::onImageFile, this::onError);
        }

        private void onError(Throwable throwable) {
            logger.error("got some error loading the image", throwable);
            AndroidUtility.logToCrashlytics(throwable);
            getActivity().finish();
        }

        private void onUploadError(Throwable throwable) {
            setFormEnabled(true);
            busyIndicator.setVisibility(View.GONE);

            if (throwable instanceof UploadService.UploadFailedException) {
                String cause = ((UploadService.UploadFailedException) throwable).getErrorCode();
                String causeText = getUploadFailureText(getActivity(), cause);
                DialogBuilder.start(getActivity())
                        .content(causeText)
                        .positive()
                        .show();

            } else {
                logger.error("got an upload error", throwable);
                AndroidUtility.logToCrashlytics(throwable);

                String str = ErrorFormatting.getFormatter(throwable).getMessage(getActivity(), throwable);
                if (str != null) {
                    ErrorDialogFragment.showErrorString(getFragmentManager(), str);
                }
            }
        }

        private void onImageFile(File file) {
            this.file = file;

            logger.info("loading image file into preview.");
            picasso.load(file).into(preview, new Callback() {
                @Override
                public void onSuccess() {
                    busyIndicator.setVisibility(View.GONE);
                }

                @Override
                public void onError() {
                    UploadFragment.this.onError(new RuntimeException("Could not load image"));
                }
            });

            if (singleShotService.isFirstTime("upload_is_experimental_warning")) {
                DialogBuilder.start(getActivity())
                        .content(R.string.upload_warning)
                        .positive()
                        .show();
            }
        }

        @SuppressLint("NewApi")
        private static Observable<File> copy(Context context, Uri source) {
            File target = getTempFileUri(context);

            return Async.fromCallable(() -> {
                try (InputStream input = context.getContentResolver().openInputStream(source)) {
                    try (OutputStream output = new FileOutputStream(target)) {
                        ByteStreams.copy(input, output);
                    }
                }

                return target;
            }, BackgroundScheduler.instance());
        }

        private static File getTempFileUri(Context context) {
            return new File(context.getCacheDir(), "upload.jpg");
        }

        private ContentType getSelectedContentType() {
            ImmutableMap<Integer, ContentType> types = ImmutableMap.<Integer, ContentType>builder()
                    .put(R.id.upload_type_sfw, ContentType.SFW)
                    .put(R.id.upload_type_nsfw, ContentType.NSFW)
                    .put(R.id.upload_type_nsfl, ContentType.NSFL)
                    .build();

            View view = getView();
            if (view != null) {
                for (Map.Entry<Integer, ContentType> entry : types.entrySet()) {
                    RadioButton button = ButterKnife.findById(view, entry.getKey());
                    if (button != null && button.isChecked())
                        return entry.getValue();
                }
            }

            return ContentType.NSFL;
        }

        private Optional<Uri> getUrlArgument() {
            Bundle arguments = getArguments();
            if (arguments == null)
                return Optional.absent();

            return Optional.fromNullable(arguments.getParcelable(EXTRA_LOCAL_URI));
        }
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

    private static String getUploadFailureText(Context context, String reason) {
        Integer textId = ImmutableMap.<String, Integer>builder()
                .put("blacklisted", R.string.upload_error_blacklisted)
                .put("internal", R.string.upload_error_internal)
                .put("invalid", R.string.upload_error_invalid)
                .put("download", R.string.upload_error_download_failed)
                .put("exists", R.string.upload_error_exists)
                .build()
                .get(reason);

        return context.getString(firstNonNull(textId, R.string.upload_error_unknown));
    }
}
