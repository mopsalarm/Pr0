package com.pr0gramm.app.vpx;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.pr0gramm.app.ui.views.viewer.SoftwareMediaPlayer;

import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

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
            while (true) {
                // load the next data frame from the container
                ensureStillRunning();
                MatroskaFileFrame mkvFrame = mkv.getNextFrame(track.getTrackNo());
                if (mkvFrame == null)
                    break;

                // fill the decoder with data
                ensureStillRunning();
                vpx.put(mkvFrame.getData());

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

    private static class InputStreamDataSource implements org.ebml.io.DataSource {
        private final InputStream stream;
        private int position;

        private InputStreamDataSource(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public byte readByte() {
            try {
                int result = stream.read();
                position++;
                return (byte) result;
            } catch (IOException err) {
                throw Throwables.propagate(err);
            }
        }

        @Override
        public int read(ByteBuffer byteBuffer) {
            try {
                int offset = byteBuffer.arrayOffset() + byteBuffer.position();
                int count = stream.read(byteBuffer.array(), offset, byteBuffer.remaining());
                byteBuffer.position(byteBuffer.position() + count);
                position += count;
                return count;
            } catch (IOException err) {
                throw Throwables.propagate(err);
            }
        }

        @Override
        public long skip(long l) {
            try {
                long skipped = stream.skip(l);
                position += (int) skipped;
                return skipped;
            } catch (IOException err) {
                throw Throwables.propagate(err);
            }
        }

        @Override
        public long length() {
            return -1;
        }

        @Override
        public long getFilePointer() {
            return position;
        }

        @Override
        public boolean isSeekable() {
            return false;
        }

        @Override
        public long seek(long l) {
            throw new UnsupportedOperationException();
        }
    }
}
