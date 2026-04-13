package com.example.firebaselabelapp.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.bluetoothprinter.PrinterManager
import com.example.firebaselabelapp.data.SettingsManager
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.subscription.SubscriptionManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDateTime

// --- State Definitions ---

sealed interface PrintUiState {
    object Idle : PrintUiState
    object Printing : PrintUiState
    object Success : PrintUiState
    data class Error(val message: String) : PrintUiState
}

sealed interface SessionState {
    object Checking : SessionState
    object LoggedOut : SessionState
    data class LoggedIn(
        val showRedBanner: Boolean = false,
        val timeUntilDeactivation: Long = 0L
    ) : SessionState
    data class Error(val message: String, val isGracePeriodError: Boolean) : SessionState
}

// --- ViewModel ---

class SharedViewModel(
    application: Application,
    private val printerManager: PrinterManager
) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    // State Flows
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Checking)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _printUiState = MutableStateFlow<PrintUiState>(PrintUiState.Idle)
    val printUiState: StateFlow<PrintUiState> = _printUiState.asStateFlow()

    private val _isManufactureDateLocked = MutableStateFlow(false)
    val isManufactureDateLocked: StateFlow<Boolean> = _isManufactureDateLocked.asStateFlow()

    val selectedItem = MutableStateFlow<ItemButton?>(null)
    val selectedMenuId = MutableStateFlow<String?>(null)
    val selectedMenuName = MutableStateFlow<String?>(null)

    val selectedLabelSize = MutableStateFlow(settingsManager.getLabelSize())
    val selectedDensity = MutableStateFlow(settingsManager.getDensity())
    val textSize = MutableStateFlow(settingsManager.getTextSize())

    // --- Opening Time State ---
    val selectedOpeningHour = MutableStateFlow(settingsManager.getOpeningHour())
    val selectedOpeningMinute = MutableStateFlow(settingsManager.getOpeningMinute())

    // --- Close Time State ---
    val selectedCloseHour = MutableStateFlow(settingsManager.getCloseHour())
    val selectedCloseMinute = MutableStateFlow(settingsManager.getCloseMinute())
    // --- Session Management ---

    fun initialize(activity: Activity) {
        viewModelScope.launch {
            // 💡 NEW: Wrap in try-catch
            try {
                if (AuthManager.isLoggedInWithValidSubscription(activity)) {
                    _sessionState.value = SessionState.LoggedIn()
                    validateSession(activity) // Perform initial validation
                } else {
                    _sessionState.value = SessionState.LoggedOut
                }
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Initialization failed, logging out.", e)
                FirebaseCrashlytics.getInstance().recordException(e)
                _sessionState.value = SessionState.Error(
                    "Failed to initialize session: ${e.message}",
                    isGracePeriodError = false
                )
                // Or simply:
                // _sessionState.value = SessionState.LoggedOut
            }
        }
    }

    fun onLoginSuccess() {
        _sessionState.value = SessionState.LoggedIn()
    }

    fun onLogout() {
        _sessionState.value = SessionState.LoggedOut
    }

    fun setSessionError(message: String, isGracePeriodError: Boolean) {
        _sessionState.value = SessionState.Error(message, isGracePeriodError)
    }

    fun revalidateAndLogin(activity: Activity) {
        viewModelScope.launch {
            val uid = AuthManager.getUid()
            if (uid != null) {
                val status = SubscriptionManager.revalidateSubscription(activity, uid)
                if (status.isValid && status.isDeviceBound) {
                    _sessionState.value = SessionState.LoggedIn()
                    validateSession(activity) // Re-validate after fixing
                } else {
                    _sessionState.value = SessionState.Error(status.errorMessage ?: "Subscription invalid", false)
                }
            } else {
                _sessionState.value = SessionState.LoggedOut
            }
        }
    }

    fun validateSession(activity: Activity) {
        viewModelScope.launch {
            if (AuthManager.getUid() == null) {
                if (_sessionState.value !is SessionState.LoggedOut) {
                    onLogout()
                }
                return@launch
            }

            val validationResult = AuthManager.validateCurrentSessionDetailed(activity)
            if (!validationResult.isValid) {
                val isGraceError = validationResult.reason == AuthManager.SessionInvalidReason.OFFLINE_GRACE_PERIOD_EXPIRED
                setSessionError(validationResult.errorMessage ?: "Session invalid", isGraceError)
            } else {
                _sessionState.value = SessionState.LoggedIn(
                    showRedBanner = validationResult.redBanner,
                    timeUntilDeactivation = validationResult.timeUntilDeactivation
                )
            }
        }
    }

    // --- UI and Settings Management ---
    fun setManufactureDateLock(isLocked: Boolean) {
        _isManufactureDateLocked.value = isLocked
    }
    fun selectItem(item: ItemButton) {
        selectedItem.value = item
    }
    fun selectMenu(id: String?, name: String?) {
        selectedMenuId.value = id
        selectedMenuName.value = name
    }
    fun updateLabelSize(newSize: String) {
        selectedLabelSize.value = newSize
        settingsManager.saveLabelSize(newSize)
    }
    fun updateDensity(newDensity: String) {
        selectedDensity.value = newDensity
        settingsManager.saveDensity(newDensity)
    }
    fun updateTextSize(newSize: Float) {
        textSize.value = newSize
        settingsManager.saveTextSize(newSize)
    }

    // --- Opening Time Update Functions ---
    fun updateOpeningHour(newHour: Int) {
        selectedOpeningHour.value = newHour
        settingsManager.saveOpeningHour(newHour)
    }

    fun updateOpeningMinute(newMinute: Int) {
        selectedOpeningMinute.value = newMinute
        settingsManager.saveOpeningMinute(newMinute)
    }

    // --- Close Time Update Functions ---
    fun updateCloseHour(newHour: Int) {
        selectedCloseHour.value = newHour
        settingsManager.saveCloseHour(newHour)
    }

    fun updateCloseMinute(newMinute: Int) {
        selectedCloseMinute.value = newMinute
        settingsManager.saveCloseMinute(newMinute)
    }

    // --- Print Job Management ---
    fun executePrintJob(
        labelName: String,
        description: String,
        manufactureTime: LocalDateTime,
        expiryDateTime: LocalDateTime,
        labelCount: Int
    ) {
        viewModelScope.launch {
            _printUiState.value = PrintUiState.Printing
            val densityValue = when (selectedDensity.value) {
                "Thin" -> 1
                "Medium" -> 7
                "Dense" -> 14
                else -> 14
            }
            val result = printerManager.printLabel(
                density = densityValue,
                labelName = labelName,
                description = description,
                manufactureTime = manufactureTime,
                expiryDateTime = expiryDateTime,
                labelCount = labelCount,
                labelSize = selectedLabelSize.value,
                textSize = textSize.value
            )
            result.onSuccess {
                _printUiState.value = PrintUiState.Success
            }.onFailure {
                _printUiState.value = PrintUiState.Error(it.message ?: "An unknown error occurred")
            }
        }
    }
    fun resetPrintState() {
        _printUiState.value = PrintUiState.Idle
    }
}