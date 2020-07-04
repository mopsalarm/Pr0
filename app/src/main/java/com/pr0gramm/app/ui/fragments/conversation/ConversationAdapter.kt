package com.pr0gramm.app.ui.fragments.conversation

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Parcel
import android.os.Parcelable
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pr0gramm.app.Instant
import com.pr0gramm.app.R
import com.pr0gramm.app.api.pr0gramm.Api
import com.pr0gramm.app.parcel.Freezable
import com.pr0gramm.app.parcel.Unfreezable
import com.pr0gramm.app.parcel.parcelableCreator
import com.pr0gramm.app.ui.DelegatePagingDataAdapter
import com.pr0gramm.app.ui.ItemAdapterDelegate
import com.pr0gramm.app.ui.ListItemTypeAdapterDelegate
import com.pr0gramm.app.ui.adaptTo
import com.pr0gramm.app.util.*
import java.text.SimpleDateFormat
import java.util.*


sealed class ConversationItem {
    class Message(val message: Api.ConversationMessage) : ConversationItem()
    class Divider(val text: String) : ConversationItem()
}


class ConversationAdapter
    : DelegatePagingDataAdapter<ConversationItem>(ItemCallback) {

    init {
        delegates += MessageAdapterDelegate(sentValue = true)
        delegates += MessageAdapterDelegate(sentValue = false)

        delegates += DividerAdapterDelegate().adaptTo { value: ConversationItem.Divider -> value.text }

//        delegates += staticLayoutAdapterDelegate(R.layout.item_conversation_empty, NoConversationsValue)
    }

    val messages: List<Api.ConversationMessage>
        get() = items.mapNotNull { (it as? ConversationItem.Message)?.message }

    private object ItemCallback : DiffUtil.ItemCallback<ConversationItem>() {
        override fun areItemsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            return when {
                oldItem is ConversationItem.Message && newItem is ConversationItem.Message ->
                    oldItem.message.id == newItem.message.id

                oldItem is ConversationItem.Divider && newItem is ConversationItem.Divider ->
                    oldItem.text == newItem.text

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ConversationItem, newItem: ConversationItem): Boolean {
            // if the items are the same, the contents is also equal.
            return true
        }
    }
}

private class MessageAdapterDelegate(private val sentValue: Boolean)
    : ItemAdapterDelegate<ConversationItem.Message, ConversationItem, MessageAdapterDelegate.ViewHolder>() {

    private val format = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun isForViewType(value: ConversationItem): Boolean {
        return value is ConversationItem.Message && value.message.sent == sentValue
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val layout = if (sentValue) R.layout.item_message_sent else R.layout.item_message_received
        return ViewHolder(parent.inflateDetachedChild(layout))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: ConversationItem.Message) {
        val context = holder.itemView.context

        val text = buildSpannedString {
            append(Linkify.linkify(context, value.message.messageText))

            inSpans(SpaceSpan(context.sp(32f).toInt())) {
                append(" ")
            }
        }

        holder.message.setTextFuture(text)

        holder.time.text = value.message.creationTime.toString(format)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val message = find<AppCompatTextView>(R.id.message)
        val time = find<TextView>(R.id.time)
    }
}

class DividerAdapterDelegate : ListItemTypeAdapterDelegate<String, String, DividerAdapterDelegate.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateDetachedChild(R.layout.item_date_divider))
    }

    override fun onBindViewHolder(holder: ViewHolder, value: String) {
        holder.textView.text = value
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView = find<TextView>(R.id.text)
    }
}

data class StringValue(val text: String)

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

private class ConversationMessageFreezer(val message: Api.ConversationMessage) : Freezable {
    override fun freeze(sink: Freezable.Sink) = with(sink) {
        writeLong(message.id)
        write(message.creationTime)
        writeString(message.messageText)
        writeBoolean(message.sent)
    }

    companion object : Unfreezable<ConversationMessageFreezer> {
        @JvmField
        val CREATOR = parcelableCreator()

        override fun unfreeze(source: Freezable.Source): ConversationMessageFreezer {
            return ConversationMessageFreezer(Api.ConversationMessage(
                    id = source.readLong(),
                    creationTime = source.read(Instant),
                    messageText = source.readString(),
                    sent = source.readBoolean()))
        }
    }
}
