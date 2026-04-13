// ItemViewModel.kt - Remove redundant sorting since DB now handles it
package com.example.firebaselabelapp.ui.viewmodel

import android.content.ClipData
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.repository.FirestoreRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

class ItemViewModel(
    private val menuId: String,
    private val repository: FirestoreRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ItemViewModel"
    }

    var itemButtons by mutableStateOf<List<ItemButton>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Access repository network state
    val isOnline: StateFlow<Boolean> = repository.isOnline
    val lastSyncFailed: StateFlow<Boolean> = repository.lastSyncFailed

    init {
        loadItems()
        observeItems()
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        viewModelScope.launch {
            repository.isOnline
                .drop(1)
                .onEach { isOnline ->
                    if (isOnline && itemButtons.isEmpty()) {
                        Log.d(TAG, "Network restored and item list is empty, reloading items.")
                        reload()
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            if (AuthManager.getUid() == null) {
                errorMessage = "User not authenticated."
                isLoading = false
                return@launch
            }

            isLoading = true
            errorMessage = null

            try {
                val items = repository.getItemButtons(menuId)
                // No need to sort - database handles ordering
                itemButtons = items
                Log.d(TAG, "Loaded ${items.size} items for menu $menuId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading items for menu $menuId", e)
                errorMessage = "Failed to load items: ${e.message}"

                // 💡 LOG NON-FATAL ERROR TO FIREBASE
                FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                isLoading = false
            }
        }
    }

    private fun observeItems() {
        viewModelScope.launch {

            // 💡 ADD THIS SAFETY CHECK 💡
            // This prevents the repository call if the user
            // has been logged out by a background process.
            if (AuthManager.getUid() == null) {
                Log.w(TAG, "Cannot observe items, user is not authenticated.")
                errorMessage = "User session ended."
                return@launch
            }

            try {
                repository.getItemButtonsFlow(menuId)
                    .catch { e ->
                        Log.e(TAG, "Error observing items for menu $menuId", e)
                        errorMessage = "Failed to observe item changes: ${e.message}"

                        // 💡 LOG NON-FATAL ERROR TO FIREBASE
                        FirebaseCrashlytics.getInstance().recordException(e)
                    }
                    .collect { items ->
                        // No need to sort - database handles ordering
                        itemButtons = items
                        Log.d(TAG, "Items updated for menu $menuId via flow: ${items.size}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up items flow for menu $menuId", e)
                errorMessage = "Failed to set up item observation: ${e.message}"

                // 💡 LOG NON-FATAL ERROR TO FIREBASE
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun addItem(
        name: String,
        expDurationYears: Int?,
        expDurationMonths: Int?,
        expDurationDays: Int?,
        expDurationHours: Int?,
        description: String?,
    ) {
        viewModelScope.launch {
            if (AuthManager.getUid() == null) {
                errorMessage = "Cannot add item: User not authenticated."
                return@launch
            }

            try {
                errorMessage = null
                val newItem = repository.addItemButton(
                    name = name,
                    menuId = menuId,
                    expDurationYears = expDurationYears,
                    expDurationMonths = expDurationMonths,
                    expDurationDays = expDurationDays,
                    expDurationHours = expDurationHours,
                    description = description,
                )
                Log.d(TAG, "Added item: ${newItem.name} to menu $menuId")
                // The UI will update automatically through the flow
            } catch (e: Exception) {
                Log.e(TAG, "Error adding item to menu $menuId", e)
                errorMessage = "Failed to add item: ${e.message}"
            }
        }
    }

    fun deleteItem(item: ItemButton) {
        viewModelScope.launch {
            if (AuthManager.getUid() == null) {
                errorMessage = "Cannot delete item: User not authenticated."
                return@launch
            }

            try {
                errorMessage = null
                repository.deleteItemButton(item)
                Log.d(TAG, "Deleted item: ${item.name} from menu $menuId")
                // The UI will update automatically through the flow
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting item from menu $menuId", e)
                errorMessage = "Failed to delete item: ${e.message}"
            }
        }
    }

    fun updateItem(updatedItem: ItemButton) {
        viewModelScope.launch {
            if (AuthManager.getUid() == null){
                errorMessage = "Cannot edit item: User not authenticated."
                return@launch
            }

            try {
                errorMessage = null
                repository.updateItemButton(updatedItem)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating item: ${updatedItem.id}(${updatedItem.name}) in menu $menuId ")
            }
        }
    }

    fun reload() {
        loadItems()
    }

    fun clearError() {
        errorMessage = null
    }
}