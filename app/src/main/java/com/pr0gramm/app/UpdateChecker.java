package com.pr0gramm.app;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import retrofit.RestAdapter;
import retrofit.http.GET;
import rx.Observable;
import rx.util.async.Async;

import static java.lang.String.format;

/**
 * Class to perform an update check.
 */
public class UpdateChecker {
    private final int currentVersion;

    public UpdateChecker(Context context) {
        this.currentVersion = Pr0grammApplication.getPackageInfo(context).versionCode;
    }

    public Observable<Update> check() {
        return Async.start(() -> {
            UpdateApi api = newRestAdapter().create(UpdateApi.class);
            return api.get();

        }).filter(update -> {
            Log.i("Update", format("Installed v%d, current v%d", currentVersion, update.getVersion()));

            // filter out if up to date
            return update.getVersion() > currentVersion;
        });
    }

    private RestAdapter newRestAdapter() {
        return new RestAdapter.Builder()
                .setEndpoint("https://raw.githubusercontent.com/mopsalarm/pr0gramm-updates/master")
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .build();
    }

    private static interface UpdateApi {
        @GET("/update.json")
        Update get();
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
}

