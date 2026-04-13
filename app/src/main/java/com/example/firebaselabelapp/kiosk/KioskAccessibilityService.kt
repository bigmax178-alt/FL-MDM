package com.example.firebaselabelapp.kiosk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enhanced Accessibility Service for both device owner and device admin kiosk modes
 * This version provides strong app blocking for device admin mode while maintaining
 * optimal performance for device owner mode
 */
class KioskAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KioskAccessibilityService"

        // Comprehensive set of packages to monitor
        private val BLOCKED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.vending", // Google Play Store
            "com.samsung.android.launcher", // Samsung launcher
            "com.miui.home", // MIUI launcher
            "com.huawei.android.launcher", // Huawei launcher
            "com.oppo.launcher", // Oppo launcher
            "com.oneplus.launcher", // OnePlus launcher
            "com.android.chrome", // Chrome browser (optional)
            "com.android.packageinstaller", // Package installer
            "com.google.android.packageinstaller" // Google package installer
        )

        // Expanded set of critical system classes
        private val CRITICAL_SYSTEM_CLASSES = setOf(
            "com.android.systemui.recents.RecentsActivity",
            "com.android.systemui.recent.RecentsPanelView",
            "com.android.settings.Settings",
            "com.android.settings.SubSettings",
            "com.android.launcher3.Launcher",
            "com.google.android.apps.nexuslauncher.NexusLauncherActivity",
            "com.samsung.android.launcher.activities.LauncherActivity",
            "com.miui.home.launcher.Launcher",
            "android.widget.Toast\$TN" // Toast notifications that might reveal system info
        )

        // Debounce delay to prevent excessive actions
        private const val DEBOUNCE_DELAY_MS = 300L

        // Allowed system packages that are safe to access
        private val ALLOWED_SYSTEM_PACKAGES = setOf(
            "android", // Core Android system
            "com.android.systemui" // Only specific components blocked above
        )
    }

    private var kioskManager: KioskManager? = null
    private var devicePolicyManager: DevicePolicyManager? = null
    private var adminComponent: ComponentName? = null
    private val isServiceActive = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private var consecutiveBlockCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()

        try {
            kioskManager = KioskManager(this)
            devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            adminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
            isServiceActive.set(true)

            Log.d(TAG, "Enhanced Kiosk Accessibility Service Connected")
            Log.d(TAG, "Device Owner Status: ${kioskManager?.isDeviceOwner()}")
            Log.d(TAG, "Device Admin Status: ${adminComponent?.let { devicePolicyManager?.isAdminActive(it) }}")

            // Configure service based on admin level
            configureService()

        } catch (e: Exception) {
            Log.e(TAG, "Error during service connection", e)
            isServiceActive.set(false)
        }
    }

    private fun configureService() {
        try {
            val kioskMgr = kioskManager
            val isDeviceOwner = kioskMgr?.isDeviceOwner() == true

            val info = AccessibilityServiceInfo().apply {
                // Monitor essential events
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED

                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

                // Enhanced flags for device admin mode to catch more events
                flags = if (isDeviceOwner) {
                    // Minimal flags for device owner (already has strong controls)
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                } else {
                    // More comprehensive monitoring for device admin mode
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                            AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
                }

                // Faster response for device admin mode
                notificationTimeout = if (isDeviceOwner) 200 else 100

                // Monitor all packages
                packageNames = null
            }

            serviceInfo = info
            Log.d(TAG, "Service configured for ${if (isDeviceOwner) "device owner" else "device admin"} mode")

        } catch (e: Exception) {
            Log.e(TAG, "Error configuring service", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Early exit if service inactive or event null
        if (!isServiceActive.get() || event == null) {
            return
        }

        val kioskMgr = kioskManager
        if (kioskMgr == null || !kioskMgr.isKioskModeEnabled()) {
            return
        }

        try {
            // Debounce to prevent excessive processing
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastActionTime < DEBOUNCE_DELAY_MS) {
                return
            }

            // Handle different event types
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    // Additional monitoring for device admin mode
                    if (!kioskMgr.isDeviceOwner()) {
                        handleWindowsChanged(event)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    /**
     * Enhanced window state change handler
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()

        // Skip if it's our own app
        if (packageName == applicationContext.packageName) {
            return
        }

        // Skip if it's an allowed system component
        if (packageName != null && isAllowedSystemPackage(packageName, className)) {
            return
        }

        Log.d(TAG, "Window changed - Package: $packageName, Class: $className")

        // Check for critical system classes first (higher priority)
        if (className != null && isCriticalSystemClass(className)) {
            Log.w(TAG, "Critical system class detected: $className")
            scheduleReturn("Critical system class: $className")
            return
        }

        // Check for blocked packages
        if (packageName != null && isBlockedPackage(packageName)) {
            Log.w(TAG, "Blocked package detected: $packageName")
            scheduleReturn("Blocked package: $packageName")
            return
        }

        // Enhanced checking for device admin mode
        val kioskMgr = kioskManager
        if (packageName != null && kioskMgr != null) {
            if (kioskMgr.isDeviceOwner()) {
                // Device owner mode - be restrictive but not overly aggressive
                if (isUnauthorizedApp(packageName)) {
                    scheduleReturn("Unauthorized app in device owner mode: $packageName")
                }
            } else {
                // Device admin mode - be more aggressive in blocking
                if (isUnauthorizedAppForAdmin(packageName)) {
                    scheduleReturn("Unauthorized app in device admin mode: $packageName")
                }
            }
        }
    }

    /**
     * Handle windows changed events (additional monitoring for device admin mode)
     */
    private fun handleWindowsChanged(event: AccessibilityEvent) {
        try {
            val windows = windows
            if (windows != null) {
                for (window in windows) {
                    val windowPackage = window.root?.packageName?.toString()
                    if (windowPackage != null &&
                        windowPackage != applicationContext.packageName &&
                        isUnauthorizedAppForAdmin(windowPackage)) {
                        Log.w(TAG, "Unauthorized window detected: $windowPackage")
                        scheduleReturn("Unauthorized window: $windowPackage")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling windows changed", e)
        }
    }

    /**
     * Check if a system package/class combination is allowed
     */
    private fun isAllowedSystemPackage(packageName: String, className: String?): Boolean {
        // Allow basic Android system components that don't pose security risks
        if (packageName == "android") {
            return true
        }

        // Allow specific system UI components that are safe
        if (packageName == "com.android.systemui" && className != null) {
            return !isCriticalSystemClass(className)
        }

        return false
    }

    /**
     * Schedule return to kiosk app with enhanced debouncing and retry logic
     */
    private fun scheduleReturn(reason: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < DEBOUNCE_DELAY_MS) {
            return // Skip if too soon
        }

        lastActionTime = currentTime
        consecutiveBlockCount++

        handler.post {
            try {
                returnToKioskApp()
                Log.d(TAG, "Returned to kiosk app - Reason: $reason (Block #$consecutiveBlockCount)")

                // Reset counter on successful return
                handler.postDelayed({
                    consecutiveBlockCount = 0
                }, 2000)

            } catch (e: Exception) {
                Log.e(TAG, "Error returning to kiosk app", e)
            }
        }
    }

    /**
     * Enhanced check for blocked packages
     */
    private fun isBlockedPackage(packageName: String): Boolean {
        return BLOCKED_PACKAGES.any { blocked ->
            packageName.startsWith(blocked, ignoreCase = true)
        }
    }

    /**
     * Enhanced check for critical system classes
     */
    private fun isCriticalSystemClass(className: String): Boolean {
        return CRITICAL_SYSTEM_CLASSES.any { critical ->
            className.contains(critical, ignoreCase = true)
        } || className.contains("Settings", ignoreCase = true) ||
                className.contains("Launcher", ignoreCase = true) ||
                className.contains("SystemUI", ignoreCase = true) ||
                className.contains("Recents", ignoreCase = true) ||
                className.contains("TaskStack", ignoreCase = true)
    }

    /**
     * Check for unauthorized apps in device owner mode (less restrictive)
     */
    private fun isUnauthorizedApp(packageName: String): Boolean {
        // In device owner mode, we have strong system-level controls
        // So we only block obviously problematic apps
        return isBlockedPackage(packageName) ||
                packageName.contains("install", ignoreCase = true) ||
                packageName.contains("uninstall", ignoreCase = true) ||
                packageName.contains("adb", ignoreCase = true) ||
                packageName.contains("root", ignoreCase = true)
    }

    /**
     * Check for unauthorized apps in device admin mode (more restrictive)
     */
    private fun isUnauthorizedAppForAdmin(packageName: String): Boolean {
        // In device admin mode, we need to be more aggressive

        // Always block these
        if (isBlockedPackage(packageName)) {
            return true
        }

        // Block system apps that could be used to escape kiosk
        if (packageName.startsWith("com.android.") &&
            !packageName.startsWith("com.android.systemui")) {

            // Allow some essential Android components
            val allowedAndroidPackages = setOf(
                "com.android.phone",
                "com.android.dialer",
                "com.android.contacts",
                "com.android.mms",
                "com.android.camera2"
            )

            return !allowedAndroidPackages.any { allowed ->
                packageName.startsWith(allowed)
            }
        }

        // Block common third-party launchers and system tools
        val suspiciousKeywords = listOf(
            "launcher", "home", "desktop", "shell", "terminal", "command",
            "install", "uninstall", "manager", "explorer", "browser",
            "root", "su", "adb", "debug", "developer"
        )

        return suspiciousKeywords.any { keyword ->
            packageName.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * Enhanced return to kiosk app with multiple strategies
     */
    private fun returnToKioskApp() {
        try {
            // Strategy 1: Standard launch intent
            val intent = packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            intent?.let {
                it.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                startActivity(it)
                return
            }

            // Strategy 2: Direct activity launch (fallback)
            val fallbackIntent = Intent().apply {
                setClassName(applicationContext.packageName, "${applicationContext.packageName}.MainActivity")
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(fallbackIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to return to kiosk app", e)
        }
    }

    /**
     * Enhanced key event handling with more comprehensive blocking
     */
    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        val kioskMgr = kioskManager
        if (kioskMgr == null || !kioskMgr.isKioskModeEnabled()) {
            return super.onKeyEvent(event)
        }

        event?.let {
            when (it.keyCode) {
                // Block navigation keys
                android.view.KeyEvent.KEYCODE_HOME,
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_APP_SWITCH -> {
                    Log.w(TAG, "Blocked navigation key: ${android.view.KeyEvent.keyCodeToString(it.keyCode)}")
                    return true
                }

                // Block system keys
                android.view.KeyEvent.KEYCODE_MENU,
                android.view.KeyEvent.KEYCODE_SEARCH -> {
                    Log.w(TAG, "Blocked system key: ${android.view.KeyEvent.keyCodeToString(it.keyCode)}")
                    return true
                }

                // Block potential escape sequences for device admin mode
                android.view.KeyEvent.KEYCODE_POWER -> {
                    if (!kioskMgr.isDeviceOwner()) {
                        // In device admin mode, be more restrictive with power button
                        Log.w(TAG, "Blocked power key in device admin mode")
                        return true
                    }
                }

                // Block volume keys if they could trigger system UI
                android.view.KeyEvent.KEYCODE_VOLUME_UP,
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // Allow volume control but block long press combinations
                    if (it.isLongPress) {
                        Log.w(TAG, "Blocked volume long press")
                        return true
                    }
                }
            }
        }

        return super.onKeyEvent(event)
    }

    /**
     * Handle gestures that might trigger system UI
     */
    override fun onGesture(gestureId: Int): Boolean {
        val kioskMgr = kioskManager
        if (kioskMgr != null && kioskMgr.isKioskModeEnabled()) {
            when (gestureId) {
                GESTURE_SWIPE_UP,
                GESTURE_SWIPE_DOWN,
                GESTURE_SWIPE_LEFT,
                GESTURE_SWIPE_RIGHT -> {
                    // Block gestures that might reveal system UI
                    Log.w(TAG, "Blocked system gesture: $gestureId")
                    return true
                }
            }
        }
        return super.onGesture(gestureId)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
        isServiceActive.set(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isServiceActive.set(false)

        // Clean up resources
        handler.removeCallbacksAndMessages(null)
        kioskManager = null
        devicePolicyManager = null
        adminComponent = null
    }

    /**
     * Get enhanced service status
     */
    fun getServiceStatus(): String {
        return buildString {
            appendLine("=== Enhanced Accessibility Service Status ===")
            appendLine("Service Active: ${isServiceActive.get()}")
            appendLine("Kiosk Mode: ${kioskManager?.isKioskModeEnabled() ?: "Unknown"}")
            appendLine("Device Owner: ${kioskManager?.isDeviceOwner() ?: "Unknown"}")
            val adminComp = adminComponent
            val isDeviceAdmin = if (adminComp != null) {
                devicePolicyManager?.isAdminActive(adminComp) == true
            } else {
                false
            }
            appendLine("Device Admin: ${if (isDeviceAdmin) "Active" else "Inactive"}")
            appendLine("Consecutive Blocks: $consecutiveBlockCount")
            appendLine("Last Action: ${if (lastActionTime > 0) System.currentTimeMillis() - lastActionTime else "Never"} ms ago")

            val mode = when {
                kioskManager?.isDeviceOwner() == true -> "Device Owner (Enhanced Control)"
//                devicePolicyManager?.isAdminActive(adminComponent) == true -> "Device Admin (Comprehensive Blocking)"
                adminComponent?.let { devicePolicyManager?.isAdminActive(it) } == true -> "Device Admin (Comprehensive Blocking)"
                else -> "No Admin Privileges"
            }
            appendLine("Operating Mode: $mode")
            appendLine("===============================================")
        }
    }
}