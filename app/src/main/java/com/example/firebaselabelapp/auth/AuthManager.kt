package com.example.firebaselabelapp.auth

import android.app.Activity // IMPORT CHANGED: From Context to Activity
import android.content.Context
import com.example.firebaselabelapp.subscription.SubscriptionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import com.example.firebaselabelapp.MyApplication

object AuthManager {
    private const val TAG = "AuthManager"
    private val auth: FirebaseAuth by  lazy { Firebase.auth }

    data class AuthResult(
        val success: Boolean,
        val message: String?,
        val needsSubscription: Boolean = false,
        val subscriptionStatus: SubscriptionManager.SubscriptionStatus? = null
    )

    data class SessionValidationResult(
        val isValid: Boolean,
        val isPaid: Boolean,
        val reason: SessionInvalidReason? = null,
        val errorMessage: String? = null,
        val redBanner: Boolean = false,
        val timeUntilDeactivation: Long = 0L
    )

    enum class SessionInvalidReason {
        OFFLINE_GRACE_PERIOD_EXPIRED,
        SUBSCRIPTION_EXPIRED,
        DEVICE_NOT_BOUND,
        TIME_MANIPULATION,
        OTHER
    }

    suspend fun isLoggedInWithValidSubscription(activity: Activity): Boolean { // PARAMETER CHANGED
        val user = auth.currentUser ?: return false

        try {
            val subscriptionStatus = SubscriptionManager.checkSubscriptionStatus(activity, user.uid)
            if (!subscriptionStatus.isValid) {
                Log.d(TAG, "Invalid subscription: ${subscriptionStatus.errorMessage}")
                return false
            }

            if (!subscriptionStatus.isDeviceBound) {
                Log.d(TAG, "Device not bound to account")
                return false
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error checking subscription status", e)
            return false
        }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null
    fun getUid(): String? = auth.currentUser?.uid

    fun loginWithSubscriptionCheck(
        activity: Activity, // PARAMETER CHANGED
        email: String,
        password: String,
        onResult: (AuthResult) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val isTimeManipulated = try {
                                    SubscriptionManager.detectTimeManipulation()
                                } catch (e: Exception) {
                                    Log.w(TAG, "Couldn't check time manipulation", e)
                                    false
                                }

                                if (isTimeManipulated) {
                                    auth.signOut()
                                    onResult(
                                        AuthResult(
                                            success = false,
                                            message = "System time has been modified. Please ensure your device time is correct.",
                                            needsSubscription = false
                                        )
                                    )
                                    return@launch
                                }

                                val subscriptionStatus =
                                    SubscriptionManager.checkSubscriptionStatus(activity, user.uid) // PARAMETER CHANGED

                                when {
                                    !subscriptionStatus.isDeviceBound -> {
                                        val bindResult = SubscriptionManager.bindDeviceToAccount(
                                            activity, // PARAMETER CHANGED
                                            user.uid
                                        )
                                        if (!bindResult) {
                                            auth.signOut()
                                            onResult(
                                                AuthResult(
                                                    success = false,
                                                    message = "Failed to bind device to account. Please try again.",
                                                    needsSubscription = false
                                                )
                                            )
                                            return@launch
                                        }

                                        val newStatus = SubscriptionManager.checkSubscriptionStatus(
                                            activity, // PARAMETER CHANGED
                                            user.uid
                                        )
                                        if (!newStatus.isValid) {
                                            onResult(
                                                AuthResult(
                                                    success = false,
                                                    message = "Account bound to device, but subscription is not active. Please purchase a subscription.",
                                                    needsSubscription = true,
                                                    subscriptionStatus = newStatus
                                                )
                                            )
                                            return@launch
                                        }
                                    }

                                    !subscriptionStatus.isValid -> {
                                        onResult(
                                            AuthResult(
                                                success = false,
                                                message = subscriptionStatus.errorMessage
                                                    ?: "Subscription is not active",
                                                needsSubscription = true,
                                                subscriptionStatus = subscriptionStatus
                                            )
                                        )
                                        return@launch
                                    }

                                    else -> {
                                        onResult(
                                            AuthResult(
                                                success = true,
                                                message = null,
                                                needsSubscription = false,
                                                subscriptionStatus = subscriptionStatus
                                            )
                                        )
                                        // NEW: Update device status on successful login
                                        CoroutineScope(Dispatchers.IO).launch {
                                            // Safely? get KioskManager from Activity's application context
                                            val myApp = activity.applicationContext as MyApplication
                                            SubscriptionManager.updateUserDeviceStatus(activity, user.uid, myApp.kioskManager)
                                        }
                                        return@launch
                                    }
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "Error during subscription check", e)
                                auth.signOut()
                                onResult(
                                    AuthResult(
                                        success = false,
                                        message = "Failed to verify subscription. Please check your internet connection and try again.",
                                        needsSubscription = false
                                    )
                                )
                            }
                        }
                    } else {
                        onResult(
                            AuthResult(
                                success = false,
                                message = "Authentication failed - no user data",
                                needsSubscription = false
                            )
                        )
                    }
                } else {
                    onResult(
                        AuthResult(
                            success = false,
                            message = task.exception?.message,
                            needsSubscription = false
                        )
                    )
                }
            }
    }

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    fun logout(context: Context) {
        SubscriptionManager.clearSubscriptionCache(context)
        auth.signOut()
        Log.d(TAG, "User logged out and caches cleared")
    }

    fun forceLogoutDueToSubscription(context: Context, reason: String) {
        Log.w(TAG, "Force logout due to subscription issue: $reason")
        logout(context)
    }

    suspend fun validateCurrentSessionDetailed(activity: Activity): SessionValidationResult { // PARAMETER CHANGED
        val user = auth.currentUser ?: return SessionValidationResult(
            isValid = false,
            isPaid = false,
            reason = SessionInvalidReason.OTHER,
            errorMessage = "User not logged in",
            redBanner = false,
            timeUntilDeactivation = 0L
        )

        try {
            val isTimeManipulated = try {
                SubscriptionManager.detectTimeManipulation()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't check time manipulation during session validation", e)
                false
            }

            if (isTimeManipulated) {
                Log.w(TAG, "Time manipulation detected during session validation")
                return SessionValidationResult(
                    isValid = false,
                    isPaid = false,
                    reason = SessionInvalidReason.TIME_MANIPULATION,
                    errorMessage = "Time manipulation detected",
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }

            val subscriptionStatus =
                SubscriptionManager.checkSubscriptionStatusDetailed(activity, user.uid) // PARAMETER CHANGED

            if (subscriptionStatus.errorMessage?.contains("Offline grace period expired") == true) {
                return SessionValidationResult(
                    isValid = false,
                    isPaid = false,
                    reason = SessionInvalidReason.OFFLINE_GRACE_PERIOD_EXPIRED,
                    errorMessage = subscriptionStatus.errorMessage,
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }

            if (subscriptionStatus.errorMessage?.contains("Payment grace period expired") == true) {
                return SessionValidationResult(
                    isValid = false,
                    isPaid = false,
                    reason = SessionInvalidReason.SUBSCRIPTION_EXPIRED,
                    errorMessage = subscriptionStatus.errorMessage,
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }

            if (!subscriptionStatus.isValid) {
                Log.w(
                    TAG,
                    "Invalid subscription during session validation: ${subscriptionStatus.errorMessage}"
                )
                return SessionValidationResult(
                    isValid = false,
                    isPaid = false,
                    reason = SessionInvalidReason.SUBSCRIPTION_EXPIRED,
                    errorMessage = subscriptionStatus.errorMessage ?: "Invalid subscription",
                    redBanner = subscriptionStatus.redBanner,
                    timeUntilDeactivation = subscriptionStatus.timeUntilDeactivation
                )
            }

            if (!subscriptionStatus.isDeviceBound) {
                Log.w(TAG, "Device not bound during session validation.")
                return SessionValidationResult(
                    isValid = false,
                    isPaid = false,
                    reason = SessionInvalidReason.DEVICE_NOT_BOUND,
                    errorMessage = "Device not bound to this account",
                    redBanner = false,
                    timeUntilDeactivation = 0L
                )
            }

            return SessionValidationResult(
                isValid = true,
                isPaid = subscriptionStatus.isPaid,
                redBanner = subscriptionStatus.redBanner,
                timeUntilDeactivation = subscriptionStatus.timeUntilDeactivation
            ).also {
                // NEW: Update device status on successful session validation
                CoroutineScope(Dispatchers.IO).launch {
                    // Safely get KioskManager from the Activity's application context
                    val myApp = activity.applicationContext as MyApplication
                    SubscriptionManager.updateUserDeviceStatus(activity, user.uid, myApp.kioskManager)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error validating session", e)
            return SessionValidationResult(
                isValid = false,
                isPaid = false,
                reason = SessionInvalidReason.OTHER,
                errorMessage = "An error occurred while validating the session",
                redBanner = false,
                timeUntilDeactivation = 0L
            )
        }
    }

    suspend fun validateCurrentSession(activity: Activity): Boolean { // PARAMETER CHANGED
        val result = validateCurrentSessionDetailed(activity)
        if (!result.isValid) {
            forceLogoutDueToSubscription(
                activity, // PARAMETER CHANGED
                result.errorMessage ?: "Session validation failed"
            )
        }
        return result.isValid
    }
}