package com.pr0gramm.app.vpx;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;

/**
 */
public class WebmTest extends IntentService {
    private static final Logger logger = LoggerFactory.getLogger(WebmTest.class);

    /**
     */
    public WebmTest() {
        super("WebmTestService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            parse();
        } catch (Exception e) {
            logger.error("oops, could not parse webm", e);
        }
    }

    @SuppressLint("NewApi")
    public void parse() throws Exception {
        byte[] bytes = Files.toByteArray(new File("/sdcard/test.webm"));

        logger.info("create new vpx wrapper");

        Stopwatch watch = Stopwatch.createStarted();
        try (InputStream input = new ByteArrayInputStream(bytes);
             VpxWrapper vpx = VpxWrapper.newInstance()) {

            MatroskaFile mkv = new MatroskaFile(new InputStreamDataSource(input));
            logger.info("time after open: {}", watch);

            mkv.readFile();
            logger.info("time after reading file header: {}", watch);

            int trackIndex = -1;
            MatroskaFileTrack[] tracks = mkv.getTrackList();
            for (MatroskaFileTrack track : tracks) {
                if (track.getTrackType() == MatroskaFileTrack.TrackType.VIDEO) {
                    trackIndex = track.getTrackNo();
                    logger.info("track #{} is {}", track.getTrackNo(), track);
                }
            }

            int frameCount = 0;
            MatroskaFileFrame frame;
            while ((frame = mkv.getNextFrame(trackIndex)) != null) {
                frameCount += 1;

                logger.info("push data to vpx wrapper {}", vpx);
                ByteBuffer data = frame.getData();
                int offset = data.arrayOffset() + data.position();
                int length = data.remaining();
                vpx.put(data.array(), offset, length);


                logger.info("start getting frames from the wrapper");
                int videoFrames = 0;
                while (vpx.get(null))
                    videoFrames++;

                logger.info("got {} images for this mkv frame", videoFrames);
            }

            logger.info("read {} frames", frameCount);
        }

        logger.info("parsing webm file took {}", watch);
    }

    private static class InputStreamDataSource implements org.ebml.io.DataSource {
        private final InputStream stream;
        private int position;

        private InputStreamDataSource(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public byte readByte() {
            byte result = wrap(() -> (byte) stream.read());
            position++;
            return result;
        }

        @Override
        public int read(ByteBuffer byteBuffer) {
            int offset = byteBuffer.arrayOffset() + byteBuffer.position();
            int count = wrap(() -> stream.read(byteBuffer.array(), offset, byteBuffer.remaining()));
            byteBuffer.position(byteBuffer.position() + count);
            position += count;
            return count;
        }

        @Override
        public long skip(long l) {
            long skipped = wrap(() -> stream.skip(l));
            position += (int) skipped;
            return skipped;
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

        private <T> T wrap(Callable<T> callable) {
            try {
                return callable.call();
            } catch (Exception err) {
                throw Throwables.propagate(err);
            }
        }
    }
}
