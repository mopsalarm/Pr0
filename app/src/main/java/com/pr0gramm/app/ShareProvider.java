package com.pr0gramm.app;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.pr0gramm.app.feed.FeedItem;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import roboguice.content.RoboContentProvider;

/**
 */
public class ShareProvider extends RoboContentProvider {
    private static final Logger logger = LoggerFactory.getLogger(ShareProvider.class);

    @Inject
    private OkHttpClient httpClient;

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }

        Long fileSize = null;
        if(Thread.currentThread() != Looper.getMainLooper().getThread()) {
            try {
                // only try to do this on some background thread.
                fileSize = getSizeForUri(uri);
            } catch (IOException error) {
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
                values[idx] = Uri.parse(decode(uri)).getLastPathSegment();
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
        String url = decode(uri);

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
        return "image/jpeg";
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
        return new String[]{"image/jpeg"};
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String url = decode(uri);

        return openPipeHelper(uri, null, null, null, (output, uri1, mimeType, opts, args) -> {
            try {
                Response response = httpClient.newCall(new Request.Builder().url(url).build()).execute();
                try (InputStream source = response.body().byteStream()) {
                    // stream the data to the caller
                    ByteStreams.copy(source, new FileOutputStream(output.getFileDescriptor()));
                }
            } catch (IOException error) {
                // do nothing
                try {
                    logger.warn("Could not stream data to client", error);
                    output.closeWithError(error.toString());

                } catch (IOException err) {
                    logger.warn("Error trying to close the stream");
                }
            }
        });
    }

    /**
     * Decodes the received url
     */
    private String decode(Uri uri) {
        BaseEncoding decoder = BaseEncoding.base64Url();
        return new String(decoder.decode(uri.getLastPathSegment()), Charsets.UTF_8);
    }

    public static Uri getShareUri(Context context, FeedItem item) {
        String uri = Uris.of(context).media(item).toString();
        String path = BaseEncoding.base64Url().encode(uri.getBytes(Charsets.UTF_8));
        return new Uri.Builder()
                .scheme("content")
                .authority("com.pr0gramm.app.ShareProvider")
                .path(path)
                .build();
    }

    public static boolean canShare(FeedItem item) {
        return item.getImage().toLowerCase().matches(".*\\.(jpg|jpeg|png)$");
    }
}
