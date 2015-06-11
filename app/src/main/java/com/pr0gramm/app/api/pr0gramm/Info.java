package com.pr0gramm.app.api.pr0gramm;

import com.google.common.collect.ImmutableList;
import com.pr0gramm.app.R;

import org.joda.time.Instant;

import java.util.List;

/**
 */
public class Info {
    private User user;
    private int likeCount;
    private int uploadCount;
    private int commentCount;
    private int tagCount;

    public User getUser() {
        return user;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getUploadCount() {
        return uploadCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getTagCount() {
        return tagCount;
    }

    public static class User {
        private int id;
        private int mark;
        private int score;
        private String name;
        private Instant registered;

        public int getId() {
            return id;
        }

        public int getMark() {
            return mark;
        }

        public int getScore() {
            return score;
        }

        public String getName() {
            return name;
        }

        public Instant getRegistered() {
            return registered;
        }
    }

    public static final List<Integer> MarkDrawables = ImmutableList.of(
            R.drawable.user_type_schwuchtel_small,
            R.drawable.user_type_neuschwuchtel_small,
            R.drawable.user_type_altschwuchtel_small,
            R.drawable.user_type_admin_small,
            R.drawable.user_type_gesperrt_small,
            R.drawable.user_type_moderator_small,
            R.drawable.user_type_fliesentisch_small,
            R.drawable.user_type_legende_small,
            R.drawable.user_type_wichtler_small,
            R.drawable.user_type_pr0mium_small);

    public static final List<Integer> MarkStrings = ImmutableList.of(
            R.string.user_type_schwuchtel,
            R.string.user_type_neuschwuchtel,
            R.string.user_type_altschwuchtel,
            R.string.user_type_admin,
            R.string.user_type_gesperrt,
            R.string.user_type_moderator,
            R.string.user_type_fliesentisch,
            R.string.user_type_legende,
            R.string.user_type_wichtler,
            R.string.user_type_pr0mium);

    public static final List<Integer> MarkColors = ImmutableList.of(
            R.color.user_type_schwuchtel,
            R.color.user_type_neuschwuchtel,
            R.color.user_type_altschwuchtel,
            R.color.user_type_admin,
            R.color.user_type_gesperrt,
            R.color.user_type_moderator,
            R.color.user_type_fliesentisch,
            R.color.user_type_legende,
            R.color.user_type_wichtler,
            R.color.user_type_pr0mium);
}
