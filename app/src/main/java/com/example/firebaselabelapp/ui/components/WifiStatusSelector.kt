package com.example.firebaselabelapp.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.text.replace

private fun getCurrentSsid(context: Context): String {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork ?: return "Не подключено"
    val networkCapabilities =
        connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "Не подключено"

    return if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Suppress warning for connectionInfo as it's handled for different SDKs
        @Suppress("DEPRECATION")
        val ssid = wifiManager.connectionInfo.ssid
        if (ssid != null && ssid != "<unknown ssid>") {
            ssid.replace("\"", "")
        } else {
            "Подключено (SSID скрыт)"
        }
    } else {
        "Не подключено"
    }
}

@Composable
fun WifiStatusSelector() {
    val context = LocalContext.current
    // State to track if the app has the required permission
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // State for the current SSID
    var currentSsid by remember { mutableStateOf("...") }

    // Function to update SSID, now checks for permission first
    val updateSsid = {
        currentSsid = if (hasPermission) {
            getCurrentSsid(context)
        } else {
            "Требуется разрешение на геолокацию" // Location permission required
        }
    }

    // This launcher requests the permission and updates the 'hasPermission' state
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

    // Update the SSID when permission status changes or on resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateSsid()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial update
        updateSsid()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { /* The resume observer handles the UI update */ }
    )

    // --- UI Logic ---
    if (hasPermission) {
        // If permission is granted, show the Wi-Fi status as before
        Row(
            modifier = Modifier.clickable { wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Статус Wi-Fi: ")
            Text(
                text = currentSsid,
                fontWeight = FontWeight.Bold,
                color = if (currentSsid == "Не подключено" || currentSsid.contains("Требуется")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Изменить Wi-Fi",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // If permission is NOT granted, show an explanation and a button to grant it
        Column {
            Text(
                "Чтобы отобразить название Wi-Fi, приложению требуется разрешение на доступ к геолокации.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("Дать разрешение")
            }
        }
    }
}