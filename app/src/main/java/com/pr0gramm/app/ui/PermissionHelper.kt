package com.pr0gramm.app.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 */
class PermissionHelper(private val activity: Activity) : ActivityCompat.OnRequestPermissionsResultCallback {
    private val requests = ArrayMap<Int, () -> Unit>()

    fun requirePermission(permission: String, callback: () -> Unit) {
        val result = ContextCompat.checkSelfPermission(activity, permission)
        if (result == PackageManager.PERMISSION_GRANTED) {
            callback()

        } else {
            val requestCode = ++PermissionHelper.nextId

            // remember for later
            requests[requestCode] = callback

            // request permission now!
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {

        val callback = requests.remove(requestCode) ?: return

        // permission request was interrupted, basically a cancellation
        if (permissions.isEmpty() && grantResults.isEmpty()) {
            return
        }

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            callback()

        } else if (permissions.isNotEmpty()) {
            // permission not granted. do nothing
        }
    }

    companion object {
        private var nextId = 0
    }
}
