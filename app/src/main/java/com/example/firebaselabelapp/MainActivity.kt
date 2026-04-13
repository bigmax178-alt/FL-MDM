package com.example.firebaselabelapp

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Toast // --- ADDED IMPORT ---
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.bluetoothprinter.PrinterManager
import com.example.firebaselabelapp.bluetoothprinter.UsbDeviceReceiver
import com.example.firebaselabelapp.subscription.SessionValidator
import com.example.firebaselabelapp.ui.components.PaymentBannerManager
import com.example.firebaselabelapp.ui.components.WifiStatusSelector
import com.example.firebaselabelapp.ui.screens.CombinedMenuItemScreen
import com.example.firebaselabelapp.ui.screens.LoginScreen
import com.example.firebaselabelapp.ui.screens.PinEntryDialog
import com.example.firebaselabelapp.ui.screens.PrintLabelScreen
import com.example.firebaselabelapp.ui.screens.PrinterSettingsScreen
import com.example.firebaselabelapp.ui.screens.SearchScreen
import com.example.firebaselabelapp.ui.theme.FirebaseLabelAppTheme
import com.example.firebaselabelapp.ui.viewmodel.MenuViewModel
import com.example.firebaselabelapp.ui.viewmodel.SessionState
import com.example.firebaselabelapp.ui.viewmodel.SharedViewModel
import com.example.firebaselabelapp.ui.viewmodel.ViewModelFactory
import com.example.firebaselabelapp.update.UpdateManager
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"

        private const val SEQUENCE_TIMEOUT_MS = 2000L

    }

    // --- Properties for secret key sequence (already existed) ---
    private val keySequence = mutableListOf<Int>()
    private var lastKeyTime = 0L
    private val targetSequence = listOf(
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_DOWN
    )

    private lateinit var usbDeviceReceiver: UsbDeviceReceiver
    private lateinit var updateManager: UpdateManager
    private lateinit var notificationPrefs: SharedPreferences
    private val showPaymentNotification = mutableStateOf(false)

    private val repository by lazy { (application as MyApplication).repository }
    private val kioskManager by lazy { (application as MyApplication).kioskManager }
    private val printerManager by lazy { (application as MyApplication).printerManager }

    private var sharedViewModelInstance: SharedViewModel? = null

    // Job to manage the periodic session validation
    private var sessionValidationJob: Job? = null

    private val sessionValidationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            FirebaseCrashlytics.getInstance()
                .log("MainActivity: Received broadcast - ${intent?.action}")
            val errorMessage = intent?.getStringExtra("error_message") ?: "Session error"
            sharedViewModelInstance?.let { vm ->
                when (intent?.action) {
                    SessionValidator.ACTION_GRACE_PERIOD_EXPIRED -> {
                        Log.d(TAG, "Received grace period expired broadcast")
                        vm.setSessionError(errorMessage, isGracePeriodError = true)
                    }

                    SessionValidator.ACTION_SUBSCRIPTION_EXPIRED -> {
                        Log.d(TAG, "Received subscription expired broadcast")
                        vm.setSessionError(errorMessage, isGracePeriodError = false)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseCrashlytics.getInstance().log("MainActivity: onCreate")
        notificationPrefs = getSharedPreferences("notification_tracker", MODE_PRIVATE)
        usbDeviceReceiver = UsbDeviceReceiver()
        updateManager = UpdateManager(this, (application as MyApplication).db)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(
                Manifest.permission.READ_PHONE_STATE
            )
        }

        Log.d(TAG, kioskManager.getKioskStatus())
        kioskManager.enforceAlwaysOnSecurity()
        updateManager.checkForUpdates()
        setupSystemUiVisibilityListener()

        setContent {
            val viewModelFactory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SharedViewModel(application, printerManager) as T
                }
            }
            val sharedViewModel: SharedViewModel = viewModel(factory = viewModelFactory)
            sharedViewModelInstance = sharedViewModel
            LaunchedEffect(Unit) {
                sharedViewModel.initialize(this@MainActivity)
            }
            FirebaseLabelApp(
                activity = this,
                sharedViewModel = sharedViewModel,
                showPaymentNotification = showPaymentNotification
            )
        }
    }

    private fun setupSystemUiVisibilityListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (kioskManager.isKioskModeEnabled() && (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    kioskManager.handleSystemUiVisibilityChange(this)
                }
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        FirebaseCrashlytics.getInstance().log("MainActivity: onStart")
        val sessionFilter = IntentFilter().apply {
            addAction(SessionValidator.ACTION_GRACE_PERIOD_EXPIRED)
            addAction(SessionValidator.ACTION_SUBSCRIPTION_EXPIRED)
        }
        ContextCompat.registerReceiver(
            this,
            sessionValidationReceiver,
            sessionFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(PrinterManager.ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(
            this,
            usbDeviceReceiver,
            usbFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        if (kioskManager.isKioskModeEnabled() && kioskManager.isPinSet()) {
            kioskManager.startLockTaskMode(this)
        }
        // Start the periodic session validation when the app becomes visible
        startSessionValidator()
    }

    override fun onResume() {
        super.onResume()
        FirebaseCrashlytics.getInstance()
            .log("MainActivity: onResume. Kiosk enabled: ${kioskManager.isKioskModeEnabled()}")
        if (kioskManager.isKioskModeEnabled()) {
            kioskManager.enableImmersiveMode(this)
        }
        // Trigger validation via ViewModel every time the app comes to the foreground
        // This is good for immediate UI feedback
        FirebaseCrashlytics.getInstance().log("MainActivity: onResume validation triggered.")
        sharedViewModelInstance?.validateSession(this)
    }

    override fun onStop() {
        FirebaseCrashlytics.getInstance().log("MainActivity: onStop")
        // Stop the periodic session validation when the app is no longer visible
        stopSessionValidator()
        unregisterReceiver(sessionValidationReceiver)
        unregisterReceiver(usbDeviceReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        FirebaseCrashlytics.getInstance().log("MainActivity: onDestroy")
        super.onDestroy()
        sharedViewModelInstance = null // Clean up reference
        updateManager.unregisterDownloadReceiver()
        printerManager.cancelScope()
        repository.cleanup()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && kioskManager.isKioskModeEnabled()) {
            kioskManager.enableImmersiveMode(this)
        }
    }

    // --- 1. ADDED onKeyDown METHOD ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // We only care about volume keys for our sequence
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {

            // Handle the sequence logic
            handleKeySequence(keyCode)

            // IMPORTANT: Return 'true' here.
            // This "consumes" the key event, preventing the system
            // from changing the volume or showing the volume UI.
            return true
        }

        // Any other key press (like 'back') resets the sequence
        clearKeySequence()

        // Let the system handle other keys (if any)
        return super.onKeyDown(keyCode, event)
    }

    // --- 2. ADDED HELPER METHODS ---

    /**
     * Resets the tracked key sequence.
     */
    private fun clearKeySequence() {
        if (keySequence.isNotEmpty()) {
            Log.d(TAG, "Key sequence reset.")
        }
        keySequence.clear()
        lastKeyTime = 0L
    }

    /**
     * Tracks key presses and checks for the target sequence.
     */
    private fun handleKeySequence(keyCode: Int) {
        val currentTime = System.currentTimeMillis()

        // If too much time has passed since the last key, reset
        if (lastKeyTime != 0L && (currentTime - lastKeyTime > SEQUENCE_TIMEOUT_MS)) {
            Log.d(TAG, "Key sequence timed out. Resetting.")
            keySequence.clear()
        }

        // Add the new key and update the time
        keySequence.add(keyCode)
        lastKeyTime = currentTime
        Log.d(TAG, "Key sequence: $keySequence")

        // Keep the list trimmed to the size of our target sequence
        while (keySequence.size > targetSequence.size) {
            keySequence.removeAt(0)
        }

        // Check for a match
        if (keySequence == targetSequence) {
            Log.i(TAG, "Secret key sequence detected! Triggering kiosk exit.")
            triggerKioskExit()
            keySequence.clear()
        }
    }

    /**
     * Exits kiosk mode.
     * --- MODIFIED to use the existing 'by lazy' kioskManager ---
     */
    private fun triggerKioskExit() {
        // We use the existing 'lazy' kioskManager property.
        // No need to check for initialization.
        try {
            // Assuming your KioskManager class has this method, as per your snippet
            val success = kioskManager.forceExitKioskMode(this) // 'this' is the Activity

            if (success) {
                Toast.makeText(this, "Kiosk mode force exited.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Failed to exit kiosk mode.", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger kiosk exit", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }


    // --- END OF ADDED METHODS ---

    private fun startSessionValidator() {
        if (sessionValidationJob?.isActive == true) return // Already running
        Log.d(TAG, "Starting periodic session validator.")
        sessionValidationJob = lifecycleScope.launch {
            while (isActive) {
                Log.d(TAG, "Periodic session check running.")
                FirebaseCrashlytics.getInstance()
                    .log("MainActivity: Periodic validator job running.")
                sharedViewModelInstance?.validateSession(this@MainActivity)
                delay(3600000L) // Check every 1 hour
            }
        }
    }

    private fun stopSessionValidator() {
        Log.d(TAG, "Stopping periodic session validator.")
        sessionValidationJob?.cancel()
        sessionValidationJob = null
    }

    @Composable
    fun FirebaseLabelApp(
        activity: Activity,
        sharedViewModel: SharedViewModel,
        showPaymentNotification: MutableState<Boolean>
    ) {
        val navController = rememberNavController()
        val sessionState by sharedViewModel.sessionState.collectAsState()
        var showBackExitDialog by remember { mutableStateOf(false) }
        var isPinError by remember { mutableStateOf(false) }

        val factory = remember { ViewModelFactory(repository) }
        val menuViewModel: MenuViewModel = viewModel(factory = factory)


        FirebaseLabelAppTheme {

            val systemUiController = rememberSystemUiController()
            val useDarkIcons = !isSystemInDarkTheme()

            // 💡 Get the colors here, in the composable context
            val primaryColor = MaterialTheme.colorScheme.primary
            val backgroundColor = MaterialTheme.colorScheme.background

            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect { backStackEntry ->
                    val route = backStackEntry.destination.route
                    val color = when (route) {
                        "home" -> primaryColor
                        "login", "print_label_screen", "printer_settings", "search_screen" -> backgroundColor
                        else -> backgroundColor // A sensible default
                    }
                    systemUiController.setStatusBarColor(
                        color = color,
                        darkIcons = useDarkIcons
                    )
                }
            }

            Box(Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)) {

                val (showBanner, timeUntilDeactivation) = when (val state = sessionState) {
                    is SessionState.LoggedIn -> Pair(
                        state.showRedBanner,
                        state.timeUntilDeactivation
                    )

                    else -> Pair(false, 0L)
                }

                // PaymentBannerManager now only handles the full-screen RED banner.
                // The yellow banner logic is delegated to the specific screen.
                PaymentBannerManager(
                    redBanner = showBanner,
                    timeUntilDeactivation = timeUntilDeactivation
                ) { _ -> // We ignore showYellowBanner here
                    NavHost(navController = navController, startDestination = "loader") {
                        composable("loader") {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        composable("login") {
                            LoginScreen(activity = activity, onLoginSuccess = {
                                sharedViewModel.onLoginSuccess()
                            })
                        }
                        composable("home") {
                            val onBackClick = {
                                if (navController.currentDestination?.route == "home") {
                                    if (kioskManager.isKioskModeEnabled() && kioskManager.isPinSet()) {
                                        showBackExitDialog = true
                                    } else {
                                        finish()
                                    }
                                }
                            }
                            BackHandler(enabled = true) { onBackClick() }

                            LaunchedEffect(Unit) {
                                FirebaseCrashlytics.getInstance()
                                    .log("MainActivity(home): LaunchedEffect(Unit) triggered. Calling loadData.")
                                menuViewModel.loadData()
                            }

                            CombinedMenuItemScreen(
                                sharedViewModel = sharedViewModel,
                                menuViewModel = menuViewModel,
                                repository = repository,
                                onItemClick = { item ->
                                    sharedViewModel.selectItem(item); navController.navigate(
                                    "print_label_screen"
                                )
                                },
                                onSettingsClick = { navController.navigate("printer_settings") },
                                onSearchClick = { navController.navigate("search_screen") },
                                onBackClick = onBackClick
                            )
                        }
                        composable("search_screen") {
                            SearchScreen(
                                repository = repository,
                                onItemClick = { item ->
                                    sharedViewModel.selectItem(item); navController.navigate(
                                    "print_label_screen"
                                )
                                },
                                onBackClick = {
                                    if (navController.currentDestination?.route == "search_screen") {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                        composable("print_label_screen") {
                            val selectedItem by sharedViewModel.selectedItem.collectAsState()
                            PrintLabelScreen(
                                item = selectedItem,
                                sharedViewModel = sharedViewModel,
                                onBackClick = {
                                    if (navController.currentDestination?.route == "print_label_screen") {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                        composable("printer_settings") {
                            PrinterSettingsScreen(
                                sharedViewModel = sharedViewModel,
                                onLogout = { AuthManager.logout(this@MainActivity); sharedViewModel.onLogout() },
                                onBackClick = {
                                    if (navController.currentDestination?.route == "printer_settings") {
                                        navController.popBackStack()
                                    }
                                },
                                updateManager = updateManager,
                                menuViewModel = menuViewModel
                            )
                        }
                    }
                }

                LaunchedEffect(sessionState) {
                    FirebaseCrashlytics.getInstance()
                        .log("MainActivity: SessionState changed to ${sessionState::class.simpleName}")
                    when (sessionState) {
                        is SessionState.Checking -> { /* Stay on loader */
                        }

                        is SessionState.LoggedOut, is SessionState.Error -> {
                            FirebaseCrashlytics.getInstance()
                                .log("SessionState changed to LoggedOut/Error Navigating to 'login'.") // 💡 BREADCRUMB
                            // 💡 NEW: Tell the MenuViewModel to clean up!
                            menuViewModel.clearDataAndJobs()


                            if (navController.currentDestination?.route != "login") {
                                navController.navigate("login") { popUpTo(0) }
                            }

                            // If kiosk mode IS enabled, we do nothing here. The error dialogs
                            // will correctly appear over the main screen.
                        }

                        is SessionState.LoggedIn -> {
                            val mainRoutes = listOf(
                                "home",
                                "search_screen",
                                "print_label_screen",
                                "printer_settings"
                            )
                            if (navController.currentDestination?.route !in mainRoutes) {
                                FirebaseCrashlytics.getInstance()
                                    .log("MainActivity: State is LoggedIn. Navigating to 'home'.")
                                navController.navigate("home") { popUpTo(0) }
                            } else {
                                // ADD THIS LOG
                                FirebaseCrashlytics.getInstance()
                                    .log("MainActivity: State is LoggedIn. Already on a main route. No navigation.")
                            }
                        }
                    }
                }

                when (val state = sessionState) {
                    is SessionState.Error -> {
                        if (state.isGracePeriodError) {
                            GracePeriodExpiredDialog(
                                errorMessage = state.message,
                                onCheckConnection = { sharedViewModel.revalidateAndLogin(activity) },
                                onLogout = { AuthManager.logout(this@MainActivity); sharedViewModel.onLogout() }
                            )
                        } else {
                            SubscriptionExpiredDialog(
                                errorMessage = state.message,
                                onConfirm = { sharedViewModel.onLogout() }
                            )
                        }
                    }

                    else -> {}
                }

                if (showPaymentNotification.value) {
                    PaymentNotificationDialog(onDismiss = { showPaymentNotification.value = false })
                }

                PinEntryDialog(
                    title = "Введите PIN для выхода",
                    isVisible = showBackExitDialog,
                    onDismiss = {
                        showBackExitDialog = false
                        isPinError = false // Reset error on dismiss
                    },
                    onPinEntered = { pin ->
                        if (kioskManager.verifyPin(pin)) {
                            kioskManager.disableKioskMode()
                            kioskManager.stopLockTaskMode(this@MainActivity)
                            finish()
                        } else {
                            isPinError = true
                        }
                    },
                    isError = isPinError,
                    errorMessage = if (isPinError) "Неверный PIN-код" else ""
                )
            }
        }
    }
}

// --- DIALOG COMPOSABLES ---

@Composable
fun GracePeriodExpiredDialog(
    errorMessage: String,
    onCheckConnection: () -> Unit,
    onLogout: () -> Unit
) {
    var isChecking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Требуется подключение к интернету") },
        text = {
            Column {
                Text(errorMessage)
                Spacer(Modifier.height(16.dp))
                // Let the user to connect to wifi to not block the app
                WifiStatusSelector()
                if (isChecking) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Проверка подключения...")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isChecking = true
                    onCheckConnection()
                },
                enabled = !isChecking
            ) {
                Text("Проверить")
            }
        },
        dismissButton = {
            TextButton(onClick = onLogout, enabled = !isChecking) {
                Text("Выйти")
            }
        }
    )
}

@Composable
fun SubscriptionExpiredDialog(errorMessage: String, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Сессия недействительна") },
        text = { Text(errorMessage) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Перейти к входу")
            }
        }
    )
}

@Composable
fun PaymentNotificationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Требуется оплата") },
        text = { Text("Ваша подписка активна, но требует оплаты. Пожалуйста, внесите платеж, чтобы продолжить использование без прерываний.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}