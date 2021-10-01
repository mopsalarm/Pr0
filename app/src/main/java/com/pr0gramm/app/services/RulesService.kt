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
                displayInto(targetView, text)
            }
        }
    }

    private fun displayInto(rulesView: TextView, rules: String) {
        val list = "<pre>(.+?)</pre>".toRegex(RegexOption.DOT_MATCHES_ALL).findAll(rules).mapIndexed { _, match ->
            var id = "";
            var rule = match.groupValues[1].replace("<b>(.+?)</b>".toRegex(RegexOption.IGNORE_CASE)) { id = it.groupValues[1]; ""}
            rule = rule.replace("<[^>]+>".toRegex(), "").trim { it <= ' ' }

            "#$id  $rule"
        }

        val resources = rulesView.context.resources
        val offset = resources.getDimensionPixelSize(R.dimen.bullet_list_leading_margin)
        rulesView.text = AndroidUtility.makeBulletList(offset, list.toList())
    }
}

private const val defaultRulesText = """
        <pre><b>1</b> nsfp/nsfw/nsfl Inhalte müssen vor dem Upload entsprechend markiert werden.</pre>
		<pre><b>2</b> <i>Minderjährige:</i></pre>
		<pre><b>2.1</b> Keine suggestive oder nackte Darstellung von Minderjährigen.</pre>
		<pre><b>2.2</b> Keine rohe Gewalt an Minderjährigen.</pre>
		<pre><b>3</b> <i>Tiere:</i></pre>
		<pre><b>3.1</b> Keine Tierquälerei.</pre>
		<pre><b>3.2</b> Keine Zoophilie oder Fetischvideos mit Tieren.</pre>
		<pre><b>4</b> <i>Rassismus und Hetze:</i></pre>
		<pre><b>4.1</b> Kein stumpfer Rassismus, kein rechtes Gedankengut.</pre>
		<pre><b>4.2</b> Keine Hetze, egal ob politisch, rassistisch oder religiös motiviert.</pre>
		<pre><b>4.3</b> Keine Aufrufe zu Gewalt.</pre>
		<pre><b>4.4</b> Keine Nazi-Nostalgie/Nazi-Nostalgia</pre>
		<pre><b>4.5</b> Kein Agenda Pushing oder Verbreiten von Propaganda.</pre>
		<pre><b>5</b> Keine Werbung</pre>
		<pre><b>6</b> Keine Informationen oder Bilder/Videos von Privatpersonen.</pre>
		<pre><b>7</b> <i>Contentqualität:</i></pre>
		<pre><b>7.1</b> Ein Mindestmaß an Bild/Videoqualität wird erwartet.</pre>
		<pre><b>7.2</b> Reposts gilt es zu vermeiden.</pre>
		<pre><b>7.3</b> Keine Müllposts/Privatmüll.</pre>
		<pre><b>7.4</b> Keine langweiligen Raids.</pre>
		<pre><b>8</b> Keine Bilder/Videos mit ähnlichem Inhalt kurz hintereinander posten.</pre>
		<pre><b>9</b> <i>Vandalismus:</i></pre>
		<pre><b>9.1</b> Kein Tag-Vandalismus.</pre>
		<pre><b>9.2</b> Kein Spam/Kommentarvandalismus.</pre>
		<pre><b>9.3</b> Kein Downvote-Spam.</pre>
		<pre><b>9.4</b> Keine Vote-Manipulation.</pre>
		<pre><b>9.5</b> Kein Downvoten sinnvoller Tags.</pre>
		<pre><b>10</b> Keine frühen Spoiler.</pre>
		<pre><b>11</b> Pro Benutzer ist nur ein Account erlaubt.</pre>
		<pre><b>12</b> Keine Warez, Links zu illegalen Angeboten, gestohlene Logins zu Pay Sites o.Ä.</pre>
		<pre><b>13</b> Keine übermäßigen Beleidigungen oder Hetzen gegen andere Benutzer, die Community oder die Moderation.</pre>
		<pre><b>14</b> Keine “Screamer” oder sonstige Ton-Videos mit der Absicht, Benutzer zu erschrecken oder zu trollen.</pre>
		<pre><b>15</b> <i>Musikuploads:</i></pre>
		<pre><b>15.1</b> Keine reinen Musikuploads.</pre>
		<pre><b>15.2</b> Keine Musikvideos.</pre>
		<pre><b>16</b> <i>Störungen der Moderation:</i></pre>
		<pre><b>16.1</b> Kein unnötiges Markieren von Moderatoren oder Nerven von Community-Helfern.</pre>
		<pre><b>16.2</b> Kein Missbrauch der Melden-Funktion.</pre>
    """