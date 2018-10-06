package com.pr0gramm.app.ui

import androidx.core.app.ActivityCompat

import rx.Observable

/**
 */
interface PermissionHelperActivity : ActivityCompat.OnRequestPermissionsResultCallback {
    fun requirePermission(permission: String): Observable<Void>
}
