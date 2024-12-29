package com.pr0gramm.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.fragment.app.FragmentActivity
import com.pr0gramm.app.BuildConfig
import com.pr0gramm.app.Logger
import com.pr0gramm.app.MoshiInstance
import com.pr0gramm.app.Settings
import com.pr0gramm.app.model.update.UpdateModel
import com.pr0gramm.app.ui.Screen
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchUntilDestroy
import com.pr0gramm.app.ui.fragments.ProgressDialogController
import com.pr0gramm.app.util.AndroidUtility
import com.pr0gramm.app.util.di.injector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
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
            .validateEagerly(BuildConfig.DEBUG)
            .build().create<UpdateApi>()

    private val endpoints: List<String> = mutableListOf<String>().also { urls ->
        if (Settings.useBetaChannel) {
            urls += "https://app.pr0gramm.com/updates/beta/update.json"
            urls += "https://raw.githubusercontent.com/pr0gramm-com/pr0gramm-app/refs/heads/updates/beta.json"
        } else {
            urls += "https://app.pr0gramm.com/updates/stable/update.json"
            urls += "https://raw.githubusercontent.com/pr0gramm-com/pr0gramm-app/refs/heads/updates/stable.json"
        }
    }

    private suspend fun queryOne(endpoint: String): Update {
        val update = Update(updateApi.fetchUpdateAsync(
                endpoint, androidVersion = Build.VERSION.SDK_INT))

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
        val results = tasks.map { runCatching { it.await() } }

        if (results.all { it.isFailure }) {
            val err = results.first().exceptionOrNull() ?: return Response.NoUpdate
            return Response.Error(err)
        }

        val update = results
                .mapNotNull { it.getOrNull() }.maxByOrNull { it.version }
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
        suspend fun fetchUpdateAsync(@Url url: String, @Query("androidVersion") androidVersion: Int): UpdateModel
    }

    companion object {
        private val logger = Logger("UpdateChecker")

        fun download(activity: FragmentActivity, update: Update) {
            // rotating crashes the dialog
            Screen.lockOrientation(activity)

            val downloadService = activity.injector.instance<DownloadService>()
            val notificationService = activity.injector.instance<NotificationService>()

            activity as BaseAppCompatActivity

            activity.launchUntilDestroy {

                val progress = downloadService.downloadUpdateFile(Uri.parse(update.apk))

                // show the progress dialog.
                val dialog = ProgressDialogController(activity)

                dialog.show()

                val fileUri = try {
                    // download the file and show progress as long as the dialog is open
                    progress.onEach { status -> dialog.updateStatus(status) }
                            .firstOrNull { state -> state.file != null }
                            ?.file

                } finally {
                    // remove dialog again
                    dialog.dismiss()
                }

                if (fileUri != null) {
                    install(activity, fileUri.toFile())
                }
            }

            // remove pending upload notification
            notificationService.cancelForUpdate()
        }

        @SuppressLint("SdCardPath")
        private suspend fun install(context: Context, apk: File) {
            val uri = withContext(Dispatchers.IO + NonCancellable) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                    val provider = BuildConfig.APPLICATION_ID + ".FileProvider"
                    FileProvider.getUriForFile(context, provider, apk)

                } else {
                    val candidates = listOf(
                            { context.externalCacheDir },
                            { Environment.getExternalStorageDirectory() },
                            { File("/sdcard") }
                    )


                    val directory = candidates
                            .mapNotNull { runCatching(it).getOrNull() }
                            .firstOrNull { dir -> dir.canWrite() }
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

                    Uri.fromFile(file)
                }
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }
}
