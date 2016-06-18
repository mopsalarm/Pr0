package com.pr0gramm.app.ui.upload;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.jakewharton.rxbinding.view.RxView;
import com.pr0gramm.app.ActivityComponent;
import com.pr0gramm.app.R;
import com.pr0gramm.app.RequestCodes;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.feed.FeedType;
import com.pr0gramm.app.services.HasThumbnail;
import com.pr0gramm.app.services.MimeTypeHelper;
import com.pr0gramm.app.services.RulesService;
import com.pr0gramm.app.services.UploadService;
import com.pr0gramm.app.services.UriHelper;
import com.pr0gramm.app.ui.DialogBuilder;
import com.pr0gramm.app.ui.MainActivity;
import com.pr0gramm.app.ui.TagInputView;
import com.pr0gramm.app.ui.base.BaseFragment;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.views.BusyIndicator;
import com.pr0gramm.app.ui.views.viewer.MediaUri;
import com.pr0gramm.app.ui.views.viewer.MediaView;
import com.pr0gramm.app.ui.views.viewer.MediaViews;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.ErrorFormatting;
import com.pr0gramm.app.util.Noop;
import com.trello.rxlifecycle.FragmentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.defaultOnError;
import static com.pr0gramm.app.ui.fragments.BusyDialogFragment.busyDialog;
import static com.pr0gramm.app.util.AndroidUtility.checkMainThread;

/**
 * This activity performs the actual upload.
 */
public class UploadFragment extends BaseFragment {
    private static final Logger logger = LoggerFactory.getLogger("UploadFragment");
    public static final String EXTRA_LOCAL_URI = "UploadFragment.localUri";
    public static final String EXTRA_MEDIA_TYPE = "UploadFragment.mediaType";

    @Inject
    UploadService uploadService;

    @Inject
    RulesService rulesService;

    @BindView(R.id.preview)
    FrameLayout preview;

    @BindView(R.id.upload)
    Button upload;

    @BindView(R.id.busy_indicator)
    BusyIndicator busyIndicator;

    @BindView(R.id.tags)
    MultiAutoCompleteTextView tags;

    @BindView(R.id.scrollView)
    ScrollView scrollView;

    @BindView(R.id.content_type_group)
    RadioGroup contentTypeGroup;

    private File file;
    private MediaUri.MediaType fileMediaType;

    @Nullable
    private UploadService.UploadInfo uploadInfo;


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

        } else if (savedInstanceState == null) {
            String type = "image/*";
            if (getArguments() != null)
                type = getArguments().getString(EXTRA_MEDIA_TYPE, type);

            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(type);
            startActivityForResult(intent, RequestCodes.SELECT_MEDIA);
        }

        // enable auto-complete
        TagInputView.setup(tags);

        // add the small print to the view
        TextView smallPrintView = ButterKnife.findById(view, R.id.small_print);
        rulesService.displayInto(smallPrintView);

        upload.setOnClickListener(v -> onUploadClicked());

    }

    private void onUploadClicked() {
        if (file == null) {
            DialogBuilder.start(getActivity())
                    .content(R.string.hint_upload_something_happen_try_again)
                    .positive(R.string.okay, () -> getActivity().finish())
                    .show();

            return;
        }

        // we have an already started upload. lets just continue with it
        if (uploadInfo != null) {
            startUpload();
            return;
        }

        uploadService.sizeOkay(file)
                .onErrorResumeNext(Observable.empty())
                .compose(bindToLifecycleAsync())
                .subscribe(sizeOkay -> {
                    if (sizeOkay) {
                        startUpload();
                    } else {
                        handleSizeNotOkay();
                    }
                });
    }

    private void startUpload() {
        checkMainThread();

        setFormEnabled(false);

        // get those from UI
        ContentType type = getSelectedContentType();
        Set<String> tags = ImmutableSet.copyOf(Splitter.on(",")
                .trimResults().omitEmptyStrings()
                .split(this.tags.getText().toString()));

        // start the upload
        Observable<UploadService.UploadInfo> upload;
        if (uploadInfo == null) {
            upload = uploadService.upload(file, type, tags);
            busyIndicator.setVisibility(View.VISIBLE);
            busyIndicator.setProgress(0.f);
        } else {
            upload = uploadService.post(uploadInfo, type, tags, false);
            busyIndicator.setVisibility(View.VISIBLE);
            busyIndicator.spin();
        }

        logger.info("Start upload of type {} with tags {}", type, tags);
        upload.compose(bindUntilEventAsync(FragmentEvent.DESTROY_VIEW))
                .doAfterTerminate(() -> busyIndicator.setVisibility(View.GONE))
                .subscribe(status -> {
                    if (status.isFinished()) {
                        logger.info("finished! item id is {}", status.getId());
                        onUploadComplete(status.getId());

                    } else if (status.hasSimilar()) {
                        logger.info("Found similar posts. Showing them now");
                        this.uploadInfo = status;
                        showSimilarPosts(status.similar());
                        setFormEnabled(true);

                    } else if (status.getProgress() >= 0.99) {
                        logger.info("uploading, progress is nearly finished");
                        if (!busyIndicator.isSpinning())
                            busyIndicator.spin();

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

    @SuppressWarnings("ConstantConditions")
    private void showSimilarPosts(List<HasThumbnail> similar) {
        ButterKnife.findById(getView(), R.id.similar_hint).setVisibility(View.VISIBLE);

        SimilarImageView view = ButterKnife.findById(getView(), R.id.similar_list);
        view.setVisibility(View.VISIBLE);
        view.setThumbnails(similar);

        scrollView.requestChildFocus(view, view);
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
        if (requestCode == RequestCodes.SELECT_MEDIA) {
            if (resultCode == Activity.RESULT_OK) {
                Uri image = intent.getData();
                handleImageUri(image);
            } else {
                getActivity().finish();
            }
        }
    }

    @MainThread
    private void handleImageUri(Uri image) {
        checkMainThread();

        busyIndicator.setVisibility(View.VISIBLE);

        logger.info("copy image to private memory");
        copy(getActivity(), image)
                .compose(bindToLifecycleAsync())
                .subscribe(this::onMediaFile, this::onError);
    }

    private void onError(Throwable throwable) {
        //noinspection ThrowableResultOfMethodCallIgnored
        if (Throwables.getRootCause(throwable) instanceof MediaNotSupported) {
            showCanNotHandleTypeDialog();
            return;
        }

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

    private void onMediaFile(File file) {
        this.file = file;

        logger.info("loading file into view.");
        MediaUri uri = MediaUri.of(-1, Uri.fromFile(file));

        fileMediaType = uri.getMediaType();

        MediaView viewer = MediaViews.newInstance(getActivity(), uri, Noop.noop);
        viewer.setHasAudio(false);
        viewer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        RxView.attaches(viewer).subscribe(event -> viewer.playMedia());

        preview.removeAllViews();
        preview.addView(viewer);

        busyIndicator.setVisibility(View.GONE);
    }

    private void handleSizeNotOkay() {
        checkMainThread();
        if (fileMediaType == MediaUri.MediaType.IMAGE) {
            DialogBuilder.start(getActivity())
                    .content(R.string.hint_image_too_large)
                    .positive(R.string.down_size, di -> shrinkImage())
                    .negative(android.R.string.no)
                    .show();
        } else {
            DialogBuilder.start(getActivity())
                    .content(R.string.upload_hint_too_large)
                    .positive()
                    .show();
        }
    }

    private void shrinkImage() {
        uploadService.downsize(file)
                .compose(bindToLifecycleAsync())
                .lift(busyDialog(this))
                .subscribe(newFile -> {
                    handleImageUri(Uri.fromFile(newFile));
                    imageShrankSuccess();
                }, defaultOnError());
    }

    @MainThread
    private void imageShrankSuccess() {
        Snackbar.make(checkNotNull(getView()), R.string.hint_shrank_successful, Snackbar.LENGTH_LONG)
                .setAction(R.string.okay, Noop.noop)
                .show();
    }

    @SuppressLint("NewApi")
    private static Observable<File> copy(Context context, Uri source) {
        return Observable.fromCallable(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(source)) {
                checkNotNull(input, "Could not open input stream");

                // read the "header"
                byte[] bytes = new byte[512];
                int count = ByteStreams.read(input, bytes, 0, bytes.length);

                // and guess the type
                Optional<String> ext = MimeTypeHelper.guess(bytes);
                if (ext.isPresent())
                    ext = MimeTypeHelper.extension(ext.get());

                // fail if we couldnt get the type
                if (!ext.isPresent())
                    throw new MediaNotSupported();

                File target = getTempFileUri(context, ext.get());

                try (OutputStream output = new FileOutputStream(target)) {
                    output.write(bytes, 0, count);

                    long copied = ByteStreams.copy(input, output);
                    logger.info("Copied {}kb", copied / 1024);
                }

                return target;
            }
        });
    }

    private static File getTempFileUri(Context context, String ext) {
        return new File(context.getCacheDir(), "upload." + ext);
    }

    private void showCanNotHandleTypeDialog() {
        checkMainThread();
        DialogBuilder.start(getActivity())
                .content(R.string.upload_error_invalid_type)
                .positive(R.string.okay, () -> getActivity().finish())
                .show();
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

    private static class MediaNotSupported extends RuntimeException {
        MediaNotSupported() {
            super("Media type not supported");
        }
    }
}
