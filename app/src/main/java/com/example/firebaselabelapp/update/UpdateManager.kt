package com.example.firebaselabelapp.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.firebaselabelapp.kiosk.KioskManager
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class UpdateManager(private val activity: Activity, private val db: FirebaseFirestore) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_DOCUMENT_ID = "app_updates"
        private const val VERSIONS_JSON_URL =
            "https://raw.githubusercontent.com/puntusovdima/FoodLabelPro/main/Versions.json"
    }

    private var downloadCompleteReceiver: DownloadCompleteReceiver? = null

    init {
        // Sync security rules from Firestore immediately on startup
        AppSecurityManager.syncWithFirestore(activity, db)
    }

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            packageInfo.versionName?.toString() ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    fun getCurrentVersionNameAndCode(): String {
        val versionCode = try {
            val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get package info", e)
            -1
        }
        val versionName = getCurrentVersionName()
        return "$versionName ($versionCode)"
    }

    /**
     * Checks for updates for the current app (Self-Update).
     */
    fun checkForUpdates(specificVersion: String? = null) {
        val currentCode = getCurrentVersionCode()
        Thread {
            try {
                if (specificVersion != null) {
                    val url = "https://github.com/puntusovdima/FoodLabelPro/releases/download/$specificVersion/FoodLabelPro.apk"
                    downloadAndInstallSilently(url, "app-release.apk")
                } else {
                    val url = URL(VERSIONS_JSON_URL)
                    val json = JSONObject(url.readText())
                    if (json.getLong("latest_version_code") > currentCode) {
                        downloadAndInstallSilently(json.getString("apk_url"), "app-release.apk")
                    } else {
                        fallbackToFirestore(currentCode)
                    }
                }
            } catch (_: Exception) {
                fallbackToFirestore(currentCode)
            }
        }.start()
    }

    /**
     * Generic method to install ANY allowed app given its URL.
     */
    fun installApp(url: String, appName: String) {
        if (url.isBlank()) {
            Log.e(TAG, "Cannot install $appName: URL is empty")
            return
        }
        val safeFileName = "${appName.replace(" ", "_")}.apk"
        Thread {
            downloadAndInstallSilently(url, safeFileName)
        }.start()
    }

    /**
     * Handles installation from a local URI (e.g. file picker).
     * Copies the file to a temp location, performs security check, then installs or blocks.
     */
    fun installLocalApk(uri: Uri) {
        Thread {
            try {
                val tempFile = File(activity.cacheDir, "temp_install.apk")
                if (tempFile.exists()) tempFile.delete()

                // Copy Uri content to temp file
                activity.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                activity.runOnUiThread {
                    Toast.makeText(activity, "Verifying APK security...", Toast.LENGTH_SHORT).show()
                }

                // Security check happens here
                installWithSecurityCheck(tempFile)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process local APK", e)
                activity.runOnUiThread {
                    Toast.makeText(activity, "Error reading APK file", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun fallbackToFirestore(currentCode: Long) {
        db.collection(UPDATE_DOCUMENT_ID).document("latest_release").get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val latest = doc.getLong("latest_version_code") ?: -1
                    val url = doc.getString("apk_url")
                    if (latest > currentCode && !url.isNullOrEmpty()) {
                        downloadAndInstallSilently(url, "app-release.apk")
                    }
                }
            }
    }

    private fun downloadAndInstallSilently(url: String, fileName: String) {
        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        registerDownloadReceiver()
        cleanupPreviousDownloads(downloadManager)

        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        File(dir, fileName).takeIf { it.exists() }?.delete()

        Log.d(TAG, "Starting download: $url -> $fileName")
        val request = DownloadManager.Request(url.toUri())
            .setTitle("Downloading Update")
            .setDescription("Downloading $fileName")
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        try {
            val id = downloadManager.enqueue(request)
            startDownloadStatusPolling(downloadManager, id)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
        }
    }

    private fun registerDownloadReceiver() {
        if (downloadCompleteReceiver == null) {
            downloadCompleteReceiver = DownloadCompleteReceiver()
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ContextCompat.registerReceiver(activity, downloadCompleteReceiver!!, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        }
    }

    fun unregisterDownloadReceiver() {
        downloadCompleteReceiver?.let {
            try { activity.unregisterReceiver(it) } catch (_: Exception) {}
            downloadCompleteReceiver = null
        }
    }

    private fun cleanupPreviousDownloads(dm: DownloadManager) {
        val query = DownloadManager.Query()
        try {
            val cursor = dm.query(query)
            cursor?.use {
                val idIndex = it.getColumnIndex(DownloadManager.COLUMN_ID)
                if (idIndex != -1) {
                    while (it.moveToNext()) {
                        // Cleanup logic here
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Cleanup error", e) }
    }

    private fun startDownloadStatusPolling(dm: DownloadManager, id: Long) {
        Thread {
            var attempts = 0
            while (attempts < 120) {
                try {
                    Thread.sleep(1000)
                    attempts++
                    val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            if (statusIndex != -1 && it.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                                val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                if (uriIndex != -1) {
                                    val uriString = it.getString(uriIndex)
                                    handlePollingInstall(uriString)
                                    return@Thread
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                }
            }
        }.start()
    }

    private fun handlePollingInstall(uriString: String?) {
        if (uriString == null) return
        try {
            val file = File(uriString.toUri().path ?: return)
            if (file.exists()) {
                installWithSecurityCheck(file)
            }
        } catch (e: Exception) { Log.e(TAG, "Polling install error", e) }
    }

    private fun installWithSecurityCheck(apkFile: File) {
        // 1. SECURITY CHECK (Delegated to AppSecurityManager)
        val validPackageName = AppSecurityManager.verifyAndGetPackageName(activity, apkFile)

        if (validPackageName == null) {
            // Blocked by security. Log this attempt so admin can review it.
            Log.w(TAG, "Security check blocked an install attempt. Reporting to Firestore.")
            activity.runOnUiThread {
                Toast.makeText(activity, "Security Block: This app is not authorized.", Toast.LENGTH_LONG).show()
            }
            logBlockedInstallAttempt(apkFile)

            apkFile.delete()
            return
        }

        val kioskManager = KioskManager(activity)
        if (kioskManager.isDeviceOwner()) {
            kioskManager.setInstallRestrictions(false)
        }

        try {
            val pm = activity.packageManager
            val installer = pm.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setAppPackageName(validPackageName)
            val sessionId = installer.createSession(params)

            installer.openSession(sessionId).use { session ->
                FileInputStream(apkFile).use { input ->
                    session.openWrite("package", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val intent = android.content.Intent(activity, InstallStatusReceiver::class.java)
                intent.putExtra("updated_package", validPackageName)

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pending = android.app.PendingIntent.getBroadcast(activity, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            apkFile.delete()
        } catch (_: Exception) {
            if (kioskManager.isDeviceOwner()) kioskManager.setInstallRestrictions(true)
        }
    }

    private fun logBlockedInstallAttempt(apkFile: File) {
        try {
            val pm = activity.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA
            }

            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return

            // Fix: Source dir must be set manually for uninstalled APKs to read signatures/resources
            info.applicationInfo?.sourceDir = apkFile.absolutePath
            info.applicationInfo?.publicSourceDir = apkFile.absolutePath

            val packageName = info.packageName

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }

            val hash = if (!signatures.isNullOrEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(signatures[0].toByteArray())
                hashBytes.joinToString(":") { "%02X".format(it) }
            } else {
                "NO_SIGNATURE"
            }

            val attemptData = hashMapOf(
                "packageName" to packageName,
                "signatureHash" to hash,
                "timestamp" to java.util.Date(),
                "deviceModel" to Build.MODEL,
                "androidId" to (android.provider.Settings.Secure.getString(activity.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown")
            )

            db.collection("blocked_installs")
                .add(attemptData)
                .addOnSuccessListener { Log.d(TAG, "Reported blocked install for $packageName") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to report blocked install", e) }

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing blocked APK", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
        } catch (_: Exception) { -1 }
    }
}