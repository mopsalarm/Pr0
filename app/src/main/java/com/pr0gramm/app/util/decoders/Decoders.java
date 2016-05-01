package com.pr0gramm.app.util.decoders;

import android.graphics.Bitmap;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.squareup.picasso.Downloader;

/**
 * A class
 */
public class Decoders {
    public static ImageRegionDecoder regionDecoder(Downloader downloader) {
        //noinspection unchecked
        return new DownloadingRegionDecoder(downloader,
                FallbackRegionDecoder.chain(
                        new AndroidRegionDecoder(Bitmap.Config.RGB_565),
                        new AndroidRegionDecoder(Bitmap.Config.ARGB_8888)
                ));
    }
}
