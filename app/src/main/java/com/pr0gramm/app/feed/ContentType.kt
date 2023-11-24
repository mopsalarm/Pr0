package com.pr0gramm.app.feed

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.pr0gramm.app.R
import com.pr0gramm.app.model.user.LoginState
import com.pr0gramm.app.parcel.creator
import java.util.EnumSet

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

    fun isAvailable(loginState: LoginState): Boolean {
        if (!loginState.authorized) {
            return this == SFW
        }

        if (!loginState.verified) {
            return this == SFW || this == NSFP
        }

        return true
    }

    companion object {
        val All: List<ContentType> = values().toList()
        val AllSet: EnumSet<ContentType> = EnumSet.allOf(ContentType::class.java)

        private val values: Array<ContentType> = values()

        fun combine(flags: Iterable<ContentType>): Int {
            return flags.sumOf { it.flag }
        }

        fun combine(vararg flags: ContentType): Int {
            return flags.sumOf { it.flag }
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
         * Returns the first [ContentType] that is part of the given flags.
         */
        fun firstOf(flags: Int): ContentType {
            return All.firstOrNull { it.flag and flags != 0 } ?: SFW
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

        @JvmField
        val CREATOR = creator {
            val idx = it.readInt()
            values[idx]
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
