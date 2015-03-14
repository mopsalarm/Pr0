package com.pr0gramm.app.api.pr0gramm.response;

import org.joda.time.Instant;

/**
 */
public class Login {
    private boolean success;
    private BanInfo ban;

    Login() {
    }

    public Login(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public BanInfo getBan() {
        return ban;
    }

    public static class BanInfo {
        private boolean banned;
        private Instant till;
        private String reason;

        public boolean isBanned() {
            return banned;
        }

        public Instant getTill() {
            return till;
        }

        public String getReason() {
            return reason;
        }
    }
}
