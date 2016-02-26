package com.pr0gramm.app.services;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;

import com.akodiakson.sdk.simple.Sdk;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.pr0gramm.app.BuildConfig;
import com.pr0gramm.app.Dagger;
import com.pr0gramm.app.feed.FeedItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.google.common.base.Preconditions.checkArgument;

/**
 */
public class ShareProvider extends ContentProvider {
    private static final Logger logger = LoggerFactory.getLogger("ShareProvider");

    @Inject
    OkHttpClient httpClient;

    @Override
    public boolean onCreate() {
        Dagger.appComponent(getContext()).inject(this);
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }

        Long fileSize = null;
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            try {
                // only try to do this on some background thread.
                fileSize = getSizeForUri(uri);
            } catch (IOException | NetworkOnMainThreadException error) {
                logger.warn("could not estimate size");
            }
        }

        // adapted from FileProvider.query
        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        for (int idx = 0; idx < projection.length; idx++) {
            String col = projection[idx];
            cols[idx] = col;
            values[idx] = null;

            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                values[idx] = decode(uri).getLastPathSegment();
            }

            if (OpenableColumns.SIZE.equals(col)) {
                values[idx] = fileSize;
            }
        }

        MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    private long getSizeForUri(Uri uri) throws IOException {
        String url = decode(uri).toString();

        Request request = new Request.Builder().url(url).head().build();
        Response response = httpClient.newCall(request).execute();
        try {
            return response.body().contentLength();

        } finally {
            response.body().close();
        }
    }

    @Override
    public String getType(Uri uri) {
        return guessMimetype(decode(uri)).orNull();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        return new String[]{guessMimetype(decode(uri)).orNull()};
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String url = decode(uri).toString();

        return openPipeHelper(uri, null, null, null, (output, uri1, mimeType, opts, args) -> {
            try {
                if (url.matches("https?://.*")) {
                    Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();
                    try (InputStream source = response.body().byteStream()) {
                        // stream the data to the caller
                        ByteStreams.copy(source, new FileOutputStream(output.getFileDescriptor()));
                    }
                } else {
                    try (InputStream source = getContext().getContentResolver().openInputStream(Uri.parse(url))) {
                        ByteStreams.copy(source, new FileOutputStream(output.getFileDescriptor()));
                    }
                }
            } catch (IOException error) {
                // do nothing
                try {
                    logger.warn("Could not stream data to client", error);
                    if (Sdk.isAtLeastKitKat()) {
                        output.closeWithError(error.toString());
                    } else {
                        output.close();
                    }

                } catch (IOException err) {
                    logger.warn("Error trying to close the stream");
                }
            }
        });
    }

    /**
     * Decodes the received url
     */
    private Uri decode(Uri uri) {
        BaseEncoding decoder = BaseEncoding.base64Url();
        return Uri.parse(new String(decoder.decode(uri.getLastPathSegment()), Charsets.UTF_8));
    }

    /**
     * Returns an uri for the given item to share that item with other apps.
     */
    public static Uri getShareUri(Context context, FeedItem item) {
        checkArgument(canShare(context, item), "Can not share item %s", item.id());

        String uri = getMediaUri(context, item).toString();
        String path = BaseEncoding.base64Url().encode(uri.getBytes(Charsets.UTF_8));
        return new Uri.Builder()
                .scheme("content")
                .authority(BuildConfig.APPLICATION_ID + ".ShareProvider")
                .path(path)
                .build();
    }

    public static boolean canShare(Context context, FeedItem feedItem) {
        return guessMimetype(context, feedItem).isPresent();
    }

    private static Uri getMediaUri(Context context, FeedItem item) {
        return UriHelper.of(context).media(item);
    }

    public static Optional<String> guessMimetype(Context context, FeedItem item) {
        return guessMimetype(getMediaUri(context, item));
    }

    private static Optional<String> guessMimetype(Uri uri) {
        String url = uri.toString();
        if (url.length() < 4)
            return Optional.absent();

        String extension = url.substring(url.length() - 4).toLowerCase();
        return Optional.fromNullable(EXT_MIMETYPE_MAP.get(extension));
    }

    private static final ImmutableMap<String, String> EXT_MIMETYPE_MAP = ImmutableMap.<String, String>builder()
            .put(".png", "image/png")
            .put(".jpg", "image/jpg")
            .put("jpeg", "image/jpeg")
            .put("webm", "video/webm")
            .put(".mp4", "video/mp4")
                    // .put(".gif", "image/gif")
            .build();
}
