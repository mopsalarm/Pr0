package com.pr0gramm.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;

/**
 */
public class TagInputView {
    private static final Logger logger = LoggerFactory.getLogger(TagInputView.class);

    private TagInputView() {
    }

    /**
     * Parses the list of tags. It is provided as one tag per line in utf8 encoding.
     */
    @SuppressLint("NewApi")
    private static List<String> loadTagJson(Context context) {
        try (InputStream stream = context.getAssets().open("tags.txt")) {
            try (Reader reader = new InputStreamReader(stream, Charsets.UTF_8)) {
                ImmutableList.Builder<String> result = ImmutableList.builder();
                for (String line : CharStreams.readLines(reader)) {
                    line = line.trim();
                    if (line.length() > 1) {
                        result.add(line.trim());
                    }
                }

                return result.build();
            }
        } catch (Exception error) {
            logger.error("Could not load list of tags", error);
            return Collections.emptyList();
        }
    }

    public static void setup(MultiAutoCompleteTextView tagInput) {
        if (TAGS == null) {
            // load the tags from the compressed json file
            TAGS = loadTagJson(tagInput.getContext());
        }

        // get the auto-suggestion list.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(tagInput.getContext(),
                android.R.layout.simple_dropdown_item_1line,
                TAGS);

        tagInput.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
        tagInput.setAdapter(adapter);
    }

    // holds the list of tags
    private static List<String> TAGS;
}
