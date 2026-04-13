package com.example.firebaselabelapp.subscription

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.firebaselabelapp.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * DEPRECATED: This service is kept for compatibility but WorkManager is preferred.
 * Background service for periodic subscription validation.
 */
@Deprecated("Use SubscriptionValidationWorker with WorkManager instead.")
class SubscriptionValidationService : Service() {

    companion object {
        private const val TAG = "SubscriptionService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Subscription validation service started")
        validateSubscription()
        return START_NOT_STICKY
    }

    private fun validateSubscription() {
        serviceScope.launch {
            try {
                val uid = AuthManager.getUid()
                if (uid == null) {
                    Log.d(TAG, "No user logged in, stopping service.")
                    return@launch
                }

                Log.d(TAG, "Validating session in background service for user: $uid")
                // Use a background-safe validation method from SubscriptionManager
                val status = SubscriptionManager.checkSubscriptionStatusDetailed(
                    this@SubscriptionValidationService,
                    uid
                )

                if (!status.isValid) {
                    Log.w(
                        TAG,
                        "Subscription validation failed in service. Reason: ${status.errorMessage}"
                    )

                    val intent = Intent(SessionValidator.ACTION_SUBSCRIPTION_EXPIRED).apply {
                        putExtra("error_message", status.errorMessage)
                    }
                    sendBroadcast(intent)

                    Log.d(TAG, "Logging out user due to background service validation failure.")
                    // Perform a background-safe logout
                    AuthManager.logout(this@SubscriptionValidationService)
                } else {
                    Log.d(TAG, "Background session validation passed in service.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background subscription validation", e)
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Subscription validation service destroyed")
    }
}


/**
 * Broadcast receiver for subscription expired events. Can be used for logging or analytics.
 */
class SubscriptionExpiredReceiver : android.content.BroadcastReceiver() {
    companion object {
        private const val TAG = "SubscriptionExpiredReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // This receiver now listens for more specific actions
        when (intent.action) {
            SessionValidator.ACTION_SUBSCRIPTION_EXPIRED -> {
                val reason = intent.getStringExtra("reason")
                Log.w(TAG, "Received subscription expired broadcast. Reason: $reason")
            }

            SessionValidator.ACTION_GRACE_PERIOD_EXPIRED -> {
                Log.w(TAG, "Received grace period expired broadcast.")
            }
        }
    }
}

