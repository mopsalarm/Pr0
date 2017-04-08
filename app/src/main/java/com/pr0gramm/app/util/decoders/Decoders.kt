package com.pr0gramm.app.util.decoders

import android.graphics.Bitmap

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder
import com.squareup.picasso.Downloader

object Decoders {
    @JvmStatic
    fun newFancyRegionDecoder(downloader: Downloader): ImageRegionDecoder {
        return DownloadingRegionDecoder(downloader, FallbackRegionDecoder.chain(
                AndroidRegionDecoder(Bitmap.Config.RGB_565),
                AndroidRegionDecoder(Bitmap.Config.ARGB_8888),
                SimpleRegionDecoder(Bitmap.Config.RGB_565),
                SimpleRegionDecoder(Bitmap.Config.ARGB_8888)
        ))
    }
}
