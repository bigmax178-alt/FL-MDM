package com.example.firebaselabelapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.example.firebaselabelapp.kiosk.KioskManager

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val updatedPackage = intent.getStringExtra("updated_package")
        val kioskManager = KioskManager(context)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.d("InstallStatusReceiver", "Install SUCCESS for: $updatedPackage")

                if (updatedPackage != context.packageName) {
                    // CASE A: Remote App Updated
                    // Our app is still running. We must manually Close the Gate.
                    Log.d("InstallStatusReceiver", "External app updated. Closing Security Gate.")
                    kioskManager.setInstallRestrictions(true)
                } else {
                    // CASE B: This App Updated
                    // We are about to be killed and restarted.
                    // The re-lock will happen in MainActivity.onCreate().
                    Log.d("InstallStatusReceiver", "Self-update successful. Restarting...")
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }
            }
            else -> {
                // FAILURE
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("InstallStatusReceiver", "Install FAILED. Status: $status, Msg: $message")

                // Always re-lock on failure to ensure security
                if (kioskManager.isDeviceOwner()) {
                    Log.w("InstallStatusReceiver", "Closing Security Gate after failure.")
                    kioskManager.setInstallRestrictions(true)
                }
            }
        }
    }
}