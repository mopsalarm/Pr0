package com.pr0gramm.app.parcel

import com.pr0gramm.app.api.pr0gramm.Api

/**
 */
class NewCommentParceler(val value: Api.NewComment) : Freezable {
    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeLong(value.commentId)
        write(CommentListParceler(value.comments))
    }

    companion object : Unfreezable<NewCommentParceler> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): NewCommentParceler = with(source) {
            val id = readLong()
            val comments = read(CommentListParceler).comments
            NewCommentParceler(Api.NewComment(id, comments))
        }
    }
}
