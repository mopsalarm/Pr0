package com.pr0gramm.app.services;

import android.content.SharedPreferences;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Helps with recent searches
 */
@Singleton
public class RecentSearchesServices {
    private static final String KEY = "RecentSearchesServices.terms";
    private static final Logger logger = LoggerFactory.getLogger("RecentSearchesServices");

    private final List<String> searches = new ArrayList<>();
    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    @Inject
    public RecentSearchesServices(SharedPreferences sharedPreferences, Gson gson) {
        this.sharedPreferences = sharedPreferences;
        this.gson = gson;

        restoreState();
    }

    public void storeTerm(String term) {
        synchronized (searches) {
            removeCaseInsensitive(term);
            searches.add(0, term);

            persistStateAsync();
        }
    }

    public ImmutableList<String> searches() {
        synchronized (searches) {
            return ImmutableList.copyOf(searches);
        }
    }

    public void clearHistory() {
        synchronized (searches) {
            searches.clear();
            persistStateAsync();
        }
    }

    /**
     * Removes all occurrences of the given term, independend of case.
     */
    private void removeCaseInsensitive(String term) {
        ListIterator<String> iter = searches.listIterator();
        while (iter.hasNext()) {
            if (iter.next().equalsIgnoreCase(term)) {
                iter.remove();
            }
        }
    }

    private void persistStateAsync() {
        try {
            // write down
            sharedPreferences.edit()
                    .putString(KEY, gson.toJson(searches, LIST_OF_STRINGS.getType()))
                    .apply();
        } catch (Exception ignored) {
            logger.warn("Could not presist recent searches");
        }
    }

    private void restoreState() {
        try {
            String serialized = sharedPreferences.getString(KEY, "[]");
            searches.addAll(gson.fromJson(serialized, LIST_OF_STRINGS.getType()));

        } catch (Exception error) {
            logger.warn("Could not deserialize recent searches", error);
        }
    }

    private static final TypeToken<List<String>> LIST_OF_STRINGS = new TypeToken<List<String>>() {
    };
}
