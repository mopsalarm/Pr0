package com.pr0gramm.app.vpx;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;

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
    public WebmMediaPlayer(InputStream inputStream) {
        super(inputStream);
    }

    @SuppressLint("NewApi")
    @Override
    protected void playOnce(InputStream stream) throws IOException {
        logger.info("opening webm/mkv file");
        MatroskaFile mkv = new MatroskaFile(new InputStreamDataSource(stream));
        mkv.readFile();

        // get video info
        MatroskaFileTrack track = findFirstVideoTrack(mkv).get();
        MatroskaFileTrack.MatroskaVideoTrack videoInfo = track.getVideo();
        reportSize(videoInfo.getDisplayWidth(), videoInfo.getDisplayHeight());

        try (VpxWrapper vpx = VpxWrapper.newInstance()) {
            int frameIndex = 0;
            long previousTimecode = 0;
            while (true) {
                // load the next data frame from the container
                ensureStillRunning();
                MatroskaFileFrame mkvFrame = mkv.getNextFrame(track.getTrackNo());
                if (mkvFrame == null)
                    break;

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

                    Bitmap bitmap = requestBitmap(videoInfo.getPixelWidth(), videoInfo.getPixelHeight());
                    boolean success;
                    try {
                        success = vpx.get(bitmap);
                    } catch (Exception error) {
                        returnBitmap(bitmap);
                        throw error;
                    }

                    if (success) {
                        publishBitmap(bitmap);
                    } else {
                        returnBitmap(bitmap);
                        break;
                    }
                } while (true);
            }
        }
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
}
