package com.example.firebaselabelapp.kiosk

import android.app.Activity
import android.os.Bundle

/**
 * Activity required for Android 10+ DPC provisioning.
 * Finalizes the setup process by confirming policy compliance.
 */
class AdminPolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Finalize provisioning
        setResult(RESULT_OK)
        finish()
    }
}
