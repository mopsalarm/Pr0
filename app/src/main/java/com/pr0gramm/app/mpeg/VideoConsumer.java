package com.pr0gramm.app.mpeg;

/**
 */
public interface VideoConsumer {
    void sequenceStarted();

    void sequenceEnded();

    void pictureDecoded(PictureBuffer picture);

    PictureBuffer fetchBuffer(VideoDecoder decoder) throws InterruptedException;
}
