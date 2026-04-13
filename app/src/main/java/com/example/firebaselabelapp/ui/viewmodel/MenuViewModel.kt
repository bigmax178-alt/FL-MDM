package com.example.firebaselabelapp.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.firebaselabelapp.data.local.entities.ItemButtonEntity
import com.example.firebaselabelapp.data.local.entities.MenuButtonEntity
import com.example.firebaselabelapp.data.mappers.toModel
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.model.MenuButton
import com.example.firebaselabelapp.repository.ChangeType
import com.example.firebaselabelapp.repository.FirestoreRepository
import com.example.firebaselabelapp.repository.SuggestedChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuViewModel(private val repository: FirestoreRepository) : ViewModel() {

    companion object {
        private const val TAG = "MenuViewModel"
    }

    // --- States for Menus and Items (for CombinedMenuItemScreen) ---
    private val _menus = MutableStateFlow<List<MenuButton>>(emptyList())
    val menus: StateFlow<List<MenuButton>> = _menus.asStateFlow()

    private val _items = MutableStateFlow<List<ItemButton>>(emptyList())
    val items: StateFlow<List<ItemButton>> = _items.asStateFlow()

    private var menusJob: Job? = null
    private var itemsJob: Job? = null

    // --- Loading and Error States (for PrinterSettingsScreen & Home) ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // --- 💡 ADDED BACK: Sync Status Properties ---
    val isOnline: StateFlow<Boolean> = repository.isOnline
    val lastSyncFailed: StateFlow<Boolean> = repository.lastSyncFailed
    val isCompleteSyncRequired: StateFlow<Boolean> = repository.isCompleteSyncRequired
    // --- END ---

    // --- New Template Sync States (for PrinterSettingsScreen) ---
    private val _isCheckingSyncPlan = MutableStateFlow(false)
    val isCheckingSyncPlan: StateFlow<Boolean> = _isCheckingSyncPlan.asStateFlow()

    private val _isApplyingSyncPlan = MutableStateFlow(false)
    val isApplyingSyncPlan: StateFlow<Boolean> = _isApplyingSyncPlan.asStateFlow()

    private val _templateSyncPlan = MutableStateFlow<List<SuggestedChange>>(emptyList())
    val templateSyncPlan: StateFlow<List<SuggestedChange>> = _templateSyncPlan.asStateFlow()

    private val _loadingMessage = MutableStateFlow<String?>("Подождите...")
    val loadingMessage: StateFlow<String?> = _loadingMessage.asStateFlow()

    // --- Functions from MainActivity ---

    /**
     * Called from 'home' composable to load initial menu data.
     */
    fun loadData() {
        // Only start the job if it's not already running
        if (menusJob?.isActive == true) return

        _isLoading.value = true
        menusJob = viewModelScope.launch {
            try {
                repository.getMenuButtonsFlow()
                    .catch { e ->
                        _errorMessage.value = "Ошибка загрузки меню: ${e.message}"
                        _isLoading.value = false
                    }
                    .collect { menuList ->
                        _menus.value = menuList
                        // If a menu is selected or it's the first menu, load its items
                        if (menuList.isNotEmpty()) {
                            // Automatically load items for the first menu
                            loadItemsForMenu(menuList.first().id!!)
                        } else {
                            _items.value = emptyList() // No menus, so no items
                        }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Loads items for a specific menu. (Called from CombinedMenuItemScreen)
     */
    fun loadItemsForMenu(menuId: String) {
        // Cancel any previous item-loading job
        itemsJob?.cancel()
        _isLoading.value = true // Show loading while switching item lists
        itemsJob = viewModelScope.launch {
            try {
                repository.getItemButtonsFlow(menuId)
                    .catch { e ->
                        _errorMessage.value = "Ошибка загрузки товаров: ${e.message}"
                        _isLoading.value = false
                    }
                    .collect { itemList ->
                        _items.value = itemList
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка: ${e.message}"
                _isLoading.value = false
            }
        }
    }


    /**
     * Called from MainActivity when user logs out.
     */
    fun clearDataAndJobs() {
        menusJob?.cancel()
        itemsJob?.cancel()
        _menus.value = emptyList()
        _items.value = emptyList()
        _isLoading.value = false
        _errorMessage.value = null
        _successMessage.value = null
        _templateSyncPlan.value = emptyList()
    }

    // --- 💡 ADDED BACK: CRUD Functions for Menus ---

    fun addMenu(name: String) {
        if (name.isBlank()) {
            _errorMessage.value = "Название категории не может быть пустым"
            return
        }

        viewModelScope.launch {
            try {
                _errorMessage.value = null
                // Assuming the repository can take a null number for manual adds
                val newMenu = repository.addMenuButton(name.trim(), null)
                Log.d(TAG, "Added menu: ${newMenu.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding menu", e)
                _errorMessage.value = "Не удалось добавить категорию: ${e.message}"
            }
        }
    }

    fun deleteMenu(menu: MenuButton) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                repository.deleteMenuButton(menu)
                Log.d(TAG, "Deleted menu: ${menu.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting menu", e)
                _errorMessage.value = "Не удалось удалить категорию: ${e.message}"
            }
        }
    }

    fun updateMenu(updatedMenu: MenuButton) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null
                repository.updateMenuButton(updatedMenu)
                Log.d(TAG, "Menu's new name: ${updatedMenu.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error editing menu", e)
                _errorMessage.value = "Не удалось редактировать категорию: ${e.message}"
            }
        }
    }

    // --- END ---

    // --- Functions from PrinterSettingsScreen ---

    fun performDeltaSync() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.performDeltaSync()
                Log.d(TAG, "Delta sync completed from ViewModel")
            } catch (e: Exception) {
                Log.e(TAG, "Delta sync failed", e)
                _errorMessage.value = "Синхронизация не удалась: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.refreshDataFromServer()
                _successMessage.value = "База данных успешно обновлена"
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка обновления: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importData(text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.importDataAndSync(text)
                _successMessage.value = "Данные успешно импортированы"
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка импорта: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.forceSync()
                Log.d(TAG, "Force sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Force sync failed", e)
                _errorMessage.value = "Синхронизация не удалась: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun hardResetDatabase() {
        // CRITICAL FIX: We use GlobalScope here instead of viewModelScope.
        // This ensures that even if the Activity dies, the user navigates away,
        // or the ViewModel is cleared during the heavy DB operation,
        // the reset process WILL NOT be cancelled and will finish.
        GlobalScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            _loadingMessage.value = "Инициализация сброса..."

            try {
                // 1. Delete File
                // (We pass a callback to update the UI text from the background thread)
                repository.obliterateDatabaseFile { status ->
                    _loadingMessage.value = status
                }

                // 2. Force Sync
                _loadingMessage.value = "Загрузка данных из облака..."

                // We need to re-initialize the connection.
                // Since we destroyed the instance, the next call to repository methods
                // will automatically create a NEW fresh database instance.
                repository.forceSync()

                _successMessage.value = "База данных успешно восстановлена."
            } catch (e: Exception) {
                Log.e(TAG, "Hard reset failed", e)
                _errorMessage.value = "Ошибка сброса: ${e.message}"
            } finally {
                _isLoading.value = false
                _loadingMessage.value = null
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    // --- New Template Sync Functions (from PrinterSettingsScreen) ---

    fun loadTemplateSyncPlan() {
        viewModelScope.launch {
            _isCheckingSyncPlan.value = true
            _errorMessage.value = null
            _templateSyncPlan.value = emptyList()
            try {
                _templateSyncPlan.value = repository.generateTemplateSyncPlan()
                if (_templateSyncPlan.value.isEmpty()) {
                    _successMessage.value = "Сроки не требуют обновления"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка проверки сроков: ${e.message}"
            } finally {
                _isCheckingSyncPlan.value = false
            }
        }
    }

    fun clearTemplateSyncPlan() {
        _templateSyncPlan.value = emptyList()
    }


    fun applyTemplateSyncChanges(changes: List<SuggestedChange>) {
        viewModelScope.launch {
            _isApplyingSyncPlan.value = true
            _errorMessage.value = null
            try {
                // --- MODIFIED: Delegate all logic to the repository ---
                val successCount = repository.applyTemplateSyncChanges(changes)
                // --- END MODIFICATION ---

                _successMessage.value = "Успешно применено $successCount изменений"
                // Refresh the plan to show it's empty now (or show remaining failures)
                loadTemplateSyncPlan()
            } catch (e: Exception) {
                Log.e(TAG, "Error applying template changes", e)
                _errorMessage.value = "Ошибка применения изменений: ${e.message}"
            } finally {
                _isApplyingSyncPlan.value = false
            }
        }
    }
}