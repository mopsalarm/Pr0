package com.pr0gramm.app.services;

import android.content.res.Resources;
import android.widget.TextView;

import com.google.common.base.Strings;
import com.pr0gramm.app.R;
import com.pr0gramm.app.util.AndroidUtility;
import com.pr0gramm.app.util.BackgroundScheduler;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.trello.rxlifecycle.RxLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.util.async.Async;

/**
 */
@Singleton
public class RulesService {
    private static final String DEFAULT_RULES_TEXT = "<li>nsfw/nsfl Bilder müssen vor dem Upload entsprechend markiert werden!</li> <li>Keine suggestiven Bilder oder Gore von/mit Minderjährigen/Babys/Föten.</li> <li>Keine Tierpornos.</li> <li>Kein stumpfer Rassismus, kein rechtes Gedankengut, keine Nazi-Nostalgie.</li> <li>Keine Werbung; keine Affiliate Links in den Bildern; kein Spam.</li> <li>Keine Informationen oder Bilder von Privatpersonen; Keine Klarnamen in den Uploads, Tags oder Kommentaren.</li> <li>Ein Mindestmaß an Bildqualität wird erwartet. Bildmaterial mit starken Kompressionsartefakten, übermäßig großen Watermarks oder unsinnig beschnittene/skalierte Bilder werden gelöscht.</li> <li>Keine Bilder mit ähnlichem Inhalt in Reihe. Zugehöriger Content kann in den Kommentaren verlinkt werden.</li> <li> Kommentare wie <em>“Tag deinen Scheiß”</em> und ähnliches gehören nicht in die Tags. Genaueres im FAQ: <a href=\"#faq:tags\">Was gehört in die Tags?</a> </li> <li>Downvote-Spam, Vote-Manipulation und Tag-Vandalismus werden nicht geduldet.</li> <li>Pro Benutzer ist nur ein Account erlaubt. Indizien für Multiaccounts sind gegenseitige Upvotes oder Spamaktionen.</li> <li>Keine Warez, gestohlene Logins zu Pay Sites o.ä.</li> <li>Überzogene oder häufige Beleidigungen anderen Benutzern und insbesondere gegenüber Moderatoren wird mit einer Sperrung bestraft.</li>";

    private final Observable<String> rules;

    @Inject
    public RulesService(OkHttpClient okHttpClient) {
        this.rules = Async.fromCallable(() -> {
                    long cacheSlayer = System.currentTimeMillis() / (24 * 3600);
                    String url = "https://pr0gramm.com/media/pr0gramm.min.js?app" + cacheSlayer;
                    Request request = new Request.Builder().url(url).build();
                    Response response = okHttpClient.newCall(request).execute();
                    return response.isSuccessful() ? response.body().string() : null;
                },

                /* run on background scheduler */
                BackgroundScheduler.instance())

                /* skip errors */
                .filter(responseText -> !Strings.isNullOrEmpty(responseText))
                .onErrorResumeNext(Observable.empty())

                /* extract from javascript */
                .flatMap(this::extractRulesFromJavascript)

                /* start with the default */
                .startWith(DEFAULT_RULES_TEXT)

                /* Cache the last one */
                .replay(1).autoConnect();
    }

    public void displayInto(TextView targetView) {
        this.rules
                .subscribeOn(BackgroundScheduler.instance())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(RxLifecycle.bindView(targetView))

                /* display the result */
                .subscribe(text -> displayInto(targetView, text));
    }

    private Observable<? extends String> extractRulesFromJavascript(String response) {
        Matcher matcher = Pattern.compile("Rules=[^']+'([^']+)").matcher(response);
        return matcher.find() ? Observable.just(matcher.group(1)) : Observable.empty();
    }

    private void displayInto(TextView rulesView, String rules) {
        int idx = 0;
        List<String> list = new ArrayList<>();
        Matcher matcher = Pattern.compile("<li>(.+?)</li>").matcher(rules);
        while (matcher.find()) {
            String rule = matcher.group(1).replaceAll("<[^>]+>", "").trim();
            list.add((idx + 1) + ". " + rule);
        }

        Resources resources = rulesView.getContext().getResources();
        int offset = resources.getDimensionPixelSize(R.dimen.bullet_list_leading_margin);
        rulesView.setText(AndroidUtility.makeBulletList(offset, list));
    }
}
