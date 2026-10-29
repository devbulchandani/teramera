package com.example.teramera.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.teramera.core.network.LedgerApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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

    private var receiver: BroadcastReceiver? = null

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
        // A timestamped name avoids DownloadManager's "file already exists" failure
        // when the user taps Update a second time.
        val fileName = "teramera-update-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("teramera update")
            .setDescription("Downloading the latest version")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = dm.enqueue(request)

        unregisterReceiver()

        val next = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                unregisterReceiver()
                if (!isSuccessful(dm, downloadId)) {
                    Toast.makeText(ctx, "Couldn't download the update. Try again.", Toast.LENGTH_LONG).show()
                    return
                }
                val uri = dm.getUriForDownloadedFile(downloadId) ?: return
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
        receiver = next
        ContextCompat.registerReceiver(
            context,
            next,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun isSuccessful(dm: DownloadManager, id: Long): Boolean =
        dm.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            cursor.moveToFirst() &&
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                DownloadManager.STATUS_SUCCESSFUL
        } ?: false

    private fun unregisterReceiver() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}
