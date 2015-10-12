package com.pr0gramm.app.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;

import java.util.Map;

import javax.inject.Inject;

import rx.Observable;
import rx.subjects.ReplaySubject;

/**
 */
public class PermissionHelper implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static int nextId = 0;
    private final Map<Integer, ReplaySubject<Void>> requests = new ArrayMap<>();

    private final Activity activity;

    @Inject
    public PermissionHelper(Activity activity) {
        this.activity = activity;
    }

    public Observable<Void> requirePermission(String permission) {
        ReplaySubject<Void> subject = ReplaySubject.create();

        int result = ContextCompat.checkSelfPermission(activity, permission);
        if (result == PackageManager.PERMISSION_GRANTED) {
            emitPermissionGranted(subject);
        } else {
            int requestCode = ++PermissionHelper.nextId;

            // remember for later
            requests.put(requestCode, subject);

            // request permission now!
            ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
        }

        return subject;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        ReplaySubject<Void> subject = requests.remove(requestCode);
        if (subject == null)
            return;

        if (Iterables.all(Ints.asList(grantResults), val -> val == PackageManager.PERMISSION_GRANTED)) {
            emitPermissionGranted(subject);
        } else if (permissions.length >= 1) {
            emitPermissionNotGranted(subject, permissions[0]);
        }
    }

    private void emitPermissionNotGranted(ReplaySubject<Void> subject, String permission) {
        subject.onError(new PermissionNotGranted(permission));
    }

    private void emitPermissionGranted(ReplaySubject<Void> subject) {
        subject.onNext(null);
        subject.onCompleted();
    }

    public static class PermissionNotGranted extends RuntimeException {
        private final String permission;

        public PermissionNotGranted(String permission) {
            this.permission = permission;
        }

        public String getPermission() {
            return permission;
        }
    }
}
