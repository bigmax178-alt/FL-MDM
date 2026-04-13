package com.example.firebaselabelapp.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.example.firebaselabelapp.kiosk.KioskManager
import java.io.File
import java.io.FileInputStream

class DownloadCompleteReceiver : BroadcastReceiver() {

    @Suppress("PrivatePropertyName")
    private val TAG = "DownloadCompleteReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            Log.d(TAG, "Download completed. ID: $downloadId")
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Check status and get file URI
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUriString = cursor.getString(uriIndex)
                        if (localUriString != null) {
                            val apkFile = getFileFromUri(context, localUriString.toUri())
                            if (apkFile != null && apkFile.exists()) {
                                installSilently(context, apkFile)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            when (uri.scheme) {
                "file" -> File(uri.path!!)
                "content" -> {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                    val tempFile = File(context.cacheDir, "temp_update.apk")
                    tempFile.outputStream().use { inputStream.copyTo(it) }
                    tempFile
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file from URI", e)
            null
        }
    }

    private fun installSilently(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // 1. SECURITY CHECK (Delegated to AppSecurityManager)
        val validPackageName = AppSecurityManager.verifyAndGetPackageName(context, apkFile)

        if (validPackageName == null) {
            Log.e(TAG, "Install aborted: APK failed security check.")
            apkFile.delete()
            return
        }

        // 2. OPEN THE GATE (Unlock restrictions)
        val kioskManager = KioskManager(context)
        if (kioskManager.isDeviceOwner()) {
            Log.i(TAG, "Package authorized: $validPackageName. Opening Security Gate...")
            kioskManager.setInstallRestrictions(false)
        }

        try {
            val pm = context.packageManager
            val packageInstaller = pm.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            // Ensure we are installing exactly what we checked
            params.setAppPackageName(validPackageName)

            val sessionId = packageInstaller.createSession(params)

            packageInstaller.openSession(sessionId).use { session ->
                FileInputStream(apkFile).use { inputStream ->
                    session.openWrite("package", 0, apkFile.length()).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        session.fsync(outputStream)
                    }
                }

                val intent = Intent(context, InstallStatusReceiver::class.java)
                // Pass package name so we know if we need to manually re-lock later
                intent.putExtra("updated_package", validPackageName)

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }

            // Cleanup
            apkFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            // EMERGENCY RE-LOCK
            if (kioskManager.isDeviceOwner()) {
                kioskManager.setInstallRestrictions(true)
            }
        }
    }
}