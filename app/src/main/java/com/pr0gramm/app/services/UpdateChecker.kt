package com.pr0gramm.app.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.support.v4.app.FragmentActivity
import android.support.v4.content.FileProvider
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.fragments.DownloadUpdateDialog
import com.pr0gramm.app.util.*
import org.kodein.di.erased.instance
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.functions.Action1
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * Class to perform an update check.
 */
class UpdateChecker {
    private val currentVersion = AndroidUtility.buildVersionCode()
    private val endpoints = updateUrls(Settings.get().useBetaChannel)
    private val converterFactory = MoshiConverterFactory.create(MoshiInstance)

    private fun check(endpoint: String): Observable<Update> {
        return newRestAdapter(endpoint)
                .create(UpdateApi::class.java)
                .fetchUpdate()
                .filter { update ->
                    logger.info("Installed v{}, found update v{} at {}",
                            currentVersion, update.version, endpoint)

                    // filter out if up to date
                    update.version > currentVersion
                }
                .map { update ->
                    // rewrite url to make it absolute
                    var apk = update.apk
                    if (!apk.startsWith("http")) {
                        apk = Uri.withAppendedPath(Uri.parse(endpoint), apk).toString()
                    }

                    logger.info("Got new update at url " + apk)
                    update.copy(apk = apk)
                }
    }

    fun check(): Observable<Update> {
        val updates = Observable.from(endpoints).flatMap { endpoint ->
            check(endpoint)
                    .doOnError { err -> logger.warn("Could not check for update at {}: {}", endpoint, err.toString()) }
                    .onErrorResumeEmpty()
        }

        return updates.take(1)
    }

    private fun newRestAdapter(endpoint: String): Retrofit {
        return Retrofit.Builder()
                .baseUrl(endpoint)
                .addConverterFactory(converterFactory)
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(BackgroundScheduler.instance()))
                .build()
    }

    private interface UpdateApi {
        @GET("update.json")
        fun fetchUpdate(): Observable<Update>
    }

    companion object {
        private val logger = logger("UpdateChecker")

        /**
         * Returns the Endpoint-URL that is to be queried
         */
        private fun updateUrls(betaChannel: Boolean): List<String> {
            val urls = ArrayList<String>()

            if (betaChannel) {
                urls.add("https://pr0.wibbly-wobbly.de/beta/")
                urls.add("https://github.com/mopsalarm/pr0gramm-updates/raw/beta/")
                urls.add("http://pr0.wibbly-wobbly.de/beta/")
            } else {
                urls.add("https://pr0.wibbly-wobbly.de/stable/")
                urls.add("https://github.com/mopsalarm/pr0gramm-updates/raw/master/")
                urls.add("http://pr0.wibbly-wobbly.de/stable/")
            }

            return urls
        }


        fun download(activity: FragmentActivity, update: Update) = with(activity.directKodein) {
            val downloadService = instance<DownloadService>()
            val notificationService = instance<NotificationService>()

            val progress = downloadService
                    .downloadUpdateFile(update.apk)
                    .subscribeOn(BackgroundScheduler.instance())
                    .unsubscribeOn(BackgroundScheduler.instance())
                    .observeOn(AndroidSchedulers.mainThread())
                    .share()

            // install on finish
            val appContext = activity.applicationContext
            progress.filter { it.file != null }
                    .flatMap<Any> {
                        try {
                            install(appContext, it.file!!)
                            Observable.empty()

                        } catch (error: IOException) {
                            Observable.error(error)
                        }
                    }
                    .subscribe(Action1 {}, defaultOnError())

            // show a progress dialog
            val dialog = DownloadUpdateDialog(progress)
            dialog.show(activity.supportFragmentManager, null)

            // remove pending upload notification
            notificationService.cancelForUpdate()
        }

        private fun install(context: Context, apk: File) {
            val uri: Uri
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                val provider = BuildConfig.APPLICATION_ID + ".FileProvider"
                uri = FileProvider.getUriForFile(context, provider, apk)

            } else {
                val candidates = listOf<File?>(context.externalCacheDir,
                        Environment.getExternalStorageDirectory(),
                        File("/sdcard"))

                val directory = candidates.firstOrNull { it != null && it.canWrite() }
                        ?: throw IOException("Could not find a public place to write the update to")

                val file = File(directory, "update.apk")

                logger.info("Copy apk to public space.")
                FileInputStream(apk).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                // make file readable
                if (!file.setReadable(true)) {
                    logger.info("Could not make file readable")
                }

                uri = Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }
}

