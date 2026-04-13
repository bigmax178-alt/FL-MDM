package com.example.firebaselabelapp.subscription

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.auth.AuthManager.SessionInvalidReason

object SessionValidator {
    private const val TAG = "SessionValidator"

    const val ACTION_GRACE_PERIOD_EXPIRED = "com.example.firebaselabelapp.GRACE_PERIOD_EXPIRED"
    const val ACTION_SUBSCRIPTION_EXPIRED = "com.example.firebaselabelapp.SUBSCRIPTION_EXPIRED"

    // The validator no longer runs its own loop. It's triggered externally.
    suspend fun checkSession(activity: Activity) {
        Log.d(TAG, "Performing session validation check...")

        val validationResult = AuthManager.validateCurrentSessionDetailed(activity)

        if (!validationResult.isValid) {
            when (validationResult.reason) {
                SessionInvalidReason.OFFLINE_GRACE_PERIOD_EXPIRED -> {
                    Log.w(TAG, "Offline grace period expired during check.")
                    val intent = Intent(ACTION_GRACE_PERIOD_EXPIRED).apply {
                        putExtra("error_message", validationResult.errorMessage)
                    }
                    activity.sendBroadcast(intent)
                }

                SessionInvalidReason.SUBSCRIPTION_EXPIRED,
                SessionInvalidReason.DEVICE_NOT_BOUND,
                SessionInvalidReason.TIME_MANIPULATION,
                SessionInvalidReason.OTHER,
                null -> {
                    Log.w(TAG, "Critical session validation failure: ${validationResult.reason}")
                    val intent = Intent(ACTION_SUBSCRIPTION_EXPIRED).apply {
                        putExtra("reason", validationResult.reason?.name)
                        putExtra("error_message", validationResult.errorMessage)
                    }
                    activity.sendBroadcast(intent)

                    AuthManager.forceLogoutDueToSubscription(
                        activity,
                        validationResult.errorMessage ?: "Session validation failed"
                    )
                }
            }
        } else {
            Log.d(TAG, "Session validation passed")
        }
    }
}