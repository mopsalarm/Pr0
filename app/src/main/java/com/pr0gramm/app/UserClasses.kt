package com.pr0gramm.app

/**
 */
object UserClasses {
    @JvmStatic
    val MarkSymbol: List<String> = listOf(
            "\u25CF", // user_type_schwuchtel
            "\u25CF", // user_type_neuschwuchtel
            "\u25CF", // user_type_altschwuchtel
            "\u25CF", // user_type_admin
            "\u25CF", // user_type_gesperrt
            "\u25CF", // user_type_moderator
            "\u25CF", // user_type_fliesentisch
            "\u25C6", // user_type_legende
            "\u25A7", // user_type_wichtler
            "\u25CF") // user_type_pr0mium

    @JvmStatic
    val MarkStrings: List<Int> = listOf(
            R.string.user_type_schwuchtel,
            R.string.user_type_neuschwuchtel,
            R.string.user_type_altschwuchtel,
            R.string.user_type_admin,
            R.string.user_type_gesperrt,
            R.string.user_type_moderator,
            R.string.user_type_fliesentisch,
            R.string.user_type_legende,
            R.string.user_type_wichtler,
            R.string.user_type_pr0mium)

    @JvmStatic
    val MarkColors: List<Int> = listOf(
            R.color.user_type_schwuchtel,
            R.color.user_type_neuschwuchtel,
            R.color.user_type_altschwuchtel,
            R.color.user_type_admin,
            R.color.user_type_gesperrt,
            R.color.user_type_moderator,
            R.color.user_type_fliesentisch,
            R.color.user_type_legende,
            R.color.user_type_wichtler,
            R.color.user_type_pr0mium)
}
