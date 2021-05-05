package com.pr0gramm.app.parcel

import android.os.Parcel
import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class NewCommentParceler(val value: Api.NewComment) : DefaultParcelable {
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(value.commentId ?: 0)
        dest.write(CommentListParceler(value.comments))
    }

    companion object CREATOR : SimpleCreator<NewCommentParceler>(javaClassOf()) {
        override fun createFromParcel(source: Parcel): NewCommentParceler {
            return with(source) {
                val id = readLong().takeIf { it > 0 }
                val comments = read(CommentListParceler).comments
                NewCommentParceler(Api.NewComment(id, comments))
            }
        }
    }
}
