package com.pr0gramm.app.api;

import org.joda.time.Instant;

/**
 */
public class LoginResponse {
    private boolean success;
    private BanInfo ban;

    LoginResponse() {
    }

    public LoginResponse(boolean success) {
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
