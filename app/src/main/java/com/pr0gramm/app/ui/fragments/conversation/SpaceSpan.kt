package com.pr0gramm.app.ui.fragments.conversation

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Parcel
import android.os.Parcelable
import android.text.TextPaint
import android.text.style.ReplacementSpan

class SpaceSpan(private val pxWidth: Int) : ReplacementSpan(), Parcelable {

    constructor(parcel: Parcel) : this(parcel.readInt())

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        return pxWidth
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {}

    override fun updateMeasureState(textPaint: TextPaint) {}

    override fun updateDrawState(tp: TextPaint?) {}

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(pxWidth)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SpaceSpan> {
        override fun createFromParcel(parcel: Parcel): SpaceSpan {
            return SpaceSpan(parcel)
        }

        override fun newArray(size: Int): Array<SpaceSpan?> {
            return arrayOfNulls(size)
        }
    }
}