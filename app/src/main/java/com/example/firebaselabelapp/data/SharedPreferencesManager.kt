package com.example.firebaselabelapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, value) }

    // In SharedPreferencesManager.kt

    fun saveSyncedMenuIds(ids: Set<String>) {
        prefs.edit { putStringSet("synced_menu_ids", ids) }
    }

    fun getSyncedMenuIds(): Set<String> {
        return prefs.getStringSet("synced_menu_ids", emptySet()) ?: emptySet()
    }
}