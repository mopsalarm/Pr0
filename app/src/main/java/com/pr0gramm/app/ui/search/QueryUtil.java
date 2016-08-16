package com.pr0gramm.app.ui.search;

/**
 */
public class QueryUtil {
    public static String escapeQuery(String query) {
        if (isSimpleQuery(query)) {
            return query;
        } else {
            return "(" + query + ")";
        }
    }

    private static boolean isSimpleQuery(String query) {
        return query.matches("[A-Za-z0-9:]+");
    }
}
