// MenuButtonEntity.kt  
package com.example.firebaselabelapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "menu_buttons")
data class MenuButtonEntity(
    @PrimaryKey val id: String = "",
    val number: String? = null, // ADDED: number field
    val name: String = "",
    val userId: String = "",
    val isDeleted: Boolean = false,
    val needsUpload: Boolean = true,
    val lastModified: Date = Date()
) {
    // No-argument constructor required for Firestore deserialization
    constructor() : this("", null, "", "", false, true, Date())
}