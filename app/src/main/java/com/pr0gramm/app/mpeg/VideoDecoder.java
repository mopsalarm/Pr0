package com.pr0gramm.app.mpeg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 *
 */
@SuppressWarnings("unused")
public class VideoDecoder {
    private static final Logger logger = LoggerFactory.getLogger("VideoDecoder");

    private final InputBitStream stream;
    private final VideoConsumer consumer;

    public VideoDecoder(VideoConsumer consumer, InputStream input) {
        this.stream = new InputBitStream(input);
        this.consumer = consumer;
    }

    // MPEG data

    private static final int[][] mbTypeTables = {
            null,
            InputBitStream.macroblockTypeI,
            InputBitStream.macroblockTypeP,
            InputBitStream.macroblockTypeB
    };

    private static final double[] pelAspectRatioValues = {
            0.0000, 1.0000, 0.6735, 0.7031, 0.7615, 0.8055, 0.8437, 0.8935,
            0.9375, 0.9815, 1.0255, 1.0695, 1.1250, 1.1575, 1.2015, 0.0000
    };

    private static final double[] pictureRateValues = {
            0.000, 23.976, 24.000, 25.000, 29.970, 30.000, 50.000, 59.940,
            60.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000
    };

    private static final int[] zigZag = {
            0, 1, 8, 16, 9, 2, 3, 10,
            17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63
    };

    private static final int[] defaultIntraQuantMatrix = {
            8, 16, 19, 22, 26, 27, 29, 34,
            16, 16, 22, 24, 27, 29, 34, 37,
            19, 22, 26, 27, 29, 34, 34, 38,
            22, 22, 26, 27, 29, 34, 37, 40,
            22, 26, 27, 29, 32, 35, 40, 48,
            26, 27, 29, 32, 35, 40, 48, 58,
            26, 27, 29, 34, 38, 46, 56, 69,
            27, 29, 35, 38, 46, 56, 69, 83
    };

    private static final int[] defaultNonIntraQuantMatrix = {
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16,
            16, 16, 16, 16, 16, 16, 16, 16
    };

    private int[] customIntraQuantMatrix = new int[64];
    private int[] customNonIntraQuantMatrix = new int[64];

    private int[] intraQuantMatrix = new int[64];
    private int[] nonIntraQuantMatrix = new int[64];

    private static final int[] premultiplierMatrix = {
            32, 44, 42, 38, 32, 25, 17, 9,
            44, 62, 58, 52, 44, 35, 24, 12,
            42, 58, 55, 49, 42, 33, 23, 12,
            38, 52, 49, 44, 38, 30, 20, 10,
            32, 44, 42, 38, 32, 25, 17, 9,
            25, 35, 33, 30, 25, 20, 14, 7,
            17, 24, 23, 20, 17, 14, 9, 5,
            9, 12, 12, 10, 9, 7, 5, 2
    };

    // Start codes

    private int startCode = -1;

    private static final int PICTURE_START_CODE = 0x00;
    private static final int SLICE_START_CODE_FIRST = 0x01;
    private static final int SLICE_START_CODE_LAST = 0xaf;
    private static final int USER_DATA_START_CODE = 0xb2;
    private static final int SEQUENCE_HEADER_CODE = 0xb3;
    private static final int EXTENSION_START_CODE = 0xb5;
    private static final int SEQUENCE_END_CODE = 0xb7;
    private static final int GROUP_START_CODE = 0xb8;

    private static final int START_CODE_PATIENCE = 256 * 1024;

    private int nextStartCode() throws IOException {
        int counter = 0;
        int state = 0;

        while (state < 3) {
            switch (state) {
                case 0:
                case 1:
                    if (stream.readNextByte() == 0) ++state;
                    else state = 0;
                    break;
                case 2:
                    switch (stream.readNextByte()) {
                        case 0:
                            break;
                        case 1:
                            state = 3;
                            break;
                        default:
                            state = 0;
                    }
            }
            if (++counter >= START_CODE_PATIENCE) {
                throw new IOException("No start code found");
            }
        }
        return startCode = stream.readNextByte();
    }

    // Methods for querying the basic properties of the video

    public int getWidth() {
        if (sequenceStarted) {
            return horizontalSize;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getHeight() {
        if (sequenceStarted) {
            return verticalSize;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getCodedWidth() {
        if (sequenceStarted) {
            return codedWidth;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getCodedHeight() {
        if (sequenceStarted) {
            return codedHeight;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getDisplayWidth() {
        if (sequenceStarted) {
            double PAR = getPelAspectRatio();
            if (PAR < 1.0) return horizontalSize;
            else return (int) Math.round(horizontalSize * PAR);
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getDisplayHeight() {
        if (sequenceStarted) {
            double PAR = getPelAspectRatio();
            if (PAR < 1.0) return (int) Math.round(verticalSize / PAR);
            else return verticalSize;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public double getPelAspectRatio() {
        if (sequenceStarted) {
            return pelAspectRatioValues[pelAspectRatioCode];
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getPelAspectRatioCode() {
        if (sequenceStarted) {
            return pelAspectRatioCode;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public double getPictureRate() {
        if (sequenceStarted) {
            return pictureRateValues[pictureRateCode];
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public double getPictureRateCode() {
        if (sequenceStarted) {
            return pictureRateCode;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getRoundedPictureRate() {
        if (sequenceStarted) {
            return roundedPictureRate;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    public int getBitRate() {
        if (sequenceStarted) {
            return bitRate;
        } else {
            throw new IllegalStateException("Sequence not started");
        }
    }

    // User data
    private void skipUserData() throws IOException {
        int state = 0;
        while (state < 3) {
            int data = stream.readNextByte();
            switch (state) {
                case 0:
                case 1:
                    if (data == 0) {
                        ++state;
                    } else {
                        // TODO: Append a zero byte to the decoded user data
                        state = 0;
                    }
                    break;
                case 2:
                    switch (data) {
                        case 0:
                            // TODO: Append a zero byte to the decoded user data
                            break;
                        case 1:
                            state = 3;
                            break;
                        default:
                            // TODO: Append a zero byte to the decoded user data
                            state = 0;
                    }
            }
        }
        // TODO: Call some event handler

        startCode = stream.readNextByte();
    }

    // Sequence layer

    private int horizontalSize;
    private int verticalSize;
    private int pelAspectRatioCode;
    private int pictureRateCode;
    private int roundedPictureRate;
    private int bitRate;

    private int mbWidth;
    private int mbSize;
    private int codedWidth;
    private int codedHeight;
    private int halfWidth;
    private int halfHeight;

    private boolean sequenceStarted = false;

    public void decodeSequence() throws InterruptedException, IOException {
        try {
            // Find the first valid sequence header
            while (!sequenceStarted) {
                while (startCode != SEQUENCE_HEADER_CODE) {
                    nextStartCode();
                }
                decodeSequenceHeader();
            }

            while (startCode == GROUP_START_CODE) {
                // Decode groups of pictures and any repeated sequence headers
                while (startCode == GROUP_START_CODE) {
                    decodeGroup();
                }
                if (startCode == SEQUENCE_HEADER_CODE) {
                    decodeSequenceHeader();
                }
            }

            // startCode should be SEQUENCE_END_CODE. Ignore error if it isn't.
        } catch (EOFException eof) {
            logger.warn("eof reached premature");

        }

        if (reorderBuffer != null) {
            consumer.pictureDecoded(reorderBuffer);
        }

        sequenceStarted = false;
        consumer.sequenceEnded();
    }

    private void decodeSequenceHeader() throws IOException {
        int hSize = stream.readBits(12);
        int vSize = stream.readBits(12);
        int PARCode = stream.readBits(4);   // pel_aspect_ratio
        int PRCode = stream.readBits(4);   // picture_rate code
        int BR = stream.readBits(18);  // bitrate

        // marker, vbv_buffer_size, constrained_parameters (ignored)
        stream.readBits(12);

        // Ignore errors on sequence header mismatch

        if (!sequenceStarted) {
            if (hSize == 0 || vSize == 0) {
                // Ignore illegal sequence header
                return;
            }

            if (pelAspectRatioValues[PARCode] == 0.0) {
                // Unknown pel_aspect_ratio, assume 1.0
                PARCode = 1;
            }

            if (pictureRateValues[PRCode] == 0.0) {
                // Unknown picture_rate, assume 25.0
                PRCode = 3;
            }


            horizontalSize = hSize;
            verticalSize = vSize;

            // Width, height and size in macroblocks
            mbWidth = (hSize + 15) >> 4;
            int mbHeight = (vSize + 15) >> 4;
            mbSize = mbWidth * mbHeight;

            codedWidth = mbWidth << 4;
            codedHeight = mbHeight << 4;
            int codedSize = codedWidth * codedHeight;

            halfWidth = mbWidth << 3;
            halfHeight = mbHeight << 3;

            pelAspectRatioCode = PARCode;
            pictureRateCode = PRCode;
            roundedPictureRate = (int) Math.round(pictureRateValues[PRCode]);
            bitRate = BR;

            sequenceStarted = true;

            // Allocate buffers
            currentY = new int[codedSize];
            currentCr = new int[codedSize >> 2];
            currentCb = new int[codedSize >> 2];
            forwardY = new int[codedSize];
            forwardCr = new int[codedSize >> 2];
            forwardCb = new int[codedSize >> 2];
            backwardY = new int[codedSize];
            backwardCr = new int[codedSize >> 2];
            backwardCb = new int[codedSize >> 2];

            consumer.sequenceStarted();
        }

        // Read intra_quantizer_matrix if present
        if (stream.readBit() != 0) {
            for (int i = 0; i < 64; ++i) {
                int quantizer = stream.readBits(8);
                // Ignore error on zero quantizer value
                customIntraQuantMatrix[zigZag[i]] = quantizer;
            }
            // DC quantizer value should be 8. Ignore error if it isn't.
            customIntraQuantMatrix[0] = 8;
            intraQuantMatrix = customIntraQuantMatrix;
        } else {
            intraQuantMatrix = defaultIntraQuantMatrix;
        }

        // Read non_intra_quantizer_matrix if present
        if (stream.readBit() != 0) {
            for (int i = 0; i < 64; ++i) {
                int quantizer = stream.readBits(8);
                // Ignore error on zero quantizer value
                customNonIntraQuantMatrix[zigZag[i]] = quantizer;
            }
            nonIntraQuantMatrix = customNonIntraQuantMatrix;
        } else {
            nonIntraQuantMatrix = defaultNonIntraQuantMatrix;
        }

        // Skip any extension data
        if (nextStartCode() == EXTENSION_START_CODE) {
            nextStartCode();
        }

        // Skip any user data
        if (startCode == USER_DATA_START_CODE) {
            skipUserData();
        }
    }

    // Group of pictures layer

    private boolean dropFrame;
    private int timeCodeHours;
    private int timeCodeMinutes;
    private int timeCodeSeconds;
    private int timeCodePictures;

    private void decodeGroup() throws IOException, InterruptedException {
        int timeCode = stream.readBits(25);
        // drop_frame_flag should be 1 only if picture_rate_code is 4
        dropFrame = ((timeCode & 0x1000000) != 0) && (pictureRateCode != 4);
        timeCodeHours = (timeCode >> 19) & 0x1f;
        timeCodeMinutes = (timeCode >> 13) & 0x3f;
        timeCodeSeconds = (timeCode >> 6) & 0x3f;
        timeCodePictures = timeCode & 0x3f;

        stream.readBit();  // closed_gop (ignored)

        //noinspection unused
        boolean brokenLink = (stream.readBit() != 0);

        // Skip any extension data
        if (nextStartCode() == EXTENSION_START_CODE) {
            nextStartCode();
        }

        // Skip any user data
        if (startCode == USER_DATA_START_CODE) {
            skipUserData();
        }

        // Decode picutres
        while (startCode == PICTURE_START_CODE) {
            decodePicture();
            findNextPicture();
        }
    }


    private PictureBuffer reorderBuffer = null;

    private void outputPicture() {
        PictureBuffer output;

        // Frame reordering
        if (pictureCodingType == 3) {
            // No reordering for B-pictures
            output = currentBuffer;
        } else {
            // If this is a reference picture then output the preverious one
            output = reorderBuffer;
            reorderBuffer = currentBuffer;
        }

        if (output == null) {
            return;
        }

        // Set time code data
        output.hours = timeCodeHours;
        output.minutes = timeCodeMinutes;
        output.seconds = timeCodeSeconds;
        output.pictures = timeCodePictures;
        output.pictureCounter = pictureCounter;

        consumer.pictureDecoded(output);

        // Increment picture counter and time code
        ++pictureCounter;
        if (++timeCodePictures == roundedPictureRate) {
            timeCodePictures = 0;
            if (++timeCodeSeconds == 60) {
                timeCodeSeconds = 0;
                if (++timeCodeMinutes == 60) {
                    ++timeCodeHours;
                }
                if (dropFrame && (timeCodeMinutes % 10 != 0)) {
                    timeCodePictures = 2;
                }
            }
        }
    }

    // Picture layer

    private int[] currentY;
    private int[] currentCr;
    private int[] currentCb;

    private PictureBuffer currentBuffer;
    private int[] currentRGB;

    private int pictureCodingType;

    // Buffers for motion compensation
    private int[] forwardY;
    private int[] forwardCr;
    private int[] forwardCb;

    private int[] backwardY;
    private int[] backwardCr;
    private int[] backwardCb;

    private boolean fullPelForward;
    private int forwardRSize;
    private int forwardF;
    private boolean fullPelBackward;
    private int backwardRSize;
    private int backwardF;

    private int pictureCounter = 0;

    private void skipToNextPicture() throws IOException {
        // Ignore current picture and find the next one
        do {
            nextStartCode();
        } while ((startCode != PICTURE_START_CODE) && (startCode !=
                GROUP_START_CODE) && (startCode != SEQUENCE_HEADER_CODE) &&
                (startCode != SEQUENCE_END_CODE));
    }

    private void findNextPicture() throws IOException {
        while ((startCode != PICTURE_START_CODE) && (startCode !=
                GROUP_START_CODE) && (startCode != SEQUENCE_HEADER_CODE) &&
                (startCode != SEQUENCE_END_CODE)) {
            nextStartCode();
        }
    }

    private void decodePicture() throws IOException, InterruptedException {
        stream.readBits(10); // temporal_reference (ignored)

        pictureCodingType = stream.readBits(3);

        // Skip D-pictures
        if (pictureCodingType == 4) {
            skipToNextPicture();
            return;
        }

        // Ignore pictures with unknown coding type (and report no error)
        if ((pictureCodingType == 0) || (pictureCodingType > 4)) {
            skipToNextPicture();
            return;
        }

        // bronen_link flag is ignored
        // TODO: Do something meaningful when broken_link is set

        stream.readBits(16); // vbv_delay (ignored)

        // full_pel_forward, forward_f_code
        if ((pictureCodingType == 2) || (pictureCodingType == 3)) {
            fullPelForward = (stream.readBit() != 0);
            int forwardFCode = stream.readBits(3);
            if (forwardFCode == 0) {
                // Ignore picture with zero forward_f_code
                skipToNextPicture();
                return;
            }
            forwardRSize = forwardFCode - 1;
            forwardF = 1 << forwardRSize;
        }

        // full_pel_backward, backward_f_code
        if (pictureCodingType == 3) {
            fullPelBackward = (stream.readBit() != 0);
            int backwardFCode = stream.readBits(3);
            if (backwardFCode == 0) {
                // Ignore picture with zero backward_f_code
                skipToNextPicture();
                return;
            }
            backwardRSize = backwardFCode - 1;
            backwardF = 1 << backwardRSize;
        }

        int[] tmp1 = forwardY;
        int[] tmp2 = forwardCr;
        int[] tmp3 = forwardCb;
        if (pictureCodingType < 3) {
            forwardY = backwardY;
            forwardCr = backwardCr;
            forwardCb = backwardCb;
        }

        // Skip extra_picture_information
        while (stream.readBit() != 0) {
            stream.readBits(8);
        }

        if (nextStartCode() == EXTENSION_START_CODE) {
            // Skip extension data
            nextStartCode();
        }

        if (startCode == USER_DATA_START_CODE) {
            skipUserData();
        }

        // Decode slices
        while ((startCode >= SLICE_START_CODE_FIRST) &&
                (startCode <= SLICE_START_CODE_LAST)) {
            decodeSlice();
        }

        // Fetch a buffer for the current picture
        currentBuffer = consumer.fetchBuffer(this);
        currentRGB = currentBuffer.pixels;

        YCrCbToRGB();
        outputPicture();

        // If this is a reference picture then rotate the prediction pointers
        if (pictureCodingType < 3) {
            backwardY = currentY;
            backwardCr = currentCr;
            backwardCb = currentCb;
            currentY = tmp1;
            currentCr = tmp2;
            currentCb = tmp3;
        }
    }

    private void YCrCbToRGB() {
        int fullPointer = 0;
        int qPointer = 0;
        int C, D, E, R, G, B;

        for (int y = 0; y < halfHeight; ++y) {
            for (int x = 0; x < halfWidth; ++x) {
                D = currentCr[qPointer] - 128;
                E = currentCb[qPointer] - 128;

                // Top left pixel
                C = 298 * currentY[fullPointer] - 4496;
                R = (C + 409 * E) >> 8;
                if (R < 0) R = 0;
                else if (R > 255) R = 255;
                G = (C - 100 * D - 208 * E) >> 8;
                if (G < 0) G = 0;
                else if (G > 255) G = 255;
                B = (C + 516 * D) >> 8;
                if (B < 0) B = 0;
                else if (B > 255) B = 255;
                currentRGB[fullPointer] = 0xff000000 | (R << 16) | (G << 8) | B;
                fullPointer += codedWidth;

                // Bottom left pixel
                C = 298 * currentY[fullPointer] - 4496;
                R = (C + 409 * E) >> 8;
                if (R < 0) R = 0;
                else if (R > 255) R = 255;
                G = (C - 100 * D - 208 * E) >> 8;
                if (G < 0) G = 0;
                else if (G > 255) G = 255;
                B = (C + 516 * D) >> 8;
                if (B < 0) B = 0;
                else if (B > 255) B = 255;
                currentRGB[fullPointer] = 0xff000000 | (R << 16) | (G << 8) | B;
                ++fullPointer;

                // Bottom right pixel
                C = 298 * currentY[fullPointer] - 4496;
                R = (C + 409 * E) >> 8;
                if (R < 0) R = 0;
                else if (R > 255) R = 255;
                G = (C - 100 * D - 208 * E) >> 8;
                if (G < 0) G = 0;
                else if (G > 255) G = 255;
                B = (C + 516 * D) >> 8;
                if (B < 0) B = 0;
                else if (B > 255) B = 255;
                currentRGB[fullPointer] = 0xff000000 | (R << 16) | (G << 8) | B;
                fullPointer -= codedWidth;

                // Top right pixel
                C = 298 * currentY[fullPointer] - 4496;
                R = (C + 409 * E) >> 8;
                if (R < 0) R = 0;
                else if (R > 255) R = 255;
                G = (C - 100 * D - 208 * E) >> 8;
                if (G < 0) G = 0;
                else if (G > 255) G = 255;
                B = (C + 516 * D) >> 8;
                if (B < 0) B = 0;
                else if (B > 255) B = 255;
                currentRGB[fullPointer] = 0xff000000 | (R << 16) | (G << 8) | B;
                ++fullPointer;

                ++qPointer;
            }
            fullPointer += codedWidth;
        }
    }

    // Slice layer

    private int quantizerScale;
    private boolean sliceBegin;

    private void decodeSlice() throws IOException {
        sliceBegin = true;

        // Calculate macroblock address
        int sliceVerticalPosition = startCode & 0xff;
        macroblockAddress = (sliceVerticalPosition - 1) * mbWidth - 1;

        // Reset motion vectors and DC predictors
        motionFwH = motionFwHPrev = 0;
        motionFwV = motionFwVPrev = 0;
        motionBwH = motionBwHPrev = 0;
        motionBwV = motionBwVPrev = 0;
        DC_PredictorY = 128;
        DC_PredictorCr = 128;
        DC_PredictorCb = 128;

        // Ignore slices with illegal slice_vertical_position (report no error)
        if (macroblockAddress >= mbSize) {
            nextStartCode();
            return;
        }

        quantizerScale = stream.readBits(5);
        // quantizer_scale should be non-zero. Ignore error if it's zero.

        // Skip any extra slice information
        while (stream.readBit() != 0) {
            stream.readBits(8);
        }

        // Read macroblocks
        try {
            while (stream.noStartCode()) {
                decodeMacroblock();
            }
        } catch (InputBitStream.InvalidVLCException e) {
            logger.warn("Invalid mpeg stream (vlc), ignore error");

        } catch (ArrayIndexOutOfBoundsException e) {
            logger.warn("missing end of block marker");
        }

        nextStartCode();
    }

    // Macroblock layer

    private int macroblockAddress;
    private int mbRow;
    private int mbCol;

    private boolean macroblockIntra;
    private boolean macroblockMotFw;
    private boolean macroblockMotBw;

    private int motionFwH;
    private int motionFwV;
    private int motionBwH;
    private int motionBwV;
    private int motionFwHPrev;
    private int motionFwVPrev;
    private int motionBwHPrev;
    private int motionBwVPrev;

    private void decodeMacroblock() throws IOException {
        // Decode macroblock_address_increment
        int increment = 0;
        int inc = stream.readCode(InputBitStream.macroblockAddressIncrement);
        while (inc == 34) {
            // macroblock_stuffing
            inc = stream.readCode(InputBitStream.macroblockAddressIncrement);
        }
        while (inc == 35) {
            // macroblock_escape
            increment += 33;
            inc = stream.readCode(InputBitStream.macroblockAddressIncrement);
        }
        increment += inc;

        // Process any skipped macroblocks
        if (sliceBegin) {
            // The first macroblock_address_increment of each slice is relative
            // to beginning of the preverious row, not the preverious macroblock
            sliceBegin = false;
            macroblockAddress += increment;
        } else {
            if (macroblockAddress + increment >= mbSize) {
                // Illegal (too large) macroblock_address_increment
                return;
            }
            if (increment > 1) {
                // Skipped macroblocks reset DC predictors
                DC_PredictorY = 128;
                DC_PredictorCr = 128;
                DC_PredictorCb = 128;

                // Skipped macroblocks in P-pictures reset motion vectors
                if (pictureCodingType == 2) {
                    motionFwH = motionFwHPrev = 0;
                    motionFwV = motionFwVPrev = 0;
                }
            }

            // Predict skipped macroblocks
            while (increment > 1) {
                ++macroblockAddress;
                mbRow = macroblockAddress / mbWidth;
                mbCol = macroblockAddress % mbWidth;
                predictMacroblock();
                --increment;
            }
            ++macroblockAddress;
        }
        mbRow = macroblockAddress / mbWidth;
        mbCol = macroblockAddress % mbWidth;

        // Process the current macroblock
        int macroblockType = stream.readCode(mbTypeTables[pictureCodingType]);
        macroblockIntra = (macroblockType & 0x01) != 0;
        macroblockMotFw = (macroblockType & 0x08) != 0;
        macroblockMotBw = (macroblockType & 0x04) != 0;

        // Quantizer scale
        if ((macroblockType & 0x10) != 0) {
            quantizerScale = stream.readBits(5);
            // Ignore error on zero quantizer_scale
        }

        if (macroblockIntra) {
            // Intra-coded macroblocks reset motion vectors
            motionFwH = motionFwHPrev = 0;
            motionFwV = motionFwVPrev = 0;
            motionBwH = motionBwHPrev = 0;
            motionBwV = motionBwVPrev = 0;
        } else {
            // Non-intra macroblocks reset DC predictors
            DC_PredictorY = 128;
            DC_PredictorCr = 128;
            DC_PredictorCb = 128;

            decodeMotionVectors();
            predictMacroblock();
        }

        // Decode blocks
        int cbp = ((macroblockType & 0x02) != 0) ?
                stream.readCode(InputBitStream.codedBlockPattern) :
                (macroblockIntra ? 63 : 0);

        for (int block = 0, mask = 0x20; block < 6; ++block) {
            if ((cbp & mask) != 0) {
                decodeBlock(block);
            }
            mask >>= 1;
        }
    }

    private void decodeMotionVectors() throws IOException {
        int code;
        int d;
        int r;

        // Forward
        if (macroblockMotFw) {
            // Horizontal forward
            code = stream.readCode(InputBitStream.motion);
            if ((code != 0) && (forwardF != 1)) {
                r = stream.readBits(forwardRSize);
                d = ((Math.abs(code) - 1) << forwardRSize) + r + 1;
                if (code < 0) {
                    d = -d;
                }
            } else {
                d = code;
            }
            motionFwHPrev += d;
            if (motionFwHPrev > (forwardF << 4) - 1) {
                motionFwHPrev -= forwardF << 5;
            } else if (motionFwHPrev < ((-forwardF) << 4)) {
                motionFwHPrev += forwardF << 5;
            }
            motionFwH = motionFwHPrev;
            if (fullPelForward) {
                motionFwH <<= 1;
            }

            // Vertical forward
            code = stream.readCode(InputBitStream.motion);
            if ((code != 0) && (forwardF != 1)) {
                r = stream.readBits(forwardRSize);
                d = ((Math.abs(code) - 1) << forwardRSize) + r + 1;
                if (code < 0) {
                    d = -d;
                }
            } else {
                d = code;
            }
            motionFwVPrev += d;
            if (motionFwVPrev > (forwardF << 4) - 1) {
                motionFwVPrev -= forwardF << 5;
            } else if (motionFwVPrev < ((-forwardF) << 4)) {
                motionFwVPrev += forwardF << 5;
            }
            motionFwV = motionFwVPrev;
            if (fullPelForward) {
                motionFwV <<= 1;
            }
        } else if (pictureCodingType == 2) {
            // No motion information in P-picture, reset vectors
            motionFwH = motionFwHPrev = 0;
            motionFwV = motionFwVPrev = 0;
        }

        // Backward
        if (macroblockMotBw) {
            // Horizontal backward
            code = stream.readCode(InputBitStream.motion);
            if ((code != 0) && (backwardF != 1)) {
                r = stream.readBits(backwardRSize);
                d = ((Math.abs(code) - 1) << backwardRSize) + r + 1;
                if (code < 0) {
                    d = -d;
                }
            } else {
                d = code;
            }
            motionBwHPrev += d;
            if (motionBwHPrev > (backwardF << 4) - 1) {
                motionBwHPrev -= backwardF << 5;
            } else if (motionBwHPrev < ((-backwardF) << 4)) {
                motionBwHPrev += backwardF << 5;
            }
            motionBwH = motionBwHPrev;
            if (fullPelForward) {
                motionBwH <<= 1;
            }

            // Vertical backward
            code = stream.readCode(InputBitStream.motion);
            if ((code != 0) && (backwardF != 1)) {
                r = stream.readBits(backwardRSize);
                d = ((Math.abs(code) - 1) << backwardRSize) + r + 1;
                if (code < 0) {
                    d = -d;
                }
            } else {
                d = code;
            }
            motionBwVPrev += d;
            if (motionBwVPrev > (backwardF << 4) - 1) {
                motionBwVPrev -= backwardF << 5;
            } else if (motionBwVPrev < ((-backwardF) << 4)) {
                motionBwVPrev += backwardF << 5;
            }
            motionBwV = motionBwVPrev;
            if (fullPelForward) {
                motionBwV <<= 1;
            }
            motionBwV = motionBwVPrev;
            if (fullPelBackward) {
                motionBwVPrev <<= 1;
            }
        }
    }

    private void copyMacroblock(int motionH, int motionV, int[] Y, int[] Cr, int[] Cb) {
        int V, H;
        boolean oddV, oddH;
        int dest, scan, last;

        // Luminance
        dest = (mbRow * codedWidth + mbCol) << 4;
        scan = codedWidth - 16;
        H = motionH >> 1;
        V = motionV >> 1;
        oddH = (motionH & 1) == 1;
        oddV = (motionV & 1) == 1;
        last = dest + (codedWidth << 4);
        int src = ((mbRow << 4) + V) * codedWidth + (mbCol << 4) + H;
        if (oddH) {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] = (Y[src] + Y[src + 1] +
                                Y[src + codedWidth] + Y[src + codedWidth + 1] + 2) >> 2;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] = (Y[src] + Y[src + 1] + 1) >> 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        } else {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] = (Y[src] + Y[src + codedWidth] + 1) >> 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] = Y[src];
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        }

        // Chrominance
        dest = (mbRow * halfWidth + mbCol) << 3;
        scan = halfWidth - 8;
        H = (motionH / 2) >> 1;
        V = (motionV / 2) >> 1;
        oddH = ((motionH / 2) & 1) == 1;
        oddV = ((motionV / 2) & 1) == 1;
        src = ((mbRow << 3) + V) * halfWidth + (mbCol << 3) + H;
        last = dest + (halfWidth << 3);
        if (oddH) {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] = (Cr[src] + Cr[src + 1] +
                                Cr[src + halfWidth] + Cr[src + halfWidth + 1] + 2) >> 2;
                        currentCb[dest] = (Cb[src] + Cb[src + 1] +
                                Cb[src + halfWidth] + Cb[src + halfWidth + 1] + 2) >> 2;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] = (Cr[src] + Cr[src + 1] + 1) >> 1;
                        currentCb[dest] = (Cb[src] + Cb[src + 1] + 1) >> 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        } else {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] = (Cr[src] + Cr[src + halfWidth] + 1) >> 1;
                        currentCb[dest] = (Cb[src] + Cb[src + halfWidth] + 1) >> 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] = Cr[src];
                        currentCb[dest] = Cb[src];
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        }
    }

    private void interpolateMacroblock() {
        int V, H;
        boolean oddV, oddH;
        int dest, scan, last;

        // Luminance
        dest = (mbRow * codedWidth + mbCol) << 4;
        scan = codedWidth - 16;
        H = motionBwH >> 1;
        V = motionBwV >> 1;
        oddH = (motionBwH & 1) == 1;
        oddV = (motionBwV & 1) == 1;
        last = dest + (codedWidth << 4);
        int src = ((mbRow << 4) + V) * codedWidth + (mbCol << 4) + H;
        if (oddH) {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] += ((backwardY[src] + backwardY[src + 1] +
                                backwardY[src + codedWidth] + backwardY[src + codedWidth + 1] + 2) >> 2) + 1;
                        currentY[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] += ((backwardY[src] + backwardY[src + 1] + 1) >> 1) + 1;
                        currentY[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        } else {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] += ((backwardY[src] + backwardY[src + codedWidth] + 1) >> 1) + 1;
                        currentY[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 16; ++x) {
                        currentY[dest] += backwardY[src] + 1;
                        currentY[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        }

        // Chrominance
        dest = (mbRow * halfWidth + mbCol) << 3;
        scan = halfWidth - 8;
        H = (motionBwH / 2) >> 1;
        V = (motionBwV / 2) >> 1;
        oddH = ((motionBwH / 2) & 1) == 1;
        oddV = ((motionBwV / 2) & 1) == 1;
        src = ((mbRow << 3) + V) * halfWidth + (mbCol << 3) + H;
        last = dest + (halfWidth << 3);
        if (oddH) {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] += ((backwardCr[src] + backwardCr[src + 1] +
                                backwardCr[src + halfWidth] + backwardCr[src + halfWidth + 1] + 2) >> 2) + 1;
                        currentCb[dest] += ((backwardCb[src] + backwardCb[src + 1] +
                                backwardCb[src + halfWidth] + backwardCb[src + halfWidth + 1] + 2) >> 2) + 1;
                        currentCr[dest] >>= 1;
                        currentCb[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] += ((backwardCr[src] + backwardCr[src + 1] + 1) >> 1) + 1;
                        currentCb[dest] += ((backwardCb[src] + backwardCb[src + 1] + 1) >> 1) + 1;
                        currentCr[dest] >>= 1;
                        currentCb[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        } else {
            if (oddV) {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] += ((backwardCr[src] + backwardCr[src + halfWidth] + 1) >> 1) + 1;
                        currentCb[dest] += ((backwardCb[src] + backwardCb[src + halfWidth] + 1) >> 1) + 1;
                        currentCr[dest] >>= 1;
                        currentCb[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            } else {
                while (dest < last) {
                    for (int x = 0; x < 8; ++x) {
                        currentCr[dest] += backwardCr[src] + 1;
                        currentCb[dest] += backwardCb[src] + 1;
                        currentCr[dest] >>= 1;
                        currentCb[dest] >>= 1;
                        ++dest;
                        ++src;
                    }
                    dest += scan;
                    src += scan;
                }
            }
        }
    }

    private void predictMacroblock() {
        try {
            if (pictureCodingType == 3) {
                if (macroblockMotFw) {
                    copyMacroblock(motionFwH, motionFwV, forwardY, forwardCr, forwardCb);
                    if (macroblockMotBw) {
                        interpolateMacroblock();
                    }
                } else {
                    copyMacroblock(motionBwH, motionBwV, backwardY, backwardCr, backwardCb);
                }
            } else {
                copyMacroblock(motionFwH, motionFwV, forwardY, forwardCr, forwardCb);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // Ignore error on motion vectors pointing outside the bitmap
        }
    }

    // Block layer

    private int DC_PredictorY;
    private int DC_PredictorCr;
    private int DC_PredictorCb;

    private final int[] blockData = new int[64];

    private void decodeBlock(int block) throws IOException {
        int n = 0;
        int coeff;
        int[] quantMatrix;

        // Clear preverious data
        Arrays.fill(blockData, 0);

        // Decode DC coefficient of intra-coded blocks
        if (macroblockIntra) {
            int predictor;
            int DCT_DC_Size;

            // DC prediction
            if (block < 4) {
                predictor = DC_PredictorY;
                DCT_DC_Size = stream.readCode(InputBitStream.DCT_DC_SizeLuminance);
            } else {
                predictor = (block == 4 ? DC_PredictorCr : DC_PredictorCb);
                DCT_DC_Size = stream.readCode(InputBitStream.DCT_DC_SizeChrominance);
            }

            // Read DC coeff
            if (DCT_DC_Size > 0) {
                int differential = stream.readBits(DCT_DC_Size);
                if ((differential & (1 << (DCT_DC_Size - 1))) != 0) {
                    blockData[0] = predictor + differential;
                } else {
                    blockData[0] = predictor + ((-1 << DCT_DC_Size) | (differential + 1));
                }
            } else {
                blockData[0] = predictor;
            }

            // Save predictor value
            if (block < 4) {
                DC_PredictorY = blockData[0];
            } else if (block == 4) {
                DC_PredictorCr = blockData[0];
            } else {
                DC_PredictorCb = blockData[0];
            }

            // Dequantize + premultiply
            blockData[0] <<= (3 + 5);

            quantMatrix = intraQuantMatrix;
            n = 1;
        } else {
            quantMatrix = nonIntraQuantMatrix;
        }

        // Decode AC coefficients (+DC for non-intra)
        int level;
        while (true) {
            int run;
            coeff = stream.readCode(InputBitStream.DCT_Coeff);
            if ((coeff == 0x0001) && (n > 0) && (stream.readBit() == 0)) {
                // end_of_block
                break;
            }
            if (coeff == 0xffff) {
                // escape
                run = stream.readBits(6);
                int i = stream.readBits(8);
                if (i < 128) {
                    if (i == 0) {
                        level = stream.readBits(8);
                    } else {
                        level = i;
                    }
                } else {
                    if (i == 128) {
                        level = stream.readBits(8) - 256;
                    } else {
                        level = i - 256;
                    }
                }
            } else {
                run = coeff >> 8;
                level = coeff & 0xff;
                if (stream.readBit() != 0) {
                    level = -level;
                }
            }

            n += run;
            int dezigZagged = zigZag[n];
            ++n;

            // Dequantize, oddify, clip
            level <<= 1;
            if (!macroblockIntra) {
                level += (level < 0 ? -1 : 1);
            }
            level = (level * quantizerScale * quantMatrix[dezigZagged]) / 16;
            if ((level & 1) == 0) {
                level -= level > 0 ? 1 : -1;
            }
            if (level > 2047) level = 2047;
            else if (level < -2048) level = -2048;

            // Save premultiplied coefficient
            blockData[dezigZagged] = level * premultiplierMatrix[dezigZagged];
        }

        // Transform block data to the spatial domain
        if (n == 1) {
            // Only DC coeff., no IDCT needed
            Arrays.fill(blockData, (blockData[0] + 128) >> 8);
        } else {
            IDCT();
        }

        // Move block to its place
        int[] destArray;
        int destIndex;
        int scan;
        if (block < 4) {
            destArray = currentY;
            scan = codedWidth - 8;
            destIndex = (mbRow * codedWidth + mbCol) << 4;
            if ((block & 1) != 0) destIndex += 8;
            if ((block & 2) != 0) destIndex += codedWidth << 3;
        } else {
            destArray = (block == 4) ? currentCr : currentCb;
            scan = (codedWidth >> 1) - 8;
            destIndex = ((mbRow * codedWidth) << 2) + (mbCol << 3);
        }

        n = 0;
        if (macroblockIntra) {
            // Overwrite (no prediction)
            for (int i = 0; i < 8; ++i) {
                for (int j = 0; j < 8; ++j) {
                    int value = blockData[n];
                    ++n;
                    if (value < 0) value = 0;
                    else if (value > 255) value = 255;
                    destArray[destIndex] = value;
                    ++destIndex;
                }
                destIndex += scan;
            }
        } else {
            // Add data to the predicted macroblock
            for (int i = 0; i < 8; ++i) {
                for (int j = 0; j < 8; ++j) {
                    int value = blockData[n] + destArray[destIndex];
                    ++n;
                    if (value < 0) value = 0;
                    else if (value > 255) value = 255;
                    destArray[destIndex] = value;
                    ++destIndex;
                }
                destIndex += scan;
            }
        }
    }

    private void IDCT() {
        // See http://vsr.informatik.tu-chemnitz.de/~jan/MPEG/HTML/IDCT.html
        // for more info.

        int b1, b3, b4, b6, b7, tmp1, tmp2, m0;
        int x0, x1, x2, x3, x4, y3, y4, y5, y6, y7;
        int i;

        // Transform columns
        for (i = 0; i < 8; ++i) {
            b1 = blockData[4 * 8 + i];
            b3 = blockData[2 * 8 + i] + blockData[6 * 8 + i];
            b4 = blockData[5 * 8 + i] - blockData[3 * 8 + i];
            tmp1 = blockData[8 + i] + blockData[7 * 8 + i];
            tmp2 = blockData[3 * 8 + i] + blockData[5 * 8 + i];
            b6 = blockData[8 + i] - blockData[7 * 8 + i];
            b7 = tmp1 + tmp2;
            m0 = blockData[i];
            x4 = ((b6 * 473 - b4 * 196 + 128) >> 8) - b7;
            x0 = x4 - (((tmp1 - tmp2) * 362 + 128) >> 8);
            x1 = m0 - b1;
            x2 = (((blockData[2 * 8 + i] - blockData[6 * 8 + i]) * 362 + 128) >> 8) - b3;
            x3 = m0 + b1;
            y3 = x1 + x2;
            y4 = x3 + b3;
            y5 = x1 - x2;
            y6 = x3 - b3;
            y7 = -x0 - ((b4 * 473 + b6 * 196 + 128) >> 8);
            blockData[i] = b7 + y4;
            blockData[8 + i] = x4 + y3;
            blockData[2 * 8 + i] = y5 - x0;
            blockData[3 * 8 + i] = y6 - y7;
            blockData[4 * 8 + i] = y6 + y7;
            blockData[5 * 8 + i] = x0 + y5;
            blockData[6 * 8 + i] = y3 - x4;
            blockData[7 * 8 + i] = y4 - b7;
        }

        // Transform rows
        for (i = 0; i < 64; i += 8) {
            b1 = blockData[4 + i];
            b3 = blockData[2 + i] + blockData[6 + i];
            b4 = blockData[5 + i] - blockData[3 + i];
            tmp1 = blockData[1 + i] + blockData[7 + i];
            tmp2 = blockData[3 + i] + blockData[5 + i];
            b6 = blockData[1 + i] - blockData[7 + i];
            b7 = tmp1 + tmp2;
            m0 = blockData[i];
            x4 = ((b6 * 473 - b4 * 196 + 128) >> 8) - b7;
            x0 = x4 - (((tmp1 - tmp2) * 362 + 128) >> 8);
            x1 = m0 - b1;
            x2 = (((blockData[2 + i] - blockData[6 + i]) * 362 + 128) >> 8) - b3;
            x3 = m0 + b1;
            y3 = x1 + x2;
            y4 = x3 + b3;
            y5 = x1 - x2;
            y6 = x3 - b3;
            y7 = -x0 - ((b4 * 473 + b6 * 196 + 128) >> 8);
            blockData[i] = (b7 + y4 + 128) >> 8;
            blockData[1 + i] = (x4 + y3 + 128) >> 8;
            blockData[2 + i] = (y5 - x0 + 128) >> 8;
            blockData[3 + i] = (y6 - y7 + 128) >> 8;
            blockData[4 + i] = (y6 + y7 + 128) >> 8;
            blockData[5 + i] = (x0 + y5 + 128) >> 8;
            blockData[6 + i] = (y3 - x4 + 128) >> 8;
            blockData[7 + i] = (y4 - b7 + 128) >> 8;
        }
    }
}
