package com.pr0gramm.app.ui

import androidx.core.app.ActivityCompat

/**
 */
interface PermissionHelperActivity : ActivityCompat.OnRequestPermissionsResultCallback {
    fun requirePermission(permission: String, callback: () -> Unit)
}
