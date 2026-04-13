// Updated FirestoreRepository.kt - Added complete sync state tracking
package com.example.firebaselabelapp.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import android.widget.Toast
import com.example.firebaselabelapp.auth.AuthManager
import com.example.firebaselabelapp.data.SharedPreferencesManager
import com.example.firebaselabelapp.data.local.AppDatabase
import com.example.firebaselabelapp.data.local.entities.ItemButtonEntity
import com.example.firebaselabelapp.data.local.entities.MenuButtonEntity
import com.example.firebaselabelapp.data.mappers.toEntity
import com.example.firebaselabelapp.data.mappers.toModel
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.model.MenuButton
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale
import java.util.UUID

// NEW: Data classes for the template sync plan
enum class ChangeType {
    ADD,
    UPDATE,
    DELETE
}

data class SuggestedChange(
    val type: ChangeType,
    val entityType: String, // "Menu" or "Item"
    val userEntity: Any?, // User's MenuButtonEntity or ItemButtonEntity (null if ADD)
    val templateEntity: Any?, // Template's MenuButton or ItemButton (null if DELETE)
    val reason: String // e.g., "New item from template", "Expiry mismatch", "Not in template"
)

class FirestoreRepository(private val context: Context) {

    private val db = Firebase.firestore
//    private val localDb = AppDatabase.getDatabase(context)
//    private val menuDao = localDb.menuButtonDao()
//    private val itemDao = localDb.itemButtonDao()
    private val localDb get() = AppDatabase.getDatabase(context)
    private val menuDao get() = localDb.menuButtonDao()
    private val itemDao get() = localDb.itemButtonDao()
    private val TAG = "FirestoreRepository"
    private val russianLocale = Locale("ru", "RU")
    private val prefsManager = SharedPreferencesManager(context)

    // Network state tracking
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastSyncFailed = MutableStateFlow(false)
    val lastSyncFailed: StateFlow<Boolean> = _lastSyncFailed.asStateFlow()

    // This flag tracks if the local data is incomplete (persistent state)
    private val _isSyncRequired = MutableStateFlow(false)


    // NEW: Track which menus have had their items synced
    private val syncedMenuIds = prefsManager.getSyncedMenuIds().toMutableSet()
    private val _isCompleteSyncRequired = MutableStateFlow(true) // Start as true - assume we need full sync initially
    val isCompleteSyncRequired = _isCompleteSyncRequired.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        setupNetworkCallback()
        checkInitialNetworkState()

        // Check sync status as soon as the app starts
        checkInitialSyncStatus()
    }

    private fun checkInitialSyncStatus() {
        // Use a scope that lives with the repository to perform this initial check
        // This is safe to do in the init block.
        kotlinx.coroutines.GlobalScope.launch {
            updateCompleteSyncStatus()
        }
    }

    // Add this new function to FirestoreRepository class
// In FirestoreRepository.kt

    // Update the signature to accept a status callback
    suspend fun obliterateDatabaseFile(onStatusUpdate: (String) -> Unit) {
        onStatusUpdate("Закрытие соединений...")

        // 1. Kill the Room instance completely
        AppDatabase.destroyInstance()

        // Small delay to let the OS release file locks
        kotlinx.coroutines.delay(500)

        onStatusUpdate("Удаление файлов...")

        // 2. Try to delete the database
        // We delete the main file AND the WAL/SHM temporary files
        val dbName = "app_database"
        val dbFile = context.getDatabasePath(dbName)

        val deleted = if (dbFile.exists()) {
            context.deleteDatabase(dbName)
        } else {
            // If file doesn't exist, we consider it "deleted"
            true
        }

        if (deleted) {
            Log.d(TAG, "DATABASE FILE DELETED SUCCESSFULLY")

            // Reset sync flags
            _isCompleteSyncRequired.value = true
            syncedMenuIds.clear()
            prefsManager.saveSyncedMenuIds(emptySet())

        } else {
            Log.e(TAG, "FAILED TO DELETE DATABASE FILE")
            throw Exception("Не удалось удалить файл базы данных. Попробуйте очистить данные приложения в настройках Android.")
        }
    }

    private fun setupNetworkCallback() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                _isOnline.value = false
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed. Has internet: $hasInternet")
                _isOnline.value = hasInternet
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback?.let { callback ->
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        }
    }

    private fun checkInitialNetworkState() {
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val hasInternet =
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        _isOnline.value = hasInternet
        Log.d(TAG, "Initial network state: $hasInternet")
    }

    fun cleanup() {
        networkCallback?.let { callback ->
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    // Get current UID dynamically to handle auth state changes
    private fun getCurrentUid(): String? = AuthManager.getUid()

    suspend fun getAllItems(): List<ItemButton> {
        val currentUid = getCurrentUid() ?: return emptyList()
        Log.d(TAG, "Getting all local items for search for user: $currentUid")
        // Fetch all items directly from the local database
        return itemDao.getAllItemButtons(currentUid).map { it.toModel() }
    }


    // Helper method to handle Firestore operations with proper error handling
    private suspend fun <T> executeFirestoreOperation(
        operation: suspend () -> T,
        onSuccess: ((T) -> Unit)? = null,
        onFailure: ((Exception) -> Unit)? = null
    ): T? {
        return try {
            val result = operation()
            _lastSyncFailed.value = false
            onSuccess?.invoke(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Firestore operation failed", e)
            _lastSyncFailed.value = true
            onFailure?.invoke(e)
            null
        }
    }

    // NEW: Helper method to update complete sync status
    private suspend fun updateCompleteSyncStatus() {
        val currentUid = getCurrentUid() ?: return

        try {
            // Get all menus from local DB
            val localMenus = menuDao.getAllMenuButtonsSync(currentUid)
            val allMenuIds = localMenus.map { it.id }.toSet()

            // Check if all menus have been synced
            val allMenusSynced = allMenuIds.isNotEmpty() && syncedMenuIds.containsAll(allMenuIds)

            // Update the complete sync status
            val wasCompleteSyncRequired = _isCompleteSyncRequired.value
            _isCompleteSyncRequired.value = !allMenusSynced || _isSyncRequired.value

            Log.d(
                TAG,
                "Complete sync status updated: was=$wasCompleteSyncRequired, now=${_isCompleteSyncRequired.value}"
            )
            Log.d(TAG, "All menus (${allMenuIds.size}): $allMenuIds")
            Log.d(TAG, "Synced menus (${syncedMenuIds.size}): $syncedMenuIds")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating complete sync status", e)
            _isCompleteSyncRequired.value = true
        }
    }

    // MENU BUTTONS

    suspend fun getMenuButtons(): List<MenuButton> {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")

        Log.d(TAG, "Getting menu buttons for user: $currentUid from LOCAL DB")

        // Always return from local database
        val localMenus = menuDao.getAllMenuButtonsSync(currentUid).map { it.toModel() }
            .sortedBy { it.name.lowercase(russianLocale) }
        Log.d(TAG, "Returning ${localMenus.size} menu buttons from local DB")

        return localMenus
    }

    fun getMenuButtonsFlow(): Flow<List<MenuButton>> {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        Log.d(TAG, "Setting up menu buttons flow for user: $currentUid")
        return menuDao.getAllMenuButtons(currentUid).map { entities ->
            val menus = entities.map { it.toModel() }.sortedBy { it.name.lowercase(russianLocale) }
            Log.d(TAG, "Flow emitting ${menus.size} menu buttons")
            menus
        }
    }

    suspend fun refreshDataFromServer() {
        Log.d(TAG, "Starting non-destructive refresh from server")
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")

        if (!_isOnline.value) {
//            throw Exception("Cannot refresh while offline")
            Toast.makeText(context, "Нет подключения к интернету", Toast.LENGTH_SHORT).show()
            return
        }

        var didAnySyncFail = false

        // 1. Sync all menus. This will add new ones and update existing ones
        //    without deleting anything.
        try {
            syncMenuButtonsFromFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh menus.", e)
            didAnySyncFail = true
            // We probably should stop if we can't even get the menu list
            _lastSyncFailed.value = true
            return
        }

        // 2. Get all the local menus (which are now up-to-date).
        val localMenus = menuDao.getAllMenuButtonsSync(currentUid)
        Log.d(TAG, "Found ${localMenus.size} menus, refreshing their items")

        // 3. Sync items for each menu.
        for (menu in localMenus) {
            try {
                syncItemButtonsFromFirestore(menu.id)
                syncedMenuIds.add(menu.id) // Mark as synced
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync items for menu ${menu.id}. Continuing...", e)
                didAnySyncFail = true
            }
        }

        // 4. Update sync flags
        _lastSyncFailed.value = didAnySyncFail
        if (!didAnySyncFail) {
            Log.d(TAG, "Refresh completed successfully.")
            _isCompleteSyncRequired.value = false // Everything is now synced
            prefsManager.saveSyncedMenuIds(syncedMenuIds)
        } else {
            Log.w(TAG, "Refresh completed with some failures.")
        }
    }

    suspend fun syncMenuButtonsFromFirestore() {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated.")
        Log.d(TAG, "Performing full menu reconciliation for user: $currentUid")

        try {
            val userMenuButtonsCollection = db.collection("users").document(currentUid).collection("menu_buttons")

            // 1. Fetch ALL remote documents for the user
            var allRemoteDocs = userMenuButtonsCollection.get().await().documents

            // Check if the collection is empty OR if all existing documents are marked as deleted.
            val shouldLoadTemplate = allRemoteDocs.isEmpty() || allRemoteDocs.all { doc ->
                doc.getBoolean("isDeleted") == true
            }

            // If the collection is empty, it's a new user. So we populate with template data
            if (shouldLoadTemplate) {
                if (allRemoteDocs.isEmpty()) {
                    Log.d(TAG, "No menu buttons found in Firestore. Populating from template.")
                } else {
                    Log.d(TAG, "All existing menu buttons are marked as deleted. Populating from template.")
                }
                copyTemplateDataToUser(currentUid)

                // After copying, we must re-fetch the documents
                // so the rest of this function can process them.
                allRemoteDocs = userMenuButtonsCollection.get().await().documents
                Log.d(TAG, "Re-fetched ${allRemoteDocs.size} menu buttons after template copy.")
            }

            // 2. Partition remote data into active entities and IDs of deleted items
            val activeRemoteEntities = mutableListOf<MenuButtonEntity>()
            val deletedRemoteIds = mutableSetOf<String>()

            allRemoteDocs.forEach { doc ->
                if (doc.getBoolean("isDeleted") == true) {
                    deletedRemoteIds.add(doc.id)
                } else {
                    // This will now correctly map the 'number' field from Firestore
                    doc.toObject(MenuButtonEntity::class.java)?.copy(id = doc.id, userId = currentUid)?.let {
                        activeRemoteEntities.add(it)
                    }
                }
            }
            Log.d(TAG, "Found ${activeRemoteEntities.size} active and ${deletedRemoteIds.size} deleted menus on server.")

            // 3. Get all current LOCAL menu IDs
            val localMenuIds = menuDao.getAllMenuButtonsSync(currentUid).map { it.id }.toSet()
            val activeRemoteIds = activeRemoteEntities.map { it.id }.toSet()

            // 4. Calculate which local menus to delete. This includes:
            //    - Menus that are no longer active on the server.
            //    - Menus explicitly marked as deleted on the server.
            val idsToDeleteLocally = localMenuIds.filter { it !in activeRemoteIds }.toMutableSet()
            idsToDeleteLocally.addAll(deletedRemoteIds)

            // 5. Perform the database operations
            if (activeRemoteEntities.isNotEmpty()) {
                Log.d(TAG, "Upserting ${activeRemoteEntities.size} active menus locally.")
                menuDao.insertMenuButtons(activeRemoteEntities)
            }

            if (idsToDeleteLocally.isNotEmpty()) {
                Log.d(TAG, "Deleting ${idsToDeleteLocally.size} stale/deleted menus locally.")
                menuDao.deleteMenuButtonsByIds(idsToDeleteLocally.toList())
            }

            Log.d(TAG, "Menu reconciliation complete.")

        } catch (e: Exception) {
            Log.e(TAG, "A failure occurred during full menu sync", e)
            throw e // Re-throw to be caught by the calling function (refreshDataFromServer)
        }
    }

    suspend fun addMenuButton(name: String, number: String? = null): MenuButton {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        val id = UUID.randomUUID().toString()

        Log.d(TAG, "Adding menu button: $name with ID: $id")

        // User-created buttons have a null number
        val menuButton = MenuButton(id = id, name = name, number = null)
        val entity = menuButton.toEntity(currentUid)

        // Save locally first
        menuDao.insertMenuButton(entity)
        Log.d(TAG, "Menu button saved locally")

        // Update complete sync status since we added a new menu without syncing its items
        _isCompleteSyncRequired.value = true

        // Try to sync to Firestore if online
        if (_isOnline.value) {
            executeFirestoreOperation(
                operation = {
                    val docRef = db.collection("users").document(currentUid)
                        .collection("menu_buttons").document(id)
                    docRef.set(
                        mapOf(
                            "name" to name,
                            "id" to id,
                            "number" to number,
                            "isDeleted" to false,
                            "lastModified" to FieldValue.serverTimestamp()
                        )
                    ).await()
                },
                onSuccess = { Log.d(TAG, "Menu button synced to Firestore") },
                onFailure = { Log.w(TAG, "Failed to sync menu button to Firestore: ${it.message}") }
            )
        } else {
            Log.d(TAG, "Offline - menu button will sync when connection is restored")
            _lastSyncFailed.value = true
        }

        return menuButton
    }

    suspend fun deleteMenuButton(button: MenuButton) {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        val menuId = button.id ?: throw Exception("Button ID is null")

        Log.d(TAG, "Deleting menu button: ${button.name} with ID: $menuId")

        // Remove from synced set when deleting
        syncedMenuIds.remove(menuId)

        // Delete locally first (hard delete)
        menuDao.hardDeleteMenuButton(menuId)
        itemDao.deleteItemButtonsByMenuId(menuId)

        // Update complete sync status
        updateCompleteSyncStatus()

        // Try to sync deletion to Firestore if online
        if (_isOnline.value) {
            executeFirestoreOperation(
                operation = {
                    // Delete the menu button document
                    val menuRef = db.collection("users").document(currentUid)
                        .collection("menu_buttons")
                        .document(menuId)
                    menuRef.update(mapOf(
                        "isDeleted" to true,
                        "lastModified" to FieldValue.serverTimestamp()
                    ))

                    // Query and delete all associated item buttons
                    val itemsToDeleteSnapshot = db.collection("users").document(currentUid)
                        .collection("item_buttons")
                        .whereEqualTo("menuId", menuId).get().await()

                    itemsToDeleteSnapshot.documents.forEach { document ->
                        document.reference.update(mapOf(
                            "isDeleted" to true,
                            "lastModified" to FieldValue.serverTimestamp()
                        )).await()
                    }

                    itemsToDeleteSnapshot.documents.size
                },
                onSuccess = { deletedItemsCount ->
                    Log.d(TAG, "Menu button and $deletedItemsCount items deleted from Firestore")
                },
                onFailure = {
                    Log.w(
                        TAG,
                        "Failed to sync menu button deletion to Firestore: ${it.message}"
                    )
                }
            )
        } else {
            Log.d(TAG, "Offline - menu button deletion will sync when connection is restored")
            _lastSyncFailed.value = true
        }
    }

    suspend fun updateMenuButton(updatedMenuButton: MenuButton) {
        val currentId = getCurrentUid() ?: throw Exception("User is not authenticated")
        val buttonId = updatedMenuButton.id ?: throw Exception("Menu button should have an id")
        Log.d(TAG, "Updating button with id: $buttonId to new name: ${updatedMenuButton.name}")

        menuDao.updateMenuButton(updatedMenuButton.toEntity(currentId))


        if (_isOnline.value) {
            executeFirestoreOperation(
                operation = {
                    val menuRef = db.collection("users").document(currentId)
                        .collection("menu_buttons").document(buttonId)
                    val data = mapOf(
                        "name" to updatedMenuButton.name,
                        "number" to updatedMenuButton.number, // UPDATED: sync number
                        "lastModified" to FieldValue.serverTimestamp()
                    )
                    menuRef.update(data)
                },
                onSuccess = { Log.d(TAG, "Edited menu button synced to Firestore")

                            },
                onFailure = { Log.w(TAG, "Failed to sync edited menu button to Firestore") }
            )
        } else {
            Log.d(TAG, "Offline - menu button changes will sync when connection is restored")
            _lastSyncFailed.value = true
        }
    }

    // ITEM BUTTONS

    suspend fun getItemButtons(menuId: String): List<ItemButton> {
        val currentUid = getCurrentUid() ?: return emptyList()

        Log.d(TAG, "Getting item buttons for menu: $menuId, user: $currentUid")

        // Always return from local database
        val localItems = itemDao.getItemButtonsByMenuIdSync(menuId, currentUid).map { it.toModel() }
            .sortedBy { it.name.lowercase(russianLocale) }
        Log.d(TAG, "Returning ${localItems.size} item buttons from local DB")
        return localItems
    }

    fun getItemButtonsFlow(menuId: String): Flow<List<ItemButton>> {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        Log.d(TAG, "Setting up item buttons flow for menu: $menuId, user: $currentUid")
        return itemDao.getItemButtonsByMenuId(menuId, currentUid).map { entities ->
            val items = entities.map { it.toModel() }.sortedBy { it.name.lowercase(russianLocale) }
            Log.d(TAG, "Flow emitting ${items.size} item buttons for menu: $menuId")
            items
        }
    }


    private suspend fun syncItemButtonsFromFirestore(menuId: String) {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated.")
        Log.d(TAG, "Performing full item reconciliation for menu: $menuId")

        try {
            // 1. Fetch ALL remote items for this specific menu
            val snapshot = db.collection("users").document(currentUid)
                .collection("item_buttons").whereEqualTo("menuId", menuId).get().await()
            Log.d(TAG, "For menuId '$menuId', Firestore query found ${snapshot.documents.size} items.")
            // 2. Partition remote data
            val activeRemoteEntities = mutableListOf<ItemButtonEntity>()
            val deletedRemoteIds = mutableSetOf<String>()

            snapshot.documents.forEach { doc ->
                if (doc.getBoolean("isDeleted") == true) {
                    deletedRemoteIds.add(doc.id)
                } else {
                    // This will now correctly map the 'number' field
                    doc.toObject(ItemButtonEntity::class.java)?.copy(id = doc.id, userId = currentUid)?.let {
                        activeRemoteEntities.add(it)
                    }
                }
            }
            Log.d(TAG, "Found ${activeRemoteEntities.size} active and ${deletedRemoteIds.size} deleted items on server for menu $menuId.")

            // 3. Get all current LOCAL item IDs for this menu
            val localItemIds = itemDao.getItemButtonsByMenuIdSync(menuId, currentUid).map { it.id }.toSet()
            val activeRemoteIds = activeRemoteEntities.map { it.id }.toSet()

            // 4. Calculate which local items to delete
            val idsToDeleteLocally = localItemIds.filter { it !in activeRemoteIds }.toMutableSet()
            idsToDeleteLocally.addAll(deletedRemoteIds)

            // 5. Perform the database operations
            if (activeRemoteEntities.isNotEmpty()) {
                itemDao.insertItemButtons(activeRemoteEntities)
            }
            if (idsToDeleteLocally.isNotEmpty()) {
                itemDao.deleteItemButtonsByIds(idsToDeleteLocally.toList())
            }

            Log.d(TAG, "Item reconciliation complete for menu: $menuId.")

        } catch (e: Exception) {
            Log.e(TAG, "A failure occurred during full item sync for menu: $menuId", e)
            throw e
        }
    }

    suspend fun addItemButton(
        name: String,
        menuId: String,
        expDurationYears: Int?,
        expDurationMonths: Int?,
        expDurationDays: Int?,
        expDurationHours: Int?,
        description: String?,
        number: String? = null
    ): ItemButton {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        val id = UUID.randomUUID().toString()

        Log.d(TAG, "Adding item button: $name with ID: $id to menu: $menuId")

        val itemButton = ItemButton(
            id = id,
            number = number, // UPDATED: Set number
            name = name,
            menuId = menuId,
            expDurationYears = expDurationYears,
            expDurationMonths = expDurationMonths,
            expDurationDays = expDurationDays,
            expDurationHours = expDurationHours,
            description = description,
        )
        val entity = itemButton.toEntity(currentUid)

        // Save locally first
        itemDao.insertItemButton(entity)
        Log.d(TAG, "Item button saved locally")

        // Try to sync to Firestore if online
        if (_isOnline.value) {
            executeFirestoreOperation(
                operation = {
                    val docRef = db.collection("users").document(currentUid)
                        .collection("item_buttons").document(id)

                    val data = mapOf(
                        "id" to id,
                        "number" to number, // UPDATED: Add number to map
                        "name" to name,
                        "menuId" to menuId,
                        "expDurationYears" to expDurationYears,
                        "expDurationMonths" to expDurationMonths,
                        "expDurationDays" to expDurationDays,
                        "expDurationHours" to expDurationHours,
                        "description" to description,
                        "isDeleted" to false, // Important for soft deletes
                        "lastModified" to FieldValue.serverTimestamp()
                    )

                    docRef.set(data).await()
                },
                onSuccess = { Log.d(TAG, "Item button synced to Firestore") },
                onFailure = { Log.w(TAG, "Failed to sync item button to Firestore: ${it.message}") }
            )
        } else {
            Log.d(TAG, "Offline - item button will sync when connection is restored")
            _lastSyncFailed.value = true
        }

        return itemButton
    }

    suspend fun deleteItemButton(item: ItemButton) {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        val itemId = item.id ?: throw Exception("Item ID is null")

        Log.d(TAG, "Deleting item button: ${item.name} with ID: $itemId")

        // Delete locally first (soft delete)
//        itemDao.softDeleteItemButton(itemId)
        // Perform a HARD delete from the local DB
        itemDao.hardDeleteItemButton(itemId)
        // Try to sync deletion to Firestore if online
        if (_isOnline.value) {
            executeFirestoreOperation(
                operation = {
                    val itemRef = db.collection("users").document(currentUid)
                        .collection("item_buttons")
                        .document(itemId)
                    // Use update for a soft delete, not delete()
                    itemRef.update(
                        mapOf(
                            "isDeleted" to true,
                            "lastModified" to FieldValue.serverTimestamp()
                        )
                    ).await()
                },
                onSuccess = { Log.d(TAG, "Item button deleted from Firestore") },
                onFailure = {
                    Log.w(
                        TAG,
                        "Failed to sync item button deletion to Firestore: ${it.message}"
                    )
                }
            )
        } else {
            Log.d(TAG, "Offline - item button deletion will sync when connection is restored")
            _lastSyncFailed.value = true
        }
    }

    suspend fun updateItemButton(updatedItem: ItemButton) {
        val currentId = getCurrentUid() ?: throw Exception("User is not authenticated")

        Log.d(TAG, "Updating item button: ${updatedItem.name} with id: ${updatedItem.id}")
        itemDao.updateItemButton(updatedItem.toEntity(currentId))

        if (_isOnline.value) {
            executeFirestoreOperation(
                operation = {
                    val itemRef = db.collection("users").document(currentId)
                        .collection("item_buttons").document(updatedItem.id!!)
                    val data = mapOf(
                        "id" to updatedItem.id,
                        "number" to updatedItem.number, // UPDATED: Add number
                        "name" to updatedItem.name,
                        "menuId" to updatedItem.menuId,
                        "expDurationYears" to updatedItem.expDurationYears,
                        "expDurationMonths" to updatedItem.expDurationMonths,
                        "expDurationDays" to updatedItem.expDurationDays,
                        "expDurationHours" to updatedItem.expDurationHours,
                        "description" to updatedItem.description,
                        "lastModified" to FieldValue.serverTimestamp()
                    )
                    itemRef.update(data)
                },
                onSuccess = { Log.d(TAG, "Edited item button synced to Firestore") },
                onFailure = { Log.w(TAG, "Failed to sync edited item button to Firestore") }
            )
        } else {
            Log.d(TAG, "Offline - item button changes will sync when connection is restored")
            _lastSyncFailed.value = true
        }

    }

    // TEMPLATE COPYING (with better logging)
    private suspend fun copyTemplateDataToUser(userId: String) {
        Log.d(TAG, "Copying template data to user: $userId")

        val userDocRef = db.collection("users").document(userId)
        val templateMenuRef =
            db.collection("templates").document("default").collection("menu_buttons")
        val templateItemRef =
            db.collection("templates").document("default").collection("item_buttons")

        val templateMenuButtons = templateMenuRef.get().await()
        val templateItemButtons = templateItemRef.get().await()

        Log.d(TAG, "Found ${templateMenuButtons.documents.size} template menu buttons")
        Log.d(TAG, "Found ${templateItemButtons.documents.size} template item buttons")

        if (templateMenuButtons.isEmpty) {
            Log.w(TAG, "Warning: Template 'menu_buttons' collection is empty.")
            return
        }

        val userMenuButtonsRef = userDocRef.collection("menu_buttons")
        val userItemButtonsRef = userDocRef.collection("item_buttons")
        val idMapping = mutableMapOf<String, String>()

        // Copy menu buttons and map old IDs to new IDs
        for (templateDoc in templateMenuButtons.documents) {
            val templateData = templateDoc.data
            if (templateData != null) {
                // ADDED: Ensure lastModified and isDeleted are set for the new user
                val newMenuData = templateData.toMutableMap()
                newMenuData["lastModified"] = FieldValue.serverTimestamp()
                newMenuData["isDeleted"] = false

                val newDocRef = userMenuButtonsRef.add(newMenuData).await()
                idMapping[templateDoc.id] = newDocRef.id
                Log.d(TAG, "Copied menu button: ${templateDoc.id} -> ${newDocRef.id}")
            }
        }

        // Copy item buttons, updating their menuId to the new mapped IDs
        for (templateDoc in templateItemButtons.documents) {
            val templateData = templateDoc.data?.toMutableMap()
            if (templateData != null) {
                val oldMenuId = templateData["menuId"] as? String
                if (oldMenuId != null && idMapping.containsKey(oldMenuId)) {
                    templateData["menuId"] = idMapping[oldMenuId]!!

                    // ADDED: Ensure lastModified and isDeleted are set for the new user
                    templateData["lastModified"] = FieldValue.serverTimestamp()
                    templateData["isDeleted"] = false

                    Log.d(TAG, "Mapping item menuId: $oldMenuId -> ${idMapping[oldMenuId]}")
                    userItemButtonsRef.add(templateData).await()
                } else {
                    Log.w(TAG, "Skipping item ${templateDoc.id}, could not map old menuId '$oldMenuId'")
                }
            }
        }
        Log.d(TAG, "Successfully copied template data to user $userId")
    }

    // UTILITY METHODS
    suspend fun clearLocalData() {
        val currentUid = getCurrentUid() ?: return
        Log.d(TAG, "Clearing local data for user: $currentUid")
        menuDao.deleteAllMenuButtons(currentUid)
        itemDao.deleteAllItemButtons(currentUid)

        // Clear sync tracking
        syncedMenuIds.clear()
        prefsManager.saveSyncedMenuIds(emptySet())
        _isCompleteSyncRequired.value = true
    }

    suspend fun performDeltaSync() {
        val currentUid = getCurrentUid() ?: return
        if (!_isOnline.value) {
            Log.w(TAG, "Cannot sync, user is offline.")
            return
        }

        try {
            // OPTIMIZED PRE-SYNC CHECK
            // Check if the user's menu collection is effectively empty.
            val activeMenuCheck = db.collection("users").document(currentUid)
                .collection("menu_buttons")
                .whereEqualTo("isDeleted", false) // Find documents that are active
                .limit(1)                         // Stop as soon as you find one
                .get()
                .await()

            if (activeMenuCheck.isEmpty) {
                Log.d(TAG, "User has no active menus. Running full refresh instead of delta sync.")
                refreshDataFromServer()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform pre-sync check.", e)
            _lastSyncFailed.value = true
            return // Can't proceed if the check fails
        }


        Log.d(TAG, "Starting delta sync...")
        val lastSyncTimestamp = prefsManager.lastSyncTimestamp
        Log.d(TAG, "Last sync timestamp: $lastSyncTimestamp")
        val newSyncTimestamp = System.currentTimeMillis()

        try {
            // 1. Sync Menu Buttons
            val menuQuery = db.collection("users").document(currentUid)
                .collection("menu_buttons")
                .whereGreaterThan("lastModified", Date(lastSyncTimestamp))

            val changedMenusSnapshot = menuQuery.get().await()

            val menusToUpsert = mutableListOf<MenuButtonEntity>()
            val menuIdsToDelete = mutableListOf<String>()

            changedMenusSnapshot.documents.forEach { doc ->
                val isDeleted = doc.getBoolean("isDeleted") == true
                if (isDeleted) {
                    menuIdsToDelete.add(doc.id)
                } else {
                    doc.toObject(MenuButtonEntity::class.java)?.copy(id = doc.id, userId = currentUid)?.let {
                        menusToUpsert.add(it)
                    }
                }
            }

            Log.d(TAG, "Found ${menusToUpsert.size} changed/new menus and ${menuIdsToDelete.size} deleted menus.")
            if (menusToUpsert.isNotEmpty()) {
                menuDao.insertMenuButtons(menusToUpsert)
            }
            if (menuIdsToDelete.isNotEmpty()) {
                menuDao.deleteMenuButtonsByIds(menuIdsToDelete) // You'll need to add this to your DAO
            }

            // 2. Sync Item Buttons
            val itemQuery = db.collection("users").document(currentUid)
                .collection("item_buttons")
                .whereGreaterThan("lastModified", Date(lastSyncTimestamp))

            val changedItemsSnapshot = itemQuery.get().await()
            val itemsToUpsert = mutableListOf<ItemButtonEntity>()
            val itemIdsToDelete = mutableListOf<String>()

            changedItemsSnapshot.documents.forEach { doc ->
                val isDeleted = doc.getBoolean("isDeleted") == true
                if (isDeleted) {
                    itemIdsToDelete.add(doc.id)
                } else {
                    doc.toObject(ItemButtonEntity::class.java)?.copy(id = doc.id, userId = currentUid)?.let {
                        itemsToUpsert.add(it)
                    }
                }
            }

            Log.d(TAG, "Found ${itemsToUpsert.size} changed/new items and ${itemIdsToDelete.size} deleted items.")
            if (itemsToUpsert.isNotEmpty()) {
                itemDao.insertItemButtons(itemsToUpsert)
            }
            if (itemIdsToDelete.isNotEmpty()) {
                itemDao.deleteItemButtonsByIds(itemIdsToDelete) // You'll need to add this to your DAO
            }


            // 3. If everything succeeded, update the timestamp
            prefsManager.lastSyncTimestamp = newSyncTimestamp
            _lastSyncFailed.value = false
            Log.d(TAG, "Delta sync successful. New timestamp: $newSyncTimestamp")
            Log.d(TAG, "Updatigng synced menu IDs post-delta sync.")
            val localMenus = menuDao.getAllMenuButtonsSync(currentUid)
            syncedMenuIds.clear()
            syncedMenuIds.addAll(localMenus.map {it.id})
            prefsManager.saveSyncedMenuIds(syncedMenuIds)

            updateCompleteSyncStatus()

        } catch (e: Exception) {
            Log.e(TAG, "Delta sync failed", e)
            _lastSyncFailed.value = true
        }
    }

    suspend fun forceSync() {
        Log.d(TAG, "Starting force sync")
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")

        if (!_isOnline.value) {
            throw Exception("Cannot sync while offline")
        }

        var didAnySyncFail = false

        // Clear local data and sync tracking
        clearLocalData()

        try {
            syncMenuButtonsFromFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Failed to sync menus. Aborting sync.", e)
            _lastSyncFailed.value = true
            _isSyncRequired.value = true
            _isCompleteSyncRequired.value = true
            return
        }

        val localMenus = menuDao.getAllMenuButtonsSync(currentUid)
        Log.d(TAG, "Found ${localMenus.size} menus, syncing their items")

        // Sync items for each menu
        for (menu in localMenus) {
            try {
                Log.d(TAG, "Syncing items for menu: ${menu.name} (${menu.id})")
                syncItemButtonsFromFirestore(menu.id)
                syncedMenuIds.add(menu.id) // Mark as synced
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync items for menu ${menu.id}. Continuing...", e)
                didAnySyncFail = true
            }
        }

        // Update all sync flags based on the final outcome
        _isSyncRequired.value = didAnySyncFail
        _lastSyncFailed.value = didAnySyncFail
        _isCompleteSyncRequired.value = didAnySyncFail

        if (!didAnySyncFail) {
            Log.d(TAG, "Force sync completed successfully - all menus and items synced")
            prefsManager.saveSyncedMenuIds(syncedMenuIds)
        } else {
            Log.w(TAG, "Force sync completed with some failures.")
        }
    }

    // Data class to hold 4 values (since Kotlin doesn't have built-in Quadruple)
    private data class LabelDateData<out A, out B, out C, out D, out E>(
        val first: A,   // name
        val second: B,  // years
        val third: C,   // months
        val fourth: D,  // days
        val fifth: E    // hours
    )

    /**
     * Parses a line of text to extract an item name and its expiration duration.
     */
    private fun parseItemAndDuration(line: String): LabelDateData<String, Int, Int, Int, Int> {
        val trimmed = line.trim()
        val match = "(\\d+)(?!.*\\d)".toRegex().find(trimmed)

        if (match == null) {
            return LabelDateData(trimmed, 0, 0, 0, 0)
        }

        val numberValue = match.value
        val numberStartIndex = match.range.first
        val name = trimmed.substring(0, numberStartIndex).trim()
        val unit = trimmed.substring(numberStartIndex + numberValue.length).trim().lowercase()
        val value = numberValue.toInt()

        var years = 0
        var months = 0
        var days = 0
        var hours = 0

        when {
            unit.startsWith("год") || unit.startsWith("г") -> years = value
            unit.startsWith("мес") -> months = value
            unit.startsWith("сут") || unit.startsWith("д") -> days = value
            unit.startsWith("ч") || unit.startsWith("час") -> hours = value
            else -> {
                Log.w("FirestoreRepository", "Unrecognized duration unit in line: '$line'")
                return LabelDateData(line, 0, 0, 0, 0)
            }
        }
        return LabelDateData(name, years, months, days, hours)
    }

    /**
     * Populates the user's Firestore database with parsed data and forces a local sync.
     */
    suspend fun importDataAndSync(inputText: String) {
        Log.d(TAG, "Starting user data population...")
        val currentUserId = getCurrentUid()
            ?: throw IllegalStateException("User not authenticated")

        val userMenuRef = db.collection("users").document(currentUserId).collection("menu_buttons")
        val userItemRef = db.collection("users").document(currentUserId).collection("item_buttons")

        // Get existing menus to check for duplicates
        val existingMenusSnapshot = userMenuRef.get().await()
        val existingMenusMap = mutableMapOf<String, String>()   // name -> menuId

        existingMenusSnapshot.documents.forEach { doc ->
            val menuName = doc.getString("name")
            if (menuName != null) {
                existingMenusMap[menuName] = doc.id
                Log.d(TAG, "Found existing menu: '$menuName' with id: ${doc.id}")
            }
        }

        val menuBlocks = inputText.split("\n\n")

        for (block in menuBlocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            val menuName = lines.first()
            val itemLines = lines.drop(1)

            // Check if menu already exists
            val menuId = if (existingMenusMap.containsKey(menuName)) {
                val existingMenuId = existingMenusMap[menuName]!!
                Log.d(TAG, "Using existing menu '$menuName' (ID = $existingMenuId)")
                existingMenuId
            } else {
                // Create new menu
                val newMenuDoc = userMenuRef.add(mapOf("name" to menuName)).await()
                val newMenuId = newMenuDoc.id
                existingMenusMap[menuName] = newMenuId // Add to our tracking map
                Log.d(TAG, "Created new menu '$menuName' (ID=$newMenuId)")
                newMenuId
            }


            for (itemLine in itemLines) {
                val (itemName, years, months, days, hours) = parseItemAndDuration(itemLine)
                if (itemName.isNotBlank()) {
                    val itemData = mapOf(
                        "name" to itemName,
                        "menuId" to menuId,
                        "expDurationYears" to years,
                        "expDurationMonths" to months,
                        "expDurationDays" to days,
                        "expDurationHours" to hours,
                    )
                    userItemRef.add(itemData).await()
                    Log.d(TAG, "Added item '$itemName' to menu '$menuName'")
                }
            }

        }
        Log.d(TAG, "User data population finished. Starting force sync.")
        // After successfully importing to Firestore, force sync to update the local DB
        forceSync()
    }

    // --- NEW TEMPLATE SYNC PLAN ---

    // Helper to get all template menus
    private suspend fun getTemplateMenus(): List<MenuButton> {
        return try {
            db.collection("templates").document("default").collection("menu_buttons")
                .whereEqualTo("isDeleted", false)
                .get().await().documents.mapNotNull {doc ->
                    val entity = doc.toObject(MenuButtonEntity::class.java)?.copy(id = doc.id)
                    entity?.toModel()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch template menus", e)
            emptyList()
        }
    }

    // Helper to get all template items
    private suspend fun getTemplateItems(): List<ItemButton> {
        return try {
            db.collection("templates").document("default").collection("item_buttons")
                .whereEqualTo("isDeleted", false)
                .get().await().documents.mapNotNull { doc ->
                    val entity = doc.toObject(ItemButtonEntity::class.java)?.copy(id = doc.id)
                    entity?.toModel()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch template items", e)
            emptyList()
        }
    }

    // Helper to check for expiry differences
    private fun isExpiryDifferent(userItem: ItemButtonEntity, templateItem: ItemButton): Boolean {
        return (userItem.expDurationYears ?: 0) != (templateItem.expDurationYears ?: 0) || // Compare non-null
                (userItem.expDurationMonths ?: 0) != (templateItem.expDurationMonths ?: 0) ||
                (userItem.expDurationDays ?: 0) != (templateItem.expDurationDays ?: 0) ||
                (userItem.expDurationHours ?: 0) != (templateItem.expDurationHours ?: 0)
    }

    /**
     * Compares the user's local database against the Firestore template
     * and generates a list of suggested changes.
     */
    suspend fun generateTemplateSyncPlan(): List<SuggestedChange> {
        val changes = mutableListOf<SuggestedChange>()
        val currentUid = getCurrentUid() ?: return emptyList()

        // 1. Fetch all data
        val tplMenus = getTemplateMenus()
        val tplItems = getTemplateItems()
        val userMenus = menuDao.getAllMenuButtonsSync(currentUid)
        val userItems = itemDao.getAllItemButtonsSync(currentUid) // This DAO method is now added

        // 2. Create lookups for quick matching
        // ... (rest of the lookups) ...
        val tplItemMapByTplMenuId = tplItems.groupBy { it.menuId }
        val userMenuMapByNum = userMenus.filter { it.number != null }.associateBy { it.number!! }
        val userMenuMapByName = userMenus.associateBy { it.name.trim().lowercase(russianLocale) }
        val userItemMapByMenuId = userItems.groupBy { it.menuId }
        val processedUserMenuIds = mutableSetOf<String>()
        val processedUserItemIds = mutableSetOf<String>()

        // --- Phase 1: Iterate Template Menus (Find ADDs and UPDATEs) ---
        for (tplMenu in tplMenus) {
            val tplKey = tplMenu.name.lowercase(russianLocale)
            Log.d(TAG, "Looking for Tpl Key: '[${tplKey}]'")

            val userMatch = (tplMenu.number?.let { userMenuMapByNum[it] })
                ?: userMenuMapByName[tplMenu.name.trim().lowercase(russianLocale)]

            if (userMatch != null) {
                Log.d(TAG, "  -> MATCH FOUND for '[${tplKey}]'. User ID: ${userMatch.id}")
                processedUserMenuIds.add(userMatch.id)

                if (userMatch.number != tplMenu.number || userMatch.name != tplMenu.name) {
                    changes.add(
                        SuggestedChange(
                            type = ChangeType.UPDATE,
                            entityType = "Menu",
                            userEntity = userMatch,
                            templateEntity = tplMenu,
                            // --- 💡 TRANSLATED ---
                            reason = "Свойства меню (Имя/Номер) не совпадают с шаблоном."
                        )
                    )
                }

                // --- Now process items for this matched menu ---
                val tplItemsForMenu = tplItemMapByTplMenuId[tplMenu.id] ?: emptyList()
                val userItemsForMenu = userItemMapByMenuId[userMatch.id] ?: emptyList()
                val userItemMapByName = userItemsForMenu.associateBy { it.name.trim() .lowercase(russianLocale) }
                val userItemMapByNum = userItemsForMenu.filter { it.number != null }.associateBy { it.number!! }

                for (tplItem in tplItemsForMenu) {
                    val userItemMatch = (tplItem.number?.let { userItemMapByNum[it] })
                        ?: userItemMapByName[tplItem.name.trim().lowercase(russianLocale)]

                    if (userItemMatch != null) {
                        processedUserItemIds.add(userItemMatch.id)

                        val expiryDiff = isExpiryDifferent(userItemMatch, tplItem)
                        if (expiryDiff || userItemMatch.number != tplItem.number || userItemMatch.name != tplItem.name) {
                            // --- 💡 TRANSLATED (with logic) ---
                            val reason = if (expiryDiff) "Срок годности товара не совпадает."
                            else "Свойства товара (Имя/Номер) не совпадают."
                            changes.add(
                                SuggestedChange(
                                    type = ChangeType.UPDATE,
                                    entityType = "Item",
                                    userEntity = userItemMatch,
                                    templateEntity = tplItem,
                                    reason = reason
                                )
                            )
                        }
                    } else {
                        // --- No matching user item found ---
                        changes.add(
                            SuggestedChange(
                                type = ChangeType.ADD,
                                entityType = "Item",
                                userEntity = null,
                                templateEntity = tplItem,
                                // --- 💡 TRANSLATED ---
                                reason = "Новый товар из шаблона."
                            )
                        )
                    }
                } // end tplItem loop

            } else {
                // --- No matching user menu found ---
                Log.w(TAG, "  -> MATCH NOT FOUND for '[${tplKey}]'. Generating ADD/DELETE.")
                changes.add(
                    SuggestedChange(
                        type = ChangeType.ADD,
                        entityType = "Menu",
                        userEntity = null,
                        templateEntity = tplMenu,
                        // --- 💡 TRANSLATED ---
                        reason = "Новое меню из шаблона."
                    )
                )

                // Also ADD all its items
                val tplItemsForMenu = tplItemMapByTplMenuId[tplMenu.id] ?: emptyList()
                for (tplItem in tplItemsForMenu) {
                    changes.add(
                        SuggestedChange(
                            type = ChangeType.ADD,
                            entityType = "Item",
                            userEntity = null,
                            templateEntity = tplItem,
                            // --- 💡 TRANSLATED ---
                            reason = "Новый товар (в новом меню)."
                        )
                    )
                }
            }
        } // end tplMenu loop

        // --- Phase 2: Iterate User Menus (Find DELETEs) ---
        for (userMenu in userMenus) {
            if (userMenu.id !in processedUserMenuIds) {
                changes.add(
                    SuggestedChange(
                        type = ChangeType.DELETE,
                        entityType = "Menu",
                        userEntity = userMenu,
                        templateEntity = null,
                        // --- 💡 TRANSLATED ---
                        reason = "Меню не найдено в шаблоне (будет удалено)."
                    )
                )
                val userItemsForMenu = userItemMapByMenuId[userMenu.id] ?: emptyList()
                processedUserItemIds.addAll(userItemsForMenu.map { it.id })
            }
        }

        // --- Phase 3: Iterate User Items (Find DELETEs) ---
        for (userItem in userItems) {
            if (userItem.id !in processedUserItemIds) {
                changes.add(
                    SuggestedChange(
                        type = ChangeType.DELETE,
                        entityType = "Item",
                        userEntity = userItem,
                        templateEntity = null,
                        // --- 💡 TRANSLATED ---
                        reason = "Товар не найден в шаблоне (будет удален)."
                    )
                )
            }
        }

        Log.d(TAG, "Generated ${changes.size} suggested template changes.")
        return changes
    }


    /**
     * Applies a list of suggested template changes, handling ID mapping
     * for new and existing menus to prevent foreign key violations.
     */
    suspend fun applyTemplateSyncChanges(changes: List<SuggestedChange>): Int {
        val currentUid = getCurrentUid() ?: throw Exception("User not authenticated")
        var successCount = 0

        // This map is CRITICAL.
        // It will map <Template Menu ID, Local Menu ID>
        val menuIdMap = mutableMapOf<String, String>()

        // 1. Pre-populate the map with all *existing* user menus that
        //    match the template. This finds existing mappings.
        val tplMenus = getTemplateMenus()
        val userMenus = menuDao.getAllMenuButtonsSync(currentUid)
        val userMenuMapByNum = userMenus.filter { it.number != null }.associateBy { it.number!! }
        val userMenuMapByName = userMenus.associateBy { it.name.trim().lowercase(russianLocale) }

        for (tplMenu in tplMenus) {
            val userMatch = (tplMenu.number?.let { userMenuMapByNum[it] })
                ?: userMenuMapByName[tplMenu.name.trim().lowercase(russianLocale)]

            if (userMatch != null && tplMenu.id != null) {
                // We found a match! Store its ID mapping.
                menuIdMap[tplMenu.id!!] = userMatch.id
            }
        }
        Log.d(TAG, "applyChanges: Pre-populated menu map with ${menuIdMap.size} existing matches.")

        // 2. Now, iterate the changes.
        //    We must process ADD Menu changes FIRST to get their new local IDs.
        val addMenuChanges = changes.filter { it.type == ChangeType.ADD && it.entityType == "Menu" }
        val otherChanges = changes.filterNot { it.type == ChangeType.ADD && it.entityType == "Menu" }

        // --- Process ADD Menu ---
        for (change in addMenuChanges) {
            val tplMenu = change.templateEntity as MenuButton
            try {
                // addMenuButton creates a new local entity with a new UUID
                val newMenu = addMenuButton(tplMenu.name, tplMenu.number)
                if (tplMenu.id != null && newMenu.id != null) {
                    // Add the *newly created* menu to our map
                    menuIdMap[tplMenu.id!!] = newMenu.id
                    Log.d(TAG, "applyChanges: Added new menu ${newMenu.name}, mapped ${tplMenu.id} -> ${newMenu.id}")
                }
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply ADD Menu change for ${tplMenu.name}", e)
                // Continue or throw? Let's throw to stop the process
                throw e
            }
        }

        // --- Process all OTHER changes ---
        for (change in otherChanges) {
            try {
                when (change.type) {
                    ChangeType.ADD -> {
                        // This can only be "Item" changes now
                        if (change.templateEntity !is ItemButton) continue

                        val tplItem = change.templateEntity as ItemButton

                        // *** THE FIX ***
                        // Find the LOCAL menu ID using our map
                        val localMenuId = menuIdMap[tplItem.menuId]

                        if (localMenuId == null) {
                            Log.e(TAG, "applyChanges: CRITICAL: Could not find local menu ID for template menu ID ${tplItem.menuId} when adding item ${tplItem.name}. Skipping.")
                            continue // Skip this change
                        }

                        // Call addItemButton with the CORRECT localMenuId
                        addItemButton(
                            name = tplItem.name,
                            menuId = localMenuId, // <-- Use the correct local ID
                            expDurationYears = tplItem.expDurationYears,
                            expDurationMonths = tplItem.expDurationMonths,
                            expDurationDays = tplItem.expDurationDays,
                            expDurationHours = tplItem.expDurationHours,
                            description = tplItem.description,
                            number = tplItem.number
                        )
                    }
                    ChangeType.UPDATE -> {
                        when (val userEntity = change.userEntity) {
                            is MenuButtonEntity -> {
                                val tplMenu = change.templateEntity as MenuButton
                                val updatedMenu = userEntity.toModel().copy(
                                    name = tplMenu.name,
                                    number = tplMenu.number
                                )
                                updateMenuButton(updatedMenu)
                            }
                            is ItemButtonEntity -> {
                                val tplItem = change.templateEntity as ItemButton
                                val updatedItem = userEntity.toModel().copy(
                                    name = tplItem.name,
                                    number = tplItem.number,
                                    expDurationYears = tplItem.expDurationYears,
                                    expDurationMonths = tplItem.expDurationMonths,
                                    expDurationDays = tplItem.expDurationDays,
                                    expDurationHours = tplItem.expDurationHours,
                                    description = tplItem.description
                                )
                                // The menuId of an *existing* item doesn't change
                                // so this is safe.
                                updateItemButton(updatedItem)
                            }
                        }
                    }
                    ChangeType.DELETE -> {
                        when (val userEntity = change.userEntity) {
                            is MenuButtonEntity -> deleteMenuButton(userEntity.toModel())
                            is ItemButtonEntity -> deleteItemButton(userEntity.toModel())
                        }
                    }
                }
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply change: $change", e)
                // If one change fails (e.g., UNIQUE constraint on item number),
                // the whole batch should stop.
                throw e
            }
        }

        return successCount
    }

}