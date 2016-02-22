package com.pr0gramm.app.vpx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.common.base.Throwables;

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
    private VpxChecker() {
    }

    /**
     * Check if the vpx decoder is supported and is working correctly.
     */
    public static Observable<Boolean> vpxOkay(Context context) {
        return Observable.fromCallable(() -> decodeFirstFrame(context))
                .map(VpxChecker::decodedCorrectly)
                .onErrorResumeNext(Observable.just(false))
                .defaultIfEmpty(false);
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
