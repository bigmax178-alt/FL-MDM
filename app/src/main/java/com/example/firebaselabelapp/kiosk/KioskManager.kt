package com.example.firebaselabelapp.kiosk

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.firebaselabelapp.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom

class KioskManager(private val context: Context) {

    companion object {
        private const val TAG = "KioskManager"
        private const val PREFS_NAME = "kiosk_prefs"
        private const val PIN_KEY = "kiosk_pin_hash"
        private const val PIN_SALT_KEY = "kiosk_pin_salt"
        private const val KIOSK_ENABLED_KEY = "kiosk_enabled"
        private const val REQUEST_CODE_ENABLE_ADMIN = 1001
        private const val PIN_LENGTH = 6
    }

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create encrypted preferences, falling back to regular preferences", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val devicePolicyManager: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(context, KioskDeviceAdminReceiver::class.java)
    }

    // --- SECURITY & RESTRICTION METHODS (NEW) ---

    fun enforceAlwaysOnSecurity() {
        if (isDeviceOwner()) {
            setInstallRestrictions(true)
        }
    }

    fun setInstallRestrictions(restricted: Boolean) {
        if (!isDeviceOwner()) return

        // === DEBUG BUILD EXCEPTION ===
        // We MUST allow the OS to accept installs in Debug mode, otherwise
        // Android Studio (ADB) cannot update the app during development.
        // NOTE: Your internal 'AppSecurityManager' will STILL check hashes
        // for any updates the app tries to download itself.
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "DEBUG BUILD: Lifting OS install restrictions to allow Android Studio deployment.")
            try {
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear debug restrictions", e)
            }
            return
        }

        try {
            if (restricted) {
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                Log.d(TAG, "Install restrictions ENABLED (Security Gate CLOSED)")
            } else {
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_APPS)
                devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
                Log.w(TAG, "Install restrictions DISABLED (Security Gate OPEN)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change install restrictions", e)
        }
    }

    private fun setAdditionalRestrictions() {
        if (!isDeviceOwner()) return

        try {
            devicePolicyManager.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            setInstallRestrictions(true) // Enforce the gate

            try {
                devicePolicyManager.setGlobalSetting(adminComponent, Settings.Global.ADB_ENABLED, "0")
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable USB debugging", e)
            }

            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set additional restrictions", e)
        }
    }

    private fun clearAdditionalRestrictions() {
        if (!isDeviceOwner()) return

        try {
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            // Note: We deliberately do NOT clear install restrictions here to keep device secure
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear restrictions", e)
        }
    }

    fun enforceAppAllowlist(allowedPackageNames: List<String>) {
        if (!isDeviceOwner()) return
        try {
            val installedApps = context.packageManager.getInstalledApplications(0)
            for (appInfo in installedApps) {
                val packageName = appInfo.packageName
                if (packageName == context.packageName || allowedPackageNames.contains(packageName)) {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, false)
                    continue
                }
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                if (!isSystemApp) {
                    devicePolicyManager.setApplicationHidden(adminComponent, packageName, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enforce app allowlist", e)
        }
    }

    // --- KIOSK LOGIC ---

    fun enableKioskMode(): Boolean {
        return try {
            val isDeviceOwner = isDeviceOwner()
            val isDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)

            if (!isDeviceOwner && !isDeviceAdmin) return false

            sharedPreferences.edit { putBoolean(KIOSK_ENABLED_KEY, true) }

            if (isDeviceOwner) enableDeviceOwnerKiosk() else enableDeviceAdminKiosk()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable kiosk mode", e)
            false
        }
    }

    private fun enableDeviceOwnerKiosk() {
        try {
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
            devicePolicyManager.setStatusBarDisabled(adminComponent, true)
            devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            devicePolicyManager.setGlobalSetting(adminComponent, "policy_control", "immersive.full=*")
            devicePolicyManager.setGlobalSetting(adminComponent, "safe_boot_disallowed", "1")

            grantUsbPermissionsSilently()
            setAdditionalRestrictions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply device owner kiosk policies", e)
        }
    }

    fun disableKioskMode(): Boolean {
        return try {
            sharedPreferences.edit { putBoolean(KIOSK_ENABLED_KEY, false) }
            if (isDeviceOwner()) disableDeviceOwnerKiosk() else disableDeviceAdminKiosk()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable kiosk mode", e)
            false
        }
    }

    private fun disableDeviceOwnerKiosk() {
        try {
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf())
            devicePolicyManager.setStatusBarDisabled(adminComponent, false)
            devicePolicyManager.setKeyguardDisabled(adminComponent, false)
            devicePolicyManager.setGlobalSetting(adminComponent, "policy_control", null)
            devicePolicyManager.setGlobalSetting(adminComponent, "safe_boot_disallowed", "0")
            clearAdditionalRestrictions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear device owner policies", e)
        }
    }

    // --- RESTORED MISSING METHODS ---

    fun canUseLockTaskMode(): Boolean {
        return try {
            val isDeviceOwner = isDeviceOwner()
            val isDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)
            val isPermitted = devicePolicyManager.isLockTaskPermitted(context.packageName)
            isDeviceOwner || isPermitted || isDeviceAdmin
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check lock task mode permission", e)
            isDeviceAdminActive()
        }
    }

    fun requestDeviceAdminPermission(activity: Activity) {
        if (isDeviceOwner()) return

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Kiosk mode requires admin access.")
            }
            try {
                activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start device admin request", e)
            }
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (accessibilityEnabled == 1) {
                val services = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                services.contains("${context.packageName}/.kiosk.KioskAccessibilityService")
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check accessibility service status", e)
            false
        }
    }

    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun isKioskReady(): Boolean {
        val isPinSet = isPinSet()
        val isAdminOrOwner = isDeviceAdminActive()
        val canUseLockTask = canUseLockTaskMode()
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()

        return if (isDeviceOwner()) {
            isPinSet && isAdminOrOwner && canUseLockTask
        } else {
            isPinSet && isAdminOrOwner && canUseLockTask && isAccessibilityEnabled
        }
    }

    fun clearKioskData() {
        try {
            sharedPreferences.edit { clear() }
            Log.d(TAG, "Kiosk data cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear kiosk data", e)
        }
    }

    fun getKioskStatus(): String {
        return buildString {
            appendLine("=== Kiosk Status ===")
            appendLine("Enabled: ${isKioskModeEnabled()}")
            appendLine("Device Owner: ${isDeviceOwner()}")
            appendLine("Kiosk Ready: ${isKioskReady()}")
        }
    }

    fun handleSystemUiVisibilityChange(activity: Activity) {
        if (isKioskModeEnabled()) {
            enableImmersiveMode(activity)
        }
    }

    fun forceExitKioskMode(activity: Activity): Boolean {
        return try {
            if (!isDeviceOwner()) return false
            stopLockTaskMode(activity)
            disableKioskMode()
            disableImmersiveMode(activity)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force exit kiosk mode", e)
            false
        }
    }

    // --- USB & OTHER HELPERS ---

    fun grantUsbPermissionsSilently() {
        if (!isDeviceOwner()) return
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            for (device in usbManager.deviceList.values) {
                if (!usbManager.hasPermission(device)) {
                    grantUsbPermissionForDevice(device)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error granting USB permissions", e)
        }
    }

    fun grantUsbPermissionForDevice(device: UsbDevice): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.setPermissionGrantState(
                    adminComponent,
                    context.packageName,
                    "android.hardware.usb.action.USB_DEVICE_ATTACHED",
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            } else {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val permissionIntent = PendingIntent.getBroadcast(
                    context, 0, Intent("com.example.firebaselabelapp.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE
                )
                usbManager.requestPermission(device, permissionIntent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isDeviceOwner() = try {
        devicePolicyManager.isDeviceOwnerApp(context.packageName)
    } catch (e: Exception) { false }

    fun isKioskModeEnabled() = sharedPreferences.getBoolean(KIOSK_ENABLED_KEY, false)

    fun startLockTaskMode(activity: Activity) {
        if (!isKioskModeEnabled()) return
        if (isDeviceOwner()) {
            activity.startLockTask()
            enableImmersiveMode(activity)
            setAdditionalRestrictions()
        } else {
            activity.startLockTask()
            enableImmersiveMode(activity)
        }
    }

    fun stopLockTaskMode(activity: Activity) {
        if (isDeviceOwner()) clearAdditionalRestrictions()
        activity.stopLockTask()
        disableImmersiveMode(activity)
    }

    fun enableImmersiveMode(activity: Activity) {
        val decorView = activity.window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setDecorFitsSystemWindows(false)
            activity.window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun disableImmersiveMode(activity: Activity) {
        val decorView = activity.window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setDecorFitsSystemWindows(true)
            activity.window.insetsController?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun enableDeviceAdminKiosk() {}
    private fun disableDeviceAdminKiosk() {}
    fun isDeviceAdminActive() = isDeviceOwner() || devicePolicyManager.isAdminActive(adminComponent)

    fun savePin(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashedPin = digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        sharedPreferences.edit { putString(PIN_KEY, hashedPin).putString(PIN_SALT_KEY, saltHex) }
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = sharedPreferences.getString(PIN_KEY, null) ?: return false
        val saltHex = sharedPreferences.getString(PIN_SALT_KEY, null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val inputHash = digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
        return storedHash == inputHash
    }

    fun isPinSet() = sharedPreferences.contains(PIN_KEY)
    fun isValidPinFormat(pin: String) = pin.length == PIN_LENGTH && pin.all { it.isDigit() }
    fun emergencyDisable() { sharedPreferences.edit { clear() } }
}