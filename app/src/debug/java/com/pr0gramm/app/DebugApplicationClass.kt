package com.pr0gramm.app

import android.content.Context
import android.os.Debug
import android.os.StrictMode
import androidx.multidex.MultiDex
import com.pr0gramm.app.util.doInBackground


class DebugApplicationClass : ApplicationClass() {
    init {
        StrictMode.enableDefaults()

        if (false) {
            try {
                // Debug.startMethodTracing(null, 128 * 1024 * 1024)
                Debug.startMethodTracingSampling(null, 16 * 1024 * 1024, 500)

                doInBackground {
                    kotlinx.coroutines.delay(6_000)
                    Debug.stopMethodTracing()
                }
            } catch (err: Throwable) {
                Logger("DebugApplicationClass").error(err) {
                    "Could not start method sampling during bootup."
                }
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
