package com.example.firebaselabelapp.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/**
 * Activity required for Android 10+ DPC provisioning.
 * Tells the system that this app supports Device Owner (Fully Managed) mode.
 */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val resultIntent = Intent()
        resultIntent.putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        )
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
