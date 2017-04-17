package com.pr0gramm.app

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.android.autoAndroidModule
import com.github.salomonbrys.kodein.lazy

internal class Modules(private val app: ApplicationClass) : KodeinAware {
    override val kodein: Kodein by Kodein.lazy {
        import(autoAndroidModule(app))
        import(appModule(app), allowOverride = true)
        import(httpModule(app))
        import(trackingModule(app))
        import(servicesModule(app))
    }
}
