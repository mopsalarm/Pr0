package com.pr0gramm.app.parcel

import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class NewCommentParceler(val value: Api.NewComment) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeLong(value.commentId)
            writeTyped(CommentListParceler(value.comments))
        }
    }

    companion object {
        @JvmField
        val CREATOR = creator { parcel ->
            val id = parcel.readLong()
            val comments = parcel.readTyped(CommentListParceler.CREATOR).comments
            NewCommentParceler(Api.NewComment(id, comments))
        }
    }
}
