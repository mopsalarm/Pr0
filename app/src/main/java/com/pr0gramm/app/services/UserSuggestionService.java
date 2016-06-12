package com.pr0gramm.app.services;

import android.support.annotation.NonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.pr0gramm.app.api.pr0gramm.Api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Response;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

/**
 */
@Singleton
public class UserSuggestionService {
    private static final Logger logger = LoggerFactory.getLogger("UserSuggestionService");

    private final LoadingCache<String, List<String>> suggestionCache;
    private final Api api;

    @Inject
    public UserSuggestionService(Api api) {
        this.api = api;

        suggestionCache = CacheBuilder.<String, List<String>>newBuilder()
                .maximumSize(100)
                .build(CacheLoader.from(this::internalSuggestUsers));
    }

    public List<String> suggestUsers(@NonNull String prefix) {
        return suggestionCache.getUnchecked(prefix.toLowerCase());
    }

    @NonNull
    private List<String> internalSuggestUsers(@NonNull String prefix) {
        if (prefix.length() <= 1)
            return emptyList();

        try {
            Response<Api.Names> reponse = api.suggestUsers(prefix).execute();
            if (!reponse.isSuccessful())
                return emptyList();

            return firstNonNull(reponse.body().users(), emptyList());
        } catch (Exception error) {
            logger.warn("Could not fetch username suggestions for prefix={}: {}", prefix, error);
            return emptyList();
        }
    }
}
