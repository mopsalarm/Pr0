package com.pr0gramm.app

import android.content.Context
import android.os.Debug
import android.os.StrictMode
import androidx.multidex.MultiDex
import com.gu.toolargetool.TooLargeTool
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.delay


class DebugApplicationClass : ApplicationClass() {
    init {
        StrictMode.enableDefaults()

        if (false) {
            try {
                // Debug.startMethodTracing(null, 128 * 1024 * 1024)
                Debug.startMethodTracingSampling(null, 16 * 1024 * 1042, 500)

                doInBackground {
                    delay(6000)
                    Debug.stopMethodTracing()
                }
            } catch (err: Throwable) {
                Logger("DebugApplicationClass").error(err) {
                    "Could not start method sampling during bootup."
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        TooLargeTool.startLogging(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
