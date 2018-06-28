package com.pr0gramm.app.services

import android.widget.TextView
import com.pr0gramm.app.R
import com.pr0gramm.app.util.*
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import okhttp3.OkHttpClient
import okhttp3.Request
import rx.Observable
import java.util.regex.Pattern

/**
 */
class RulesService(okHttpClient: OkHttpClient) {
    private val DEFAULT_RULES_TEXT = "<li>nsfw/nsfl Bilder/Videos müssen vor dem Upload entsprechend markiert werden! Sinnlose oder übertriebene Nutzung des NSFP wird ebenso ungern gesehen.</li> <li>Keine suggestiven Bilder/Videos oder Gore von/mit Minderjährigen/Babys/Föten.</li> <li>Keine Tierpornos.</li> <li>Kein stumpfer Rassismus, kein rechtes Gedankengut, keine Nazi-Nostalgie.</li> <li>Keine Werbung; keine Affiliate Links in den Bildern; kein Spam.</li> <li>Keine Informationen oder Bilder/Videos von Privatpersonen; Keine Klarnamen in den Uploads, Tags oder Kommentaren.</li> <li>Ein Mindestmaß an Bildqualität wird erwartet. Bildmaterial mit starken Kompressionsartefakten, übermäßig großen Watermarks oder unsinnig beschnittene/skalierte Bilder werden gelöscht.</li> <li>Keine Bilder/Videos mit ähnlichem Inhalt in Reihe in der neu Ansicht. Die Bilder/Videos können über mehrere Stunden verteilt gepostet oder der zugehöriger Content kann in den Kommentaren verlinkt werden.</li> <li> Kommentare wie <em>“Tag deinen Scheiß”</em> und ähnliches gehören nicht in die Tags. </li> <li>Downvote-Spam, Vote-Manipulation und Tag-Vandalismus werden nicht geduldet.</li> <li>Pro Benutzer ist nur ein Account erlaubt. Indizien für Multiaccounts sind gegenseitige Upvotes oder Spamaktionen.</li> <li>Keine Warez, gestohlene Logins zu Pay Sites o.ä</li> <li>Überzogene oder häufige Beleidigungen anderen Benutzern und insbesondere gegenüber Moderatoren wird mit einer Sperrung bestraft.</li> <li>Keine “Screamer” oder sonstige Videos mit der Absicht, Benutzer zu erschrecken.</li> <li>Keine reinen Musikuploads (außer bei OC).</li> <li>Moderatoren sinnlos/unnötig/aus Spass in Kommentaren markieren wird eine Sperrung zur Folge haben. Wenn du Hilfe benötigst, wende dich an das Kontaktformular. Dort wird dir weitaus schneller geholfen. Solltest du etwas direkt von einem Moderator wollen, dann schreibe ihm eine Private Nachricht.</li>"

    private val rules: Observable<String> = Observable
                .fromCallable {
                    val url = "https://pr0gramm.com/media/templates/rules.html"
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) response.body()?.string() else null
                }

                // do this in background
                .subscribeOn(BackgroundScheduler.instance())

                // but skip errors
            .filter { responseText -> !responseText.isNullOrBlank() }
                .onErrorResumeEmpty()
            .ofType<String>()

                // start with the default we know about
                .startWith(DEFAULT_RULES_TEXT)

                // Cache the last one
                .replay(1).autoConnect(0)

    fun displayInto(targetView: TextView) {
        this.rules
                .observeOnMainThread()
                .bindToLifecycle(targetView)
                .subscribe { text -> displayInto(targetView, text) }
    }

    private fun extractRulesFromJavascript(response: String): Observable<String> {
        val matcher = Pattern.compile("Rules=[^']+'([^']+)").matcher(response)
        return if (matcher.find()) Observable.just(matcher.group(1)) else Observable.empty<String>()
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
