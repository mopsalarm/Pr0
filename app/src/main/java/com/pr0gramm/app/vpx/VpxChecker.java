package com.pr0gramm.app.vpx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.common.base.Throwables;
import com.pr0gramm.app.util.AndroidUtility;

import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;

import java.io.InputStream;

import rx.Observable;

import static com.pr0gramm.app.vpx.WebmMediaPlayer.findFirstVideoTrack;

/**
 * This tests if the vpx decoder works
 */
public class VpxChecker {
    private static final int OKAY_UNKNOWN = 0;
    private static final int OKAY_TRUE = 1;
    private static final int OKAY_FALSE = 2;

    /**
     * Check if the vpx decoder is supported and is working correctly.
     */
    public static Observable<Boolean> vpxOkay(Context context) {
        Context appContext = context.getApplicationContext();
        return Observable.fromCallable(() -> runCheck(appContext))
                .onErrorResumeNext(Observable.just(false))
                .defaultIfEmpty(false);
    }

    @SuppressLint("CommitPrefEdits")
    private static boolean runCheck(Context context) {
        int version = AndroidUtility.getPackageVersionCode(context);

        SharedPreferences preferences = context.getSharedPreferences("vpxChecker", Context.MODE_PRIVATE);
        int okayValue = preferences.getInt(version + ".okay", OKAY_UNKNOWN);
        if (okayValue != OKAY_UNKNOWN)
            return okayValue == OKAY_TRUE;

        // remember that we started the process
        preferences.edit().putInt(version + ".okay", OKAY_FALSE).commit();

        boolean result = false;
        try {
            result = decodedCorrectly(decodeFirstFrame(context));
        } catch (Throwable ignored) {
        }

        // looks like we finished successfully
        preferences.edit()
                .putInt(version + ".okay", result ? OKAY_TRUE : OKAY_FALSE)
                .commit();

        return result;
    }

    private static boolean decodedCorrectly(Bitmap bitmap) {
        int topLeft = bitmap.getPixel(0, 0);
        int bottomLeft = bitmap.getPixel(0, bitmap.getHeight() - 1);
        int bottomRight = bitmap.getPixel(bitmap.getWidth() - 1, bitmap.getHeight() - 1);

        return Color.red(topLeft) > 240 && Color.green(topLeft) < 20 && Color.blue(topLeft) < 20
                && Color.green(bottomLeft) > 240 && Color.red(bottomLeft) < 20 && Color.blue(bottomLeft) < 20
                && Color.green(bottomRight) > 240 && Color.red(bottomRight) > 240 && Color.blue(bottomRight) > 240;

    }

    @SuppressLint("NewApi")
    private static Bitmap decodeFirstFrame(Context context) {
        try (InputStream input = context.getAssets().open("test.webm")) {
            MatroskaFile mkv = new MatroskaFile(new InputStreamDataSource(input));
            mkv.readFile();

            MatroskaFileTrack track = findFirstVideoTrack(mkv).get();
            MatroskaFileTrack.MatroskaVideoTrack videoInfo = track.getVideo();

            int pixelWidth = videoInfo.getPixelWidth();
            int pixelHeight = videoInfo.getPixelHeight();

            if (pixelHeight != 64 && pixelWidth != 64)
                throw new IllegalStateException("Decoded size is not correct");

            try (VpxWrapper vpx = VpxWrapper.newInstance(context)) {
                MatroskaFileFrame mkvFrame = mkv.getNextFrame(track.getTrackNo());
                vpx.put(mkvFrame.getData());

                Bitmap bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.RGB_565);
                vpx.get(bitmap, 0);
                return bitmap;
            }
        } catch (Throwable error) {
            throw Throwables.propagate(error);
        }
    }
}
