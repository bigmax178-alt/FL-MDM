package com.example.firebaselabelapp.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Data class to hold allowed app info.
 */
data class AllowedApp(
    val packageName: String,
    val sha256: String,
    val name: String = "",
    val downloadUrl: String = ""
)

/**
 * Manages the allowlist of apps dynamically using Firestore + Local Caching.
 *
 * Firestore Structure:
 * Collection: "allowed_apps"
 * Documents contain fields:
 * - "packageName": String
 * - "sha256": String
 * - "name": String (Optional, for UI)
 * - "downloadUrl": String (Optional, for installation)
 */
object AppSecurityManager {

    private const val TAG = "AppSecurityManager"
    private const val PREFS_NAME = "security_params"
    private const val KEY_ALLOWLIST = "trusted_apps_map"

    // === HARDCODED SAFETY NET ===
    // These apps are trusted AUTOMATICALLY.
    private val HARDCODED_RULES = mapOf(
        "com.example.firebaselabelapp" to AllowedApp(
            packageName = "com.example.firebaselabelapp",
            sha256 = "60:F7:38:92:DD:6D:5E:AA:71:FF:92:6F:1C:6D:F7:04:4C:1D:74:76:03:24:F9:56:75:B8:D7:F6:14:B8:29:CC",
            name = "Food Label Pro",
            downloadUrl = ""
        )
    )

    // Reactive list for UI - Initialize with Hardcoded rules immediately
    private val _allowedApps = MutableStateFlow<List<AllowedApp>>(HARDCODED_RULES.values.toList())
    val allowedApps: StateFlow<List<AllowedApp>> = _allowedApps.asStateFlow()

    // Internal lookup map for fast verification - Initialize with Hardcoded rules
    private var allowedAppsCache: Map<String, AllowedApp> = HARDCODED_RULES

    /**
     * Call this from UpdateManager to pull the latest rules from the cloud.
     */
    fun syncWithFirestore(context: Context, db: FirebaseFirestore) {
        // 1. Ensure we at least have local cache loaded before fetching remote
        ensureCacheLoaded(context)

        Log.d(TAG, "Syncing allowlist from Firestore...")
        db.collection("allowed_apps").get()
            .addOnSuccessListener { snapshot ->
                val newMap = mutableMapOf<String, AllowedApp>()

                // 1. Add Hardcoded Defaults FIRST
                newMap.putAll(HARDCODED_RULES)

                // 2. Add/Overwrite with Remote apps
                for (doc in snapshot.documents) {
                    val pkg = doc.getString("packageName")
                    val hash = doc.getString("sha256")?.trim()
                    val name = doc.getString("name") ?: pkg ?: "Unknown App"
                    val url = doc.getString("downloadUrl") ?: ""

                    if (!pkg.isNullOrEmpty() && !hash.isNullOrEmpty()) {
                        newMap[pkg] = AllowedApp(pkg, hash, name, url)
                    }
                }

                if (newMap.isNotEmpty()) {
                    saveToPrefs(context, newMap)
                    updateCache(newMap)
                    Log.i(TAG, "Allowlist updated. Count: ${newMap.size}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync allowlist. Using local cache.", e)
                // Cache is already loaded by ensureCacheLoaded
            }
    }

    /**
     * Main Entry Point: Verifies APK against the cached list.
     */
    fun verifyAndGetPackageName(context: Context, apkFile: File): String? {
        ensureCacheLoaded(context)

        if (!apkFile.exists()) {
            Log.e(TAG, "File does not exist: ${apkFile.absolutePath}")
            return null
        }

        // 1. Parse APK to get Package Name
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA
        }

        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return null

        // Fix for uninstalled APK reading (SourceDir needed for resources/signatures)
        packageInfo.applicationInfo?.sourceDir = apkFile.absolutePath
        packageInfo.applicationInfo?.publicSourceDir = apkFile.absolutePath

        val incomingPackageName = packageInfo.packageName

        // 2. Check Name against Cache
        val allowedApp = allowedAppsCache[incomingPackageName]
        if (allowedApp == null) {
            Log.w(TAG, "BLOCKED: Package '$incomingPackageName' not found in allowlist.")
            return null
        }

        // 3. Verify Signature Hash
        val expectedHash = allowedApp.sha256
        val actualHash = getApkSignatureHash(packageInfo)

        return if (actualHash.equals(expectedHash, ignoreCase = true)) {
            Log.i(TAG, "SUCCESS: Verified '$incomingPackageName'")
            incomingPackageName
        } else {
            Log.e(TAG, "=== SECURITY CHECK FAILED ===")
            Log.e(TAG, "Package: $incomingPackageName")
            Log.e(TAG, "EXPECTED Hash: '$expectedHash'")
            Log.e(TAG, "ACTUAL Hash:   '$actualHash'")
            Log.e(TAG, "===============================")
            null
        }
    }

    private fun updateCache(newMap: Map<String, AllowedApp>) {
        allowedAppsCache = newMap
        _allowedApps.value = newMap.values.toList()
    }

    private fun ensureCacheLoaded(context: Context) {
        // Always reload from prefs if cache is only defaults (or empty) to catch offline updates
        if (allowedAppsCache.size <= HARDCODED_RULES.size) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_ALLOWLIST, "{}")
            try {
                val json = JSONObject(jsonString ?: "{}")
                val map = mutableMapOf<String, AllowedApp>()

                // 1. Initialize with Hardcoded Defaults
                map.putAll(HARDCODED_RULES)

                // 2. Load from Prefs
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val appJson = json.getJSONObject(key)
                    map[key] = AllowedApp(
                        packageName = appJson.getString("packageName"),
                        sha256 = appJson.getString("sha256"),
                        name = appJson.optString("name", "Unknown"),
                        downloadUrl = appJson.optString("downloadUrl", "")
                    )
                }
                updateCache(map)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cache. Fallback to hardcoded rules.", e)
                updateCache(HARDCODED_RULES)
            }
        }
    }

    private fun saveToPrefs(context: Context, map: Map<String, AllowedApp>) {
        val rootJson = JSONObject()
        map.forEach { (key, value) ->
            val appJson = JSONObject()
            appJson.put("packageName", value.packageName)
            appJson.put("sha256", value.sha256)
            appJson.put("name", value.name)
            appJson.put("downloadUrl", value.downloadUrl)
            rootJson.put(key, appJson)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_ALLOWLIST, rootJson.toString())
            }
    }

    private fun getApkSignatureHash(packageInfo: android.content.pm.PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        if (signatures.isNullOrEmpty()) return null

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(signatures[0].toByteArray())
            hashBytes.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) { null }
    }
}