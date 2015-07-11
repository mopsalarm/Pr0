package com.pr0gramm.app.mpeg;

import android.graphics.Bitmap;

import com.pr0gramm.app.mpeg.PictureBuffer;
import com.pr0gramm.app.mpeg.VideoConsumer;
import com.pr0gramm.app.mpeg.VideoDecoder;
import com.pr0gramm.app.ui.views.viewer.SoftwareMediaPlayer;

import java.io.InputStream;

/**
 * Plays mpeg files.
 */
public class MpegSoftwareMediaPlayer extends SoftwareMediaPlayer implements VideoConsumer {
    private PictureBuffer buffer;

    public MpegSoftwareMediaPlayer(InputStream inputStream) {
        super(inputStream);
    }

    @Override
    protected void playOnce(InputStream stream) throws Exception {
        logger.info("creating video decoder instance");
        VideoDecoder decoder = new VideoDecoder(this, stream);

        logger.info("start decoding mpeg file now");
        decoder.decodeSequence();

        logger.info("decoding mepg stream finished");
    }

    @Override
    public void sequenceStarted() {
        logger.info("sequence started");
    }

    @Override
    public void sequenceEnded() {
        logger.info("sequence ended");
    }

    @Override
    public void pictureDecoded(PictureBuffer picture) {
        Bitmap bitmap = requestBitmap(picture.width, picture.height);
        try {
            // post information about the newly received size info
            reportSize(picture.width, picture.height);

            bitmap.setPixels(picture.pixels, 0, picture.codedWidth, 0, 0,
                    picture.width, picture.height);
        } catch (Exception error) {
            returnBitmap(bitmap);
            throw error;
        }

        publishBitmap(bitmap);
    }

    @Override
    public PictureBuffer fetchBuffer(VideoDecoder decoder) throws InterruptedException {
        ensureStillRunning();

        // set the current delay
        publishFrameDelay((long) (1000 / decoder.getPictureRate()));

        // do nothing while paused.
        blockWhilePaused();

        if (buffer == null) {
            int width = decoder.getWidth();
            int height = decoder.getHeight();
            int codedWidth = decoder.getCodedWidth();
            int codedHeight = decoder.getCodedHeight();

            // logger.info("requesting buffer at {}x{} ({}x{})", width, height, codedWidth, codedHeight);
            buffer = new PictureBuffer(width, height, codedWidth, codedHeight);
        }

        return buffer;
    }
}
