package com.pr0gramm.app.feed

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.R
import java.util.*

/**
 * Content type to load.
 */
enum class ContentType constructor(val flag: Int, val title: Int) : Parcelable {
    SFW(1, R.string.type_sfw), NSFW(2, R.string.type_nsfw), NSFL(4, R.string.type_nsfl), NSFP(8, R.string.type_nsfp);

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, f: Int) {
        dest.writeInt(ordinal)
    }

    companion object {
        val All: List<ContentType> = values().toList()
        val AllSet: EnumSet<ContentType> = EnumSet.allOf(ContentType::class.java)

        fun combine(flags: Iterable<ContentType>): Int {
            return flags.sumBy { it.flag }
        }

        /**
         * Gets a all the content types that are encoded in the given
         * flags number. This is the reverse of [.combine].
         * @param flags The encoded content types.
         */
        fun decompose(flags: Int): Set<ContentType> {
            return All.filter { (it.flag and flags) != 0 }.toSet()
        }

        /**
         * Returns the [com.pr0gramm.app.feed.ContentType] that matches the given
         * flag's value. There must be only one bit set on the flags parameter.
         * This returns an empty optional, if no content type could be found.
         */
        fun valueOf(flag: Int): ContentType? {
            return All.firstOrNull { it.flag == flag }
        }

        fun toString(context: Context, types: Collection<ContentType>): String {
            return types.map { context.getString(it.title) }.joinToString("+")
        }

        @JvmStatic
        val CREATOR: Parcelable.Creator<ContentType> = object : Parcelable.Creator<ContentType> {
            override fun createFromParcel(source: Parcel): ContentType {
                val idx = source.readInt()
                return ContentType.values()[idx]
            }

            override fun newArray(size: Int): Array<ContentType?> {
                return arrayOfNulls(size)
            }
        }
    }
}

/**
 * Removes implicitly added content types like [.NSFP]
 */
fun Set<ContentType>.withoutImplicit(): EnumSet<ContentType> {
    val types = EnumSet.copyOf(this)
    types.remove(ContentType.NSFP)
    return types
}
