package com.pr0gramm.app;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import retrofit.RestAdapter;
import retrofit.http.GET;
import rx.Observable;
import rx.util.async.Async;

import static java.lang.String.format;

/**
 * Class to perform an update check.
 */
public class UpdateChecker {
    private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);

    private final int currentVersion;
    private final String endpoint;

    public UpdateChecker(Context context) {
        this.currentVersion = Pr0grammApplication.getPackageInfo(context).versionCode;

        boolean betaChannel = Settings.of(context).useBetaChannel();
        endpoint = getEndpointUrl(betaChannel);
    }

    public Observable<Update> check() {
        return Async.start(() -> {
            UpdateApi api = newRestAdapter(endpoint).create(UpdateApi.class);
            return api.get();

        }).filter(update -> {
            logger.info(format("Installed v%d, current v%d", currentVersion, update.getVersion()));

            // filter out if up to date
            return update.getVersion() > currentVersion;
        }).map(update -> {
            // rewrite url to make it absolute
            String apk = update.getApk();
            if (!apk.startsWith("http")) {
                apk = Uri.withAppendedPath(Uri.parse(endpoint), apk).toString();
            }

            logger.info("Got new update at url " + apk);
            return new Update(update.version, apk, update.changelog);
        });
    }

    public static class Update implements Parcelable {
        private int version;
        private String apk;
        private String changelog;

        public int getVersion() {
            return version;
        }

        public Update() {
        }

        public Update(int version, String apk, String changelog) {
            this.version = version;
            this.apk = apk;
            this.changelog = changelog;
        }

        public String getApk() {
            return apk;
        }

        public String getChangelog() {
            return changelog;
        }


        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.version);
            dest.writeString(this.apk);
            dest.writeString(this.changelog);
        }

        private Update(Parcel in) {
            this.version = in.readInt();
            this.apk = in.readString();
            this.changelog = in.readString();
        }

        public static final Parcelable.Creator<Update> CREATOR = new Parcelable.Creator<Update>() {
            public Update createFromParcel(Parcel source) {
                return new Update(source);
            }

            public Update[] newArray(int size) {
                return new Update[size];
            }
        };
    }

    private static RestAdapter newRestAdapter(String endpoint) {
        return new RestAdapter.Builder()
                .setEndpoint(endpoint)
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .build();
    }

    private static interface UpdateApi {
        @GET("/update.json")
        Update get();
    }

    /**
     * Returns the Endpoint-URL that is to be queried
     */
    private static String getEndpointUrl(boolean betaChannel) {
        String endpoint;
        if (betaChannel) {
            endpoint = "https://github.com/mopsalarm/pr0gramm-updates/raw/beta";
        } else {
            endpoint = "https://github.com/mopsalarm/pr0gramm-updates/raw/master";
        }
        return endpoint;
    }
}

