package com.pr0gramm.app.ui;

import android.support.v4.app.ActivityCompat;

import rx.Observable;

/**
 */
public interface PermissionHelperActivity extends ActivityCompat.OnRequestPermissionsResultCallback {
    Observable<Void> requirePermission(String permission);
}
