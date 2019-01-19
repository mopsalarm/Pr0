package com.pr0gramm.app.util

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.security.ProviderInstaller
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

//Setting testMode configuration. If set as testMode, the connection will skip certification check
fun OkHttpClient.Builder.configureSSLSocketFactoryAndSecurity(app: Application): OkHttpClient.Builder {
    val logger = Logger("OkHttpSSL")

    val availability = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(app, 11925000)
    when (availability) {
        ConnectionResult.SUCCESS -> {
            logger.info { "Found google services, installing SSL security provider" }

            try {
                logger.time("Trying to install security provider") {
                    ProviderInstaller.installIfNeeded(app)
                }

                logger.info { "SSL security provider installed" }
                return this

            } catch (err: Throwable) {
                logger.warn(err) { "Could not install SSL security provider" }
            }
        }

        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
            logger.warn { "Google services are too old." }
        }

    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // no known problems on lollipop with ssl, just do nothing here.
        return this
    }

    // Okay fuck, we don't have the google services available, and ssl doesn't work on older
    // android versions. What do we do now? We allow everything... well, fuck it!
    logger.warn { "Disable validity checking of SSL certificates" }

    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }
    })

    // Install the all-trusting trust manager
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, SecureRandom())

    // Create an ssl socket factory with our all-trusting manager
    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)

    // also don't do host name verification either
    // hostnameVerifier { hostname, session -> true }

    return this
}


