package com.pr0gramm.app.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import rx.Observable
import rx.subjects.ReplaySubject

/**
 */
class PermissionHelper(private val activity: Activity) : ActivityCompat.OnRequestPermissionsResultCallback {
    private val requests = androidx.collection.ArrayMap<Int, ReplaySubject<Void>>()

    fun requirePermission(permission: String): Observable<Void> {
        val subject = ReplaySubject.create<Void>()

        val result = ContextCompat.checkSelfPermission(activity, permission)
        if (result == PackageManager.PERMISSION_GRANTED) {
            subject.onNext(null)
            subject.onCompleted()

        } else {
            val requestCode = ++PermissionHelper.nextId

            // remember for later
            requests.put(requestCode, subject)

            // request permission now!
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        }

        return subject
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {

        val subject = requests.remove(requestCode) ?: return

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            subject.onNext(null)
            subject.onCompleted()

        } else if (permissions.isNotEmpty()) {
            subject.onError(PermissionNotGranted(permissions[0]))
        }
    }

    class PermissionNotGranted(val permission: String) : RuntimeException()

    companion object {
        private var nextId = 0
    }
}
