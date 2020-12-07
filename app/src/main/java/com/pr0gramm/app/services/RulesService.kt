package com.pr0gramm.app.services

import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.ignoreAllExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 */
class RulesService(private val okHttpClient: OkHttpClient) {
    private var cachedText: String = defaultRulesText

    suspend fun displayInto(targetView: TextView) {
        displayInto(targetView, cachedText)

        // now try to fetch the updated text
        ignoreAllExceptions {
            val text = runInterruptible(Dispatchers.IO) {
                val url = "https://pr0gramm.com/media/templates/rules.html"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) response.body?.string() else null
            }

            if (text != null) {
                // display the new text again on success
                displayInto(targetView, cachedText)
            }
        }
    }

    private fun displayInto(rulesView: TextView, rules: String) {
        val list = "<li>(.+?)</li>".toRegex(RegexOption.DOT_MATCHES_ALL).findAll(rules).mapIndexed { idx, match ->
            val rule = match.groupValues[1].replace("<[^>]+>".toRegex(), "").trim { it <= ' ' }
            "#" + (idx + 1) + "  " + rule
        }

        val resources = rulesView.context.resources
        val offset = resources.getDimensionPixelSize(R.dimen.bullet_list_leading_margin)
        rulesView.text = AndroidUtility.makeBulletList(offset, list.toList())
    }
}

private const val defaultRulesText = """
        <li>nsfw/nsfl Inhalte müssen vor dem Upload entsprechend markiert werden.</li>
        <li>Keine suggestiven Bilder/Videos oder Gore von/mit Minderjährigen/Babys/Föten.</li>
        <li>Keine Tierpornos. Keine Tierquälerei.</li>
        <li>Kein stumpfer Rassismus, kein rechtes Gedankengut, keine Nazi-Nostalgie. Das gilt auch für die Tags.</li>
        <li>Keine Werbung, keine Affiliate-Links in den Bildern, kein Spam, kein Vandalismus.</li>
        <li>Keine Informationen oder Bilder von Privatpersonen; keine Klarnamen in den Uploads, Tags oder Kommentaren.</li>
        <li>Ein Mindestmaß an Bildqualität wird erwartet. Bildmaterial mit starken Kompressionsartefakten, übermäßig großen Watermarks, Mobil-Statusleiste oder unsinnig beschnittene/skalierte Bilder werden gelöscht.</li>
        <li>Keine Bilder/Videos mit ähnlichem Inhalt kurz hintereinander posten. Zugehöriger Content kann in den Kommentaren verlinkt werden.</li>
        <li>
            Kommentare wie <em>“Tag deinen Scheiß”</em> gehören nicht in die Tags. Mehr im FAQ:
            <a href="#faq:tags">Was gehört in die Tags?</a>
        </li>
        <li>Kein Downvote-Spam, Vote-Manipulation oder Tag-Vandalismus.</li>
        <li>Pro Benutzer ist nur ein Account erlaubt, das Teilen eines Accounts mit mehreren Personen ist verboten. Indizien für Multiaccounts sind gegenseitige Upvotes oder Spamaktionen.</li>
        <li>Keine Warez, Links zu illegalen Angeboten, gestohlene Logins zu Pay Sites o.Ä.</li>
        <li>Keine übermäßigen Beleidigungen anderer Benutzer, insbesondere Moderatoren.</li>
        <li>Keine “Screamer” oder sonstige Ton-Videos mit der Absicht Benutzer zu erschrecken.</li>
        <li>Keine reinen Musikuploads (außer bei OC). Das gilt auch für Musikvideos, wir sind hier nicht bei Youtube.</li>
        <li>Kein unnötiges Markieren von Moderatoren oder Nerven von Community-Helfern. Wenn Du Hilfe benötigst oder petzen willst, benutze das <a href="#contact">Kontaktformular</a>.</li>
        <li>Kein Missbrauch der Melden Funktion</li>
        <li>Politisch, rassistisch oder religiös motivierte Hetze, Aufruf zu Gewalt und Mord, werden nicht toleriert.</li>
    """