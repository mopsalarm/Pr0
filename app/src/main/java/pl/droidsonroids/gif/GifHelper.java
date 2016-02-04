package pl.droidsonroids.gif;

import com.google.common.io.Closeables;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

public class GifHelper {
    public static InputSource closingInputSource(FileInputStream stream) throws IOException {
        return new ClosingInputSource(stream, stream.getFD());
    }

    public static InputSource closingInputSource(RandomAccessFile file) throws IOException {
        return new ClosingInputSource(file, file.getFD());
    }

    private static class ClosingInputSource extends InputSource {
        private final Closeable closeable;
        private final FileDescriptor fd;

        public ClosingInputSource(Closeable closeable, FileDescriptor fd) {
            this.closeable = closeable;
            this.fd = fd;
        }

        @Override
        GifInfoHandle open() throws IOException {
            return GifInfoHandle.openFd(fd, 0, false);
        }

        @Override
        protected void finalize() throws Throwable {
            Closeables.close(closeable, true);
            super.finalize();
        }
    }
}
