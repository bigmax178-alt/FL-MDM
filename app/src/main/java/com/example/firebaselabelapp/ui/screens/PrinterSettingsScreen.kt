// Fixed PrinterSettingsScreen.kt - Centered Text in Split Button
package com.example.firebaselabelapp.ui.screens

import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaselabelapp.MyApplication
import com.example.firebaselabelapp.R
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.bluetoothprinter.PrinterManager
import com.example.firebaselabelapp.kiosk.KioskManager
import com.example.firebaselabelapp.ui.components.DatabaseResetButton
import com.example.firebaselabelapp.ui.components.ImportDataDialog
import com.example.firebaselabelapp.ui.components.LabelSizeIcon
import com.example.firebaselabelapp.ui.components.OpeningTimeInput
import com.example.firebaselabelapp.ui.components.PrimaryButton
import com.example.firebaselabelapp.ui.components.TemplateSyncDialog
import com.example.firebaselabelapp.ui.components.TextSizeInput
import com.example.firebaselabelapp.ui.components.UnifiedPrinterDiscoveryDialog
import com.example.firebaselabelapp.ui.components.WifiStatusSelector
import com.example.firebaselabelapp.ui.theme.FirebaseLabelAppTheme
import com.example.firebaselabelapp.ui.viewmodel.MenuViewModel
import com.example.firebaselabelapp.ui.viewmodel.SharedViewModel
import com.example.firebaselabelapp.update.AppSecurityManager
import com.example.firebaselabelapp.update.UpdateManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.launch

// Helper to compare versions
// Returns true if targetVersion > currentVersion
fun isVersionNewer(currentVersion: String, targetVersion: String): Boolean {
    // Remove 'v' or 'V' prefix
    val currentClean = currentVersion.replace(Regex("^[vV]"), "")
    val targetClean = targetVersion.replace(Regex("^[vV]"), "")

    val currentParts = currentClean.split(".").map { it.toIntOrNull() ?: 0 }
    val targetParts = targetClean.split(".").map { it.toIntOrNull() ?: 0 }

    val length = maxOf(currentParts.size, targetParts.size)

    for (i in 0 until length) {
        val c = if (i < currentParts.size) currentParts[i] else 0
        val t = if (i < targetParts.size) targetParts[i] else 0

        if (t > c) return true
        if (t < c) return false
    }
    return false
}

@Composable
private fun BrightnessControl() {
    val context = LocalContext.current
    // Safely cast context to Activity
    val activity = (context as? ComponentActivity)

    // Function to get current brightness (0f to 1f)
    fun getCurrentBrightness(): Float {
        // screenBrightness of -1.0f means to use the system's default
        val windowBrightness = activity?.window?.attributes?.screenBrightness
        return if (windowBrightness != null && windowBrightness >= 0) {
            windowBrightness
        } else {
            // Fallback to system setting
            try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Settings.SettingNotFoundException) {
                Log.e("BrightnessControl", "Cannot read system brightness", e)
                0.5f // Default to 50% if system setting is not found
            }
        }
    }

    var sliderPosition by remember { mutableFloatStateOf(getCurrentBrightness()) }

    // Update brightness when the slider is moved
    val onBrightnessChange = { newBrightness: Float ->
        val adjustedBrightness = newBrightness.coerceIn(0.25f, 1.0f)
        sliderPosition = adjustedBrightness

        // Use a standard 'if' check instead of ?.let as the last statement
        if (activity != null) {
            val window = activity.window
            val layoutParams = window.attributes
            layoutParams.screenBrightness = adjustedBrightness
            window.attributes = layoutParams
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Яркость экрана",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${(sliderPosition * 100).toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = sliderPosition,
            onValueChange = onBrightnessChange,
            valueRange = 0.25f..1.0f
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    sharedViewModel: SharedViewModel = viewModel(),
    onLogout: () -> Unit = {},
    onBackClick: () -> Unit,
    updateManager: UpdateManager,
    menuViewModel: MenuViewModel
) {

    val context = LocalContext.current
    val app = context.applicationContext as MyApplication
    val db = app.db
    val kioskManager = remember { KioskManager(context) }
    val printerManager = remember { PrinterManager(context, kioskManager) }
    val coroutineScope = rememberCoroutineScope()
    @Suppress("LocalVariableName") val Orange = Color(0xFFFFA500)


    // Collect state from StateFlows to use in the Composable
    val selectedDensity by sharedViewModel.selectedDensity.collectAsState()
    val selectedLabelSize by sharedViewModel.selectedLabelSize.collectAsState()
    val isManufactureDateLocked by sharedViewModel.isManufactureDateLocked.collectAsState()

    // Collect state from StateFlows from MenuViewModel:
    val isLoading by menuViewModel.isLoading.collectAsState()
    val errorMessage by menuViewModel.errorMessage.collectAsState()
    val successMessage by menuViewModel.successMessage.collectAsState()
    val isCheckingSyncPlan by menuViewModel.isCheckingSyncPlan.collectAsState()

    // State to hold the selected printer name for display
    var selectedPrinterName by remember { mutableStateOf(printerManager.getSelectedPrinterName()) }

    // Get current user email
    val userEmail = AuthManager.getCurrentUserEmail() ?: "Аноним"

    // Density options
    val densityOptions = listOf("Thin", "Medium", "Dense")
    var expandedDensityDropdown by remember { mutableStateOf(false) }
    var expandedOpeningTimesDropdown by remember { mutableStateOf(false) }

    // Update Dropdown states
    var expandedUpdateDropdown by remember { mutableStateOf(false) }
    var showPreReleaseDialog by remember { mutableStateOf(false) }
    var preReleaseVersionInput by remember { mutableStateOf("") }
    var preReleaseVersionError by remember { mutableStateOf("") }
    var showAllowedAppsDialog by remember { mutableStateOf(false) }

    // Advanced settings expansion state
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    // Kiosk settings expansion state
    var isKioskExpanded by remember { mutableStateOf(false) }

    // ... (rest of the state declarations remain the same)
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var isKioskEnabled by remember { mutableStateOf(kioskManager.isKioskModeEnabled()) }
    var isPinSet by remember { mutableStateOf(kioskManager.isPinSet()) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinSetupVerifyPinDialog by remember { mutableStateOf(false) }
    var showLogoutPinDialog by remember { mutableStateOf(false) }
    var showPinError by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingKioskState by remember { mutableStateOf(false) }
    var showPinToBlockDate by remember { mutableStateOf(false) }
    var isPinBlockChecked by remember { mutableStateOf(false) }
    var showPinVerificationDialog by remember { mutableStateOf(false) }
    var pinVerificationAttempts by remember { mutableIntStateOf(0) }
    val maxPinAttempts by remember { mutableIntStateOf(3) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showSimpleLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showUnifiedPrinterDialog by remember { mutableStateOf(false) }
    var isCheckingForAppUpdate by remember { mutableStateOf(false) }

    val isAnyUpdateInProgress = isCheckingForAppUpdate || isLoading
    var secretClickCount by remember { mutableIntStateOf(0) }
    val secretClickTarget = 25
    var showTemplateSyncDialog by remember { mutableStateOf(false) }

    // APK Picker Launcher
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            Toast.makeText(context, "Processing APK...", Toast.LENGTH_SHORT).show()
            updateManager.installLocalApk(it)
        }
    }

// This will run whenever the errorMessage value changes
    LaunchedEffect(key1 = menuViewModel.errorMessage) {
        // A safe way to run code only if the message is not null
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            // After showing the message, clear the error in the ViewModel
            // so it doesn't show again on a configuration change.
            menuViewModel.clearError()
        }
    }

    LaunchedEffect(key1 = menuViewModel.successMessage) {
        successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            // Now set successMessage to null
            menuViewModel.clearSuccessMessage()
        }
    }

    // Update states when kiosk settings change
    LaunchedEffect(Unit) {
        isKioskEnabled = kioskManager.isKioskModeEnabled()
        isPinSet = kioskManager.isPinSet()
    }

    FirebaseLabelAppTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            // ... (Header and other sections remain the same)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    FirebaseCrashlytics.getInstance()
                        .log("PrinterSettings: Logout button clicked. Kiosk enabled: ${kioskManager.isKioskModeEnabled()}")
                    onBackClick()
                }

                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go Back"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    "Настройки",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        FirebaseCrashlytics.getInstance()
                            .log("PrinterSettings: Logout button clicked. Kiosk enabled: ${kioskManager.isKioskModeEnabled()}")
                        if (kioskManager.isKioskModeEnabled() && kioskManager.isPinSet()) {
                            Log.d(
                                "PrinterSettings",
                                "Logout requested in kiosk mode - showing PIN dialog"
                            )
                            showLogoutPinDialog = true
                        } else {
                            Log.d("PrinterSettings", "Normal logout - showing confirmation")
                            showSimpleLogoutConfirmDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Выйти")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column for the text info
                Column(modifier = Modifier.weight(1f)) {
                    Text("Версия приложения: ${updateManager.getCurrentVersionNameAndCode()}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Email пользователя: $userEmail")
                    Spacer(modifier = Modifier.height(8.dp))
                    WifiStatusSelector()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Bluetooth Разрешения: ${if (printerManager.hasBluetoothPermissions()) "Есть" else "Отсутствуют"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("USB Разрешения: ${if (printerManager.hasUsbPermissionForSelectedDevice()) "Есть" else "Отсутствуют"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Выбранный Принтер: $selectedPrinterName")
                }

                Spacer(modifier = Modifier.width(16.dp)) // Add spacing

                // Column for the QR code
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ТЕХ ПОДДЕРЖКА В ТГ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Image(
                        painter = painterResource(id = R.drawable.qr2),
                        contentDescription = "QR Support Bot",
                        modifier = Modifier.size(200.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            PrimaryButton(
                text = "Выбрать Принтер",
                onClick = {
                    FirebaseCrashlytics.getInstance()
                        .log("PrinterSettings: 'Select Printer' clicked")
                    showUnifiedPrinterDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (!printerManager.hasBluetoothPermissions()) {
                PrimaryButton(
                    text = "Запросить разрешения Bluetooth",
                    onClick = {
                        printerManager.requestBluetoothPermissions()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))

            // Advanced Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Дополнительные настройки",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Дополнительные настройки",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { isAdvancedExpanded = !isAdvancedExpanded }) {
                            Icon(
                                imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isAdvancedExpanded) "Свернуть" else "Развернуть"
                            )
                        }
                    }

                    if (isAdvancedExpanded) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Density Picker
                        ExposedDropdownMenuBox(
                            expanded = expandedDensityDropdown,
                            onExpandedChange = {
                                expandedDensityDropdown = !expandedDensityDropdown
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedDensity, // FIXED: Use the collected state value
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Плотность печати") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDensityDropdown) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = expandedDensityDropdown,
                                onDismissRequest = { expandedDensityDropdown = false },
                                modifier = Modifier.exposedDropdownSize()
                            ) {
                                densityOptions.forEach { density ->
                                    DropdownMenuItem(
                                        text = { Text(density) },
                                        onClick = {
                                            sharedViewModel.updateDensity(density)
                                            expandedDensityDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        TextSizeInput(sharedViewModel)
                        Spacer(modifier = Modifier.height(16.dp))
                        // 1. Wrap the header Row in an OutlinedCard
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedOpeningTimesDropdown = !expandedOpeningTimesDropdown
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Add padding inside the card for breathing room
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                // Your bolded, titleMedium text
                                Text(
                                    text = "Настроить время открытия",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                // Just the Icon, as the whole card is clickable
                                Icon(
                                    imageVector = if (expandedOpeningTimesDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expandedOpeningTimesDropdown) "Свернуть" else "Развернуть"
                                )
                            }
                        }

                        // 2. The content, shown only if the state is true
                        if (expandedOpeningTimesDropdown) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Place your component directly here
                            OpeningTimeInput(sharedViewModel)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Label Size Pickers
                        Text(
                            text = "Размер этикетки:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LabelSizeIcon(
                                width = 60,
                                height = 45,
                                isSelected = selectedLabelSize == "40x30", // FIXED
                                onClick = { sharedViewModel.updateLabelSize("40x30") },
                                size = "40x30"
                            )
                            LabelSizeIcon(
                                width = 45,
                                height = 30,
                                isSelected = selectedLabelSize == "30x20", // FIXED
                                onClick = { sharedViewModel.updateLabelSize("30x20") },
                                size = "30x20"
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                FirebaseCrashlytics.getInstance()
                                    .log("PrinterSettings: 'Import Data' clicked")
                                showImportDialog = true
                            },
                            enabled = !isAnyUpdateInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Import",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Импорт данных")
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- CHANGED: SEAMLESS SPLIT BUTTON FOR UPDATES ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp) // Updated to 60.dp to match PrimaryButton
                                .clip(RoundedCornerShape(24.dp)) // Match PrimaryButton corner radius
                                .background(
                                    if (!isAnyUpdateInProgress) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. MAIN ACTION AREA (Left Side)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        enabled = !isAnyUpdateInProgress,
                                        onClick = {
                                            FirebaseCrashlytics.getInstance()
                                                .log("PrinterSettings: 'Check for Updates' clicked")
                                            coroutineScope.launch {
                                                isCheckingForAppUpdate = true
                                                try {
                                                    updateManager.checkForUpdates()
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            "Проверка обновлений запущена.",
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "Settings",
                                                        "Error checking for app updates",
                                                        e
                                                    )
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            "Ошибка при проверке обновлений.",
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                } finally {
                                                    isCheckingForAppUpdate = false
                                                }
                                            }
                                        }
                                    )
                                    // FIX: Add padding to start equal to the width of the right side elements (Divider + Menu).
                                    // Right side = 1.dp (Divider) + 48.dp (Menu) = 49.dp.
                                    // This pushes the centered content right by half that amount, aligning it perfectly
                                    // with the center of the PARENT row.
                                    .padding(start = 49.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isCheckingForAppUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Check for Updates",
                                        tint = MaterialTheme.colorScheme.onSecondary, // Text color
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "Проверить обновления",
                                        color = MaterialTheme.colorScheme.onSecondary, // Text color
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            // 2. DIVIDER
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight(0.6f)
                                    .background(MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.3f))
                            )

                            // 3. MENU ACTION AREA (Right Side, 3 Dots)
                            Box(
                                modifier = Modifier
                                    .width(48.dp) // Standard touch target width
                                    .fillMaxHeight()
                                    .clickable(
                                        enabled = !isAnyUpdateInProgress,
                                        onClick = { expandedUpdateDropdown = true }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More update options",
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )

                                DropdownMenu(
                                    expanded = expandedUpdateDropdown,
                                    onDismissRequest = { expandedUpdateDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Скачать пред-релизную версию") },
                                        onClick = {
                                            expandedUpdateDropdown = false
                                            showPreReleaseDialog = true
                                            preReleaseVersionInput = ""
                                            preReleaseVersionError = ""
                                        }
                                    )
                                }
                            }
                        }
                        // --- END OF SPLIT BUTTON ---

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- NEW ALLOWED APPS BUTTON ---
                        Button(
                            onClick = {
                                FirebaseCrashlytics.getInstance()
                                    .log("PrinterSettings: 'Install Apps' clicked")
                                AppSecurityManager.syncWithFirestore(context, db)
                                showAllowedAppsDialog = true
                            },
                            enabled = !isAnyUpdateInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Install Allowed Apps",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Установка приложений")
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                FirebaseCrashlytics.getInstance()
                                    .log("PrinterSettings: 'Check Templates' clicked")
                                showTemplateSyncDialog = true
                            },
                            enabled = !isAnyUpdateInProgress && !isCheckingSyncPlan,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            if (isCheckingSyncPlan) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                    contentDescription = "Check Templates",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Проверить сроки")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                FirebaseCrashlytics.getInstance()
                                    .log("PrinterSettings: 'Refresh Database' clicked")
                                menuViewModel.refreshData()
                            },
                            enabled = !isAnyUpdateInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {

                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = "Update Database",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Обновить базу данных")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        DatabaseResetButton(
                            menuViewModel = menuViewModel,
                            isEnabled = !isAnyUpdateInProgress
                        )
                        Spacer(modifier = Modifier.height(32.dp))


                        // Row for the new setting
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Заблокировать изменение даты",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Запрещает менять дату изготовления для обычных товаров (кроме подписанных \"Дата\")",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                enabled = isPinSet,
                                checked = isManufactureDateLocked, // FIXED: Use collected state
                                onCheckedChange = { isChecked ->
                                    showPinToBlockDate = true
                                    isPinBlockChecked = isChecked
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        BrightnessControl()
                    }
                }
            }

            // ... (Kiosk Mode section and Dialogs remain the same)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isKioskExpanded = !isKioskExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lock_open_24px),
                                contentDescription = "Безопасность",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Режим киоска",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { isKioskExpanded = !isKioskExpanded }
                        ) {
                            Icon(
                                imageVector = if (isKioskExpanded)
                                    Icons.Default.KeyboardArrowUp
                                else
                                    Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isKioskExpanded) "Свернуть" else "Развернуть"
                            )
                        }
                    }
                    if (isKioskExpanded) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PIN код:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = if (isPinSet) rememberVectorPainter(Icons.Default.Lock)
                                            else painterResource(id = R.drawable.lock_open_24px),
                                            contentDescription = null,
                                            tint = if (isPinSet) Color.Green else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = if (isPinSet) "Установлен" else "Не установлен",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isPinSet) Color.Green else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Режим владельца устройства:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.security_24px),
                                            contentDescription = null,
                                            tint = if (kioskManager.isDeviceOwner()) Color.Green else Orange,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = if (kioskManager.isDeviceOwner()) "Активен" else "Неактивен",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (kioskManager.isDeviceOwner()) Color.Green else Orange
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Права администратора:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.security_24px),
                                            contentDescription = null,
                                            tint = if (kioskManager.isDeviceAdminActive()) Color.Green else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = if (kioskManager.isDeviceAdminActive()) "Активны" else "Неактивны",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (kioskManager.isDeviceAdminActive()) Color.Green else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (!kioskManager.isDeviceOwner()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                                                alpha = 0.3f
                                            )
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "⚠️",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Режим владельца устройства не активен",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Для максимальной защиты киоска установите приложение как владельца устройства через ADB",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = if (isPinSet)
                                "Изменить PIN" else "Установить PIN",
                            onClick = {
                                if (!isPinSet)
                                    showPinSetupDialog = true
                                else
                                    showPinSetupVerifyPinDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!kioskManager.isDeviceOwner() && !kioskManager.isDeviceAdminActive()) {
                            Button(
                                onClick = {
                                    kioskManager.requestDeviceAdminPermission(context as ComponentActivity)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.security_24px),
                                    contentDescription = "Админ права",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Запросить права администратора")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (!kioskManager.isDeviceOwner()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.3f
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "💡",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = "Настройка владельца устройства",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Для максимальной защиты киоска выполните команду:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.Black.copy(alpha = 0.8f)
                                        )
                                    ) {
                                        Text(
                                            text = "adb shell dpm set-device-owner ${context.packageName}/.kiosk.KioskDeviceAdminReceiver",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Green,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "⚠️ Устройство должно быть сброшено к заводским настройкам перед выполнением команды",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isKioskEnabled)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                if (!isKioskEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "Активировать режим киоска",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (kioskManager.isDeviceOwner()) {
                                                    "Полная блокировка устройства с максимальной защитой"
                                                } else {
                                                    "Базовая блокировка приложения"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = false,
                                            onCheckedChange = { enabled ->
                                                if (enabled) {
                                                    when {
                                                        !isPinSet -> {
                                                            showPinError = true
                                                            pinErrorMessage =
                                                                "Сначала необходимо установить PIN код для разблокировки."
                                                        }

                                                        !kioskManager.isDeviceAdminActive() -> {
                                                            showPinError = true
                                                            pinErrorMessage =
                                                                "Необходимо предоставить права администратора устройства."
                                                        }

                                                        else -> {
                                                            pendingKioskState = true
                                                            showConfirmDialog = true
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) {
                                                    if (isKioskEnabled) {
                                                        secretClickCount++
                                                        Log.d(
                                                            "SecretUnlock",
                                                            "Kiosk status clicked. Count: $secretClickCount"
                                                        )
                                                        if (secretClickCount >= secretClickTarget) {
                                                            Log.w(
                                                                "SecretUnlock",
                                                                "Secret unlock triggered!"
                                                            )
                                                            if (kioskManager.isDeviceOwner()) {
                                                                kioskManager.forceExitKioskMode(
                                                                    context as ComponentActivity
                                                                )
                                                            } else {
                                                                kioskManager.disableKioskMode()
                                                                kioskManager.stopLockTaskMode(
                                                                    context as ComponentActivity
                                                                )
                                                            }
                                                            isKioskEnabled = false
                                                            secretClickCount = 0
                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    if (kioskManager.isDeviceOwner()) {
                                                                        "EMERGENCY UNLOCK: Device owner kiosk mode force disabled!"
                                                                    } else {
                                                                        "Kiosk mode unlocked via secret gesture."
                                                                    },
                                                                    Toast.LENGTH_LONG
                                                                )
                                                                .show()
                                                        }
                                                    }
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.lock_open_24px),
                                                contentDescription = "Заблокировано",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "РЕЖИМ КИОСКА АКТИВЕН",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (kioskManager.isDeviceOwner()) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Card(
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = Color.Red.copy(
                                                                    alpha = 0.2f
                                                                )
                                                            )
                                                        ) {
                                                            Text(
                                                                text = "MAX",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = Color.Red,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(
                                                                    horizontal = 6.dp,
                                                                    vertical = 2.dp
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = if (kioskManager.isDeviceOwner()) {
                                                        "Максимальная защита - полная блокировка устройства"
                                                    } else {
                                                        "Базовая защита - ограниченная блокировка"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(
                                                        color = if (kioskManager.isDeviceOwner()) Color.Red else Color.Green,
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (kioskManager.isDeviceOwner()) {
                                                    Color.Red.copy(alpha = 0.1f)
                                                } else {
                                                    MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.3f
                                                    )
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (kioskManager.isDeviceOwner()) "🔴" else "⚠️",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = if (kioskManager.isDeviceOwner()) {
                                                            "КРИТИЧЕСКОЕ ПРЕДУПРЕЖДЕНИЕ"
                                                        } else {
                                                            "Для выхода из режима киоска требуется PIN код"
                                                        },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (kioskManager.isDeviceOwner()) Color.Red else MaterialTheme.colorScheme.onErrorContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (kioskManager.isDeviceOwner()) {
                                                        Text(
                                                            text = "Устройство полностью заблокировано. Только PIN код или сброс к заводским настройкам может разблокировать его.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.Red
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                showPinVerificationDialog = true
                                                pinVerificationAttempts = 0
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (kioskManager.isDeviceOwner()) {
                                                    Color.Red.copy(alpha = 0.9f)
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.lock_open_24px),
                                                contentDescription = "Разблокировать",
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = if (kioskManager.isDeviceOwner()) {
                                                    "🔴 ЭКСТРЕННАЯ РАЗБЛОКИРОВКА"
                                                } else {
                                                    "ОТКЛЮЧИТЬ РЕЖИМ КИОСКА"
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        val showAccessibilityText =
                                            false   // debugging version, can't
                                        if (kioskManager.isDeviceOwner() && showAccessibilityText) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Служба защиты:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = if (kioskManager.isAccessibilityServiceEnabled()) "Активна" else "Неактивна",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (kioskManager.isAccessibilityServiceEnabled()) Color.Green else Orange
                                                )
                                            }
                                            if (!kioskManager.isAccessibilityServiceEnabled()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "💡 Рекомендуется включить службу защиты для дополнительной безопасности",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!isKioskEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    kioskManager.clearKioskData()
                                    isPinSet = false
                                    isKioskEnabled = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            ) {
                                Text(
                                    color = Color.White,
                                    text = "Сбросить настройки киоска"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Collect the new state
        val loadingMessage by menuViewModel.loadingMessage.collectAsState()

        // Find where you show the CircularProgressIndicator or loading overlay
        if (isLoading) {
            AlertDialog(
                onDismissRequest = { /* Do nothing, block dismiss */ },
                title = { Text("Подождите") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        // Show the dynamic status text
                        Text(loadingMessage ?: "Обработка...")
                    }
                },
                confirmButton = {}
            )
        }

        // ... (All dialogs remain the same)
        if (showPreReleaseDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPreReleaseDialog = false
                    preReleaseVersionError = ""
                },
                title = { Text("Скачать pre-release версию") },
                text = {
                    Column {
                        Text("Введите версию (например, v1.9.9):")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = preReleaseVersionInput,
                            onValueChange = {
                                preReleaseVersionInput = it
                                preReleaseVersionError = ""
                            },
                            label = { Text("Версия") },
                            isError = preReleaseVersionError.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (preReleaseVersionError.isNotEmpty()) {
                            Text(
                                text = preReleaseVersionError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val regex = Regex("^v\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
                            if (!preReleaseVersionInput.matches(regex)) {
                                preReleaseVersionError =
                                    "Формат должен быть vXXX.XXX.XXX (например, v1.9.9)"
                                return@TextButton
                            }

                            // Check version
                            val currentVersion = updateManager.getCurrentVersionName()
                            if (isVersionNewer(currentVersion, preReleaseVersionInput)) {
                                showPreReleaseDialog = false
                                isCheckingForAppUpdate = true
                                coroutineScope.launch {
                                    try {
                                        updateManager.checkForUpdates(specificVersion = preReleaseVersionInput)
                                        Toast.makeText(
                                            context,
                                            "Загрузка версии $preReleaseVersionInput...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Ошибка при загрузке.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        Log.e("PrinterSettings", "Pre-release update failed", e)
                                    } finally {
                                        isCheckingForAppUpdate = false
                                    }
                                }
                            } else {
                                preReleaseVersionError =
                                    "Введенная версия не выше текущей ($currentVersion)"
                            }
                        }
                    ) {
                        Text("Скачать")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPreReleaseDialog = false
                            preReleaseVersionError = ""
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }

        // NEW ALLOWED APPS DIALOG
        if (showAllowedAppsDialog) {
            val allowedApps by AppSecurityManager.allowedApps.collectAsState()

            AlertDialog(
                onDismissRequest = { showAllowedAppsDialog = false },
                title = { Text("Доступные приложения") },
                text = {
                    Column {
                        // 1. FILE PICKER BUTTON
                        Button(
                            onClick = {
                                // Launch System File Browser
                                apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            enabled = !isAnyUpdateInProgress
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Установить APK из памяти",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Или выберите из списка:", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. REMOTE APPS LIST
                        if (allowedApps.isEmpty()) {
                            Text(
                                "Нет доступных приложений в списке.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(200.dp) // Adjusted height
                            ) {
                                items(allowedApps) { app ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    app.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    app.packageName,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (app.downloadUrl.isNotEmpty()) {
                                                Button(
                                                    onClick = {
                                                        updateManager.installApp(
                                                            app.downloadUrl,
                                                            app.name
                                                        )
                                                        Toast.makeText(
                                                            context,
                                                            "Скачивание ${app.name}...",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    enabled = !isAnyUpdateInProgress
                                                ) {
                                                    Text("Скачать")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAllowedAppsDialog = false }) {
                        Text("Закрыть")
                    }
                }
            )
        }

        PinEntryDialog(
            title = "Введите текущий PIN код",
            isVisible = showPinSetupVerifyPinDialog,
            onDismiss = {
                showPinSetupVerifyPinDialog = false
            },
            onPinEntered = { pin ->
                if (kioskManager.verifyPin(pin)) {
                    Log.d("PrinterSettings", "PIN correct for change PIN")
                    showPinSetupVerifyPinDialog = false
                    showPinSetupDialog = true
                    showPinError = false
                    pinErrorMessage = ""
                } else {
                    showPinError = true
                    pinErrorMessage = "Неверный PIN код. Попробуйте снова."
                }
            },
            isError = showPinError,
            errorMessage = if (showPinError) pinErrorMessage else ""
        )
        PinEntryDialog(
            title = "Введите PIN для подтверждения",
            isVisible = showPinToBlockDate,
            onDismiss = {
                showPinToBlockDate = false
            },
            onPinEntered = { pin ->
                if (kioskManager.verifyPin(pin)) {
                    Log.d("PrinterSettings", "PIN correct for DATE BLOCK")
                    sharedViewModel.setManufactureDateLock(isPinBlockChecked)
                    showPinToBlockDate = false
                    showPinError = false
                    pinErrorMessage = ""
                } else {
                    showPinError = true
                    pinErrorMessage = "Неверный PIN код. Попробуйте снова."
                }
            },
            isError = showPinError,
            errorMessage = if (showPinError) pinErrorMessage else ""
        )
        PinEntryDialog(
            title = "Введите PIN для выхода",
            isVisible = showLogoutPinDialog,
            onDismiss = {
                showLogoutPinDialog = false
                showPinError = false
                pinErrorMessage = ""
            },
            onPinEntered = { pin ->
                if (kioskManager.verifyPin(pin)) {
                    Log.d("PrinterSettings", "PIN correct for logout - showing confirmation")
                    showLogoutPinDialog = false
                    showLogoutConfirmDialog = true
                    showPinError = false
                    pinErrorMessage = ""
                } else {
                    showPinError = true
                    pinErrorMessage = "Неверный PIN код. Попробуйте снова."
                }
            },
            isError = showPinError && showLogoutPinDialog,
            errorMessage = if (showLogoutPinDialog) pinErrorMessage else ""
        )
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    showLogoutConfirmDialog = false
                },
                title = { Text("Выйти из приложения?") },
                text = {
                    Text(
                        if (kioskManager.isKioskModeEnabled()) {
                            "Вы действительно хотите выйти?\n\n" +
                                    "• Режим киоска будет отключен\n" +
                                    "• Вы будете разлогинены\n" +
                                    "• Приложение будет закрыто"
                        } else {
                            "Вы действительно хотите выйти из приложения?"
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Log.d("PrinterSettings", "Logout confirmed - proceeding with logout")
                            showLogoutConfirmDialog = false
                            if (kioskManager.isKioskModeEnabled()) {
                                Log.d("PrinterSettings", "Disabling kiosk mode before logout")
                                kioskManager.disableKioskMode()
                                try {
                                    kioskManager.stopLockTaskMode(context as ComponentActivity)
                                } catch (e: Exception) {
                                    Log.e("PrinterSettings", "Error stopping lock task mode", e)
                                }
                            }
                            onLogout()
                        }
                    ) {
                        Text("Выйти", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirmDialog = false
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }
        if (showSimpleLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSimpleLogoutConfirmDialog = false },
                title = { Text("Выход из приложения") },
                text = { Text("Вы уверены что хотите выйти?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSimpleLogoutConfirmDialog = false
                            onLogout()
                        }
                    ) {
                        Text("Выйти", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSimpleLogoutConfirmDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
        PinEntryDialog(
            title = "Введите PIN для разблокировки",
            isVisible = showPinVerificationDialog,
            onDismiss = {
                showPinVerificationDialog = false
                pinVerificationAttempts = 0
            },
            onPinEntered = { pin ->
                if (kioskManager.verifyPin(pin)) {
                    showPinVerificationDialog = false
                    pendingKioskState = false
                    showConfirmDialog = true
                    pinVerificationAttempts = 0
                } else {
                    pinVerificationAttempts++
                    if (pinVerificationAttempts >= maxPinAttempts) {
                        showPinVerificationDialog = false
                        showPinError = true
                        pinErrorMessage =
                            "Превышено количество попыток ввода PIN. Попробуйте позже."
                        pinVerificationAttempts = 0
                    } else {
                        showPinError = true
                        pinErrorMessage =
                            "Неверный PIN код. Осталось попыток: ${maxPinAttempts - pinVerificationAttempts}"
                    }
                }
            },
            isError = showPinError && showPinVerificationDialog,
            errorMessage = if (showPinVerificationDialog) pinErrorMessage else ""
        )
        PinEntryDialog(
            title = "Установите PIN код (6 цифр)",
            isVisible = showPinSetupDialog,
            onDismiss = {
                showPinSetupDialog = false
                showPinError = false
            },
            onPinEntered = { pin ->
                if (kioskManager.savePin(pin)) {
                    showPinSetupDialog = false
                    isPinSet = true
                    showPinError = false
                    pinErrorMessage = ""
                } else {
                    showPinError = true
                    pinErrorMessage = "Ошибка при сохранении PIN кода. Попробуйте снова."
                }
            },
            isError = showPinError && showPinSetupDialog,
            errorMessage = if (showPinSetupDialog) pinErrorMessage else ""
        )
        if (showPinError && !showPinSetupDialog && !showPinVerificationDialog && !showLogoutPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPinError = false
                    pinErrorMessage = ""
                },
                title = { Text("Ошибка") },
                text = { Text(pinErrorMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPinError = false
                            pinErrorMessage = ""
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = {
                    Text(
                        if (pendingKioskState) "Активировать режим киоска?"
                        else "Деактивировать режим киоска?"
                    )
                },
                text = {
                    Text(
                        if (pendingKioskState) {
                            if (kioskManager.isDeviceOwner()) {
                                "⚠️ МАКСИМАЛЬНАЯ ЗАЩИТА ⚠️\n\n" +
                                        "Внимание! После активации режима киоска с правами владельца устройства:\n\n" +
                                        "• Приложение будет НАМЕРТВО заблокировано в полноэкранном режиме\n" +
                                        "• ВСЕ системные кнопки и жесты будут заблокированы\n" +
                                        "• Строка состояния будет полностью отключена\n" +
                                        "• Экран блокировки будет отключен\n" +
                                        "• Доступ к настройкам Android будет невозможен\n" +
                                        "• Для выхода потребуется только PIN код\n" +
                                        "• Сброс к заводским настройкам будет заблокирован\n\n" +
                                        "🔴 УБЕДИТЕСЬ, ЧТО ВЫ ПОМНИТЕ PIN КОД! 🔴\n\n" +
                                        "Продолжить?"
                            } else {
                                "Внимание! После активации режима киоска:\n\n" +
                                        "• Приложение будет заблокировано в полноэкранном режиме\n" +
                                        "• Системные кнопки будут ограничены\n" +
                                        "• Для выхода потребуется ввод PIN кода\n" +
                                        "• Уведомления будут скрыты\n\n" +
                                        "⚠️ Базовый режим - возможны способы выхода через системные настройки\n\n" +
                                        "Продолжить?"
                            }
                        } else {
                            "Вы действительно хотите отключить режим киоска?\n\n" +
                                    "Устройство вернется к обычному режиму работы."
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            if (pendingKioskState) {
                                if (kioskManager.enableKioskMode()) {
                                    isKioskEnabled = true
                                    kioskManager.startLockTaskMode(context as ComponentActivity)
                                    Toast.makeText(
                                        context,
                                        if (kioskManager.isDeviceOwner()) {
                                            "Режим киоска активирован с максимальной защитой!"
                                        } else {
                                            "Режим киоска активирован (базовая защита)"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                if (kioskManager.disableKioskMode()) {
                                    isKioskEnabled = false
                                    kioskManager.stopLockTaskMode(context as ComponentActivity)
                                }
                            }
                        }
                    ) {
                        Text(
                            if (pendingKioskState) {
                                if (kioskManager.isDeviceOwner()) "🔴 АКТИВИРОВАТЬ МАКСИМАЛЬНУЮ ЗАЩИТУ"
                                else "Активировать"
                            } else "Деактивировать",
                            color = if (pendingKioskState && kioskManager.isDeviceOwner()) {
                                MaterialTheme.colorScheme.error
                            } else if (pendingKioskState) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontWeight = if (kioskManager.isDeviceOwner()) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
        if (showImportDialog) {
            ImportDataDialog(
                text = importText,
                onTextChange = { importText = it },
                isImporting = isImporting,
                errorMessage = importError,
                onDismiss = {
                    showImportDialog = false
                    importText = ""
                    importError = null
                },
                onImport = {
                    if (importText.isNotBlank()) {
                        isImporting = true
                        importError = null
                        menuViewModel.importData(importText)
                    }
                }
            )
        }
        if (showUnifiedPrinterDialog) {
            UnifiedPrinterDiscoveryDialog(
                onDismiss = {
                    showUnifiedPrinterDialog = false
                },
                onPrinterSelected = { printerType, address, port ->
                    // Save the selection
                    printerManager.savePrinterFromUnifiedDialog(printerType, address, port)

                    // Update the displayed name
                    selectedPrinterName = printerManager.getSelectedPrinterName()

                    // Show confirmation toast
                    val printerInfo = when (printerType) {
                        "Bluetooth" -> "Bluetooth принтер: ${printerManager.getSelectedPrinterName()}"
                        "USB" -> "USB принтер: ${printerManager.getSelectedPrinterName()}"
                        "WiFi" -> "WiFi принтер: $address:${port ?: 9100}"
                        else -> "Принтер выбран"
                    }

                    Toast.makeText(
                        context,
                        printerInfo,
                        Toast.LENGTH_LONG
                    ).show()

                    showUnifiedPrinterDialog = false
                }
            )
        }

        if (showTemplateSyncDialog) {
            TemplateSyncDialog(
                menuViewModel = menuViewModel,
                onDismiss = { showTemplateSyncDialog = false }
            )
        }
    }
}