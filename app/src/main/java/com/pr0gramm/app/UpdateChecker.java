package com.pr0gramm.app;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.base.Throwables;

import retrofit.RestAdapter;
import retrofit.http.GET;
import rx.Observable;
import rx.util.async.Async;

/**
 */
public class UpdateChecker {
    private final Context context;

    public UpdateChecker(Context context) {
        this.context = context;
    }

    private int getVersionCode() {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;

        } catch (PackageManager.NameNotFoundException err) {
            throw Throwables.propagate(err);
        }
    }

    public Observable<Update> check() {
        return Async.start(() -> {
            UpdateApi api = new RestAdapter.Builder()
                    .setEndpoint("https://github.com/Graphicscore/Pr0/raw/master/update.json")
                    .setLogLevel(RestAdapter.LogLevel.BASIC)
                    .build()
                    .create(UpdateApi.class);

            return api.get();
        }).filter(update -> update.getVersion() > getVersionCode());
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

    public static class UpdateDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Update update = getArguments().getParcelable("update");
            return new MaterialDialog.Builder(getActivity())
                    .content(getString(R.string.new_update_available, update.getChangelog()))
                    .positiveText(R.string.download)
                    .negativeText(R.string.ignore)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            download(update);
                        }
                    })
                    .build();
        }

        private void download(Update update) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(update.getApk()));
            startActivity(intent);
        }

        public static UpdateDialogFragment newInstance(Update update) {
            Bundle bundle = new Bundle();
            bundle.putParcelable("update", update);

            UpdateDialogFragment dialog = new UpdateDialogFragment();
            dialog.setArguments(bundle);
            return dialog;
        }
    }

}

