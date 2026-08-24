package com.example.teramera.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.example.teramera.core.network.LedgerApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ledgerApi: LedgerApi,
) {

    /** Returns UpdateInfo when the server has a newer build, else null. */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = try {
        val v = ledgerApi.appVersion()
        if (v.versionCode > currentVersionCode)
            UpdateInfo(v.versionCode, v.versionName, v.apkUrl)
        else null
    } catch (_: Exception) {
        null // offline / server down — never block the app over an update check
    }

    /** Downloads the APK via DownloadManager and opens the system installer when done. */
    fun downloadAndInstall(apkUrl: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("teramera update")
            .setDescription("Downloading the latest version")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "teramera-update.apk")
        val downloadId = dm.enqueue(request)

        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                    val uri = dm.getUriForDownloadedFile(downloadId) ?: return
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
