package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.pr0gramm.app.ErrorFormatting;
import com.pr0gramm.app.R;
import com.pr0gramm.app.feed.ContentType;
import com.pr0gramm.app.services.UploadService;
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment;
import com.pr0gramm.app.ui.views.BusyIndicator;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.InjectView;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.util.async.Async;

import static com.google.common.base.Preconditions.checkState;
import static rx.android.observables.AndroidObservable.bindActivity;

/**
 */
public class UploadActivity extends RoboActionBarActivity {
    private static final Logger logger = LoggerFactory.getLogger(UploadActivity.class);

    private final int REQ_SELECT_IMAGE = 1;

    @Inject
    private Picasso picasso;

    @Inject
    private UploadService uploadService;

    @InjectView(R.id.preview)
    private ImageView preview;

    @InjectView(R.id.upload)
    private Button upload;

    @InjectView(R.id.busy_indicator)
    private BusyIndicator busyIndicator;
    private File file;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQ_SELECT_IMAGE);

        upload.setOnClickListener(v -> onUploadClicked());
    }

    private void onUploadClicked() {
        checkState(file != null, "File must be set on the activity");

        upload.setEnabled(false);
        busyIndicator.setVisibility(View.VISIBLE);
        busyIndicator.setProgress(0.f);

        // get those from UI
        ContentType type = ContentType.SFW;
        Set<String> tags = ImmutableSet.of("app");

        bindActivity(this, uploadService.upload(file, type, tags))
                .subscribe(status -> {
                    if (status.isFinished()) {
                        logger.info("finished! item id is {}", status.getId());
                        upload.postDelayed(this::finish, 500);

                    } else if (status.getProgress() >= 0) {
                        float progress = status.getProgress();
                        logger.info("uploading, progress is {}", progress);
                        busyIndicator.setProgress(progress);

                    } else {
                        busyIndicator.spin();
                    }
                }, this::onUploadError);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQ_SELECT_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri image = intent.getData();
                handleImageUri(image);
            } else {
                finish();
            }
        }
    }

    private void handleImageUri(Uri image) {
        busyIndicator.setVisibility(View.VISIBLE);

        bindActivity(this, copy(image)).subscribe(this::onImageFile, this::onError);
    }

    private void onError(Throwable throwable) {
        // TODO implement better error handling!!
        throwable.printStackTrace();
        finish();
    }

    private void onUploadError(Throwable throwable) {
        upload.setEnabled(true);
        busyIndicator.setVisibility(View.GONE);

        String str = ErrorFormatting.getFormatter(throwable).getMessage(this, throwable);
        ErrorDialogFragment.showErrorString(getSupportFragmentManager(), str);
    }

    private void onImageFile(File file) {
        this.file = file;

        picasso.load(file).into(preview, new Callback() {
            @Override
            public void onSuccess() {
                busyIndicator.setVisibility(View.GONE);
            }

            @Override
            public void onError() {
                UploadActivity.this.onError(new RuntimeException("Could not load image"));
            }
        });
    }

    @SuppressLint("NewApi")
    private Observable<File> copy(Uri source) {
        File target = getTempFileUri(this);

        return Async.fromCallable(() -> {
            try (InputStream input = getContentResolver().openInputStream(source)) {
                try (OutputStream output = new FileOutputStream(target)) {
                    ByteStreams.copy(input, output);
                }
            }

            return target;
        }, Schedulers.io());
    }

    private static File getTempFileUri(Context context) {
        return new File(context.getCacheDir(), "upload.jpg");
    }
}
