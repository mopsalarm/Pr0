package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.defaultOnError
import com.pr0gramm.app.ui.fragments.DownloadUpdateDialog
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.BackgroundScheduler
import com.pr0gramm.app.util.Logger
import com.pr0gramm.app.util.MainThreadScheduler
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Url
import rx.Observable
import rx.functions.Action1
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Class to perform an update check.
 */
class UpdateChecker {
    private val currentVersion = AndroidUtility.buildVersionCode()

    private val updateApi = Retrofit.Builder()
            .baseUrl("https://example.com")
            .addConverterFactory(MoshiConverterFactory.create(MoshiInstance))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .validateEagerly(BuildConfig.DEBUG)
            .build().create<UpdateApi>()

    private val endpoints: List<String> = mutableListOf<String>().also { urls ->
        if (Settings.get().useBetaChannel) {
            urls += "https://pr0.wibbly-wobbly.de/beta/update.json"
            urls += "https://github.com/mopsalarm/pr0gramm-updates/raw/beta/update.json"
            urls += "https://app.pr0gramm.com/updates/beta/update.json"
        } else {
            urls += "https://pr0.wibbly-wobbly.de/stable/update.json"
            urls += "https://github.com/mopsalarm/pr0gramm-updates/raw/master/update.json"
            urls += "https://app.pr0gramm.com/updates/stable/update.json"
        }
    }

    private suspend fun queryOne(endpoint: String): Update {
        val update = updateApi.fetchUpdate(endpoint).await()

        // make path absolute if needed
        var apk = update.apk
        if (!apk.startsWith("http")) {
            apk = Uri.withAppendedPath(Uri.parse(endpoint), apk).toString()
        }

        // use this one as our update.
        return update.copy(apk = apk)
    }

    suspend fun queryAll(): Response {
        // start all jobs in parallel
        val tasks = supervisorScope {
            endpoints.map { async { queryOne(it) } }
        }

        // wait for them to finish
        val results = tasks.map { kotlin.runCatching { it.await() } }

        if (results.all { it.isFailure }) {
            val err = results.first().exceptionOrNull() ?: return Response.NoUpdate
            return Response.Error(err)
        }

        val update = results
                .mapNotNull { it.getOrNull() }.maxBy { it.version }
                ?.takeIf { it.version > currentVersion }
                ?: return Response.NoUpdate

        return Response.UpdateAvailable(update)
    }

    sealed class Response {
        object NoUpdate : Response()

        class Error(val err: Throwable) : Response()

        class UpdateAvailable(val update: Update) : Response()
    }

    private interface UpdateApi {
        @GET
        fun fetchUpdate(@Url url: String): Deferred<Update>
    }

    companion object {
        private val logger = Logger("UpdateChecker")

        fun download(activity: FragmentActivity, update: Update) {
            val downloadService = activity.injector.instance<DownloadService>()
            val notificationService = activity.injector.instance<NotificationService>()

            val progress = downloadService
                    .downloadUpdateFile(Uri.parse(update.apk))
                    .subscribeOn(BackgroundScheduler)
                    .unsubscribeOn(BackgroundScheduler)
                    .observeOn(MainThreadScheduler)
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

        @SuppressLint("SdCardPath")
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

                logger.info { "Copy apk to public space." }
                FileInputStream(apk).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                // make file readable
                if (!file.setReadable(true)) {
                    logger.info { "Could not make file readable" }
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

