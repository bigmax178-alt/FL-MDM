package com.example.firebaselabelapp.subscription

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.firebaselabelapp.kiosk.KioskManager
import com.example.firebaselabelapp.update.UpdateManager
import com.google.firebase.Firebase
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Date
import kotlin.math.abs
import com.google.firebase.crashlytics.FirebaseCrashlytics

object SubscriptionManager {
    private const val TAG = "SubscriptionManager"

    private val crashlytics get() = FirebaseCrashlytics.getInstance()

    private val db get() = Firebase.firestore

    // 30 days in milliseconds
    private const val SUBSCRIPTION_VALIDITY_PERIOD = 30 * 24 * 60 * 60 * 1000L

    // Grace period for offline validation (7 days)
    private const val OFFLINE_GRACE_PERIOD = 7 * 24 * 60 * 60 * 1000L

    // --- TESTING VALUES ---
    // Uncomment these lines to test the banner flow quickly.
    // The yellow banner will show for 1 minute, then the red banner for 30 seconds.
//     internal const val PAYMENT_GRACE_PERIOD = 1 * 60 * 1000L      // 1 minute for yellow banner
//     internal const val RED_BANNER_DURATION = 30 * 1000L             // 30 seconds for red banner

    // --- PRODUCTION VALUES ---
    // Comment out the testing values and use these for the final app.
    internal const val PAYMENT_GRACE_PERIOD = 24 * 60 * 60 * 1000L // 24 hours (yellow banner)
    internal const val RED_BANNER_DURATION = 12 * 60 * 60 * 1000L  // 12 hours (red banner)


    data class SubscriptionStatus(
        val isValid: Boolean,
        val isDeviceBound: Boolean,
        val isPaid: Boolean,
        val daysUntilExpiry: Int,
        val lastValidationTime: Long,
        val deviceId: String,
        val errorMessage: String? = null,
        val redBanner: Boolean = false,
        val timeUntilDeactivation: Long = 0L
    )

    @IgnoreExtraProperties
    data class UserSubscription(
        @get:PropertyName("userId")
        val userId: String = "",

        // Kept for backward compatibility/primary device reference
        @get:PropertyName("deviceId")
        val deviceId: String = "",

        @get:PropertyName("lastPayment")
        val lastPayment: Long = 0L,

        @get:PropertyName("subscriptionExpiry")
        val subscriptionExpiry: Long = 0L,

        @get:PropertyName("isPaid")
        val isPaid: Boolean = false,

        @get:PropertyName("isActive")
        val isActive: Boolean = false,

        // Kept for backward compatibility
        @get:PropertyName("deviceBinding")
        val deviceBinding: DeviceBinding = DeviceBinding(),

        // NEW: List of allowed devices
        @get:PropertyName("boundDevices")
        val boundDevices: List<DeviceBinding> = emptyList(),

        // NEW: Max number of devices allowed (Set by Admin in Firestore)
        @get:PropertyName("maxDevices")
        val maxDevices: Int = 1,

        @get:PropertyName("paymentDueDate")
        val paymentDueDate: Long = 0L,

        @get:PropertyName("gracePeriodEnd")
        val gracePeriodEnd: Long = 0L,

        @get:PropertyName("appVersionName")
        val appVersionName: String = "",

        @get:PropertyName("appVersionCode")
        val appVersionCode: Long = 0L,

        @get:PropertyName("lastSeenAt")
        val lastSeenAt: Date = Date(),

        @get:PropertyName("isInKiosk")
        val isInKiosk: Boolean = false
    )

    data class DeviceBinding(
        @get:PropertyName("deviceId")
        val deviceId: String = "",
        @get:PropertyName("imei")
        val imei: String = "",
        @get:PropertyName("androidId")
        val androidId: String = "",
        @get:PropertyName("serialNumber")
        val serialNumber: String = "",
        @get:PropertyName("boundAt")
        val boundAt: Long = 0L,
        @get:PropertyName("deviceModel")
        val deviceModel: String = "",
        @get:PropertyName("deviceManufacturer")
        val deviceManufacturer: String = ""
    )

    /**
     * UPDATED: Collects device status using UpdateManager and sends it to Firestore.
     */
    suspend fun updateUserDeviceStatus(activity: Activity, userId: String, kioskManager: KioskManager) {
        if (userId.isBlank()) return

        Log.d(TAG, "Updating device status for user: $userId")
        try {
            val updateManager = UpdateManager(activity, db)
            val versionString = updateManager.getCurrentVersionNameAndCode()
            val versionName = versionString.substringBefore(" (").trim()
            val versionCode = versionString.substringAfter("(").substringBefore(")").toLongOrNull() ?: 0L
            val kioskStatus = kioskManager.isKioskModeEnabled()

            val statusUpdate = mapOf(
                "appVersionName" to versionName,
                "appVersionCode" to versionCode,
                "lastSeenAt" to Date(),
                "isInKiosk" to kioskStatus,
                "deviceModel" to Build.MODEL,
                "deviceManufacturer" to Build.MANUFACTURER
            )

            db.collection("subscriptions")
                .document(userId)
                .update(statusUpdate)
                .await()

            Log.d(TAG, "Successfully updated device status for user $userId with version $versionName")

        } catch (e: Exception) {
            Log.w(TAG, "Could not update user device status.", e)
            crashlytics.recordException(e)
        }
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        var deviceId = ""

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                deviceId = telephonyManager.imei ?: ""
            }

            if (deviceId.isEmpty()) {
                deviceId =
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                Log.d(TAG, "Using Android ID as device identifier")
            }

            if (deviceId.isEmpty() || deviceId == "9774d56d682e549c") {
                deviceId = try {
                    Build.getSerial()
                } catch (e: Exception) {
                    Build.SERIAL
                }
            }

            val combinedId = "$deviceId-${Build.MANUFACTURER}-${Build.MODEL}-${Build.BOARD}"
            return hashString(combinedId)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID", e)
            crashlytics.recordException(e)
            return hashString("${Build.MANUFACTURER}-${Build.MODEL}-${System.currentTimeMillis()}")
        }
    }

    private fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error hashing string", e)
            crashlytics.recordException(e)
            input
        }
    }

    private fun calculateBannerStatus(
        subscription: UserSubscription,
        currentTime: Long
    ): Pair<Boolean, Long> {
        if (subscription.isActive && !subscription.isPaid && subscription.paymentDueDate > 0) {
            val totalGracePeriodEnd = subscription.paymentDueDate + PAYMENT_GRACE_PERIOD + RED_BANNER_DURATION
            val timeUntilDeactivation = totalGracePeriodEnd - currentTime

            if (timeUntilDeactivation > 0) {
                return Pair(true, maxOf(0L, timeUntilDeactivation))
            }
        }
        return Pair(false, 0L)
    }

    private fun ensurePaymentDueDateIsTracked(
        context: Context,
        userId: String,
        subscription: UserSubscription,
        currentTime: Long
    ): UserSubscription {
        val prefs = context.getSharedPreferences("payment_tracking", Context.MODE_PRIVATE)
        val trackedUserId = prefs.getString("tracked_user_id", "")
        val localPaymentDueDate = prefs.getLong("local_payment_due_date", 0L)
        val wasPaidBefore = prefs.getBoolean("was_paid_before", true)

        if (!subscription.isPaid && subscription.paymentDueDate == 0L) {
            if (trackedUserId == userId && localPaymentDueDate > 0L) {
                Log.d(TAG, "Using locally tracked paymentDueDate: $localPaymentDueDate")
                return subscription.copy(
                    paymentDueDate = localPaymentDueDate,
                    gracePeriodEnd = localPaymentDueDate + PAYMENT_GRACE_PERIOD + RED_BANNER_DURATION
                )
            }

            if (wasPaidBefore || trackedUserId != userId) {
                Log.d(TAG, "isPaid changed to false, tracking locally with timestamp: $currentTime")
                prefs.edit {
                    putString("tracked_user_id", userId)
                    putLong("local_payment_due_date", currentTime)
                    putBoolean("was_paid_before", false)
                }

                return subscription.copy(
                    paymentDueDate = currentTime,
                    gracePeriodEnd = currentTime + PAYMENT_GRACE_PERIOD + RED_BANNER_DURATION
                )
            }
        }

        if (subscription.isPaid) {
            if (!wasPaidBefore && trackedUserId == userId) {
                Log.d(TAG, "isPaid is true, clearing local tracking")
                prefs.edit {
                    putBoolean("was_paid_before", true)
                    putLong("local_payment_due_date", 0L)
                }
            }
        }

        return subscription
    }

    suspend fun checkSubscriptionStatus(context: Context, userId: String): SubscriptionStatus {
        val deviceId = getDeviceId(context)
        val currentTime = System.currentTimeMillis()

        crashlytics.setUserId(userId)
        crashlytics.log("Checking subscription for user: $userId, device: $deviceId")

        Log.d(TAG, "Checking subscription for user: $userId, device: $deviceId")

        try {
            val subscriptionDoc = db.collection("subscriptions")
                .document(userId)
                .get(Source.SERVER)
                .await()

            if (subscriptionDoc.exists()) {
                var subscription = subscriptionDoc.toObject(UserSubscription::class.java)

                if (subscription != null) {
                    val needsUpdate = ensureFirestoreFields(userId, subscription)

                    if (needsUpdate) {
                        val updatedDoc = db.collection("subscriptions")
                            .document(userId)
                            .get(Source.SERVER)
                            .await()
                        subscription = updatedDoc.toObject(UserSubscription::class.java) ?: subscription
                    }

                    subscription = ensurePaymentDueDateIsTracked(context, userId, subscription, currentTime)

                    // --- UPDATED MULTI-DEVICE VALIDATION ---
                    // Check if current device is in the bound list OR matches the legacy binding
                    val isBoundInList = subscription.boundDevices.any { it.deviceId == deviceId }
                    // Fallback for migration: if list is empty, accept the single legacy binding
                    val isBoundLegacy = subscription.boundDevices.isEmpty() &&
                            subscription.deviceBinding.deviceId == deviceId

                    val isDeviceBound = isBoundInList || isBoundLegacy

                    if (!isDeviceBound) {
                        Log.w(
                            TAG,
                            "Device ID mismatch. Current: $deviceId not found in allowed devices."
                        )
                        crashlytics.log("Device ID mismatch. Current: $deviceId not in allowed list.")
                        return SubscriptionStatus(
                            isValid = false,
                            isDeviceBound = false,
                            isPaid = false,
                            daysUntilExpiry = 0,
                            lastValidationTime = currentTime,
                            deviceId = deviceId,
                            errorMessage = "Account is bound to a different device. Limit reached.",
                            redBanner = false,
                            timeUntilDeactivation = 0L
                        )
                    }
                    // ----------------------------------------

                    if (subscription.isActive && !subscription.isPaid && subscription.paymentDueDate > 0) {
                        val gracePeriodEnd = subscription.paymentDueDate + PAYMENT_GRACE_PERIOD + RED_BANNER_DURATION
                        if (currentTime >= gracePeriodEnd) {
                            Log.w(TAG, "Payment grace period expired")
                            crashlytics.log("Payment grace period expired for user $userId.")
                            return SubscriptionStatus(
                                isValid = false,
                                isDeviceBound = true,
                                isPaid = false,
                                daysUntilExpiry = 0,
                                lastValidationTime = currentTime,
                                deviceId = deviceId,
                                errorMessage = "Payment grace period expired. Please complete payment to continue using the app.",
                                redBanner = false,
                                timeUntilDeactivation = 0L
                            )
                        }
                    }

                    val isValid = subscription.isActive
                    val daysUntilExpiry =
                        ((subscription.subscriptionExpiry - currentTime) / (24 * 60 * 60 * 1000)).toInt()

                    val (showBanner, timeUntilDeactivation) = calculateBannerStatus(subscription, currentTime)

                    // Pass the CURRENT device ID to store locally, not the legacy one
                    storeValidationLocally(context, subscription, currentTime, deviceId)

                    Log.d(TAG, "Subscription valid: $isValid, showBanner: $showBanner, timeUntil: $timeUntilDeactivation")
                    crashlytics.log("SubManager: Online check result: isValid=$isValid, isPaid=${subscription.isPaid}, redBanner=$showBanner, error=${if (!isValid) "Subscription expired" else null}")
                    return SubscriptionStatus(
                        isValid = isValid,
                        isDeviceBound = true,
                        isPaid = subscription.isPaid,
                        daysUntilExpiry = maxOf(0, daysUntilExpiry),
                        lastValidationTime = currentTime,
                        deviceId = deviceId,
                        errorMessage = if (!isValid) "Subscription expired" else null,
                        redBanner = showBanner,
                        timeUntilDeactivation = timeUntilDeactivation
                    )
                }
            } else {
                Log.w(TAG, "No subscription found for user: $userId")
                crashlytics.log("No subscription document found for user: $userId")
                return SubscriptionStatus(
                    isValid = false,
                    isDeviceBound = false,
                    isPaid = false,
                    daysUntilExpiry = 0,
                    lastValidationTime = currentTime,
                    deviceId = deviceId,
                    errorMessage = "No active subscription found",
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }

        } catch (e: Exception) {
            if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE) {
                Log.d(TAG, "Client is offline, falling back to offline validation")
                crashlytics.log("Offline validation fallback for user $userId. Reason: ${e.message}")
            } else {
                Log.e(TAG, "Error checking subscription online", e)
                crashlytics.recordException(e)
            }
            crashlytics.log("SubManager: Online check failed. Falling back to offline. Error: ${e.message}")
            return checkSubscriptionOffline(context, userId, deviceId, currentTime)
        }
        crashlytics.log("checkSubscriptionStatus fell through to unknown error for user $userId.")
        return SubscriptionStatus(
            isValid = false,
            isDeviceBound = false,
            isPaid = false,
            daysUntilExpiry = 0,
            lastValidationTime = currentTime,
            deviceId = deviceId,
            errorMessage = "Unknown error occurred",
            redBanner = false,
            timeUntilDeactivation = 0L
        )
    }

    private suspend fun ensureFirestoreFields(
        userId: String,
        subscription: UserSubscription
    ): Boolean {
        try {
            val updates = mutableMapOf<String, Any>()
            val docRef = db.collection("subscriptions").document(userId)
            val currentDoc = docRef.get().await()

            if (!currentDoc.contains("isActive")) {
                updates["isActive"] = true
                Log.d(TAG, "Adding missing field: isActive = true")
            }

            if (!currentDoc.contains("isPaid")) {
                updates["isPaid"] = true
                Log.d(TAG, "Adding missing field: isPaid = true")
            }

            // Note: We don't force-add 'maxDevices' here, defaults handle it.
            // But you could add it if you wanted defaults explicit in DB.

            if (updates.isNotEmpty()) {
                docRef.update(updates).await()
                Log.d(TAG, "Updated Firestore document with missing fields for user: $userId")
                return true
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring Firestore fields", e)
            crashlytics.recordException(e)
            return false
        }
    }

    private fun checkSubscriptionOffline(
        context: Context,
        userId: String,
        deviceId: String,
        currentTime: Long
    ): SubscriptionStatus {
        Log.d(TAG, "Performing offline subscription validation")
        crashlytics.log("Performing offline validation for user $userId.")

        val prefs = context.getSharedPreferences("subscription_cache", Context.MODE_PRIVATE)
        val storedUserId = prefs.getString("user_id", "")
        val storedDeviceId = prefs.getString("device_id", "")
        val lastValidation = prefs.getLong("last_validation", 0L)
        val lastValidationUptime = prefs.getLong("last_validation_uptime", 0L)
        val subscriptionExpiry = prefs.getLong("subscription_expiry", 0L)
        val isActive = prefs.getBoolean("is_active", false)
        val isPaid = prefs.getBoolean("is_paid", false)
        val paymentDueDate = prefs.getLong("payment_due_date", 0L)

        val paymentPrefs = context.getSharedPreferences("payment_tracking", Context.MODE_PRIVATE)
        val localPaymentDueDate = paymentPrefs.getLong("local_payment_due_date", 0L)

        val effectivePaymentDueDate = if (paymentDueDate > 0L) paymentDueDate else localPaymentDueDate

        if (storedUserId != userId || storedDeviceId != deviceId) {
            crashlytics.log("Offline validation failed: Mismatch. StoredUser: $storedUserId, User: $userId, StoredDevice: $storedDeviceId, Device: $deviceId")
            return SubscriptionStatus(
                isValid = false,
                isDeviceBound = false,
                isPaid = false,
                daysUntilExpiry = 0,
                lastValidationTime = currentTime,
                deviceId = deviceId,
                errorMessage = "No cached subscription data or device mismatch",
                redBanner = false,
                timeUntilDeactivation = 0L
            )
        }

        val elapsedUpTime = android.os.SystemClock.elapsedRealtime() - lastValidationUptime
        if (elapsedUpTime > OFFLINE_GRACE_PERIOD) {
            crashlytics.log("Offline grace period expired for user $userId. Elapsed: $elapsedUpTime ms")
            return SubscriptionStatus(
                isValid = false,
                isDeviceBound = true,
                isPaid = false,
                daysUntilExpiry = 0,
                lastValidationTime = 0,
                deviceId = deviceId,
                errorMessage = "Offline grace period expired. Please connect to internet to validate subscription.",
                redBanner = false,
                timeUntilDeactivation = 0L
            )
        }

        if (isActive && !isPaid && effectivePaymentDueDate > 0) {
            val totalGracePeriodEnd = effectivePaymentDueDate + PAYMENT_GRACE_PERIOD + RED_BANNER_DURATION
            val timeUntilDeactivation = totalGracePeriodEnd - currentTime

            if (timeUntilDeactivation <= 0) {
                crashlytics.log("Offline payment grace period expired for user $userId.")
                return SubscriptionStatus(
                    isValid = false,
                    isDeviceBound = true,
                    isPaid = false,
                    daysUntilExpiry = 0,
                    lastValidationTime = lastValidation,
                    deviceId = deviceId,
                    errorMessage = "Payment grace period expired (offline validation)",
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }

            return SubscriptionStatus(
                isValid = isActive,
                isDeviceBound = true,
                isPaid = isPaid,
                daysUntilExpiry = ((subscriptionExpiry - currentTime) / (24 * 60 * 60 * 1000)).toInt(),
                lastValidationTime = lastValidation,
                deviceId = deviceId,
                errorMessage = if (!isActive) "Subscription expired (offline validation)" else null,
                redBanner = true,
                timeUntilDeactivation = maxOf(0L, timeUntilDeactivation)
            )
        }

        val daysUntilExpiry = ((subscriptionExpiry - currentTime) / (24 * 60 * 60 * 1000)).toInt()

        return SubscriptionStatus(
            isValid = isActive,
            isDeviceBound = true,
            isPaid = isPaid,
            daysUntilExpiry = maxOf(0, daysUntilExpiry),
            lastValidationTime = lastValidation,
            deviceId = deviceId,
            errorMessage = if (!isActive) "Subscription expired (offline validation)" else null,
            redBanner = false,
            timeUntilDeactivation = 0L
        )
    }

    private fun storeValidationLocally(
        context: Context,
        subscription: UserSubscription,
        validationTime: Long,
        currentDeviceId: String
    ) {
        val prefs = context.getSharedPreferences("subscription_cache", Context.MODE_PRIVATE)
        val validationUptime = android.os.SystemClock.elapsedRealtime()
        prefs.edit {
            putString("user_id", subscription.userId)
            // Use the specific ID of the device we just validated
            putString("device_id", currentDeviceId)
            putLong("last_validation", validationTime)
            putLong("last_validation_uptime", validationUptime)
            putLong("subscription_expiry", subscription.subscriptionExpiry)
            putBoolean("is_active", subscription.isActive)
            putBoolean("is_paid", subscription.isPaid)
            putLong("last_payment", subscription.lastPayment)
            putLong("payment_due_date", subscription.paymentDueDate)
            putLong("grace_period_end", subscription.gracePeriodEnd)
        }

        Log.d(TAG, "Stored subscription validation locally (isPaid=${subscription.isPaid}, paymentDueDate=${subscription.paymentDueDate})")
    }

    @SuppressLint("HardwareIds")
    suspend fun bindDeviceToAccount(context: Context, userId: String): Boolean {
        val deviceId = getDeviceId(context)
        val currentTime = System.currentTimeMillis()
        crashlytics.setUserId(userId)
        crashlytics.log("Binding device $deviceId to user $userId")

        Log.d(TAG, "Binding device $deviceId to user $userId")

        try {
            // 1. Create the new device binding object
            val newDeviceBinding = DeviceBinding(
                deviceId = deviceId,
                imei = getIMEI(context),
                androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ),
                serialNumber = try {
                    Build.getSerial()
                } catch (e: Exception) {
                    Build.SERIAL
                },
                boundAt = currentTime,
                deviceModel = Build.MODEL,
                deviceManufacturer = Build.MANUFACTURER
            )

            // 2. Fetch current subscription
            val subscriptionDoc = db.collection("subscriptions")
                .document(userId)
                .get()
                .await()

            val currentSubscription = if (subscriptionDoc.exists()) {
                subscriptionDoc.toObject(UserSubscription::class.java)
            } else {
                null
            }

            // 3. Prepare the list of devices
            val updatedDeviceList = if (currentSubscription != null) {
                // Start with existing list
                val list = currentSubscription.boundDevices.toMutableList()

                // MIGRATION: If list is empty but old legacy binding exists, add it first
                if (list.isEmpty() && currentSubscription.deviceBinding.deviceId.isNotEmpty()) {
                    list.add(currentSubscription.deviceBinding)
                }
                list
            } else {
                mutableListOf()
            }

            // 4. Logic: Add new, Update existing, or Reject if full
            val existingIndex = updatedDeviceList.indexOfFirst { it.deviceId == deviceId }

            val finalSubscription = if (currentSubscription == null) {
                // Case: Brand new user/subscription
                UserSubscription(
                    userId = userId,
                    deviceId = deviceId, // Legacy field
                    deviceBinding = newDeviceBinding, // Legacy field
                    boundDevices = listOf(newDeviceBinding), // New field
                    maxDevices = 1, // Default to 1
                    lastPayment = currentTime,
                    subscriptionExpiry = currentTime + SUBSCRIPTION_VALIDITY_PERIOD,
                    isActive = true,
                    isPaid = true,
                    paymentDueDate = 0L,
                    gracePeriodEnd = 0L
                )
            } else {
                if (existingIndex != -1) {
                    // Device already exists -> Update it (e.g., refresh timestamp/info)
                    updatedDeviceList[existingIndex] = newDeviceBinding
                } else {
                    // Device is new -> Check limits
                    if (updatedDeviceList.size < currentSubscription.maxDevices) {
                        updatedDeviceList.add(newDeviceBinding)
                    } else {
                        Log.e(TAG, "Device limit reached. Max: ${currentSubscription.maxDevices}")
                        crashlytics.log("Failed to bind device: Limit reached (${currentSubscription.maxDevices})")
                        return false
                    }
                }

                // Return updated subscription object
                currentSubscription.copy(
                    boundDevices = updatedDeviceList,
                    // Optionally update legacy fields to point to "last bound"
                    deviceBinding = newDeviceBinding,
                    deviceId = deviceId
                )
            }

            // 5. Save to Firestore
            db.collection("subscriptions")
                .document(userId)
                .set(finalSubscription)
                .await()

            Log.d(TAG, "Device successfully bound to account")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error binding device to account", e)
            crashlytics.log("Failed to bind device $deviceId for user $userId.")
            crashlytics.recordException(e)
            return false
        }
    }

    @SuppressLint("HardwareIds")
    private fun getIMEI(context: Context): String {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                telephonyManager.imei ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IMEI", e)
            crashlytics.recordException(e)
            ""
        }
    }

    fun clearSubscriptionCache(context: Context) {
        val prefs = context.getSharedPreferences("subscription_cache", Context.MODE_PRIVATE)
        prefs.edit { clear() }
        Log.d(TAG, "Cleared subscription cache")
    }

    suspend fun activateSubscription(userId: String, paymentTimestamp: Long): Boolean {
        try {
            val subscriptionRef = db.collection("subscriptions").document(userId)

            subscriptionRef.update(
                mapOf(
                    "isActive" to true,
                    "isPaid" to true,
                    "lastPayment" to paymentTimestamp,
                    "subscriptionExpiry" to paymentTimestamp + SUBSCRIPTION_VALIDITY_PERIOD,
                    "paymentDueDate" to 0L,
                    "gracePeriodEnd" to 0L
                )
            ).await()

            Log.d(TAG, "Subscription activated for user: $userId")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error activating subscription", e)
            crashlytics.setUserId(userId)
            crashlytics.log("Failed to activate subscription for user $userId.")
            crashlytics.recordException(e)
            return false
        }
    }

    suspend fun checkSubscriptionStatusDetailed(
        context: Context,
        userId: String
    ): SubscriptionStatus {
        crashlytics.setUserId(userId)
        crashlytics.log("Detailed subscription check for user $userId.")
        return checkSubscriptionStatus(context, userId)
    }

    suspend fun revalidateSubscription(context: Context, userId: String): SubscriptionStatus {
        val deviceId = getDeviceId(context)
        val currentTime = System.currentTimeMillis()
        crashlytics.setUserId(userId)
        crashlytics.log("Revalidating subscription for user: $userId")
        Log.d(TAG, "Revalidating subscription for user: $userId")

        return try {
            val subscriptionDoc = db.collection("subscriptions")
                .document(userId)
                .get(Source.SERVER)
                .await()

            if (subscriptionDoc.exists()) {
                var subscription = subscriptionDoc.toObject(UserSubscription::class.java)

                if (subscription != null) {
                    val needsUpdate = ensureFirestoreFields(userId, subscription)

                    if (needsUpdate) {
                        val updatedDoc = db.collection("subscriptions")
                            .document(userId)
                            .get(Source.SERVER)
                            .await()
                        subscription = updatedDoc.toObject(UserSubscription::class.java) ?: subscription
                    }

                    subscription = ensurePaymentDueDateIsTracked(context, userId, subscription, currentTime)

                    // Pass current deviceId to store
                    storeValidationLocally(context, subscription, currentTime, deviceId)

                    // --- UPDATED MULTI-DEVICE VALIDATION FOR REVALIDATION ---
                    val isBoundInList = subscription.boundDevices.any { it.deviceId == deviceId }
                    val isBoundLegacy = subscription.boundDevices.isEmpty() &&
                            subscription.deviceBinding.deviceId == deviceId

                    if (!isBoundInList && !isBoundLegacy) {
                        SubscriptionStatus(
                            isValid = false,
                            isDeviceBound = false,
                            isPaid = false,
                            daysUntilExpiry = 0,
                            lastValidationTime = currentTime,
                            deviceId = deviceId,
                            errorMessage = "Account is bound to a different device",
                            redBanner = false,
                            timeUntilDeactivation = 0L
                        )
                    } else {
                        if (subscription.isActive && !subscription.isPaid && subscription.paymentDueDate > 0) {
                            val gracePeriodEnd = subscription.paymentDueDate + PAYMENT_GRACE_PERIOD + RED_BANNER_DURATION
                            if (currentTime >= gracePeriodEnd) {
                                return SubscriptionStatus(
                                    isValid = false,
                                    isDeviceBound = true,
                                    isPaid = false,
                                    daysUntilExpiry = 0,
                                    lastValidationTime = currentTime,
                                    deviceId = deviceId,
                                    errorMessage = "Payment grace period expired. Please complete payment.",
                                    redBanner = false,
                                    timeUntilDeactivation = 0L
                                )
                            }
                        }

                        val isValid = subscription.isActive
                        val daysUntilExpiry =
                            ((subscription.subscriptionExpiry - currentTime) / (24 * 60 * 60 * 1000)).toInt()

                        val (showBanner, timeUntilDeactivation) = calculateBannerStatus(subscription, currentTime)

                        SubscriptionStatus(
                            isValid = isValid,
                            isDeviceBound = true,
                            isPaid = subscription.isPaid,
                            daysUntilExpiry = maxOf(0, daysUntilExpiry),
                            lastValidationTime = currentTime,
                            deviceId = deviceId,
                            errorMessage = if (!isValid) "Subscription expired" else null,
                            redBanner = showBanner,
                            timeUntilDeactivation = timeUntilDeactivation
                        )
                    }
                } else {
                    SubscriptionStatus(
                        isValid = false,
                        isDeviceBound = false,
                        isPaid = false,
                        daysUntilExpiry = 0,
                        lastValidationTime = currentTime,
                        deviceId = deviceId,
                        errorMessage = "Could not parse subscription data",
                        redBanner = false,
                        timeUntilDeactivation = 0L
                    )
                }
            } else {
                SubscriptionStatus(
                    isValid = false,
                    isDeviceBound = false,
                    isPaid = false,
                    daysUntilExpiry = 0,
                    lastValidationTime = currentTime,
                    deviceId = deviceId,
                    errorMessage = "No subscription found",
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error revalidating subscription", e)
            crashlytics.log("Failed to revalidate subscription for user $userId.")
            crashlytics.recordException(e)
            SubscriptionStatus(
                isValid = false,
                isDeviceBound = false,
                isPaid = false,
                daysUntilExpiry = 0,
                lastValidationTime = 0,
                deviceId = deviceId,
                errorMessage = "Could not connect to server",
                redBanner = false,
                timeUntilDeactivation = 0L
            )
        }
    }

    suspend fun detectTimeManipulation(): Boolean {
        try {
            val serverTimeDoc = db.collection("system")
                .document("time")
                .get()
                .await()

            val serverTime = serverTimeDoc.getLong("timestamp") ?: System.currentTimeMillis()
            val localTime = System.currentTimeMillis()

            val timeDifference = abs(serverTime - localTime)
            val isTimeManipulated = timeDifference > (5 * 60 * 1000)

            if (isTimeManipulated) {
                Log.w(
                    TAG,
                    "Potential time manipulation detected. Server: $serverTime, Local: $localTime, Diff: $timeDifference ms"
                )
                crashlytics.log("Potential time manipulation detected. Diff: $timeDifference ms")
                crashlytics.setCustomKey("time_diff_ms", timeDifference)
            }
            return isTimeManipulated

        } catch (e: Exception) {
            if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE
            ) {
                Log.d(TAG, "Could not check server time: Client is offline.")
                crashlytics.log("Offline, skipping time manipulation check.")
            } else {
                Log.e(TAG, "Error checking server time", e)
                crashlytics.recordException(e)
            }
            return false
        }
    }
}