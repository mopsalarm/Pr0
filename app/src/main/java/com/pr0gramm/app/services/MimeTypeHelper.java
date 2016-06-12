package com.pr0gramm.app.services;

import android.annotation.SuppressLint;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Guesses the mime type of a file or input stream
 */
public class MimeTypeHelper {

    private static final byte[] MAGIC_PNG = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private static final byte[] MAGIC_JPEG = {(byte) 0xff, (byte) 0xd8};

    private static final byte[] MAGIC_GIF = "GIF89a".getBytes();

    private static final List<byte[]> MAGIC_MP4 = ImmutableList.of(
            "ftypmp42".getBytes(),
            "moov".getBytes(),
            "isom".getBytes());

    private static final List<byte[]> MAGIC_WEBM = ImmutableList.of(
            new byte[]{0x1a, 0x54, (byte) 0xdf, (byte) 0xa3},
            "webm".getBytes());

    @SuppressWarnings("StaticPseudoFunctionalStyleMethod")
    public static Optional<String> guess(byte[] bytes) {
        if (Bytes.indexOf(bytes, MAGIC_JPEG) == 0)
            return Optional.of("image/jpeg");

        if (Bytes.indexOf(bytes, MAGIC_GIF) == 0)
            return Optional.of("image/gif");

        if (Bytes.indexOf(bytes, MAGIC_PNG) == 0)
            return Optional.of("image/png");

        if (Iterables.any(MAGIC_MP4, q -> Bytes.indexOf(bytes, q) != -1))
            return Optional.of("video/mp4");

        if (Iterables.any(MAGIC_WEBM, q -> Bytes.indexOf(bytes, q) != -1))
            return Optional.of("video/webm");

        return Optional.absent();
    }

    @SuppressLint("NewApi")
    public static Optional<String> guess(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return guess(input);
        }
    }

    public static Optional<String> guess(InputStream input) throws IOException {
        byte[] bytes = new byte[512];
        ByteStreams.read(input, bytes, 0, bytes.length);

        return guess(bytes);
    }

    public static Optional<String> extension(String type) {
        return Optional.fromNullable(EXTENSIONS.get(type));
    }

    private static ImmutableMap<String, String> EXTENSIONS = new ImmutableMap.Builder<String, String>()
            .put("image/jpeg", "jpeg")
            .put("image/png", "png")
            .put("image/gif", "gif")
            .put("video/webm", "webm")
            .put("video/mp4", "mp4")
            .build();
}
