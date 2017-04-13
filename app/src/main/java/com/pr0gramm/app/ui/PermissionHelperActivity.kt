package com.pr0gramm.app.ui

import android.support.v4.app.ActivityCompat

import rx.Observable

/**
 */
interface PermissionHelperActivity : ActivityCompat.OnRequestPermissionsResultCallback {
    fun requirePermission(permission: String): Observable<Void>
}
