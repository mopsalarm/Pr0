package com.pr0gramm.app.vpx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;

import com.akodiakson.sdk.simple.Sdk;
import com.google.common.base.Optional;
import com.pr0gramm.app.ui.views.viewer.SoftwareMediaPlayer;

import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;

import java.io.IOException;
import java.io.InputStream;

/**
 */
public class WebmMediaPlayer extends SoftwareMediaPlayer {
    public WebmMediaPlayer(Context context, InputStream inputStream) {
        super(context, inputStream);
    }

    @SuppressLint("NewApi")
    @Override
    protected void playOnce(InputStream stream) throws IOException {
        publishIsBuffering(true);

        logger.info("opening webm/mkv file");
        MatroskaFile mkv = new MatroskaFile(new InputStreamDataSource(stream));
        mkv.readFile();

        // total duration of the video in ms.
        duration = Math.max(duration, (long) mkv.getDuration());

        // get video info
        MatroskaFileTrack track = findFirstVideoTrack(mkv).get();
        MatroskaFileTrack.MatroskaVideoTrack videoInfo = track.getVideo();

        // report size
        int width = firstNotZero(videoInfo.getDisplayWidth(), videoInfo.getPixelWidth());
        int height = firstNotZero(videoInfo.getDisplayHeight(), videoInfo.getPixelHeight());
        reportSize(width, height);
        logger.info("found video track, size is {}x{}", width, height);

        // now calculate the size of our image buffers
        int pixelSkip = 0;
        int pixelWidth = videoInfo.getPixelWidth();
        int pixelHeight = videoInfo.getPixelHeight();

        int maxPixelWidth, maxPixelHeight;

        // on ics we'll activate low quality mode!
        if (Sdk.isAtLeastJellyBean()) {
            maxPixelWidth = 1024;
            maxPixelHeight = 1024;
        } else {
            maxPixelWidth = 512;
            maxPixelHeight = 512;
        }

        while (pixelWidth > maxPixelWidth || pixelHeight > maxPixelHeight) {
            pixelSkip += 1;
            pixelWidth = videoInfo.getPixelWidth() / (pixelSkip + 1);
            pixelHeight = videoInfo.getPixelHeight() / (pixelSkip + 1);
        }

        logger.info("will use image buffers with size {}x{} and pixelSkip {}",
                pixelWidth, pixelHeight, pixelSkip);

        try (VpxWrapper vpx = VpxWrapper.newInstance()) {
            int frameIndex = 0;
            long previousTimecode = 0;
            while (true) {
                // load the next data frame from the container
                ensureStillRunning();

                publishIsBuffering(true);
                try {
                    MatroskaFileFrame mkvFrame = mkv.getNextFrame(track.getTrackNo());
                    if (mkvFrame == null) {
                        break;
                    }

                    currentPosition = mkvFrame.getTimecode();

                    // estimate fps
                    long duration = mkvFrame.getTimecode() - previousTimecode;
                    previousTimecode = mkvFrame.getTimecode();

                    // fill the decoder with data
                    ensureStillRunning();
                    vpx.put(mkvFrame.getData());

                    // skip images on high frame rate.
                    boolean skipThisFrame = false;
                    if (duration < 1000 / 40) {
                        duration *= 2;
                        skipThisFrame = ++frameIndex % 2 == 0;
                    }

                    publishFrameDelay(duration);
                    if (skipThisFrame)
                        continue;

                    do {
                        blockWhilePaused();

                        Bitmap bitmap = requestBitmap(pixelWidth, pixelHeight);
                        boolean success;
                        try {
                            success = vpx.get(bitmap, pixelSkip);
                        } catch (Throwable error) {
                            returnBitmap(bitmap);
                            throw new NativeException(error);
                        }

                        if (success) {
                            publishBitmap(bitmap);
                        } else {
                            returnBitmap(bitmap);
                            break;
                        }
                    } while (true);
                } finally {
                    publishIsBuffering(false);
                }
            }
        }
    }

    private int firstNotZero(int first, int second) {
        return first != 0 ? first : second;
    }

    private static Optional<MatroskaFileTrack> findFirstVideoTrack(MatroskaFile mkv) {
        MatroskaFileTrack[] tracks = mkv.getTrackList();
        for (MatroskaFileTrack track : tracks) {
            if (track.getTrackType() == MatroskaFileTrack.TrackType.VIDEO) {
                return Optional.of(track);
            }
        }

        return Optional.absent();
    }

    /**
     * Returns true, if the player is available (i.e. the library can be loaded)
     */
    public static boolean isAvailable() {
        return VpxWrapper.isAvailable();
    }

    public static class NativeException extends RuntimeException {
        public NativeException(Throwable error) {
            super(error);
        }
    }
}
