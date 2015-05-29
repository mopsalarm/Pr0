package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;

import com.google.common.base.Charsets;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.pr0gramm.app.Lazy;
import com.pr0gramm.app.Pr0grammApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 */
public class TagInputView {
    private static final Logger logger = LoggerFactory.getLogger(TagInputView.class);

    private TagInputView() {
    }

    private static final Type listOfStringsType = new TypeToken<List<String>>() {
    }.getType();

    /**
     * Parses the list of tags.
     */
    @SuppressLint("NewApi")
    private static final Lazy<List<String>> TAGS = Lazy.of(() -> {
        try (InputStream stream = Pr0grammApplication.GLOBAL_CONTEXT.getAssets().open("tags.json")) {
            try (Reader reader = new InputStreamReader(stream, Charsets.UTF_8)) {
                List<String> tagList = new Gson().fromJson(reader, listOfStringsType);

                // group by lower-case version of each tag
                ListMultimap<String, String> byLowerCase = FluentIterable
                        .from(tagList)
                        .index(String::toLowerCase);

                // and remove duplicates by getting the first occurrence of each
                // groups values
                return FluentIterable.from(byLowerCase.asMap().entrySet())
                        .transform(e -> Iterables.getFirst(e.getValue(), null))
                        .filter(Predicates.notNull())
                        .toList();
            }

        } catch (Exception error) {
            logger.error("Could not load list of tags", error);
            return Collections.emptyList();
        }
    });

    public static void setup(MultiAutoCompleteTextView tagInput) {
        // get the auto-suggestion list.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(tagInput.getContext(),
                android.R.layout.simple_dropdown_item_1line,
                TAGS.get());

        tagInput.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        tagInput.setAdapter(adapter);
    }
}
