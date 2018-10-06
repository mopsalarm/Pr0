package com.pr0gramm.app

import android.os.StrictMode
import androidx.test.espresso.IdlingRegistry
import com.jakewharton.espresso.OkHttp3IdlingResource
import okhttp3.mockwebserver.MockWebServer
import org.kodein.di.Kodein
import org.kodein.di.direct
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance

class TestApplicationClass : ApplicationClass() {
    init {
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
    }

    val mockServer = MockWebServer().also { mock ->
        mock.setDispatcher(GenericDispatcher())
        mock.start()
    }

    override fun onCreate() {
        super.onCreate()

        IdlingRegistry.getInstance().register(
                OkHttp3IdlingResource.create("okhttp", direct.instance()))
    }

    override fun configureKodein(builder: Kodein.MainBuilder) {
        super.configureKodein(builder)

        builder.apply {
            bind<String>(TagApiURL, overrides = true) with instance(mockServer.url("/").toString())
        }
    }
}